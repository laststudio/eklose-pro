package com.shauiqiu.eklose

import android.app.Application
import android.os.Handler
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EkloseApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        appContext = this
        AppIntegrityGuard.ensureIntegrity(this)
        // Expire non-persistent sessions once per process start, not on Activity recreation.
        if (!EkwingLoginStore.shouldSaveLogin(this)) {
            EkwingLoginStore.clearSession(this)
        }
        checkRemoteConfig()
    }

    private fun checkRemoteConfig() {
        applicationScope.launch {
            try {
                val status = EkloseRemoteConfigManager.checkStatus(this@EkloseApplication)
                remoteStatus = status
                _remoteStatusFlow.value = status

                when {
                    status.isKillSwitch -> {
                        Log.w(TAG, "KillSwitch activated")
                        Toast.makeText(this@EkloseApplication, "程序异常", Toast.LENGTH_LONG).show()
                        Handler(mainLooper).postDelayed({
                            android.os.Process.killProcess(android.os.Process.myPid())
                            System.exit(0)
                        }, 2000)
                    }
                    status.requiresVerification -> {
                        updateStatus = status
                        _updateStatusFlow.value = status
                        Log.i(TAG, "需要验证码")
                    }
                    status.showUpdateDialog -> {
                        updateStatus = status
                        _updateStatusFlow.value = status
                        Log.i(TAG, "发现新版本: ${status.message}")
                    }
                    status.noticeMessage.isNotEmpty() -> {
                        Log.i(TAG, "发布公告提示: ${status.noticeMessage}")
                        _applicationNoticeFlow.tryEmit(status.noticeMessage)
                    }
                }
            } catch (error: EkloseRemoteConfigManager.NetworkException) {
                Log.e(TAG, "远程配置获取失败: ${error.message}", error)
                Toast.makeText(this@EkloseApplication, "网络异常", Toast.LENGTH_SHORT).show()
                Handler(mainLooper).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }, 1000)
            }
        }
    }

    companion object {
        private const val TAG = "EkloseApplication"

        lateinit var appContext: EkloseApplication
            private set

        @Volatile
        var updateStatus: EkloseRemoteStatus? = null
            private set

        @Volatile
        var remoteStatus: EkloseRemoteStatus? = null
            private set

        private val _updateStatusFlow = MutableStateFlow<EkloseRemoteStatus?>(null)
        val updateStatusFlow: StateFlow<EkloseRemoteStatus?> = _updateStatusFlow.asStateFlow()

        private val _remoteStatusFlow = MutableStateFlow<EkloseRemoteStatus?>(null)
        val remoteStatusFlow: StateFlow<EkloseRemoteStatus?> = _remoteStatusFlow.asStateFlow()

        private val _applicationNoticeFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
        val applicationNoticeFlow = _applicationNoticeFlow.asSharedFlow()
    }
}
