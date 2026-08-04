package com.shauiqiu.eklose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                SettingsScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var saveLogin by rememberSaveable { mutableStateOf(EkwingLoginStore.shouldSaveLogin(context)) }
    var basicApi by rememberSaveable { mutableStateOf(EkwingLoginStore.useBasicApi(context)) }
    var allPages by rememberSaveable { mutableStateOf(EkwingLoginStore.loadAllPages(context)) }
    var floatingNavigationBar by rememberSaveable {
        mutableStateOf(EkwingLoginStore.useFloatingNavigationBar(context))
    }
    val initialAlias = rememberSaveable { EkwingLoginStore.loadRealNameAlias(context) }
    var aliasFrom by rememberSaveable { mutableStateOf(initialAlias.first) }
    var aliasTo by rememberSaveable { mutableStateOf(initialAlias.second) }
    var draftAliasFrom by rememberSaveable { mutableStateOf(aliasFrom) }
    var draftAliasTo by rememberSaveable { mutableStateOf(aliasTo) }
    var showRealNameAliasDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            EkloseTopBar(onBack = onBack)
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding),
            color = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    PageLead(
                        title = "设置",
                        summary = "管理登录缓存和任务列表",
                    )
                }
                item {
                    GroupSection(title = "账号与列表") {
                        GroupItem(
                            title = "保存登录状态",
                            summary = "保存 uid/token、登录信息和密码",
                            trailing = {
                                Switch(
                                    checked = saveLogin,
                                    onCheckedChange = {
                                        saveLogin = it
                                        EkwingLoginStore.setSaveLogin(context, it)
                                    },
                                )
                            },
                        )
                        GroupDivider()
                        GroupItem(
                            title = "实名登录姓名替换",
                            summary = if (aliasFrom.isBlank() || aliasTo.isBlank()) {
                                "未设置"
                            } else {
                                "$aliasFrom -> $aliasTo"
                            },
                            onClick = {
                                draftAliasFrom = aliasFrom
                                draftAliasTo = aliasTo
                                showRealNameAliasDialog = true
                            },
                        )
                        GroupDivider()
                        GroupItem(
                            title = "Basic 接口",
                            summary = "优先使用 Basic 账号对应的列表、详情和成绩接口",
                            trailing = {
                                Switch(
                                    checked = basicApi,
                                    onCheckedChange = {
                                        basicApi = it
                                        EkwingLoginStore.setUseBasicApi(context, it)
                                    },
                                )
                            },
                        )
                        GroupDivider()
                        GroupItem(
                            title = "历史考试和常规作业全分页",
                            summary = "读取历史考试和常规作业的所有分页",
                            trailing = {
                                Switch(
                                    checked = allPages,
                                    onCheckedChange = {
                                        allPages = it
                                        EkwingLoginStore.setLoadAllPages(context, it)
                                    },
                                )
                            },
                        )
                    }
                }
                item {
                    GroupSection(title = "界面") {
                        GroupItem(
                            title = "悬浮导航栏",
                            summary = "将底部导航栏显示为悬浮样式",
                            trailing = {
                                Switch(
                                    checked = floatingNavigationBar,
                                    onCheckedChange = {
                                        floatingNavigationBar = it
                                        EkwingLoginStore.setUseFloatingNavigationBar(context, it)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        RealNameAliasDialog(
            show = showRealNameAliasDialog,
            fromName = draftAliasFrom,
            toName = draftAliasTo,
            onFromNameChange = { draftAliasFrom = it },
            onToNameChange = { draftAliasTo = it },
            onDismiss = { showRealNameAliasDialog = false },
            onConfirm = {
                aliasFrom = draftAliasFrom.trim()
                aliasTo = draftAliasTo.trim()
                EkwingLoginStore.setRealNameAlias(context, aliasFrom, aliasTo)
                showRealNameAliasDialog = false
            },
        )
    }
}

@Composable
private fun RealNameAliasDialog(
    show: Boolean,
    fromName: String,
    toName: String,
    onFromNameChange: (String) -> Unit,
    onToNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "实名登录姓名替换",
        summary = "登录页输入匹配姓名时，实际请求会使用替换后的姓名。",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = fromName,
                onValueChange = onFromNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = "输入姓名",
                singleLine = true,
            )
            TextField(
                value = toName,
                onValueChange = onToNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = "实际登录姓名",
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(20.dp))
                TextButton(
                    text = "保存",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.primary,
                        textColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}
