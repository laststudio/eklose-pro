package com.shauiqiu.eklose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.shauiqiu.eklose.ui.theme.LocalEkloseBlurEnabled
import com.shauiqiu.eklose.ui.theme.LocalEkloseCardSurface
import com.shauiqiu.eklose.ui.theme.LocalEklosePageBackground
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal object EkloseCornerRadius {
    val Container = 18.dp
    val Item = 16.dp
    val Nested = 12.dp
}

internal object EkloseLayoutBreakpoints {
    val Expanded = 840.dp
    val ContentMaxWidth = 720.dp
}


@Composable
fun rememberEkloseBlurBackdrop(): LayerBackdrop? {
    if (!LocalEkloseBlurEnabled.current || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

@Composable
fun EkloseBlurredBar(
    backdrop: LayerBackdrop?,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(MiuixTheme.colorScheme.surface.copy(alpha = 0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        },
    ) { content() }
}

@Composable
fun EkloseFloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    content: @Composable RowScope.((Int) -> Unit) -> Unit,
) {
    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(64.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content { index ->
                if (index in 0..Int.MAX_VALUE) onSelected(index)
            }
        }
    }
}

@Composable
fun RowScope.EkloseFloatingBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    badge: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minWidth = 76.dp)
            .fillMaxHeight()
            .clip(CircleShape)
            .background(
                if (selected) MiuixTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (badge != null) {
            BadgedBox(badge = { badge() }) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
                    else MiuixTheme.colorScheme.onSurface,
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
                else MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            color = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
            else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
fun EkloseSnackbarHost(
    state: SnackbarHostState,
    bottomPadding: Dp = 20.dp,
) {
    SnackbarHost(
        state = state,
        modifier = Modifier.padding(bottom = bottomPadding),
    )
}

@Composable
fun EkloseTopBar(
    title: String = "翼课校长",
    barColor: Color = LocalEklosePageBackground.current,
    onBack: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        color = barColor,
        titleColor = MiuixTheme.colorScheme.onSurface,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowRight,
                        contentDescription = "返回",
                        modifier = Modifier.graphicsLayer(scaleX = -1f),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun EkloseResponsiveList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: LazyListScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val maxContentWidth = if (maxWidth >= EkloseLayoutBreakpoints.Expanded) EkloseLayoutBreakpoints.ContentMaxWidth else maxWidth
        LazyColumn(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .widthIn(max = maxContentWidth)
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun GroupSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            SmallTitle(text = title)
        }
        EkloseCard(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
internal fun EkloseCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = EkloseCornerRadius.Container,
    color: Color = LocalEkloseCardSurface.current,
    contentColor: Color = MiuixTheme.colorScheme.onSurface,
    borderAlpha: Float = 0.16f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.squircleBorder(
            width = 0.6.dp,
            color = MiuixTheme.colorScheme.outline.copy(alpha = borderAlpha),
            cornerRadius = cornerRadius,
        ),
        cornerRadius = cornerRadius,
        colors = CardDefaults.defaultColors(
            color = color,
            contentColor = contentColor,
        ),
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = { onClick?.invoke() },
        content = content,
    )
}

@Composable
fun GroupItem(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    value: String? = null,
    showArrow: Boolean = onClick != null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    pressFeedback: Boolean = false,
) {
    BasicComponent(
        modifier = modifier.fillMaxWidth(),
        title = title,
        titleColor = BasicComponentDefaults.titleColor(
            color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
        ),
        summary = summary,
        summaryColor = BasicComponentDefaults.summaryColor(
            color = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
        ),
        startAction = leading,
        endActions = {
            if (value != null) {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (trailing != null) trailing()
            if (showArrow) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        },
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        holdDownState = pressFeedback && onClick != null,
        onClick = onClick,
    )
}

@Composable
fun EkloseWarningCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onClick?.invoke() },
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Sink,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.errorContainer,
            contentColor = MiuixTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 18.dp))
}

@Composable
fun PageLead(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = Color.Transparent,
        contentColor = MiuixTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
