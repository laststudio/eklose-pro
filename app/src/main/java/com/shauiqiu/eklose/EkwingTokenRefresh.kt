package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal class EkwingTokenExpiredException(message: String) : RuntimeException(message)

// Do not infer expired credentials from arbitrary API errors or permission failures.
// Unknown numeric business codes are intentionally not guessed.
internal fun ekwingRequestError(message: String, httpStatus: Int? = null): RuntimeException {
    val text = message.lowercase().replace("登陆", "登录")
    val invalidCredential = listOf("expired", "invalid", "过期", "失效", "无效", "超时", "校验失败", "验证失败")
        .any { it in text }
    val expired = httpStatus == 401 ||
        (listOf("token", "session", "登录凭证", "登录状态", "登录信息").any { it in text } && invalidCredential) ||
        listOf("未登录", "请先登录", "请登录", "重新登录", "登录失效", "登录过期", "登录已过期", "登录超时")
            .any { it in text } ||
        listOf("not logged in", "login required", "login expired", "session expired")
            .any { it in text }
    return if (expired) EkwingTokenExpiredException(message) else RuntimeException(message)
}

// Optional question/score APIs may still fail independently. Authentication failures
// must reach the common refresh gate instead of being cached as empty/error answers.
internal inline fun <T> ekwingReadAttempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Throwable) {
    if (error is EkwingTokenExpiredException || error is CancellationException) throw error
    Result.failure(error)
}

internal inline fun <R, T : R> Result<T>.recoverReadFailure(transform: (Throwable) -> R): Result<R> {
    val error = exceptionOrNull() ?: return this
    return ekwingReadAttempt { transform(error) }
}

internal class EkwingTokenRefreshRequest(val canRefresh: Boolean) {
    val response = CompletableDeferred<Boolean>()
}

internal object EkwingTokenRefreshManager {
    private val refreshMutex = Mutex()
    private val pendingRequest = MutableStateFlow<EkwingTokenRefreshRequest?>(null)
    val request = pendingRequest.asStateFlow()

    // Fan out a declined/failed attempt to the same batch of concurrent reads.
    // A new explicit read permits the user to try again.
    @Volatile
    private var failedRefresh: Pair<EkwingLoginSession, Throwable>? = null

    fun beginUserRead() {
        failedRefresh = null
    }

    suspend fun <T> withTokenRefresh(context: Context, read: () -> T): T {
        val appContext = context.applicationContext
        val attemptedSession = EkwingLoginStore.loadSession(appContext)
        try {
            return withContext(Dispatchers.IO) { read() }
        } catch (expired: EkwingTokenExpiredException) {
            val session = attemptedSession ?: throw RuntimeException("请先登录翼课账号")
            refreshMutex.withLock {
                val latest = EkwingLoginStore.loadSession(appContext)
                    ?: throw RuntimeException("登录状态已清除，请重新登录")
                if (latest.uid != session.uid) {
                    throw RuntimeException("账号已变化，请重新读取")
                }
                // Another concurrent read already replaced the invalid token.
                if (latest.token == session.token) {
                    failedRefresh?.takeIf { it.first == session }?.let { throw it.second }
                    try {
                        if (!EkwingLoginStore.shouldAutoRefreshToken(appContext)) {
                            val request = EkwingTokenRefreshRequest(
                                canRefresh = hasSavedCredentials(appContext),
                            )
                            pendingRequest.value = request
                            val accepted = try {
                                request.response.await()
                            } finally {
                                if (pendingRequest.value === request) pendingRequest.value = null
                            }
                            if (!accepted) {
                                throw RuntimeException("登录凭证已失效，未刷新；请登录或重新读取")
                            }
                        }
                        withContext(Dispatchers.IO) { refreshSession(appContext, session) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        failedRefresh = session to error
                        throw error
                    }
                }
            }
            // Retry the interrupted read once only. A second expired response surfaces
            // normally rather than causing an infinite login/prompt loop.
            return withContext(Dispatchers.IO) { read() }
        }
    }

    private fun hasSavedCredentials(context: Context): Boolean {
        return EkwingLoginStore.shouldSaveLogin(context) &&
            EkwingReloginPlanner.plan(EkwingLoginStore.loadIdentity(context)) !is EkwingReloginPlan.None
    }

    private fun refreshSession(context: Context, expectedSession: EkwingLoginSession) {
        if (EkwingLoginStore.loadSession(context) != expectedSession) {
            throw RuntimeException("登录状态已变化，请重新读取")
        }
        if (!hasSavedCredentials(context)) {
            throw RuntimeException("未保存完整的账号密码，请重新登录后再读取")
        }
        val identity = EkwingLoginStore.loadIdentity(context)
        val result = when (val plan = EkwingReloginPlanner.plan(identity)) {
            is EkwingReloginPlan.Account -> EkwingAuthClient(context).loginByAccount(plan.username, plan.password)
            is EkwingReloginPlan.RealName -> EkwingAuthClient(context).loginByRealName(
                name = EkwingLoginStore.resolveRealName(context, plan.name),
                password = plan.password,
                schoolName = plan.schoolName,
                schoolId = plan.schoolId,
            )
            is EkwingReloginPlan.None -> throw RuntimeException("未保存完整的账号密码，请重新登录后再读取")
        }
        if (result.uid != expectedSession.uid) {
            throw RuntimeException("刷新账号与当前账号不一致，请手动重新登录")
        }
        // Never restore an old account after the user logs out or changes accounts.
        if (EkwingLoginStore.loadSession(context) != expectedSession) {
            throw RuntimeException("登录状态已变化，请重新读取")
        }
        EkwingLoginStore.saveSession(
            context = context,
            result = result,
            loginMethod = identity.loginMethod.orEmpty(),
            identity = identity,
        )
    }
}

@Composable
internal fun EkwingTokenRefreshDialog() {
    val context = LocalContext.current
    val request by EkwingTokenRefreshManager.request.collectAsState()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val currentRequest = request
    OverlayDialog(
        show = currentRequest != null && lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
        title = "登录凭证已失效",
        summary = if (currentRequest?.canRefresh == true) {
            "是否使用已保存的账号密码刷新 token，并继续本次读取？"
        } else {
            "未保存完整的账号密码，无法刷新 token。请重新登录后再读取。"
        },
        onDismissRequest = { currentRequest?.response?.complete(false) },
    ) {
        if (currentRequest != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = { currentRequest.response.complete(false) },
                )
                TextButton(
                    text = if (currentRequest.canRefresh) "刷新" else "前往登录",
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.primary,
                        textColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                    onClick = {
                        if (currentRequest.canRefresh) {
                            currentRequest.response.complete(true)
                        } else {
                            currentRequest.response.complete(false)
                            context.startActivity(Intent(context, LoginActivity::class.java))
                        }
                    },
                )
            }
        }
    }
}
