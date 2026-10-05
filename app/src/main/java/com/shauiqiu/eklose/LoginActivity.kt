package com.shauiqiu.eklose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import com.shauiqiu.eklose.ui.theme.LocalEkloseCardSurface
import com.shauiqiu.eklose.ui.theme.LocalEklosePageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                LoginScreen(
                    onBack = ::finish,
                    onLoginSuccess = ::returnToMain,
                )
            }
        }
    }

    private fun returnToMain() {
        startActivity(MainActivity.createHomeIntent(this))
        finish()
    }
}

private enum class LoginMode(val title: String) {
    Account("账号密码登录"),
    RealName("实名登录"),
}

private val LoginMode.methodValue: String
    get() = when (this) {
        LoginMode.Account -> "account"
        LoginMode.RealName -> "real-name"
    }

@Composable
private fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberEkloseBlurBackdrop()
    val client = remember { EkwingAuthClient(context) }
    val coroutineScope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(LoginMode.RealName) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var schoolKeyword by rememberSaveable { mutableStateOf("") }
    var schoolName by rememberSaveable { mutableStateOf("") }
    var schoolId by rememberSaveable { mutableStateOf("") }
    var searchByCounty by rememberSaveable { mutableStateOf(false) }
    var countyId by rememberSaveable { mutableStateOf("") }
    var isBusy by rememberSaveable { mutableStateOf(false) }
    var statusText by rememberSaveable { mutableStateOf("请输入登录信息") }
    var schools by remember { mutableStateOf<List<EkwingSchool>>(emptyList()) }
    var showSchoolResults by remember { mutableStateOf(false) }
    var pendingOvername by remember { mutableStateOf<EkwingOvernameRequired?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusText) {
        if (
            statusText.startsWith("搜索学校失败") ||
            statusText.startsWith("登录失败") ||
            statusText == "请选择同名账号" ||
            statusText == "未搜索到学校"
        ) {
            snackbarHostState.showSnackbar(statusText)
        }
    }

    LaunchedEffect(Unit) {
        if (EkwingLoginStore.shouldSaveLogin(context)) {
            val identity = EkwingLoginStore.loadIdentity(context)
            mode = when (identity.loginMethod) {
                "account" -> LoginMode.Account
                "real-name" -> LoginMode.RealName
                else -> mode
            }
            username = identity.username.orEmpty()
            password = identity.password.orEmpty()
            name = identity.name.orEmpty()
            schoolName = identity.schoolName.orEmpty()
            schoolId = identity.schoolId.orEmpty()
            schoolKeyword = schoolName
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LocalEklosePageBackground.current,
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        snackbarHost = {
            EkloseSnackbarHost(state = snackbarHostState)
        },
        topBar = {
            EkloseBlurredBar(blurBackdrop) {
                EkloseTopBar(title = "登录", barColor = if (blurBackdrop != null) Color.Transparent else LocalEklosePageBackground.current, onBack = onBack, scrollBehavior = scrollBehavior)
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .let { if (blurBackdrop != null) it.layerBackdrop(blurBackdrop) else it },
            color = LocalEklosePageBackground.current,
            contentColor = MiuixTheme.colorScheme.onSurface,
        ) {
            EkloseResponsiveList(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    GroupSection(title = "方式") {
                        OverlayDropdownPreference(
                            title = "登录模式",
                            summary = when (mode) {
                                LoginMode.Account -> "使用 username 和密码登录"
                                LoginMode.RealName -> "使用姓名、学校和密码登录"
                            },
                            items = LoginMode.entries.map { it.title },
                            selectedIndex = LoginMode.entries.indexOf(mode),
                            enabled = !isBusy,
                            onSelectedIndexChange = { mode = LoginMode.entries[it] },
                        )
                    }
                }
                item {
                    Column {
                        SmallTitle(text = "信息")
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (mode == LoginMode.Account) {
                                LoginField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = "账号",
                                    errorText = statusText.takeIf { it == "请输入账号" },
                                )
                            } else {
                                OverlayDropdownPreference(
                                    title = "检索方式",
                                    summary = if (searchByCounty) "按区县代码检索学校" else "按学校名称关键字检索",
                                    items = listOf("关键字检索", "区县代码检索"),
                                    selectedIndex = if (searchByCounty) 1 else 0,
                                    onSelectedIndexChange = { index ->
                                        searchByCounty = index == 1
                                        showSchoolResults = false
                                    },
                                )
                                if (searchByCounty) {
                                    LoginField(
                                        value = countyId,
                                        onValueChange = {
                                            countyId = it
                                            schoolName = ""
                                            schoolId = ""
                                            showSchoolResults = false
                                        },
                                        label = "区县代码 (County ID)",
                                        trailing = {
                                            IconButton(
                                                enabled = !isBusy,
                                                onClick = {
                                                    if (countyId.isBlank()) {
                                                        statusText = "请先输入区县代码"
                                                        return@IconButton
                                                    }
                                                    isBusy = true
                                                    statusText = "正在获取学校..."
                                                    coroutineScope.launch {
                                                        runCatching {
                                                            withContext(Dispatchers.IO) {
                                                                client.getSchoolsByCounty(countyId.trim())
                                                            }
                                                        }.onSuccess { result ->
                                                            if (result.isNotEmpty()) schools = result
                                                            showSchoolResults = result.isNotEmpty()
                                                            statusText = if (result.isEmpty()) "未获取到学校" else "请选择学校"
                                                        }.onFailure { error ->
                                                            statusText = "获取学校失败：${error.message}"
                                                        }
                                                        isBusy = false
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Basic.Search,
                                                    contentDescription = "按区县代码获取学校",
                                                    tint = MiuixTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                    )
                                } else {
                                    LoginField(
                                        value = schoolKeyword,
                                        onValueChange = {
                                            schoolKeyword = it
                                            schoolName = ""
                                            schoolId = ""
                                            showSchoolResults = false
                                        },
                                        label = "学校关键字",
                                        errorText = statusText.takeIf {
                                            it == "请先输入学校关键字" ||
                                                it == "请先搜索并选择学校" ||
                                                it == "未搜索到学校"
                                        },
                                        trailing = {
                                            IconButton(
                                                enabled = !isBusy,
                                                onClick = {
                                                    if (schoolKeyword.isBlank()) {
                                                        statusText = "请先输入学校关键字"
                                                        return@IconButton
                                                    }
                                                    isBusy = true
                                                    statusText = "正在搜索学校..."
                                                    coroutineScope.launch {
                                                        runCatching {
                                                            withContext(Dispatchers.IO) {
                                                                client.searchSchools(schoolKeyword.trim())
                                                            }
                                                        }.onSuccess { result ->
                                                            if (result.isNotEmpty()) schools = result
                                                            showSchoolResults = result.isNotEmpty()
                                                            statusText = if (result.isEmpty()) "未搜索到学校" else "请选择学校"
                                                        }.onFailure { error ->
                                                            statusText = "搜索学校失败：${error.message}"
                                                        }
                                                        isBusy = false
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = MiuixIcons.Basic.Search,
                                                    contentDescription = "搜索学校",
                                                    tint = MiuixTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                    )
                                }
                                LoginField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = "学生姓名",
                                    errorText = statusText.takeIf { it == "请输入学生姓名" },
                                )
                            }
                            LoginField(
                                value = password,
                                onValueChange = { password = it },
                                label = "密码",
                                password = true,
                                passwordVisible = passwordVisible,
                                errorText = statusText.takeIf { it == "请输入密码" },
                                trailing = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) {
                                                MiuixIcons.Hide
                                            } else {
                                                MiuixIcons.Show
                                            },
                                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            val validationError = validateLoginInput(
                                mode = mode,
                                username = username,
                                password = password,
                                name = name,
                                schoolName = schoolName,
                                schoolId = schoolId,
                            )
                            if (validationError != null) {
                                statusText = validationError
                                return@Button
                            }
                            isBusy = true
                            statusText = "正在登录..."
                            coroutineScope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        when (mode) {
                                            LoginMode.Account -> client.loginByAccount(
                                                username = username.trim(),
                                                password = password,
                                            )
                                            LoginMode.RealName -> client.loginByRealName(
                                                name = EkwingLoginStore.resolveRealName(context, name),
                                                password = password,
                                                schoolName = schoolName.trim(),
                                                schoolId = schoolId.trim(),
                                            )
                                        }
                                    }
                                }.onSuccess { result ->
                                    saveLoginResult(
                                        context = context,
                                        result = result,
                                        mode = mode,
                                        username = username,
                                        password = password,
                                        name = name,
                                        schoolName = schoolName,
                                        schoolId = schoolId,
                                    )
                                    statusText = "登录成功：uid=${result.uid}"
                                    password = ""
                                    onLoginSuccess()
                                }.onFailure { error ->
                                    if (error is EkwingOvernameRequired) {
                                        pendingOvername = error
                                        statusText = "请选择同名账号"
                                    } else {
                                        statusText = "登录失败：${error.message}"
                                    }
                                }
                                isBusy = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        if (isBusy) {
                            InfiniteProgressIndicator(
                                color = MiuixTheme.colorScheme.onPrimary,
                                size = 20.dp,
                            )
                        } else {
                            Text(text = "登录")
                        }
                    }
                }
            }
            SchoolSelectionBottomSheet(
                schools = schools,
                selectedSchoolId = schoolId,
                isBusy = isBusy,
                show = mode == LoginMode.RealName && showSchoolResults,
                onDismiss = { showSchoolResults = false },
                onChoose = { school ->
                    schoolName = school.schoolName
                    schoolId = school.schoolId
                    schoolKeyword = school.schoolName
                    showSchoolResults = false
                    statusText = "已选择学校：${school.schoolName}"
                },
            )
            OvernameBottomSheet(
                pendingOvername = pendingOvername,
                isBusy = isBusy,
                onDismiss = { pendingOvername = null },
                onChoose = { account ->
                    pendingOvername?.let { overname ->
                        isBusy = true
                        statusText = "正在登录所选账号..."
                        coroutineScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    client.loginByAccountMd5(account.username, overname.passwordMd5)
                                }
                            }.onSuccess { result ->
                                username = account.username
                                saveLoginResult(
                                    context = context,
                                    result = result,
                                    mode = LoginMode.RealName,
                                    username = username,
                                    password = password,
                                    name = name,
                                    schoolName = schoolName,
                                    schoolId = schoolId,
                                )
                                pendingOvername = null
                                password = ""
                                statusText = "登录成功：uid=${result.uid}"
                                onLoginSuccess()
                            }.onFailure { error ->
                                statusText = "登录失败：${error.message}"
                            }
                            isBusy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    passwordVisible: Boolean = false,
    errorText: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label,
            singleLine = true,
            keyboardOptions = if (password) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions.Default
            },
            colors = TextFieldDefaults.textFieldColors(
                labelColor = if (errorText != null) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSecondaryContainer
                },
                borderColor = if (errorText != null) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.primary
                },
            ),
            visualTransformation = if (password && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            trailingIcon = trailing,
        )
        if (errorText != null) {
            Text(
                text = errorText,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SchoolSelectionBottomSheet(
    schools: List<EkwingSchool>,
    selectedSchoolId: String,
    isBusy: Boolean,
    show: Boolean,
    onDismiss: () -> Unit,
    onChoose: (EkwingSchool) -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = "选择学校",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "请选择与账号对应的学校",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            schools.forEach { school ->
                EkloseCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    cornerRadius = EkloseCornerRadius.Item,
                    color = LocalEkloseCardSurface.current,
                    contentColor = MiuixTheme.colorScheme.onSurface,
                    borderAlpha = 0.12f,
                ) {
                    RadioButtonPreference(
                        title = school.schoolName,
                        summary = listOf("id=${school.schoolId}", school.zone)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        selected = selectedSchoolId == school.schoolId,
                        enabled = !isBusy,
                        onClick = { onChoose(school) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OvernameBottomSheet(
    pendingOvername: EkwingOvernameRequired?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onChoose: (EkwingOvernameAccount) -> Unit,
) {
    OverlayBottomSheet(
        show = pendingOvername != null,
        title = "选择账号",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "实名登录匹配到多个账号",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            pendingOvername?.accounts?.forEach { account ->
                EkloseCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    cornerRadius = EkloseCornerRadius.Item,
                    color = LocalEkloseCardSurface.current,
                    contentColor = MiuixTheme.colorScheme.onSurface,
                    borderAlpha = 0.12f,
                ) {
                    BasicComponent(
                        title = account.username,
                        titleColor = BasicComponentDefaults.titleColor(
                            color = MiuixTheme.colorScheme.onSurfaceContainer,
                        ),
                        summary = listOfNotNull(
                            account.uid?.let { "uid=$it" },
                            account.className,
                            account.type,
                        ).joinToString(" · "),
                        summaryColor = BasicComponentDefaults.summaryColor(
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        ),
                        endActions = {
                            Text(
                                text = if (isBusy) "登录中" else "选择",
                                style = MiuixTheme.textStyles.body2,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.primary,
                            )
                        },
                        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                        enabled = !isBusy,
                        onClick = { onChoose(account) },
                    )
                }
            }
        }
    }
}

private fun validateLoginInput(
    mode: LoginMode,
    username: String,
    password: String,
    name: String,
    schoolName: String,
    schoolId: String,
): String? {
    if (password.isBlank()) return "请输入密码"
    return when (mode) {
        LoginMode.Account -> {
            if (username.isBlank()) "请输入账号" else null
        }
        LoginMode.RealName -> {
            when {
                name.isBlank() -> "请输入学生姓名"
                schoolName.isBlank() || schoolId.isBlank() -> "请先搜索并选择学校"
                else -> null
            }
        }
    }
}

private fun saveLoginResult(
    context: android.content.Context,
    result: EkwingLoginResult,
    mode: LoginMode,
    username: String,
    password: String,
    name: String,
    schoolName: String,
    schoolId: String,
) {
    EkwingLoginStore.saveSession(
        context = context,
        result = result,
        loginMethod = mode.methodValue,
        identity = EkwingLoginIdentity(
            loginMethod = mode.methodValue,
            username = username.trim(),
            password = password,
            name = name.trim(),
            schoolName = schoolName.trim(),
            schoolId = schoolId.trim(),
        ),
    )
}
