package com.shauiqiu.eklose

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * libekwing_resolver 的进程级引导：授权配置只做一次，参数以后端下发的为准。
 *
 * 本代 eklose 不接入 lastudio 账号体系，鉴权用 so 内置的开关式授权
 * （/client-auth/features/authorize-public）：后台只有功能开关，没有账号。
 * 配置来源是远程配置里的 signingPublicKey（换钥无需发版）；静态兜底源没有
 * 该字段时传空串，so 退回内置生产公钥。
 */
object EkwingResolverBootstrap {
    private const val TAG = "EkwingResolver"
    private const val BASE_URL = "https://api.lastudio.cc"
    private const val SOFTWARE_KEY = "eklose"
    private const val FEATURE_KEY = "answer_parser"

    @Volatile
    private var configured = false

    /** 是否已完成配置（幂等，重复调用只生效一次；换钥场景重启进程生效）。 */
    val isConfigured: Boolean
        get() = configured

    /**
     * 配置并启用内部授权。必须在 Dispatchers.IO 上调用（JNI 阻塞 + 首次加载 so）。
     * signingPublicKey 传空串时使用 so 内置公钥。
     */
    suspend fun configure(signingPublicKey: String, appVersionCode: Long) {
        if (configured) return
        withContext(Dispatchers.IO) {
            synchronized(this@EkwingResolverBootstrap) {
                if (configured) return@withContext
                val rc = runCatching {
                    EkwingResolverNative.setAuthConfig(
                        baseUrl = BASE_URL,
                        softwareKey = SOFTWARE_KEY,
                        featureKey = FEATURE_KEY,
                        publicKeyB64 = signingPublicKey,
                        appVersionCode = appVersionCode,
                    )
                }.getOrElse { error ->
                    Log.w(TAG, "解析器 so 初始化失败: ${error.javaClass.simpleName}: ${error.message}")
                    return@withContext
                }
                if (rc != 0) {
                    Log.w(TAG, "解析器授权配置被拒绝: rc=$rc")
                    return@withContext
                }
                EkwingResolverNative.setAuthEnabled(true)
                configured = true
                Log.i(TAG, "解析器授权已启用")
            }
        }
    }

    /**
     * 主动执行一次授权（诊断/预热 grant 缓存）。返回错误码，null 表示授权通过。
     * 仅在已 configure 后调用；失败只上报不抛出，不阻塞任何 UI。
     */
    suspend fun probe(): String? {
        if (!configured) return "auth_not_configured"
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = EkwingResolverNative.authorizeFeature()
                val json = org.json.JSONObject(response)
                if (json.optBoolean("allowed", false) && json.has("grant")) {
                    null
                } else {
                    json.optJSONObject("error")?.optString("code") ?: "unknown"
                }
            }.getOrElse { error ->
                Log.w(TAG, "解析器授权探测失败: ${error.message}")
                "network_error"
            }
        }
    }
}
