@file:Suppress("PrivatePropertyName")

package cn.edu.hut.course.glass

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import io.flutter.plugin.platform.PlatformView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// ── PlatformView ──────────────────────────────────────────────────

/**
 * 通过 PlatformView 将 Compose 玻璃底栏嵌入 Flutter。
 * 使用 AndroidLiquidGlass (io.github.kyant0:backdrop) Maven 库实现毛玻璃 + 弹簧指示器。
 */
class GlassBottomBarPlatformView(
    context: Context,
    viewId: Int,
    creationParams: Map<*, *>?,
    private val onTabSelected: (Int) -> Unit
) : PlatformView {

    private val composeView: ComposeView = ComposeView(context).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setContent {
            GlassBottomBarCompose(
                onTabSelected = { index -> onTabSelected(index) }
            )
        }
    }

    override fun getView(): View = composeView
    override fun dispose() {}
}

/**
 * PlatformView 工厂 — 在 FlutterHostActivity#configureFlutterEngine 中注册。
 */
class GlassBottomBarFactory(
    private val onTabSelected: (Int) -> Unit
) : io.flutter.plugin.platform.PlatformViewFactory(
    io.flutter.plugin.common.StandardMessageCodec.INSTANCE
) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        return GlassBottomBarPlatformView(context, viewId, args as? Map<*, *>, onTabSelected)
    }
}

// ── 标签数据 ──────────────────────────────────────────────────────

private data class TabItem(
    val label: String,
    val icon: ImageVector
)

private val TAB_ITEMS = listOf(
    TabItem("今日", Icons.Filled.Home),
    TabItem("AI", Icons.Filled.Star),
    TabItem("课表", Icons.Filled.DateRange),
    TabItem("个人", Icons.Filled.Person)
)

// ── Glass Bottom Bar（Compose 实现，基于 backdrop 库）──────────────

/**
 * 玻璃态底栏 — AndroidLiquidGlass 风格。
 *
 * 前三项为圆形毛玻璃标签，第四项（"个人"）为染色玻璃圆形按钮。
 */
@Composable
fun GlassBottomBarCompose(
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val backgroundColor = if (isLightTheme) Color.White else Color(0xFF121212)

    var selectedIndex by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    // 预计算 px 值（避免 toPx() 作用域问题）
    val blur4Px = with(density) { 4.dp.toPx() }
    val lens16Px = with(density) { 16.dp.toPx() }
    val lens32Px = with(density) { 32.dp.toPx() }
    val tabCount = TAB_ITEMS.size

    // 弹簧动画
    val indicatorOffset = remember { Animatable(0f) }
    val tabWidthPx = with(density) {
        ((config.screenWidthDp.dp - 32.dp - 8.dp - ((tabCount - 1) * 4).dp) / tabCount).toPx()
    }

    LaunchedEffect(Unit) {
        indicatorOffset.snapTo(selectedIndex.toFloat() * tabWidthPx)
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex }
            .drop(1)
            .collectLatest { idx ->
                indicatorOffset.animateTo(
                    idx.toFloat() * tabWidthPx,
                    spring(dampingRatio = 0.7f, stiffness = 400f)
                )
                onTabSelected(idx)
            }
    }

    // ── backdrop 源：纯色背景 ──
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 选中指示器（弹簧滑动的玻璃胶囊）
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = indicatorOffset.value + 4.dp.toPx()
                    translationY = 4.dp.toPx()
                }
                .width(with(density) { tabWidthPx.toDp() - 8.dp })
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (isLightTheme) Color.White.copy(alpha = 0.4f)
                    else Color.White.copy(alpha = 0.08f)
                )
        )

        // 标签行：前三项为玻璃圆形，第四项为染色玻璃圆形按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .layerBackdrop(backdrop),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TAB_ITEMS.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val isLast = index == TAB_ITEMS.lastIndex

                if (isLast) {
                    // ── 染色玻璃圆形按钮（"个人"）──
                    Box(
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(blur4Px)
                                    lens(lens16Px, lens32Px)
                                },
                                onDrawSurface = {
                                    val tint = accentColor
                                    drawRect(tint, blendMode = BlendMode.Hue)
                                    drawRect(tint.copy(alpha = 0.75f))
                                }
                            )
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab
                            ) { selectedIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                    }
                } else {
                    // ── 普通玻璃圆形标签 ──
                    Box(
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(blur4Px)
                                    lens(lens16Px, lens32Px)
                                },
                                onDrawSurface = {
                                    drawRect(
                                        Color.White.copy(
                                            alpha = if (isSelected) 0.5f else 0.25f
                                        )
                                    )
                                }
                            )
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab
                            ) { selectedIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) accentColor
                                else if (isLightTheme) Color(0xFF888888)
                                else Color(0xFFAAAAAA)
                            )
                            Text(
                                text = item.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.W600 else FontWeight.Normal,
                                color = if (isSelected) accentColor
                                else if (isLightTheme) Color(0xFF888888)
                                else Color(0xFFAAAAAA)
                            )
                        }
                    }
                }
            }
        }
    }
}
