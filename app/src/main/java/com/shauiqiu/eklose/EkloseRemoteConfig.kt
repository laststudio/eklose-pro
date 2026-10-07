package com.shauiqiu.eklose

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import java.net.URLConnection

data class EkloseRemoteConfig(
    val latestVersionCode: Long,
    val updateUrl: String,
    val isKillSwitchOn: Boolean,
    val isForce: Boolean,
    val updateMessage: String,
    val noticeMessage: String,
    val announcementTitle: String,
    val announcementMessage: String,
    val announcementUpdatedAt: String,
    val announcementUrl: String,
    val changelogTitle: String,
    val changelogSummary: String,
    val changelogUrl: String,
    val verificationCode: String = "",
    val verificationTitle: String = "",
    val verificationMessage: String = "",
    val signingPublicKey: String = "",
    val signingKeyId: String = "",
)

data class EkloseRemoteStatus(
    val isKillSwitch: Boolean,
    val showUpdateDialog: Boolean,
    val message: String,
    val isForce: Boolean,
    val updateUrl: String,
    val noticeMessage: String,
    val announcementTitle: String,
    val announcementMessage: String,
    val announcementUpdatedAt: String,
    val announcementUrl: String,
    val changelogTitle: String,
    val changelogSummary: String,
    val changelogUrl: String,
    val requiresVerification: Boolean = false,
    val verificationCode: String = "",
    val verificationTitle: String = "",
    val verificationMessage: String = "",
)

object EkloseRemoteConfigManager {
    private const val TAG = "EkloseRemoteConfig"
    private const val TIMEOUT_MS = 5000L
    private const val CONFIG_FILE = "eklose_config.json"
    private const val BACKEND_CONFIG_URL = "https://api.lastudio.cc/software/eklose/config"

    /** 当前端版本号，供后端按版本下发差异化配置（per-release 弹窗/功能开关）。 */
    @Volatile
    private var currentVersionCodeForQuery: Long = 0L

    private val CONFIG_URLS = listOf(
        BACKEND_CONFIG_URL,
        "https://raw.giteeusercontent.com/qiuqiqiuqid/fe_config/raw/master/$CONFIG_FILE",
        "https://raw.githubusercontent.com/laststudio/Fe_config/main/$CONFIG_FILE",
    )
    private const val DEFAULT_CHANGELOG_URL =
        "https://api.lastudio.cc/software/eklose/changelog"

    class NetworkException(message: String) : Exception(message)

    var cachedStatus: EkloseRemoteStatus? = null
        private set

    suspend fun checkStatus(context: Context): EkloseRemoteStatus {
        val currentVersion = context.currentVersionCode()
        currentVersionCodeForQuery = currentVersion
        val config = fetchConfig()
        // 解析器 so 的开关式授权引导：公钥以后端下发为准（换钥无需发版）
        EkwingResolverBootstrap.configure(config.signingPublicKey, currentVersion)
        val status = when {
            config.isKillSwitchOn -> config.toStatus(
                isKillSwitch = true,
                showUpdateDialog = false,
                message = "",
                updateUrl = "",
            )
            config.verificationCode.isNotBlank() &&
                EkwingLoginStore.getLocalVerificationCode(context) != config.verificationCode -> {
                config.toStatus(
                    isKillSwitch = false,
                    showUpdateDialog = false,
                    message = "",
                    updateUrl = "",
                ).copy(requiresVerification = true)
            }
            config.latestVersionCode > currentVersion -> config.toStatus(
                isKillSwitch = false,
                showUpdateDialog = true,
                message = config.updateMessage.ifBlank { "发现新版本 ${config.latestVersionCode}" },
                updateUrl = config.updateUrl,
            )
            else -> config.toStatus(
                isKillSwitch = false,
                showUpdateDialog = false,
                message = "",
                updateUrl = "",
            )
        }
        cachedStatus = status
        return status
    }

