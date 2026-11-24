package dev.amenhancer.layoutpreview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.singleWindowApplication
import dev.amenhancer.module.hook.SongColumnTuning
import dev.amenhancer.module.hook.SongColumnVariant
import dev.amenhancer.module.hook.TabletArtworkLayoutPolicy

/**
 * Desktop mirror of the tablet dual-pane player. It renders the real
 * TabletArtworkLayoutPolicy geometry for a chosen device (resolution + density)
 * so tuned values can be pasted back into the module as constants.
 *
 * Run with: ./gradlew :layout-preview:run
 */
fun main() = singleWindowApplication(title = "AM++ 布局预览（真实策略代码）") {
    MaterialTheme(darkColorScheme()) { PreviewApp() }
}

private const val PIXEL_SCALE = TabletArtworkLayoutPolicy.REFERENCE_PIXEL_SCALE
private const val SIDE_MARGIN_DP = 18
private const val LYRICS_INSET_DP = 48
private const val RIGHT_GAP_DP = 16

@Composable
private fun PreviewApp() {
    var resW by remember { mutableStateOf(2560f) }
    var resH by remember { mutableStateOf(1600f) }
    var density by remember { mutableStateOf(2.5f) }
    val variant = SongColumnVariant.COVER_LEFT_SWITCHER
    var v2Controls by remember { mutableStateOf(false) }
    var tuning by remember { mutableStateOf(SongColumnTuning()) }
    var hint by remember { mutableStateOf("拖拽预览中的元素直接调整；拖动封面的水平位置、右下角缩放封面、拖控件块上下移动。") }

    Row(Modifier.fillMaxSize().background(Color(0xFF14141B))) {
        // ---------- 画布 ----------
        Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
            Canvas(Modifier.fillMaxSize().pointerInput(variant, v2Controls) {
                detectDragGestures(
                    onDragStart = { offset ->
                        hint = hitTest(offset.x, offset.y, geometryOf(resW, resH, density, variant, tuning), variant, viewScale)
                    },
                ) { change, drag ->
                    val (t, h) = applyDrag(change.position.x - drag.x, change.position.y - drag.y, drag.x, drag.y,
                        geometryOf(resW, resH, density, variant, tuning), variant, tuning, viewScale, resW, resH)
                    if (t != null) { tuning = t; hint = h }
                }
            }) {
                viewScale = size.width.coerceAtMost(size.height * resW / resH) / resW
                drawPlayer(this, resW, resH, density, variant, v2Controls, tuning)
            }
            Text(hint, Modifier.align(Alignment.BottomStart).padding(6.dp),
                color = Color(0xFF9A9AA5), fontSize = 11.sp)
        }

        // ---------- 参数栏 ----------
        Column(
            Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardSection("设备") {
                NumberField("分辨率宽 (px)", resW) { resW = it.coerceIn(1200f, 4000f) }
                NumberField("分辨率高 (px)", resH) { resH = it.coerceIn(800f, 3000f) }
                NumberField("DPI（density = dpi/160）", density * 160f) { density = (it / 160f).coerceIn(1f, 4f) }
                Text("density = ${"%.2f".format(density)}x · 逻辑宽度 ${resW / density * 1f / 1f}dp".let { _ ->
                    "%.0f × %.0f dp".format(resW / density, resH / density)
                }, color = Color(0xFF9A9AA5), fontSize = 11.sp)
                DevicePresets { w, h, d -> resW = w.toFloat(); resH = h.toFloat(); density = d / 160f }
            }
            CardSection("右侧面板") {
                if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !v2Controls, onClick = { v2Controls = false }, label = { Text("右面板: 歌词") })
                        FilterChip(selected = v2Controls, onClick = { v2Controls = true }, label = { Text("右面板: 控件") })
                    }
                }
            }
            CardSection("布局参数（拖拽或滑块）") {
                SliderRow("封面高度比", tuning.coverHeightFraction, 0.4f, 0.85f) { tuning = tuning.copy(coverHeightFraction = it) }
                SliderRow("左列宽度比", tuning.songWidthFraction, 0.38f, 0.62f) { tuning = tuning.copy(songWidthFraction = it) }
                SliderRow("封面左边距 css", tuning.coverLeftInsetCss, 0f, 80f) { tuning = tuning.copy(coverLeftInsetCss = it) }
                SliderRow("控件块高度比", tuning.controlsRowFraction, 0.18f, 0.4f) { tuning = tuning.copy(controlsRowFraction = it) }
                SliderRow("控件块垂直偏移 css", tuning.controlsCenterBiasCss, -120f, 120f) { tuning = tuning.copy(controlsCenterBiasCss = it) }
                if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) {
                    SliderRow("切换条宽 css", tuning.switcherStripCss, 40f, 140f) { tuning = tuning.copy(switcherStripCss = it) }
                    SliderRow("右面板内边距 css", tuning.switcherPanePadCss, 8f, 80f) { tuning = tuning.copy(switcherPanePadCss = it) }
                    SliderRow("传输按钮组宽 css", tuning.transportSpreadCss, 0f, 800f) { tuning = tuning.copy(transportSpreadCss = it) }
                }
            }
            CardSection("导出（粘贴回代码）") {
                val report = exportText(tuning, resW, resH, density)
                Text(report, Modifier.border(1.dp, Color(0xFF33333D)).background(Color(0xFF1B1B22)).padding(8.dp),
                    fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFD7D7E0))
                Button(onClick = {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(report), null)
                    hint = "已复制到剪贴板 —— 把内容发回给助手即可写回源码。"
                }) { Text("复制") }
                Button(onClick = { tuning = SongColumnTuning() }, Modifier.padding(start = 8.dp)) { Text("恢复默认") }
            }
        }
    }
    // Canvas 尺寸在 draw 时才知道；viewScale 用全局缓存传递（桌面工具的简化处理）
}

