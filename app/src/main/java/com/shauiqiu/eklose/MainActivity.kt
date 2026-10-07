package com.shauiqiu.eklose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.shauiqiu.eklose.ui.FloatingBottomBar
import com.shauiqiu.eklose.ui.FloatingBottomBarItem
import com.shauiqiu.eklose.ui.navigation.EklosePagerNavigation
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.shauiqiu.eklose.ui.theme.EkloseTheme
import com.shauiqiu.eklose.ui.theme.LocalEkloseCardSurface
import com.shauiqiu.eklose.ui.theme.LocalEkloseIsDarkTheme
import com.shauiqiu.eklose.ui.theme.LocalEklosePageBackground
import com.shauiqiu.eklose.ui.theme.rememberEkloseNavigationBadgeEnabled
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.springAnimateToPage

class MainActivity : ComponentActivity() {
    private var openHomeRequest by mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openHomeRequest = intent.openHomeRequest()
        enableEdgeToEdge()
        setContent {
            EkloseTheme {
                EkloseApp(openHomeRequest = openHomeRequest)
                EkwingTokenRefreshDialog()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val request = intent.openHomeRequest()
        if (request != 0L) {
            openHomeRequest = request
        }
    }

    companion object {
        private const val EXTRA_OPEN_HOME = "com.shauiqiu.eklose.extra.OPEN_HOME"

        fun createHomeIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_HOME, System.currentTimeMillis())
            }
        }

        private fun Intent.openHomeRequest(): Long {
            return if (hasExtra(EXTRA_OPEN_HOME)) {
                getLongExtra(EXTRA_OPEN_HOME, 0L)
            } else {
                0L
            }
        }
    }
}

private enum class RootPage(
    val label: String,
    val icon: ImageVector
) {
    Home("主页", MiuixIcons.Basic.Check),
    Reader("读取", MiuixIcons.Basic.Search),
    Settings("设置", MiuixIcons.Settings),
}

private enum class HomeworkScope(
    val label: String,
    val readerSummary: String,
    val answerSource: EkwingAnswerSource,
    val contentName: String,
    val listName: String,
) {
    CurrentExam(
        label = "当前学习中心考试任务",
        readerSummary = "读取当前学习中心考试任务，进入考试后再读取答案",
        answerSource = EkwingAnswerSource.Current,
        contentName = "考试",
        listName = "试卷",
    ),
    HistoryExam(
        label = "历史学习中心考试任务",
        readerSummary = "读取历史学习中心考试任务，进入考试后再读取答案",
        answerSource = EkwingAnswerSource.History,
        contentName = "考试",
        listName = "试卷",
    ),
    CurrentHomework(
        label = "当前作业",
        readerSummary = "读取当前常规作业，进入作业后再读取答案",
        answerSource = EkwingAnswerSource.HomeworkCurrent,
        contentName = "作业",
        listName = "作业",
    ),
    HistoryHomework(
        label = "历史作业",
        readerSummary = "读取历史常规作业，进入作业后再读取答案",
        answerSource = EkwingAnswerSource.HomeworkHistory,
        contentName = "作业",
        listName = "作业",
    ),
}

private data class RemoteDialogState(
    val title: String,
    val summary: String,
    val updateUrl: String = "",
    val locked: Boolean = false,
    val primaryActionText: String = "确定",
)

