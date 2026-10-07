package com.shauiqiu.eklose

/**
 * libekwing_resolver.so 的 JNI 绑定（无账号开关式授权版）。
 *
 * 对应 Rust 侧 ekwing_resolver `src/jni_bridge.rs` 的 JNI_OnLoad 动态注册，
 * 方法名/签名不可改（R8 keep 规则见 proguard-rules.pro）。
 * so 来源版本见 jniLibs/arm64-v8a/RESOLVER_VERSION.txt。
 *
 * 所有调用都是阻塞的，必须从 Dispatchers.IO 调用。
 * 新解析能力通过 resolveRequest 的 op 交付，不需要改这里的声明。
 */
object EkwingResolverNative {

    init {
        System.loadLibrary("ekwing_resolver")
    }

    /**
     * 配置内部授权（base_url / software_key / feature_key / 服务器公钥 / version_code）。
     * public_key_b64 传空串保留 so 内置生产公钥；正常应传后端
     * /software/eklose/config 下发的 signingPublicKey（换钥无需发版）。
     * 返回 0 成功，非 0 参数错误。
     */
    external fun setAuthConfig(
        baseUrl: String,
        softwareKey: String,
        featureKey: String,
        publicKeyB64: String,
        appVersionCode: Long,
    ): Int

    /** 启用/关闭内部授权。关闭时解析类 op 一律拒绝。 */
    external fun setAuthEnabled(enabled: Boolean)

    external fun isAuthEnabled(): Boolean

    /**
     * 仅执行一次授权（联调/诊断/预热）。
     * 返回授权 JSON（含 allowed/grant/decision_id）或错误 JSON
     * （{"ok":false,"error":{"code","message"}}）。
     */
    external fun authorizeFeature(): String

    /**
     * 跨平台固定接口（唯一入口）：`{"version":1,"op":"...","payload":{...}}` -> 响应信封。
     * 成功：{"ok":true,"version":1,"op":"...","result":...,"meta":{...}}
     * 失败：{"ok":false,...,"error":{"code","message","hint"}}
     * 可用 op 见 [capabilities]。
     */
    external fun resolveRequest(requestJson: String): String

    /** 能力清单：ops（含是否需要授权）、授权错误码。 */
    external fun capabilities(): String
}