/** 最近一次绘制的画布缩放，供指针命中测试复用。 */
private var viewScale = 1f

private fun geometryOf(resW: Float, resH: Float, density: Float, variant: SongColumnVariant, tuning: SongColumnTuning) =
    PreviewGeometry(resW.toInt(), resH.toInt(), density, variant, tuning)

private data class PreviewGeometry(
    val resW: Int, val resH: Int, val density: Float,
    val variant: SongColumnVariant, val tuning: SongColumnTuning,
) {
    val column: TabletArtworkLayoutPolicy.SongColumn? =
        TabletArtworkLayoutPolicy.songColumn(resW, resH, PIXEL_SCALE, 0, variant, tuning)
    val sideGap = SIDE_MARGIN_DP * density
    val lyricsInset = LYRICS_INSET_DP * density
    val rightGap = RIGHT_GAP_DP * density
    val songHostWidth: Float = if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) resW - sideGap
        else (tuning.songWidthFraction * resW + lyricsInset - 2 * sideGap).coerceAtLeast(1f)
    val rightWidth: Float = (resW - tuning.songWidthFraction * resW - 2 * rightGap).coerceAtLeast(1f)
}

// ---------------- 绘制 ----------------

private fun drawPlayer(scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    resW: Float, resH: Float, density: Float, variant: SongColumnVariant, v2Controls: Boolean, tuning: SongColumnTuning) {
    val g = geometryOf(resW, resH, density, variant, tuning)
    val s = viewScale
    val c = g.column ?: return
    fun x(v: Float) = v * s
    fun y(v: Float) = v * s

    // 背景
    scope.drawRect(Color(0xFF0F0F16), size = scope.size)

    // song host / right host 边界
    scope.drawRoundRect(Color(0xFF23232D), topLeft = androidx.compose.ui.geometry.Offset(x(g.sideGap), y(0f)),
        size = androidx.compose.ui.geometry.Size(x(g.songHostWidth), y(resH)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * s))
    val rightLeft = resW - g.rightGap - g.rightWidth
    if (variant != SongColumnVariant.COVER_LEFT_SWITCHER || !v2Controls) {
        scope.drawRoundRect(Color(0xFF2A2A36), topLeft = androidx.compose.ui.geometry.Offset(x(rightLeft), y(g.rightGap)),
            size = androidx.compose.ui.geometry.Size(x(g.rightWidth), y(resH - 2 * g.rightGap)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * s))
    }

    // 歌词面板
    if (variant != SongColumnVariant.COVER_LEFT_SWITCHER || !v2Controls) {
        val rows = listOf("Baby I'm just trying to play it cool", "But I just can't hide that I want you",
            "This time I want you you you", "Like it's magnetic")
        rows.forEachIndexed { i, line ->
            val active = i == 1
            scope.drawRoundRect(if (active) Color(0xFFB9B0E8) else Color(0xFF494958),
                topLeft = androidx.compose.ui.geometry.Offset(x(rightLeft + g.rightGap + g.rightWidth * 0.06f), y(resH * (0.30f + i * 0.11f))),
                size = androidx.compose.ui.geometry.Size(x(g.rightWidth * (if (active) 0.62f else 0.5f)), y(resH * (if (active) 0.045f else 0.028f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s))
        }
    }

    // 封面
    scope.drawRoundRect(Color(0xFF7C4DC4), topLeft = androidx.compose.ui.geometry.Offset(x(c.coverLeft.toFloat()), y(c.coverTop.toFloat())),
        size = androidx.compose.ui.geometry.Size(x(c.coverSize.toFloat()), x(c.coverSize.toFloat())),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * s))

    // thumb 把手
    scope.drawRoundRect(Color(0xFF9A9AA5), topLeft = androidx.compose.ui.geometry.Offset(x(c.thumbCenterX - c.thumbWidth / 2f), y(c.thumbCenterY - c.thumbHeight / 2f)),
        size = androidx.compose.ui.geometry.Size(x(c.thumbWidth.toFloat()), y(c.thumbHeight.toFloat())),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(c.thumbHeight / 2f * s))

    // 右面板控件块
    if (v2Controls) {
        // metadata
        scope.drawRoundRect(Color(0xFFE8E8F0), topLeft = androidx.compose.ui.geometry.Offset(x(c.metadataLeft.toFloat()), y(c.metadataTop.toFloat())),
            size = androidx.compose.ui.geometry.Size(x(c.metadataWidth * 0.55f), y(c.lineHeight * 0.55f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s))
        scope.drawRoundRect(Color(0xFF8F8F9C), topLeft = androidx.compose.ui.geometry.Offset(x(c.metadataLeft.toFloat()), y(c.metadataTop + c.lineHeight * 1.05f)),
            size = androidx.compose.ui.geometry.Size(x(c.metadataWidth * 0.35f), y(c.lineHeight * 0.42f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s))
        // 收藏等 actions
        repeat(2) { i ->
            scope.drawCircle(Color(0xFF8F8F9C), radius = x(c.menuSize / 2f),
                center = androidx.compose.ui.geometry.Offset(x(c.metadataLeft + c.metadataWidth - c.menuSize * (i + 0.6f)), y(c.metadataTop + c.lineHeight * 0.75f)))
        }
        // 进度条 + 时间
        scope.drawRoundRect(Color(0xFF55555F), topLeft = androidx.compose.ui.geometry.Offset(x(c.contentLeft.toFloat()), y(c.progressTop.toFloat())),
            size = androidx.compose.ui.geometry.Size(x(c.contentWidth.toFloat()), y(c.progressTrackHeight.toFloat())), cornerRadius = androidx.compose.ui.geometry.CornerRadius(c.progressTrackHeight / 2f * s))
        scope.drawRoundRect(Color(0xFFDDDDF0), topLeft = androidx.compose.ui.geometry.Offset(x(c.contentLeft.toFloat()), y(c.progressTop.toFloat())),
            size = androidx.compose.ui.geometry.Size(x(c.contentWidth * 0.38f), y(c.progressTrackHeight.toFloat())), cornerRadius = androidx.compose.ui.geometry.CornerRadius(c.progressTrackHeight / 2f * s))
        // 传输按钮组
        val spread = if (c.transportSpreadWidth > 0) c.transportSpreadWidth else c.contentWidth
        val lead = c.contentLeft + (c.contentWidth - spread) / 2
        val labels = listOf("🔀", "⏮", "▶", "⏭", "🔁")
        labels.forEachIndexed { i, _ ->
            val isPlay = i == 2
            val r = x(if (isPlay) c.playIconSize / 2f else c.skipIconSize / 2f) * 1.15f
            val gapCount = 4
            val cx = (lead + spread * (i + 1) / gapCount).toFloat()
            scope.drawCircle(if (isPlay) Color(0xFFDDDDF0) else Color(0xFF9A9AA5), radius = r,
                center = androidx.compose.ui.geometry.Offset(x(cx), y(c.mediaTop + c.mediaHeight / 2f)))
        }
        // 音量条
        scope.drawRoundRect(Color(0xFF55555F), topLeft = androidx.compose.ui.geometry.Offset(x(c.contentLeft.toFloat()), y(c.volumeTop + c.volumeHeight * 0.4f)),
            size = androidx.compose.ui.geometry.Size(x(c.contentWidth * 0.8f), y(c.volumeHeight * 0.14f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * s))
        // shuffle/repeat mode 圆
        scope.drawCircle(Color(0xFF9A9AA5), radius = x(c.modeIconSize / 2f), center = androidx.compose.ui.geometry.Offset(x(c.contentLeft + c.modeIconSize / 2f), y(c.mediaTop + c.mediaHeight / 2f)))
        scope.drawCircle(Color(0xFF9A9AA5), radius = x(c.modeIconSize / 2f), center = androidx.compose.ui.geometry.Offset(x(c.contentLeft + c.contentWidth - c.modeIconSize / 2f), y(c.mediaTop + c.mediaHeight / 2f)))
    }

    // 切换器
    if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) {
        val mid = resH / 2f
        val r = 46f * density / 2f
        scope.drawCircle(if (v2Controls) Color(0xFF8A6FDB) else Color(0xFF3A3A46), radius = x(r * 2f) / 2f,
            center = androidx.compose.ui.geometry.Offset(x(c.switcherCenterX.toFloat()), y(mid - r - 5f * density)), style = Stroke(2f * s))
        scope.drawCircle(if (!v2Controls) Color(0xFF8A6FDB) else Color(0xFF3A3A46), radius = x(r * 2f) / 2f,
            center = androidx.compose.ui.geometry.Offset(x(c.switcherCenterX.toFloat()), y(mid + r + 5f * density)), style = Stroke(2f * s))
    }
}

// ---------------- 拖拽 ----------------

private enum class DragTarget { NONE, COVER_X, COVER_SIZE, CONTROLS_Y, SWITCHER_STRIP, TRANSPORT_W }

private fun hitTest(px: Float, py: Float, g: PreviewGeometry, variant: SongColumnVariant, scale: Float): String {
    val c = g.column ?: return "几何不可用"
    val x = px / scale; val y = py / scale
    val cover = c.coverLeft.toFloat()..(c.coverLeft + c.coverSize).toFloat()
    val inCover = x in cover && y in c.coverTop.toFloat()..(c.coverTop + c.coverSize).toFloat()
    return when {
        inCover && (x > c.coverLeft + c.coverSize * 0.75f && y > c.coverTop + c.coverSize * 0.75f) -> "拖动封面右下角：缩放封面"
        inCover -> "拖动封面：水平移动封面"
        variant == SongColumnVariant.COVER_LEFT_SWITCHER && kotlin.math.abs(x - c.switcherCenterX) < 80f * 1f -> "拖动：调整切换条位置/宽度"
        x > c.contentLeft - 60f && x < c.contentLeft + c.contentWidth + 60f && y > c.controlsTop - 60f && y < c.controlsBottom + 60f ->
            "拖动控件块：垂直移动"
        else -> "空白区域"
    }
}

private fun applyDrag(prevX: Float, prevY: Float, dx: Float, dy: Float, g: PreviewGeometry,
    variant: SongColumnVariant, tuning: SongColumnTuning, scale: Float, resW: Float, resH: Float): Pair<SongColumnTuning?, String> {
    val c = g.column ?: return null to "几何不可用"
    val x = prevX / scale; val y = prevY / scale; val dxCss = dx / scale / PIXEL_SCALE; val dyCss = dy / scale / PIXEL_SCALE
    val heightCss = resH / PIXEL_SCALE
    val inCover = x in c.coverLeft.toFloat()..(c.coverLeft + c.coverSize).toFloat() && y in c.coverTop.toFloat()..(c.coverTop + c.coverSize).toFloat()
    return when {
        inCover && (x > c.coverLeft + c.coverSize * 0.78f && y > c.coverTop + c.coverSize * 0.78f) -> {
            val newSizeCss = c.coverSize / PIXEL_SCALE + dxCss
            tuning.copy(coverHeightFraction = (newSizeCss / heightCss).coerceIn(0.35f, 0.9f)) to "封面高度比 → ${"%.3f".format((newSizeCss / heightCss))}"
        }
        inCover -> tuning.copy(coverLeftInsetCss = (tuning.coverLeftInsetCss + dxCss).coerceIn(0f, 90f)) to
            "封面左边距 → ${"%.0f".format(tuning.coverLeftInsetCss + dxCss)} css"
        variant == SongColumnVariant.COVER_LEFT_SWITCHER && kotlin.math.abs(x - c.switcherCenterX) < 90f -> {
            val strip = (tuning.switcherStripCss + dxCss).coerceIn(40f, 150f)
            tuning.copy(switcherStripCss = strip) to "切换条宽 → ${"%.0f".format(strip)} css"
        }
        x > c.contentLeft - 70f && x < c.contentLeft + c.contentWidth + 70f && y > c.controlsTop - 70f && y < c.controlsBottom + 70f -> {
            val bias = (tuning.controlsCenterBiasCss + dyCss).coerceIn(-140f, 140f)
            tuning.copy(controlsCenterBiasCss = bias) to "控件块垂直偏移 → ${"%.0f".format(bias)} css"
        }
        else -> null to ""
    }
}

// ---------------- 小组件 ----------------

@Composable
private fun CardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFF33333D)).background(Color(0xFF191920)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color(0xFF9A9AA5), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun NumberField(label: String, value: Float, onCommit: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), color = Color(0xFFD7D7E0), fontSize = 12.sp)
        OutlinedTextField(text, { text = it; text.toFloatOrNull()?.let(onCommit) },
            Modifier.width(88.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun SliderRow(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFFD7D7E0), fontSize = 12.sp)
            Text("%.2f".format(value), color = Color(0xFFB9B0E8), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(value.coerceIn(min, max), onChange, valueRange = min..max)
    }
}

@Composable
private fun DevicePresets(apply: (Int, Int, Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton({ apply(2560, 1600, 400) }) { Text("Tab S9", fontSize = 11.sp) }
        TextButton({ apply(2880, 1800, 440) }) { Text("米板7Pro", fontSize = 11.sp) }
        TextButton({ apply(2000, 1200, 320) }) { Text("小新Pad", fontSize = 11.sp) }
        TextButton({ apply(2160, 1916, 400) }) { Text("折叠屏", fontSize = 11.sp) }
    }
}

private fun exportText(tuning: SongColumnTuning, resW: Float, resH: Float, density: Float): String = buildString {
    appendLine("// AM++ 布局调优导出（设备 ${resW.toInt()}×${resH.toInt()} @ ${"%.0f".format(density * 160)}dpi / ${"%.2f".format(density)}x）")
    appendLine("// 写回 core/src/main/kotlin/dev/amenhancer/module/hook/TabletArtworkLayoutPolicy.kt")
    appendLine("// （SongColumnTuning 默认值，或由助手改写对应常量）")
    appendLine("SongColumnTuning(")
    appendLine("    coverHeightFraction = ${"%.3f".format(tuning.coverHeightFraction)}f,")
    appendLine("    controlsRowFraction = ${"%.3f".format(tuning.controlsRowFraction)}f,")
    appendLine("    songWidthFraction = ${"%.3f".format(tuning.songWidthFraction)}f,")
    appendLine("    coverControlsGapCss = ${"%.1f".format(tuning.coverControlsGapCss)}f,")
    appendLine("    coverLeftInsetCss = ${"%.1f".format(tuning.coverLeftInsetCss)}f,")
    appendLine("    switcherStripCss = ${"%.1f".format(tuning.switcherStripCss)}f,")
    appendLine("    switcherPanePadCss = ${"%.1f".format(tuning.switcherPanePadCss)}f,")
    appendLine("    transportSpreadCss = ${"%.1f".format(tuning.transportSpreadCss)}f,")
    appendLine("    controlsCenterBiasCss = ${"%.1f".format(tuning.controlsCenterBiasCss)}f,")
    appendLine(")")
}
