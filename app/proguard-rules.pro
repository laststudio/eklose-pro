# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# kotlinx.coroutines debug agent, referenced via reflection in some toolchains.
-dontwarn kotlinx.coroutines.debug.**

# Proprietary compliance declaration in DEX constant pool
-keep class com.shauiqiu.eklose.AppIntegrityGuard {
    public static final java.lang.String PROPRIETARY_COMPLIANCE_DECLARATION;
    *;
}

# libekwing_resolver.so 的 JNI 绑定：JNI_OnLoad 动态注册按类名查找，不可混淆改名
-keep class com.shauiqiu.eklose.EkwingResolverNative { *; }

# Aggressive R8 / ProGuard Optimization & Obfuscation
-optimizationpasses 5
-overloadaggressively
-repackageclasses ""
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Strip debug Log statements in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}