    private suspend fun fetchConfig(): EkloseRemoteConfig {
        return withContext(Dispatchers.IO) {
            val response = runCatching {
                fetchFirstAvailableConfig()
            }.getOrElse { error ->
                throw NetworkException(error.message ?: "未知网络错误")
            }
            val json = JSONObject(response)
            val noticeMessage = json.optString("noticeMessage", "")
            val announcementMessage = json.optString("announcementMessage", noticeMessage)
            val changelogSummary = json.optString("changelogSummary", "")
            val changelogUrl = json.optString("changelogUrl", DEFAULT_CHANGELOG_URL)

            EkloseRemoteConfig(
                latestVersionCode = json.optLong("latestVersionCode", 1L),
                updateUrl = json.optString("updateUrl", ""),
                isKillSwitchOn = json.optBoolean("isKillSwitchOn", false),
                isForce = json.optBoolean("isForce", false),
                updateMessage = json.optString("updateMessage", ""),
                noticeMessage = noticeMessage,
                announcementTitle = json.optString("announcementTitle", "").ifBlank {
                    if (announcementMessage.isNotBlank()) "公告" else ""
                },
                announcementMessage = announcementMessage,
                announcementUpdatedAt = json.optString("announcementUpdatedAt", ""),
                announcementUrl = json.optString("announcementUrl", ""),
                changelogTitle = json.optString("changelogTitle", "").ifBlank {
                    if (changelogSummary.isNotBlank() || changelogUrl.isNotBlank()) "更新日志" else ""
                },
                changelogSummary = changelogSummary,
                changelogUrl = changelogUrl,
                verificationCode = json.optString("verificationCode", ""),
                verificationTitle = json.optString("verificationTitle", ""),
                verificationMessage = json.optString("verificationMessage", ""),
                signingPublicKey = json.optString("signingPublicKey", ""),
                signingKeyId = json.optString("signingKeyId", ""),
            )
        }
    }

    private suspend fun fetchFirstAvailableConfig(): String {
        val errors = mutableListOf<String>()
        for (configUrl in CONFIG_URLS) {
            try {
                val requestUrl = if (configUrl == BACKEND_CONFIG_URL && currentVersionCodeForQuery > 0) {
                    "$configUrl?version_code=$currentVersionCodeForQuery"
                } else {
                    configUrl
                }
                Log.d(TAG, "尝试远程配置: $requestUrl")
                val response = withTimeoutOrNull(TIMEOUT_MS) {
                    val connection = URL(requestUrl).openConnection() as URLConnection
                    connection.connectTimeout = TIMEOUT_MS.toInt()
                    connection.readTimeout = TIMEOUT_MS.toInt()
                    connection.getInputStream().bufferedReader().use { it.readText() }
                }
                if (!response.isNullOrBlank()) return response
                errors += "$configUrl: empty or timeout"
            } catch (error: Exception) {
                errors += "$configUrl: ${error.javaClass.simpleName}: ${error.message}"
                Log.w(TAG, "配置源失败: $configUrl", error)
            }
        }
        throw NetworkException("所有远程配置源均失败: ${errors.joinToString(" | ")}")
    }

    private fun EkloseRemoteConfig.toStatus(
        isKillSwitch: Boolean,
        showUpdateDialog: Boolean,
        message: String,
        updateUrl: String,
    ): EkloseRemoteStatus {
        return EkloseRemoteStatus(
            isKillSwitch = isKillSwitch,
            showUpdateDialog = showUpdateDialog,
            message = message,
            isForce = isForce,
            updateUrl = updateUrl,
            noticeMessage = noticeMessage,
            announcementTitle = announcementTitle,
            announcementMessage = announcementMessage,
            announcementUpdatedAt = announcementUpdatedAt,
            announcementUrl = announcementUrl,
            changelogTitle = changelogTitle,
            changelogSummary = changelogSummary,
            changelogUrl = changelogUrl,
            verificationCode = verificationCode,
            verificationTitle = verificationTitle,
            verificationMessage = verificationMessage,
        )
    }

    private fun Context.currentVersionCode(): Long {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return packageInfo.longVersionCode
    }
}