@Composable
private fun EkloseApp(openHomeRequest: Long = 0L) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedHomeworkScopeIndex by rememberSaveable { mutableStateOf(0) }
    var readerSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var readingSources by remember { mutableStateOf(emptySet<EkwingAnswerSource>()) }
    var remoteStatus by remember { mutableStateOf(EkloseApplication.remoteStatus ?: EkloseRemoteConfigManager.cachedStatus) }
    var remoteDialog by remember { mutableStateOf<RemoteDialogState?>(null) }
    var showVerificationDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showLegalDialog by rememberSaveable { mutableStateOf(false) }
    var floatingNavigationBar by remember {
        mutableStateOf(EkwingLoginStore.useFloatingNavigationBar(context))
    }
    var floatingNavigationBarGlass by remember {
        mutableStateOf(EkwingLoginStore.useFloatingNavigationBarGlass(context))
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { RootPage.entries.size })
    val navigation = remember(pagerState, scope) {
        EklosePagerNavigation(
            currentPage = { pagerState.currentPage },
            pageCount = { pagerState.pageCount },
            animateToPage = { pagerState.springAnimateToPage(it) },
            scope = scope,
        )
    }
    val selectedPage = RootPage.entries[navigation.selectedPage]
    LaunchedEffect(pagerState, navigation) {
        snapshotFlow { pagerState.currentPage }.collect { navigation.syncFromPager() }
    }
    val selectedHomeworkScope = HomeworkScope.entries[selectedHomeworkScopeIndex]
    val answerStates by EkwingAnswerState.states.collectAsState()
    val selectedSourceState = answerStates[selectedHomeworkScope.answerSource] ?: EkwingAnswerSourceState()
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationBadgeEnabled = rememberEkloseNavigationBadgeEnabled()
    // Count only the selected reading source, not all sources or in-flight operations.
    val selectedPaperCount = selectedSourceState.papers.size
    val readingBadge: (@Composable () -> Unit)? = if (navigationBadgeEnabled && selectedPaperCount > 0) {
        {
            Badge(
                containerColor = MiuixTheme.colorScheme.primary,
                contentColor = MiuixTheme.colorScheme.onPrimary,
            ) {
                Text(selectedPaperCount.toString())
            }
        }
    } else {
        null
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                floatingNavigationBar = EkwingLoginStore.useFloatingNavigationBar(context)
                floatingNavigationBarGlass = EkwingLoginStore.useFloatingNavigationBarGlass(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        EkwingAnswerCacheStore.restore(context)
    }

    LaunchedEffect(Unit) {
        EkloseApplication.remoteStatusFlow.collect { status ->
            remoteStatus = status
        }
    }

    LaunchedEffect(Unit) {
        EkloseApplication.applicationNoticeFlow.collect { notice ->
            snackbarHostState.showSnackbar(notice)
        }
    }

    LaunchedEffect(Unit) {
        EkloseApplication.updateStatusFlow.collect { status ->
            if (status != null) {
                if (status.requiresVerification) {
                    showVerificationDialog = true
                } else if (status.showUpdateDialog) {
                    remoteDialog = status.toUpdateDialog()
                }
            }
        }
    }

    LaunchedEffect(openHomeRequest) {
        if (openHomeRequest != 0L) {
            navigation.navigateTo(RootPage.Home.ordinal)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= EkloseLayoutBreakpoints.Expanded
        val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val snackbarBottomPadding = if (!useNavigationRail && floatingNavigationBar) {
            104.dp + navigationBarInset
        } else {
            20.dp
        }
        val scrollBehavior = MiuixScrollBehavior()
        val blurBackdrop = rememberEkloseBlurBackdrop()
        val floatingGlassActive = floatingNavigationBarGlass && isRenderEffectSupported()
        val floatingSurfaceColor = MiuixTheme.colorScheme.surface
        val floatingBackdrop = rememberLayerBackdrop {
            drawRect(floatingSurfaceColor)
            drawContent()
        }
        val navigationRailState = rememberNavigationRailState(
            initialValue = if (EkwingLoginStore.navigationRailExpanded(context)) {
                NavigationRailValue.Expanded
            } else {
                NavigationRailValue.Collapsed
            },
        )
        LaunchedEffect(navigationRailState.currentValue) {
            EkwingLoginStore.setNavigationRailExpanded(
                context = context,
                expanded = navigationRailState.isExpanded,
            )
        }

        fun readSelectedHomework() {
            val requestedScope = selectedHomeworkScope
            val requestedSource = requestedScope.answerSource
            if (requestedSource in readingSources) return
            EkwingTokenRefreshManager.beginUserRead()
            readingSources += requestedSource
            readerSummary = "正在读取${requestedScope.label}..."
            scope.launch {
                val message = try {
                    val result = EkwingTokenRefreshManager.withTokenRefresh(context) {
                        EkwingAnswerReader(context).readPapers(source = requestedSource)
                    }
                    "已加载 ${result.size} 份${requestedScope.label}，正在后台解析答案"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    "读取失败：${error.message ?: "未知错误"}"
                } finally {
                    readingSources -= requestedSource
                }
                if (HomeworkScope.entries[selectedHomeworkScopeIndex].answerSource == requestedSource) {
                    readerSummary = message
                    snackbarHostState.showSnackbar(message)
                }
            }
        }

        fun deleteReadResults() {
            EkwingAnswerPrefetchManager.cancelAll()
            EkwingAnswerCacheStore.clear(context)
            readerSummary = "已清空读取结果"
            scope.launch { snackbarHostState.showSnackbar("已清空读取结果") }
        }

        Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = LocalEklosePageBackground.current,
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
        topBar = {
            EkloseBlurredBar(blurBackdrop) {
                EkloseTopBar(
                    title = when (selectedPage) {
                        RootPage.Home -> "翼课校长"
                        RootPage.Reader -> "读取"
                        RootPage.Settings -> "设置"
                    },
                    scrollBehavior = scrollBehavior,
                    barColor = if (blurBackdrop != null) Color.Transparent else LocalEklosePageBackground.current,
                    actions = if (selectedPage == RootPage.Reader) {
                        {
                            val readerActions = DropdownEntry(
                                items = listOf(
                                    DropdownItem(text = "读取", onClick = ::readSelectedHomework),
                                    DropdownItem(text = "删除", onClick = ::deleteReadResults),
                                ),
                            )
                            OverlayIconDropdownMenu(entry = readerActions) {
                                Icon(
                                    imageVector = MiuixIcons.MoreCircle,
                                    contentDescription = "读取操作",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    } else {
                        {}
                    },
                )
            }
        },
        snackbarHost = {
            EkloseSnackbarHost(
                state = snackbarHostState,
                bottomPadding = snackbarBottomPadding,
            )
        },
        bottomBar = {
            if (!useNavigationRail && floatingNavigationBar) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Keep the bar intrinsically sized like KernelSU. The outer Box centers it,
                    // while each root page keeps its own minimum tab width.
                    FloatingBottomBar(
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectTapGestures { }
                            }
                            .padding(
                                start = 28.dp,
                                end = 28.dp,
                                bottom = if (navigationBarInset > 0.dp) {
                                    8.dp + navigationBarInset
                                } else {
                                    28.dp
                                },
                            ),
                        selectedIndex = selectedPage.ordinal,
                        onSelected = navigation::navigateTo,
                        backdrop = floatingBackdrop,
                        tabsCount = RootPage.entries.size,
                        isBlurEnabled = floatingGlassActive,
                    ) { activateTab ->
                        RootPage.entries.forEach { page ->
                            FloatingBottomBarItem(
                                selected = selectedPage == page,
                                onClick = { activateTab(page.ordinal) },
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                            ) {
                                val icon: @Composable () -> Unit = {
                                    Icon(
                                        imageVector = page.icon,
                                        contentDescription = page.label,
                                    )
                                }
                                if (page == RootPage.Reader && readingBadge != null) {
                                    BadgedBox(badge = { readingBadge() }) { icon() }
                                } else {
                                    icon()
                                }
                                Text(text = page.label)
                            }
                        }
                    }
                }
            } else if (!useNavigationRail) {
                EkloseBlurredBar(blurBackdrop, blurActive = false) {
                NavigationBar(color = MiuixTheme.colorScheme.surface) {
                    RootPage.entries.forEach { page ->
                        NavigationBarItem(
                            selected = selectedPage == page,
                            onClick = { navigation.navigateTo(page.ordinal) },
                            icon = page.icon,
                            label = page.label,
                            badge = if (page == RootPage.Reader) readingBadge else null,
                        )
                    }
                }
                }
            }
        },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val bottomInnerPadding = innerPadding.calculateBottomPadding()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                // Keep the pager itself behind the floating bar, as KernelSU does. Only
                // apply the top and horizontal insets here; pages receive bottomInnerPadding
                // for their scroll content separately.
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                )
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            if (useNavigationRail) {
                EkloseBlurredBar(blurBackdrop, blurActive = false) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    state = navigationRailState,
                    color = if (blurBackdrop != null) Color.Transparent else LocalEkloseCardSurface.current,
                    expandContentDescription = "展开导航栏",
                    collapseContentDescription = "收起导航栏",
                ) {
                    RootPage.entries.forEach { page ->
                        NavigationRailItem(
                            selected = selectedPage == page,
                            onClick = { navigation.navigateTo(page.ordinal) },
                            icon = page.icon,
                            label = page.label,
                            badge = if (page == RootPage.Reader) readingBadge else null,
                        )
                    }
                }
                }
            }
            // Match KernelSU's pager hierarchy: the backdrop wraps the pager directly,
            // without an extra Surface layer between the page content and the sampled layer.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MiuixTheme.colorScheme.surface),
            ) {
            Box(
                modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        // The layerBackdrop records the pager node itself, not its parent.
                        // Put the Miuix surface inside that recorded node so the source is
                        // never transparent/black when page content has no painted background.
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface)
                        .then(
                            if (floatingNavigationBar && floatingGlassActive) {
                                Modifier.layerBackdrop(floatingBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    when (RootPage.entries[pageIndex]) {
                    RootPage.Home -> HomePage(
                        remoteStatus = remoteStatus,
                        bottomInnerPadding = bottomInnerPadding,
                        onShowAbout = { showAboutDialog = true },
                    )
                    RootPage.Reader -> ReaderPage(
                        homeworkScope = selectedHomeworkScope,
                        bottomInnerPadding = bottomInnerPadding,
                        readerSummary = readerSummary,
                        selectedHomeworkScopeIndex = selectedHomeworkScopeIndex,
                        onHomeworkScopeChange = {
                            selectedHomeworkScopeIndex = it
                            readerSummary = null
                        },
                        sourceState = selectedSourceState,
                        isReading = selectedHomeworkScope.answerSource in readingSources,
                        isWideLayout = useNavigationRail,
                        onRead = ::readSelectedHomework,
                        onDelete = ::deleteReadResults,
                    )
                    RootPage.Settings -> SettingsScreenRoute(
                        onBack = {},
                        embedded = true,
                        bottomInnerPadding = bottomInnerPadding,
                    )
                }
            }
            }
        }
        }
        RemoteManagementDialog(
            dialog = remoteDialog,
            onDismiss = { if (remoteDialog?.locked != true) remoteDialog = null },
            onOpenUrl = { url ->
                if (url.isNotBlank()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
        AboutDialog(
            show = showAboutDialog,
            onDismiss = { showAboutDialog = false },
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
            onShowLegal = {
                showAboutDialog = false
                showLegalDialog = true
            },
            versionName = context.currentVersionName(),
        )
        LegalDialog(
            show = showLegalDialog,
            onDismiss = { showLegalDialog = false },
        )
        VerificationDialog(
            show = showVerificationDialog,
            status = remoteStatus,
            onVerified = {
                showVerificationDialog = false
                remoteStatus?.let { status ->
                    if (status.showUpdateDialog) {
                        remoteDialog = status.toUpdateDialog()
                    }
                }
            },
        )
    }
    }
}

