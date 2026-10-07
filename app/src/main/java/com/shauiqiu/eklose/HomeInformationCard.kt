package com.shauiqiu.eklose

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Keep this in sync with the single package declared in AndroidManifest.xml <queries>.
private const val EKWING_STUDENT_PACKAGE = "com.ekwing.students"

private data class HomeVersionInfo(
    val appVersion: String = "读取中…",
    val ekwingVersion: String = "读取中…",
)

@Composable
internal fun HomeInformationCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var versions by remember { mutableStateOf(HomeVersionInfo()) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    val deviceModel = remember {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        when {
            model.isBlank() -> "未知"
            manufacturer.isBlank() || model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }
    val androidVersion = remember {
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshRequest++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(context, refreshRequest) {
        versions = withContext(Dispatchers.IO) {
            HomeVersionInfo(
                appVersion = readInstalledVersion(context, context.packageName),
                ekwingVersion = readInstalledVersion(context, EKWING_STUDENT_PACKAGE),
            )
        }
    }

    GroupSection(title = "信息") {
        GroupItem(
            title = "软件版本",
            summary = versions.appVersion,
            showArrow = false,
        )
        GroupItem(
            title = "手机型号",
            summary = deviceModel,
            showArrow = false,
        )
        GroupItem(
            title = "安卓版本",
            summary = androidVersion,
            showArrow = false,
        )
        GroupItem(
            title = "翼课网版本",
            summary = versions.ekwingVersion,
            showArrow = false,
        )
    }
}

private fun readInstalledVersion(context: Context, packageName: String): String {
    return try {
        val info = context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        val name = info.versionName?.takeIf { it.isNotBlank() } ?: "未知版本"
        "$name (${info.longVersionCode})"
    } catch (_: PackageManager.NameNotFoundException) {
        if (packageName == EKWING_STUDENT_PACKAGE) "未安装翼课学生" else "未知"
    } catch (_: SecurityException) {
        "无法读取版本"
    }
}