@Composable
private fun RemoteManagementDialog(
    dialog: RemoteDialogState?,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    OverlayDialog(
        show = dialog != null,
        title = dialog?.title,
        summary = dialog?.summary,
        onDismissRequest = if (dialog?.locked == true) null else onDismiss,
    ) {
        if (dialog != null) {
            if (dialog.updateUrl.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (!dialog.locked) {
                        TextButton(
                            text = "稍后",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                    }
                    TextButton(
                        text = dialog.primaryActionText,
                        onClick = {
                            onOpenUrl(dialog.updateUrl)
                            if (!dialog.locked) onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.primary,
                            textColor = MiuixTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            } else if (!dialog.locked) {
                TextButton(
                    text = dialog.primaryActionText,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.primary,
                        textColor = MiuixTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun VerificationDialog(
    show: Boolean,
    status: EkloseRemoteStatus?,
    onVerified: () -> Unit,
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf("") }
    OverlayDialog(
        show = show,
        title = status?.verificationTitle?.ifBlank { "启动验证" } ?: "启动验证",
        summary = status?.verificationMessage?.ifBlank { "请输入验证码继续使用。" }
            ?: "请输入验证码继续使用。",
        onDismissRequest = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = input,
                onValueChange = {
                    input = it
                    errorText = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = "验证码",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            if (errorText.isNotBlank()) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = errorText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            TextButton(
                text = "验证",
                onClick = {
                    val code = input.trim()
                    if (code == status?.verificationCode) {
                        EkwingLoginStore.saveLocalVerificationCode(context, code)
                        input = ""
                        errorText = ""
                        onVerified()
                    } else {
                        errorText = "验证码不正确，请重新输入"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private fun EkloseRemoteStatus.toStartupDialog(): RemoteDialogState? {
    return when {
        isKillSwitch -> RemoteDialogState(
            title = "远程管理",
            summary = message.ifBlank { "程序异常，请等待远程恢复。" },
            locked = true,
        )
        showUpdateDialog -> RemoteDialogState(
            title = if (isForce) "需要更新" else "发现新版本",
            summary = message.ifBlank { "发现新版本，请更新后继续使用。" },
            updateUrl = updateUrl,
            locked = isForce,
            primaryActionText = "立即更新",
        )
        else -> null
    }
}

private fun EkloseRemoteStatus.toUpdateDialog(): RemoteDialogState = toStartupDialog()
    ?: RemoteDialogState(
        title = if (isForce) "需要更新" else "发现新版本",
        summary = message.ifBlank { "发现新版本，请更新后继续使用。" },
        updateUrl = updateUrl,
        locked = isForce,
        primaryActionText = "立即更新",
    )

@Composable
private fun HomePage(
    remoteStatus: EkloseRemoteStatus?,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
    onShowAbout: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loginSession by remember { mutableStateOf(EkwingLoginStore.loadSession(context)) }
    val activatedSession = loginSession?.takeIf { it.uid.isNotBlank() && it.token.isNotBlank() }
    val loginIdentity = remember(activatedSession?.uid) {
        EkwingLoginStore.loadIdentity(context)
    }
    val announcementTitle = remoteStatus?.announcementTitle?.takeIf { it.isNotBlank() } ?: "公告"
    val announcementSummary = remoteStatus?.announcementMessage?.takeIf { it.isNotBlank() }
        ?: "云端读取前请确认没有正在进行考试、练习、录音或提交。"
    val changelogTitle = remoteStatus?.changelogTitle?.takeIf { it.isNotBlank() } ?: "更新日志"
    val changelogSummary = remoteStatus?.changelogSummary?.takeIf { it.isNotBlank() }
        ?: "新增读取页内容来源切换与列表预览。"

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loginSession = EkwingLoginStore.loadSession(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    EkloseResponsiveList(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp + bottomInnerPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            EkloseActivationStatusCard(
                session = activatedSession,
                identityName = loginIdentity.name.takeIf { activatedSession != null },
                onActivate = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                },
            )
        }
        item {
            HomeInformationCard()
        }
        item {
            GroupSection(title = "公告与更新") {
                GroupItem(
                    title = announcementTitle,
                    summary = announcementSummary,
                    onClick = {
                        context.startActivity(AnnouncementActivity.createIntent(context, remoteStatus))
                    },
                )
                GroupDivider()
                GroupItem(
                    title = changelogTitle,
                    summary = changelogSummary,
                    onClick = {
                        context.startActivity(AnnouncementActivity.createIntent(context, remoteStatus))
                    },
                )
            }
        }
        item {
            GroupSection(title = "其他") {
                GroupItem(
                    title = "关于翼课校长",
                    summary = "应用信息、官网与致谢",
                    onClick = onShowAbout,
                )
            }
        }
    }
}

@Composable
private fun EkloseActivationStatusCard(
    session: EkwingLoginSession?,
    identityName: String?,
    onActivate: () -> Unit,
) {
    val isDarkTheme = LocalEkloseIsDarkTheme.current
    val isDynamicColor = MiuixTheme.isDynamicColor
    // Preserve the existing KernelSU-derived endpoint colors.
    val activeCardColor = when {
        isDynamicColor -> MiuixTheme.colorScheme.secondaryContainer
        isDarkTheme -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val activeContentColor = when {
        isDynamicColor -> MiuixTheme.colorScheme.onSecondaryContainer
        isDarkTheme -> Color(0xFFB8E8C5)
        else -> Color(0xFF164A29)
    }
    val cardColor by animateColorAsState(
        targetValue = if (session != null) activeCardColor else MiuixTheme.colorScheme.errorContainer,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "activationCardColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (session != null) activeContentColor else MiuixTheme.colorScheme.onErrorContainer,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "activationContentColor",
    )

    AnimatedContent(
        targetState = session to identityName,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
        contentKey = { (displayedSession, _) -> displayedSession != null },
        transitionSpec = {
            (fadeIn(tween(durationMillis = 220, delayMillis = 80)) togetherWith
                fadeOut(tween(durationMillis = 100))).using(
                // Keep the card's existing Tilt feedback from being clipped.
                SizeTransform(clip = false) { _, _ ->
                    tween(durationMillis = 320, easing = FastOutSlowInEasing)
                },
            )
        },
        label = "activationStatus",
    ) { (displayedSession, displayedIdentityName) ->
        if (displayedSession != null) {
            val iconTint = if (isDynamicColor) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.8f)
            } else {
                Color(0xFF36D167)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = cardColor,
                        contentColor = contentColor,
                    ),
                    showIndication = false,
                    pressFeedbackType = PressFeedbackType.Tilt,
                ) {
                    // This deliberately follows KernelSU's StatusCard geometry: no invented
                    // fixed card height, a 110dp status mark at bottom-right, and text overlays.
                    Box {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(27.dp, 31.dp),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            Icon(
                                modifier = Modifier.size(110.dp),
                                imageVector = Icons.Rounded.CheckCircleOutline,
                                tint = iconTint,
                                contentDescription = null,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp, 14.dp),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Column {
                                Text(
                                    text = "已激活",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
                                )
                                Spacer(Modifier.height(1.dp))
                                Text(
                                    text = displayedIdentityName?.takeIf { it.isNotBlank() }
                                        ?.let { "翼课账号：$it" }
                                        ?: "uid=${displayedSession.uid}",
                                    fontSize = 15.sp,
                                    color = contentColor,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp, 10.dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(
                                text = "可以开始读取考试和作业",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        } else {
            // Unlogged and unactivated are intentionally the same state. This is the compact
            // KernelSU not-installed branch, not the large active StatusCard.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = cardColor,
                    contentColor = contentColor,
                ),
                onClick = onActivate,
                showIndication = true,
                pressFeedbackType = PressFeedbackType.Tilt,
            ) {
                BasicComponent(
                    title = "未激活",
                    titleColor = BasicComponentDefaults.titleColor(
                        color = contentColor,
                    ),
                    summary = "点击登录翼课账号",
                    summaryColor = BasicComponentDefaults.summaryColor(
                        color = contentColor,
                    ),
                    startAction = {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "未激活",
                            modifier = Modifier.padding(end = 16.dp),
                            tint = MiuixTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowLegal: () -> Unit,
    versionName: String,
) {
    OverlayDialog(
        show = show,
        title = "关于翼课校长",
        summary = "翼课学习中心答案读取工具",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AboutInfoBlock(
                title = "软件信息",
                imageResId = R.drawable.app_icon,
                imageDescription = "应用图标",
                lines = listOf(
                    "翼课校长",
                    "版本: ${versionName.ifBlank { "未知" }}",
                    "软件许可: 非商业学习交流项目 · 保留所有权利",
                ),
            )
            AboutInfoBlock(
                title = "关于作者",
                imageResId = R.drawable.ic_author,
                imageDescription = "作者头像",
                lines = listOf(
                    "帅丘",
                ),
            )
            GroupSection {
                GroupItem(
                    title = "官方仓库与发布",
                    summary = "查看版本动态与更新",
                    onClick = { onOpenUrl("https://github.com/laststudio/eklose") },
                )
                GroupDivider()
                GroupItem(
                    title = "用户协议与免责声明",
                    summary = "使用许可 · 隐私政策 · 免责声明",
                    onClick = onShowLegal,
                )
                GroupDivider()
                GroupItem(
                    title = "访问官网",
                    summary = "lastudio.cc",
                    onClick = { onOpenUrl("https://lastudio.cc") },
                )
            }
            TextButton(
                text = "关闭",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun LegalDialog(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = "用户协议与免责声明",
        summary = "使用许可 · 隐私政策 · 免责声明",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 440.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LegalSectionCard(
                title = "一、软件性质、授权与知识产权",
                items = listOf(
                    "非商业定位与权利保留：本软件为非商业学习交流项目，按“现状”免费提供，不收取任何软件使用费用，服务器成本依靠用户自愿捐赠，历史版本所适用的任何开源许可证（包括但不限于 GPL 3.0）已不再适用。本软件源代码、核心解析逻辑与 UI 界面归 LastStudio 研发团队所有，受相关法律严格保护；本协议未明示授予的权利均予保留。",
                    "受限使用许可：仅授予用户个人的、非排他性、不可转让、不可分许可且可随时撤销的有限客户端使用许可，仅用于用户本人在经授权设备上进行合法个人课后学习参考、技术交流与本地数据解析，不构成任何商业转让或售卖。",
                    "严禁逆向与破解：严禁对本软件客户端、安装包、鉴权接口进行反编译、反汇编、动态调试、脱壳解密、接口劫持或制作任何形式的外挂插件及破解版本。",
                    "严禁未授权转售与牟利：本软件免费提供。严禁以任何名义二次打包分发、倒卖、转售或利用本软件对外提供有偿代读、代刷作业服务。",
                ),
            )
            LegalSectionCard(
                title = "二、第三方凭据与接口隐私授权",
                items = listOf(
                    "凭据处理告知：为了实现拉取作答列表及考试与作业解析数据，本软件将根据用户操作本地处理第三方平台账号凭据并向对应接口发起请求。",
                    "合法用途严格限制：相关凭据仅严格用于用户发起的身份校验与题目数据解析，绝不用于侵犯用户隐私或任何非法用途。",
                ),
            )
            LegalSectionCard(
                title = "三、免责声明",
                items = listOf(
                    "非商业与自愿承担风险：本软件为非商业学习交流项目，全部功能免费提供。请在充分了解相关风险的基础上自愿使用，并在学习或测试完成后的 24 小时内自行删除本软件。",
                    "第三方平台非官方与无背书声明：本软件为独立技术研究与效率辅助工具，与江苏聚智、翼课网（ekwing）、科大讯飞、各级学校及教育管理部门无任何隶属、合作或官方背书关系。",
                    "第三方账号“顶号”与风控免责：第三方平台实行单设备互斥登录原则，调用可能导致官方客户端被退出登录（顶号）或触发异常风控，因使用本软件导致的一切账号异常损失，在法律允许的最大范围内开发者不承担责任与补偿。",
                    "学业与考试风险免责（严禁作弊）：严禁在正规考试、模拟测验、随堂测验或课堂实时录音等关键场景中使用本软件！严禁用于考试作弊或规避教学监管。因违规使用导致的任何处分、成绩作废或纠纷，由用户全权自担全部责任。",
                ),
            )
            TextButton(
                text = "我已了解并同意",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    color = MiuixTheme.colorScheme.primary,
                    textColor = MiuixTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun LegalSectionCard(
    title: String,
    items: List<String>,
) {
    GroupSection(title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEachIndexed { index, item ->
                Text(
                    text = "${index + 1}. $item",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun AboutInfoBlock(
    title: String,
    imageResId: Int,
    imageDescription: String,
    lines: List<String>,
) {
    GroupSection(title = title) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MiuixTheme.colorScheme.surface,
                shadowElevation = 0.dp,
            ) {
                Image(
                    painter = painterResource(id = imageResId),
                    contentDescription = imageDescription,
                    modifier = Modifier.size(52.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lines.forEachIndexed { index, line ->
                    Text(
                        text = line,
                        style = if (index == 0) MiuixTheme.textStyles.body1 else MiuixTheme.textStyles.body2,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == 0) {
                            MiuixTheme.colorScheme.onSurfaceContainer
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
            }
        }
    }
}

private fun Context.currentVersionName(): String {
    return packageManager.getPackageInfo(packageName, 0).versionName ?: ""
}

@Composable
private fun LoginStatusIndicator(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    color = MiuixTheme.colorScheme.primary,
                    shape = CircleShape,
                ),
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ReaderPage(
    homeworkScope: HomeworkScope,
    bottomInnerPadding: androidx.compose.ui.unit.Dp,
    readerSummary: String?,
    selectedHomeworkScopeIndex: Int,
    onHomeworkScopeChange: (Int) -> Unit,
    sourceState: EkwingAnswerSourceState,
    isReading: Boolean,
    isWideLayout: Boolean,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val papers = sourceState.papers
    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefresh(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isReading,
            onRefresh = onRead,
            pullToRefreshState = pullToRefreshState,
            refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新", "刷新完成"),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val contentModifier = if (isWideLayout) {
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .widthIn(max = EkloseLayoutBreakpoints.ContentMaxWidth)
                        .fillMaxWidth()
                } else {
                    Modifier.fillMaxSize()
                }
                LazyColumn(
                modifier = contentModifier
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 14.dp,
                    end = 18.dp,
                    bottom = 14.dp + bottomInnerPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    GroupSection {
                        OverlayDropdownPreference(
                            title = "读取内容",
                            summary = "选择读取页使用的考试或作业来源",
                            items = HomeworkScope.entries.map { it.label },
                            selectedIndex = selectedHomeworkScopeIndex,
                            onSelectedIndexChange = onHomeworkScopeChange,
                        )
                    }
                }
                item {
                    GroupSection(title = "${homeworkScope.listName}列表") {
                        papers.forEachIndexed { index, paper ->
                            val parseState = sourceState.parseStateByPaperKey[paper.key]
                                ?: EkwingAnswerParseState(EkwingAnswerParseStatus.Pending)
                            PaperPreference(
                                paper = paper,
                                parseState = parseState,
                                onRetry = {
                                    EkwingAnswerPrefetchManager.enqueue(
                                        context = context,
                                        source = HomeworkScope.entries[selectedHomeworkScopeIndex].answerSource,
                                        paperKey = paper.key,
                                    )
                                },
                                onClick = {
                                    context.startActivity(
                                        AnswerActivity.createIntent(
                                            context = context,
                                            paperTitle = paper.title,
                                            paperSummary = paper.summary,
                                            paperKey = paper.key,
                                            source = HomeworkScope.entries[selectedHomeworkScopeIndex].answerSource,
                                        )
                                    )
                                },
                            )
                            if (index != papers.lastIndex) {
                                GroupDivider()
                            }
                        }
                        if (papers.isEmpty()) {
                            val readFailed = readerSummary?.startsWith("读取失败") == true
                            FeedbackPreference(
                                state = when {
                                    isReading -> FeedbackState.Loading
                                    readFailed -> FeedbackState.Error
                                    else -> FeedbackState.Empty
                                },
                                title = when {
                                    isReading -> "正在读取${homeworkScope.contentName}"
                                    readFailed -> "读取失败"
                                    else -> "暂无${homeworkScope.listName}"
                                },
                                summary = when {
                                    isReading -> "请稍候"
                                    readFailed -> "点击此处或底部按钮重试：${readerSummary.orEmpty()}"
                                    else -> "点击右下角读取按钮加载${homeworkScope.contentName}列表"
                                },
                                onRetry = onRead.takeIf { readFailed },
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

private enum class FeedbackState { Loading, Empty, Error }

@Composable
private fun FeedbackPreference(
    state: FeedbackState,
    title: String,
    summary: String,
    onRetry: (() -> Unit)? = null,
) {
    if (state == FeedbackState.Error) {
        EkloseWarningCard(
            title = title,
            summary = summary,
            onClick = onRetry,
        )
        return
    }
    GroupItem(
        title = title,
        summary = summary,
        onClick = onRetry,
        showArrow = onRetry != null,
        isError = false,
        trailing = if (state == FeedbackState.Loading) {
            {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.primary,
                    size = 20.dp,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun PaperPreference(
    paper: EkwingAnswerPaper,
    parseState: EkwingAnswerParseState,
    onRetry: () -> Unit,
    onClick: () -> Unit,
) {
    val statusSummary = when (parseState.status) {
        EkwingAnswerParseStatus.Pending -> "${paper.summary} · 等待解析"
        EkwingAnswerParseStatus.Loading -> "${paper.summary} · 正在解析"
        EkwingAnswerParseStatus.Ready -> paper.summary
        EkwingAnswerParseStatus.Failed -> "${paper.summary} · 解析失败：${parseState.errorMessage ?: "未知错误"}"
    }
    if (canOpenPaper(parseState)) {
        ArrowPreference(
            title = paper.title,
            summary = statusSummary,
            onClick = onClick,
        )
    } else {
        GroupItem(
            title = paper.title,
            summary = statusSummary,
            onClick = onRetry.takeIf { parseState.status == EkwingAnswerParseStatus.Failed },
            value = "重试".takeIf { parseState.status == EkwingAnswerParseStatus.Failed },
            trailing = {
                if (parseState.status == EkwingAnswerParseStatus.Pending || parseState.status == EkwingAnswerParseStatus.Loading) {
                    InfiniteProgressIndicator(
                        color = MiuixTheme.colorScheme.primary,
                        size = 20.dp,
                    )
                }
            },
            showArrow = parseState.status == EkwingAnswerParseStatus.Failed,
            isError = parseState.status == EkwingAnswerParseStatus.Failed,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EklosePreview() {
    EkloseTheme {
        EkloseApp()
    }
}
