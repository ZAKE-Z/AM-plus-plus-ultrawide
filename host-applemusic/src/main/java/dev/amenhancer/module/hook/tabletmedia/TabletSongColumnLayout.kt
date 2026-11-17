package dev.amenhancer.module.hook.tabletmedia

import android.content.Context
import android.content.ContextWrapper
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.amenhancer.module.ModuleConstants
import dev.amenhancer.module.config.TargetConfigClient
import dev.amenhancer.module.hook.ConstraintLayoutPane
import dev.amenhancer.module.hook.SongColumnVariant
import dev.amenhancer.module.hook.TabletArtworkLayoutPolicy
import dev.amenhancer.module.hook.AlphaGradientEdgeFieldProfiles
import dev.amenhancer.module.hook.ModernXposedRuntime
import dev.amenhancer.module.hook.TranslationsPopupOffsetHook
import dev.amenhancer.module.hook.targetBuild
import dev.amenhancer.module.host.OwnedHostProperty
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/** Apply reference geometry to the real bound native controls without replacing or reparenting them. */
internal class TabletSongColumnLayout(
    private val player: ViewGroup, private val songHost: ViewGroup, private val collapse: () -> Unit,
    private val modes: TabletPlaybackModes? = null, private val keepNativeFooter: Boolean = false,
    private val variant: SongColumnVariant = SongColumnVariant.CENTERED,
    /** Legacy route only: the module-created lyrics host whose visibility the switcher owns. */
    private val lyricsHost: View? = null,
    private val onRightPaneChanged: () -> Unit = {},
    private val isTranslationActive: () -> Boolean = { false },
    private val onTranslateClick: () -> Unit = {},
) : AutoCloseable {
    companion object {
        @Suppress("DEPRECATION")
        fun collapsePlayer(player: View) {
            var view: View? = player
            while (view != null) {
                if (view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_COLLAPSE, null)) return
                view = view.parent as? View
            }
            activity(player)?.onBackPressed()
        }
        private fun activity(view: View): android.app.Activity? {
            var context: Context = view.context
            while (context is ContextWrapper) {
                if (context is android.app.Activity) return context
                val base = context.baseContext
                if (base === context) break
                context = base
            }
            return null
        }
        private fun matrixValues(image: ImageView) = FloatArray(9).also(image.imageMatrix::getValues).toList()
    }
    private val ids = HashMap<String, Int>()
    private fun resource(name: String, type: String = "id") = ids.getOrPut("$type/$name") {
        player.resources.getIdentifier(name, type, ModuleConstants.TARGET_PACKAGE)
    }
    private fun find(root: View, name: String): View? = resource(name).takeIf { it != 0 }?.let { root.findViewById(it) }
    // Visual CSS units must match the reference browser, independently of Android dp.
    private val scale get() = TabletArtworkLayoutPolicy.REFERENCE_PIXEL_SCALE
    private fun css(value: Float) = (value * scale).roundToInt()
    private fun dp(value: Float) = (value * player.resources.displayMetrics.density).roundToInt()
    private var insetTop = 0
    val topInset get() = insetTop
    private var insetBottom = 0
    private val playerLocation = IntArray(2)
    private val windowLocation = IntArray(2)
    private var closed = false
    // Snapshot with the player session, rather than decoding settings on animation frames.
    private val hideStatusBarEnabled = TargetConfigClient.currentSettings().dualPaneHideStatusBarEnabled
    private var fullscreenWindow: Window? = null
    private var restoreStatusBar = false
    private var statusBarBehavior: OwnedHostProperty<Int>? = null
    private var legacyFullscreen: OwnedHostProperty<Boolean>? = null
    private var current: ViewGroup? = null
    private var currentQueue: ViewGroup? = null
    private var frame: TabletArtworkLayoutPolicy.SongColumn? = null
    val currentGeometry get() = frame
    private var presentedFrame: TabletArtworkLayoutPolicy.SongColumn? = null
    private val columns = ArrayList<ViewGroup>()
    private val queueColumns = ArrayList<FrameLayout>()
    private val queueGradients = IdentityHashMap<ViewGroup, View>()
    private val states = IdentityHashMap<View, Properties>()
    private val overlay = FrameLayout(player.context).apply { clipChildren = false; clipToPadding = false }
    private val thumb = FrameLayout(player.context).apply {
        contentDescription = resource("collapse_player", "string").takeIf { it != 0 }
            ?.let(player.resources::getString) ?: "收起播放器"
        isFocusable = true; setOnClickListener { collapse() }
    }
    private val thumbBar = View(player.context).apply {
        background = GradientDrawable().apply { setColor(0x2dffffff); cornerRadius = css(4f).toFloat() }
    }
    private var lastModeState: TabletPlaybackModes.State? = null
    private var lastModeRead = 0L
    /** Supplemental buttons retain native glyphs; only their interaction follows MediaButton. */
    private inner class ModeButton(description: String) : ImageButton(player.context) {
        private var feedbackReady = false
        private var pulseAnimator: ValueAnimator? = null
        private var glowAnimator: ValueAnimator? = null
        private var pendingPulse = false
        private var iconScale = 1f
        private var glowAlpha = 0f
        private var glowTarget = 0f
        private val cssEase = PathInterpolator(.25f, .1f, .25f, 1f)
        private val glow = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT) }
        init {
            background = glow; setPadding(0, 0, 0, 0)
            // FIT_XY gives VectorDrawable its final pixel bounds. MATRIX keeps its
            // intrinsic bounds and can scale a cached raster in hardware rendering.
            scaleType = ImageView.ScaleType.FIT_XY; cropToPadding = false
            contentDescription = description
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            feedbackReady = true
        }
        private fun animationsEnabled() = android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
        fun pulse() {
            if (!isEnabled || !isShown || !animationsEnabled()) return
            if (isPressed) { pendingPulse = true; return }
            pulseAnimator?.cancel()
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700; interpolator = LinearInterpolator()
                addUpdateListener {
                    val time = it.animatedValue as Float
                    val (start, end, local) = when {
                        time < .2f -> Triple(1f, .85f, time / .2f)
                        time < .5f -> Triple(.85f, 1.1f, (time - .2f) / .3f)
                        else -> Triple(1.1f, 1f, (time - .5f) / .5f)
                    }
                    iconScale = start + (end - start) * cssEase.getInterpolation(local.coerceIn(0f, 1f))
                    invalidate()
                }
                start()
            }
        }
        override fun drawableStateChanged() {
            super.drawableStateChanged()
            // View's constructor can dispatch this before subclass initialization.
            if (!feedbackReady) return
            if (!isEnabled || !isShown) { stopFeedback(); return }
            if (isPressed) {
                pulseAnimator?.cancel(); pulseAnimator = null; iconScale = 1f; pendingPulse = false
            } else if (pendingPulse) { pendingPulse = false; pulse() }
            val target = if (isPressed || isHovered) 34f else 0f
            if (target == glowTarget) return
            glowTarget = target; glowAnimator?.cancel()
            if (!animationsEnabled()) { glowAlpha = target; updateGlow(); return }
            glowAnimator = ValueAnimator.ofFloat(glowAlpha, target).apply {
                duration = 300; interpolator = cssEase
                addUpdateListener { glowAlpha = it.animatedValue as Float; updateGlow() }
                start()
            }
        }
        private fun updateGlow() { glow.setColor(Color.argb(glowAlpha.roundToInt(), 255, 255, 255)) }
        fun stopFeedback() {
            if (pulseAnimator == null && glowAnimator == null && !pendingPulse && iconScale == 1f && glowAlpha == 0f) return
            pulseAnimator?.cancel(); pulseAnimator = null
            glowAnimator?.cancel(); glowAnimator = null
            pendingPulse = false; iconScale = 1f; glowAlpha = 0f; glowTarget = 0f
            updateGlow(); invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            if (iconScale == 1f || isPressed) { super.onDraw(canvas); return }
            val saved = canvas.save()
            canvas.scale(iconScale, iconScale, width / 2f, height / 2f)
            super.onDraw(canvas)
            canvas.restoreToCount(saved)
        }
        override fun onDetachedFromWindow() { stopFeedback(); super.onDetachedFromWindow() }
    }
    private val shuffle = ModeButton("随机播放").apply { setOnClickListener {
        modes?.toggleShuffle(); pulse(); lastModeState = null; player.postInvalidateOnAnimation()
    } }
    private val repeat = ModeButton("循环播放").apply { setOnClickListener {
        modes?.cycleRepeat(); pulse(); lastModeState = null; player.postInvalidateOnAnimation()
    } }
    private val volume = IosVolumeSliderView(player.context, referenceLayout = true, referencePixelScale = { scale })
    private val output = ImageButton(player.context).apply {
        background = null; scaleType = ImageView.ScaleType.FIT_XY
        setPadding(0, 0, 0, 0)
        setImageDrawable(IosOutputDrawable.referenceAirPlay(scale).fittedToGlyph())
        alpha = .45f; contentDescription = "媒体输出设备"
        setOnClickListener { PlatformAudioOutputSwitcher.open(player.context) }
    }
    /** V2 bottom switcher: the translation, lyrics, and playlist row. */
    private var rightPaneIsQueueState = false
    val rightPaneIsQueue get() = rightPaneIsQueueState
    /** Swap the right pane between the lyrics and the playlist (queue) fragment. */
    fun setRightPaneQueue(queue: Boolean) {
        if (variant != SongColumnVariant.COVER_LEFT_SWITCHER || rightPaneIsQueueState == queue) return
        rightPaneIsQueueState = queue
        switcherBtnState(switchLyrics, !rightPaneIsQueueState)
        switcherBtnState(switchQueue, rightPaneIsQueueState)
        onRightPaneChanged()
    }
    private fun switcherIconBtn(native: String, description: String, fallback: Drawable, onClick: () -> Unit): ImageButton {
        return ImageButton(player.context).apply {
            background = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Small padding so the icon fills the circle prominently while keeping the circle size intact.
            setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            contentDescription = description
            val id = resource(native, "drawable")
            if (id != 0) setImageResource(id) else setImageDrawable(fallback)
            setOnClickListener { onClick() }
        }
    }
    private val switchTranslate = switcherIconBtn("selector_nowplaying_translations", "翻译", PureTranslateGlyph()) {
        onTranslateClick()
    }.apply {
        setImageDrawable(PureTranslateGlyph())
    }
    private val switchLyrics = switcherIconBtn("selector_nowplaying_lyrics", "歌词", LyricsLinesDrawable()) {
        setRightPaneQueue(false)
    }
    private val switchQueue = switcherIconBtn("selector_nowplaying_queue", "播放列表", QueueRowsDrawable()) {
        setRightPaneQueue(true)
    }
    private fun switcherBtnState(btn: ImageButton, selected: Boolean) {
        if (selected) {
            btn.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x40ffffff) }
            btn.alpha = 1f
        } else {
            btn.background = null
            btn.alpha = .45f
        }
    }
    val ready get() = !closed && frame != null && (current != null || currentQueue != null)
    fun readyForPane(songVisible: Boolean) = ready && (if (songVisible) current != null else currentQueue != null)
    init {
        thumb.addView(thumbBar, FrameLayout.LayoutParams(1, 1))
        listOf(thumb, shuffle, repeat, volume, output).forEach {
            overlay.addView(it, FrameLayout.LayoutParams(1, 1))
        }
        if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) {
            TranslationsPopupOffsetHook.customAnchorProvider = { switchTranslate }
            listOf(switchTranslate, switchLyrics, switchQueue).forEach {
                it.visibility = View.INVISIBLE
                overlay.addView(it, FrameLayout.LayoutParams(1, 1))
            }
        }
        player.addView(overlay, ViewGroup.LayoutParams(-1, -1)); overlay.visibility = View.INVISIBLE
        overlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { fullscreen(false) }
        })
    }
    /** Own only status-bar visibility; navigation bars and decor fitting remain native. */
    fun fullscreenForProgress(progress: Float) {
        // Keep the viewport stable throughout collapse. Restoring bars at its
        // first frame changes insets while native code is animating to the mini.
        // Unconditionally hide the status bar in full-screen expanded player mode.
        val shouldHide = player.isShown && progress > .001f &&
            (progress >= .999f || fullscreenWindow != null)
        fullscreen(shouldHide)
    }
    @Suppress("DEPRECATION")
    fun fullscreen(expanded: Boolean) {
        if (!expanded || closed) {
            val window = fullscreenWindow ?: return
            if (android.os.Build.VERSION.SDK_INT >= 30 && restoreStatusBar)
                window.insetsController?.show(WindowInsets.Type.systemBars())
            statusBarBehavior?.close(); statusBarBehavior = null
            legacyFullscreen?.close(); legacyFullscreen = null
            fullscreenWindow = null; restoreStatusBar = false
            return
        }
        if (fullscreenWindow != null || !player.isAttachedToWindow) return
        val window = activity(player)?.window ?: return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val controller = window.insetsController ?: return
            restoreStatusBar = window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) ?: true
            statusBarBehavior = OwnedHostProperty({ controller.systemBarsBehavior }, { controller.systemBarsBehavior = it })
                .also { it.set(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE) }
            // Hide both status bars and navigation bars for complete immersive full-screen playback
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            val flag = WindowManager.LayoutParams.FLAG_FULLSCREEN
            legacyFullscreen = OwnedHostProperty({ window.attributes.flags and flag != 0 }, {
                if (it) window.addFlags(flag) else window.clearFlags(flag)
            }).also { it.set(true) }
        }
        fullscreenWindow = window
    }
    /** Same untranslated layout space used by the native artwork transition. */
    fun origin(view: View): Pair<Int, Int> {
        var x = 0; var y = 0; var child = view
        while (child !== player) {
            x += child.left; y += child.top
            child = child.parent as? View ?: return 0 to 0
        }
        return x to y
    }
    fun geometry(topInset: Int = insetTop, bottomInset: Int = insetBottom): TabletArtworkLayoutPolicy.SongColumn? {
        insetTop = topInset.coerceAtLeast(0); insetBottom = bottomInset.coerceAtLeast(0)
        if (current == null && currentQueue == null) { frame = null; return null }
        player.getLocationInWindow(playerLocation); player.rootView.getLocationInWindow(windowLocation)
        val safe = if (android.os.Build.VERSION.SDK_INT >= 30)
            player.rootWindowInsets?.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or
                android.view.WindowInsets.Type.displayCutout())?.let { it.left to it.right }
        else @Suppress("DEPRECATION") player.rootWindowInsets?.let { it.systemWindowInsetLeft to it.systemWindowInsetRight }
        val leftInset = (windowLocation[0] + (safe?.first ?: 0) - playerLocation[0]).coerceAtLeast(0)
        val rightInset = (playerLocation[0] + player.width - windowLocation[0] - player.rootView.width +
            (safe?.second ?: 0)).coerceAtLeast(0)
        val width = player.width - leftInset - rightInset
        val base = TabletArtworkLayoutPolicy.songColumn(width, player.height, scale, variant = variant)
        val nativeLabelHeight = base?.let { nativeProgressLabelHeight(it.contentWidth) } ?: 0
        val layout = if (base != null && nativeLabelHeight > base.progressHeight - base.progressTrackHeight - base.progressLabelGap)
            TabletArtworkLayoutPolicy.songColumn(width, player.height, scale, nativeLabelHeight, variant) else base
        frame = layout?.let { it.copy(contentLeft = it.contentLeft + leftInset,
                coverLeft = it.coverLeft + leftInset,
                metadataLeft = it.metadataLeft + leftInset,
                queueLeft = it.queueLeft + leftInset,
                thumbCenterX = it.thumbCenterX + leftInset,
                switcherCenterX = it.switcherCenterX + leftInset,
                coverTop = it.coverTop - insetTop, thumbCenterY = it.thumbCenterY - insetTop,
                controlsTop = it.controlsTop - insetTop, controlsBottom = it.controlsBottom - insetTop,
                metadataTop = it.metadataTop - insetTop, progressTop = it.progressTop - insetTop,
                mediaTop = it.mediaTop - insetTop, volumeTop = it.volumeTop - insetTop,
                modeTop = it.modeTop - insetTop,
                switchTop = if (it.switchTop < 0) -1 else it.switchTop - insetTop,
                outputLeft = it.outputLeft + leftInset, outputTop = it.outputTop - insetTop) }
        return frame
    }
    /** Measure only on layout preparation, preserving every native badge property. */
    private fun nativeProgressLabelHeight(width: Int): Int {
        var height = 0
        for (column in columns + queueColumns) {
            for (name in listOf("audio_badge_with_text", "audio_badge_no_text", "karaoke_badge")) {
                val badge = find(column, name)?.takeIf { it.visibility != View.GONE } ?: continue
                val params = badge.layoutParams ?: continue
                badge.measure(
                    ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), 0, params.width),
                    ViewGroup.getChildMeasureSpec(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), 0, params.height))
                val margins = params as? ViewGroup.MarginLayoutParams
                height = maxOf(height, badge.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0))
            }
        }
        return height
    }
    fun prepare(topInset: Int = insetTop, bottomInset: Int = insetBottom): Boolean {
        if (closed) return false
        states.values.forEach { it.changed = false }; columns.clear(); queueColumns.clear()
        fun visit(view: View) {
            if (view is ViewGroup && view !== overlay) {
                if (view.id == resource("player_container") && find(view, "artwork_container")?.parent === view &&
                    (find(view, "title") as? TextView)?.parent === view &&
                    (find(view, "subtitle") as? TextView)?.parent === view &&
                    find(view, "player_controls")?.parent === view) columns += view
                else if (view is FrameLayout && view.id == resource("controls") &&
                    find(view, "player_controls")?.parent === view && find(view, "play_pause") != null)
                    queueColumns += view
                else for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(songHost)
        current = columns.filter { it.isShown }.maxByOrNull { effectiveAlpha(it) } ?: columns.firstOrNull()
        currentQueue = queueColumns.filter { it.isShown }.maxByOrNull { effectiveAlpha(it) } ?: queueColumns.firstOrNull()
        val layout = geometry(topInset, bottomInset) ?: run {
            val changed = states.isNotEmpty()
            states.values.toList().asReversed().forEach { it.close() }; states.clear()
            overlay.visibility = View.INVISIBLE; volume.setPageVisible(false); return changed
        }
        columns.forEach { prepareColumn(it, layout) }
        queueColumns.forEach { prepareQueue(it, layout) }
        queueGradients.keys.retainAll(queueColumns)
        val queueRoots = queueColumns.mapNotNull { it.parent as? View }
        states.keys.toList().filter { view ->
            var node: View? = view
            while (node != null && node !in columns && node !in queueRoots)
                node = node.parent as? View
            node == null
        }.forEach { states.remove(it)?.close() }
        return states.values.any { it.changed }
    }
    private fun prepareColumn(column: ViewGroup, g: TabletArtworkLayoutPolicy.SongColumn) {
        val controls = find(column, "player_controls") as? ViewGroup ?: return
        val at = origin(column); val left = g.metadataLeft - at.first; val top = insetTop - at.second
        fun bounds(view: View, x: Int, y: Int, width: Int, height: Int) = properties(view).bounds(x, y, width, height)
        val collaborator = find(column, "collaborator_pill")?.takeIf { it.visibility == View.VISIBLE }
        val extra = collaborator?.height ?: 0
        collaborator?.let { bounds(it, left, top + g.metadataTop, g.metadataWidth, extra) }
        val actions = listOf("list_left_icon", "list_favorite_icon", "emoji_button")
            .mapNotNull { find(column, it) }.filter { it.visibility == View.VISIBLE }
        val gap = css(8f)
        val textTop = top + g.metadataTop + extra
        if (variant == SongColumnVariant.CENTERED) {
            // Upstream layout: the actions share the metadata row, right-aligned.
            val textWidth = (g.metadataWidth - actions.size * (g.menuSize + gap)).coerceAtLeast(1)
            listOf("title", "subtitle").forEachIndexed { index, name ->
                (find(column, name) as? TextView)?.let { text ->
                    bounds(text, left, textTop + index * g.lineHeight, textWidth, g.lineHeight)
                    properties(text).text(g.metadataFontPx); properties(text).padding(0, 0, 0, 0)
                }
            }
            actions.forEachIndexed { index, view ->
                bounds(view, left + g.metadataWidth - g.menuSize - index * (g.menuSize + gap),
                    textTop + g.lineHeight - g.menuSize / 2, g.menuSize, g.menuSize)
                properties(view).padding(0, 0, 0, 0)
                (view as? ImageView)?.let { properties(it).image(g.menuSize) }
            }
        } else {
            // TV reference: title and artist centered across the column width.
            listOf("title", "subtitle").forEachIndexed { index, name ->
                (find(column, name) as? TextView)?.let { text ->
                    bounds(text, left, textTop + index * g.lineHeight, g.metadataWidth, g.lineHeight)
                    properties(text).text(if (index == 0) g.metadataFontPx else g.artistFontPx)
                    properties(text).padding(0, 0, 0, 0)
                    properties(text).gravity(Gravity.CENTER_HORIZONTAL)
                    text.textAlignment = View.TEXT_ALIGNMENT_CENTER
                }
            }
            // Directly position native favorite (slot 2) and more (slot 3) views!
            // Let native list_favorite_icon execute its original rendering method (filled star when favorited).
            val favView = find(column, "list_favorite_icon")
            val moreView = find(column, "list_left_icon")
            find(column, "emoji_button")?.let { properties(it).emptySlot(true) }

            val actionSize = minOf(g.menuSize, g.modeHeight)
            val modeY = top + g.modeTop + (g.modeHeight - actionSize) / 2
            // Pad 22% so icons scale down an additional ~20% inside the hit box
            val pad = (actionSize * 0.22f).toInt()

            favView?.let { v ->
                val slotX = slotLeft(g, 2, 4, actionSize) - at.first
                bounds(v, slotX, modeY, actionSize, actionSize)
                properties(v).padding(pad, pad, pad, pad)
                properties(v).clearBackground()
                v.bringToFront()
                v.isClickable = true
                (v as? ImageView)?.scaleType = ImageView.ScaleType.FIT_CENTER
            }

            moreView?.let { v ->
                val slotX = slotLeft(g, 3, 4, actionSize) - at.first
                bounds(v, slotX, modeY, actionSize, actionSize)
                properties(v).padding(pad, pad, pad, pad)
                properties(v).clearBackground()
                v.bringToFront()
                v.isClickable = true
                (v as? ImageView)?.scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        val bottom = if (keepNativeFooter) maxOf(g.controlsBottom, player.height - insetTop - insetBottom) else g.controlsBottom
        bounds(controls, g.contentLeft - at.first, top + g.progressTop, g.contentWidth, (bottom - g.progressTop).coerceAtLeast(1))
        prepareControls(controls, g, extra, g.contentWidth)
    }
    /** Evenly spread [count] round buttons across the controls column; slot 0/1 are shuffle/repeat. */
    private fun slotLeft(g: TabletArtworkLayoutPolicy.SongColumn, index: Int, count: Int, size: Int): Int =
        if (count <= 1) g.contentLeft + (g.contentWidth - size) / 2
        else g.contentLeft + ((g.contentWidth - size).toLong() * index / (count - 1)).toInt()
    private fun prepareQueue(column: FrameLayout, g: TabletArtworkLayoutPolicy.SongColumn) {
        val controls = find(column, "player_controls") as? ViewGroup ?: return
        val parent = column.parent as? ViewGroup ?: return
        val at = origin(parent)
        val top = insetTop + g.progressTop - at.second
        val parentHeight = parent.height.takeIf { it > 0 } ?: (player.height - at.second)
        val height = maxOf(parentHeight - top, g.controlsBottom - g.progressTop, 1)
        properties(column).bounds((g.queueLeft - at.first).coerceAtLeast(0), top, g.queueWidth, height)
        properties(column).padding(0, 0, 0, 0)
        properties(controls).frameBounds(g.queueWidth, height)
        prepareControls(controls, g, 0, g.queueWidth)
        if (variant == SongColumnVariant.CENTERED) {
            find(parent, "queue_main_content")?.let { properties(it).bottomPadding(height) }
            find(parent, "recycler_view_gradients")?.let {
                properties(it).prepareQueueMask()
                queueGradients[column] = it
            }
        } else {
            // New TV layout: completely clean up queue view. Hide fixed top history header,
            // remove bottom padding cutoff, and do not apply any clipping mask so the list
            // scrolls cleanly from top to bottom with a consistent continuous background.
            find(parent, "current_player_item")?.let { properties(it).emptySlot(true) }
            find(parent, "queue_main_content")?.let {
                properties(it).bottomPadding(0)
                (it as? androidx.recyclerview.widget.RecyclerView)?.clipToPadding = false
            }
            find(parent, "recycler_view_gradients")?.let { gView ->
                (gView as? ViewGroup)?.clipChildren = false
                gView.background = null
            }
        }
    }
    private fun prepareControls(controls: ViewGroup, g: TabletArtworkLayoutPolicy.SongColumn, extra: Int, columnWidth: Int) {
        properties(controls).padding(0, 0, 0, 0); properties(controls).allowOverflow()
        fun bounds(view: View, x: Int, y: Int, width: Int, height: Int) = properties(view).bounds(x, y, width, height)
        listOf("seek_bar_controls", "live_radio_container").mapNotNull { find(controls, it) }.forEach { progress ->
            bounds(progress, 0, extra, columnWidth, g.progressHeight)
            properties(progress).padding(0, 0, 0, 0)
            (progress as? LinearLayout)?.let { properties(it).verticalStack() }
            find(progress, "progress")?.let { track -> properties(track).apply {
                size(columnWidth, g.progressTrackHeight); minimumHeight(0); padding(0, 0, 0, 0)
                verticalMargins(0, 0); progressThickness(css(8f))
            } }
            val labels = listOf("current_time_progress", "total_time_progress")
                .mapNotNull { find(progress, it) as? TextView }
            val labelHeight = (g.progressHeight - g.progressTrackHeight - g.progressLabelGap).coerceAtLeast(1)
            (labels.firstOrNull()?.parent as? ViewGroup)?.takeIf { it.parent === progress }?.let { labelsRoot ->
                properties(labelsRoot).apply {
                    size(columnWidth, labelHeight); verticalMargins(g.progressLabelGap, 0); padding(0, 0, 0, 0)
                    allowOverflow()
                }
            }
            labels.forEach { label -> properties(label).apply {
                text(g.labelFontPx); verticalMargins(0, 0)
                if (variant != SongColumnVariant.CENTERED) {
                    padding(0, 0, 0, 0)
                    minimumHeight(0)
                    size(label.layoutParams.width, labelHeight)
                }
            } }
            // Audio-quality and karaoke badges retain their native wrap-content
            // size, padding, minimums, font, drawable, alpha and state binding.
        }
        val mediaHeight = (g.mediaHeight - extra).coerceAtLeast(0)
        val button = minOf(g.mediaButtonSize, mediaHeight)
        // Transport buttons: previous at far left, play/pause centered, next at far right.
        listOf("previous_rewind", "play_pause", "next_fast_forward").forEachIndexed { index, name ->
            (find(controls, name) as? ImageView)?.let { view ->
                val x = when (index) {
                    0 -> 0
                    1 -> (columnWidth - button) / 2
                    else -> columnWidth - button
                }
                bounds(view, x, g.mediaTop - g.progressTop + extra + (mediaHeight - button) / 2, button, button)
                properties(view).emptySlot(button <= 0)
                // Preserve Apple's glyph; normalize only its visual size to the reference
                // SVG extent (skip path uses 80/134 of its box; Apple's uses 39/40).
                val glyph = if (name == "play_pause") g.playIconSize
                    else (g.skipIconSize * (80f / 134f) / (39f / 40f)).roundToInt()
                properties(view).image(glyph)
            }
        }
    }
    private fun effectiveAlpha(view: View): Float {
        var alpha = 1f; var node: View? = view
        while (node != null && node !== player) { alpha *= node.alpha; node = node.parent as? View }
        return alpha
    }
    fun present(expansion: Float, transitioning: Boolean, songVisible: Boolean, queueVisible: Boolean = false) {
        fullscreenForProgress(expansion)
        val g = frame; val column = if (songVisible) current else if (queueVisible) currentQueue else null
        if (closed || g == null || column == null || !column.isShown || !player.isShown || expansion <= .001f) {
            overlay.visibility = View.INVISIBLE; volume.setPageVisible(false)
            output.isEnabled = false
            if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) {
                listOf(switchTranslate, switchLyrics, switchQueue).forEach { it.visibility = View.INVISIBLE }
            }
            shuffle.stopFeedback(); repeat.stopFeedback(); return
        }
        val interactive = !transitioning && expansion >= .999f
        output.isEnabled = interactive
        if (!interactive) { shuffle.stopFeedback(); repeat.stopFeedback() }
        if (queueVisible && variant == SongColumnVariant.CENTERED) queueGradients[column]?.takeIf { it.width > 0 && it.height > 0 }?.let { gradients ->
            val boundary = (insetTop + g.progressTop - origin(gradients).second - css(8f)).coerceIn(0, gradients.height)
            val fade = resource("queue_bottom_gradient_height", "dimen").takeIf { it != 0 }
                ?.let(player.resources::getDimensionPixelSize) ?: css(24f)
            properties(gradients).queueMask(boundary, fade)
        }
        overlay.alpha = effectiveAlpha(column); overlay.translationX = songHost.translationX; overlay.z = songHost.z + dp(2f)
        // Bottom controls stay at the window edge when the song column centers.
        output.translationX = -overlay.translationX
        if (!interactive && presentedFrame == g && overlay.width == player.width && overlay.height == player.height) {
            overlay.visibility = View.VISIBLE
            thumb.isEnabled = false; shuffle.isEnabled = false; repeat.isEnabled = false
            volume.setPageVisible(false)
            thumb.visibility = if (songVisible) View.VISIBLE else View.INVISIBLE
            return
        }
        place(overlay, 0, 0, player.width, player.height)
        val center = insetTop + g.thumbCenterY; val desiredHeight = maxOf(dp(44f), g.thumbHeight)
        val hitTop = (center - desiredHeight / 2).coerceAtLeast(0)
        val hitHeight = (minOf(insetTop + g.coverTop, player.height, center + desiredHeight / 2) - hitTop).coerceAtLeast(g.thumbHeight)
        val hitWidth = maxOf(dp(44f), g.thumbWidth)
        place(thumb, g.thumbCenterX - hitWidth / 2, hitTop, hitWidth, hitHeight)
        place(thumbBar, (hitWidth - g.thumbWidth) / 2, center - hitTop - g.thumbHeight / 2, g.thumbWidth, g.thumbHeight)
        (thumbBar.background as GradientDrawable).cornerRadius = g.thumbHeight / 2f; thumb.isEnabled = interactive
        if (variant != SongColumnVariant.CENTERED) {
            thumb.visibility = View.INVISIBLE
            thumb.isEnabled = false
        }
        if (!songVisible) thumb.visibility = View.INVISIBLE
        if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) {
            // TV reference bottom row: 3 large round icon buttons [翻译] [歌词] [播放列表]
            if (g.switchTop >= 0) {
                val btnSize = minOf(g.switchHeight, dp(54f))
                val barTop = insetTop + g.switchTop + (g.switchHeight - btnSize) / 2
                val btnCount = 3
                val btnSpacing = (g.contentWidth - btnSize * btnCount) / (btnCount + 1)
                val btnY = barTop

                val xTranslate = g.contentLeft + btnSpacing
                val xLyrics = g.contentLeft + btnSpacing * 2 + btnSize
                val xQueue = g.contentLeft + btnSpacing * 3 + btnSize * 2

                place(switchTranslate, xTranslate, btnY, btnSize, btnSize)
                place(switchLyrics, xLyrics, btnY, btnSize, btnSize)
                place(switchQueue, xQueue, btnY, btnSize, btnSize)

                switchTranslate.isEnabled = interactive
                switchLyrics.isEnabled = interactive
                switchQueue.isEnabled = interactive

                switcherBtnState(switchTranslate, isTranslationActive())
                switcherBtnState(switchLyrics, !rightPaneIsQueueState)
                switcherBtnState(switchQueue, rightPaneIsQueueState)
            } else {
                listOf(switchTranslate, switchLyrics, switchQueue).forEach { it.visibility = View.INVISIBLE }
            }
        }
        // Volume slider is completely removed for cover-left variants.
        if (variant == SongColumnVariant.CENTERED) {
            place(volume, g.contentLeft, insetTop + g.volumeTop, g.contentWidth, g.volumeHeight)
            volume.setPageVisible(interactive)
        } else {
            volume.visibility = View.INVISIBLE
            volume.setPageVisible(false)
        }
        presentedFrame = g
        if (modes == null) { shuffle.visibility = View.INVISIBLE; repeat.visibility = View.INVISIBLE; return }
        val now = android.os.SystemClock.uptimeMillis()
        if (lastModeState == null || now - lastModeRead >= 200L) { lastModeState = modes.state(); lastModeRead = now }
        val state = lastModeState ?: TabletPlaybackModes.State()
        val modeRowTop = if (variant == SongColumnVariant.CENTERED) g.mediaTop else g.modeTop
        val modeRowHeight = if (variant == SongColumnVariant.CENTERED) g.mediaHeight else g.modeHeight
        val modeSize = (if (variant == SongColumnVariant.CENTERED) g.mediaButtonSize
            else minOf(g.menuSize, modeRowHeight)).coerceAtLeast(1)
        fun mode(view: ModeButton, x: Int, drawable: String, enabled: Boolean, selected: Boolean) {
            val id = resource(drawable, "drawable")
            if (id == 0) { view.visibility = View.INVISIBLE; return }
            if (view.tag != id) { view.setImageResource(id); view.drawable?.mutate(); view.tag = id }
            view.isEnabled = interactive && enabled; view.isSelected = selected
            view.background = null
            val inset = (modeSize - g.modeIconSize) / 2
            val opposite = modeSize - g.modeIconSize - inset
            if (view.paddingLeft != inset || view.paddingRight != opposite || view.paddingTop != inset || view.paddingBottom != opposite)
                view.setPadding(inset, inset, opposite, opposite)
            place(view, x, insetTop + modeRowTop + (modeRowHeight - modeSize) / 2, modeSize, modeSize)
        }
        if (variant == SongColumnVariant.CENTERED) {
            mode(shuffle, g.contentLeft, if (state.shuffle == true) "ic_nowplaying_shuffleon" else "ic_nowplaying_shuffle", state.canShuffle, state.shuffle == true)
            mode(repeat, g.contentLeft + g.contentWidth - modeSize,
                when (state.repeat) { 1 -> "ic_nowplaying_repeatoneon"; 2 -> "ic_nowplaying_repeaton"; else -> "ic_nowplaying_repeat" },
                state.canRepeat, state.repeat != null && state.repeat != 0)
        } else {
            // Slot 0: shuffle, Slot 1: repeat; Slots 2 (favorite) & 3 (more) are native views in player_container
            mode(shuffle, slotLeft(g, 0, 4, modeSize),
                if (state.shuffle == true) "ic_nowplaying_shuffleon" else "ic_nowplaying_shuffle", state.canShuffle, state.shuffle == true)
            mode(repeat, slotLeft(g, 1, 4, modeSize),
                when (state.repeat) { 1 -> "ic_nowplaying_repeatoneon"; 2 -> "ic_nowplaying_repeaton"; else -> "ic_nowplaying_repeat" },
                state.canRepeat, state.repeat != null && state.repeat != 0)
        }
    }
    private fun imageMatrix(image: ImageView, size: Int): List<Float>? {
        val drawable = image.drawable ?: return null
        val width = drawable.intrinsicWidth; val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return null
        val factor = size.toFloat() / maxOf(width, height)
        return FloatArray(9).also { values -> Matrix().apply {
            setScale(factor, factor)
            postTranslate((image.layoutParams.width - width * factor) / 2f, (image.layoutParams.height - height * factor) / 2f)
            getValues(values)
        } }.toList()
    }
    private fun place(view: View, x: Int, y: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) { view.visibility = View.INVISIBLE; return }
        (view.layoutParams as? FrameLayout.LayoutParams)?.let { p ->
            if (p.width != width || p.height != height || p.leftMargin != x || p.topMargin != y) {
                p.width = width; p.height = height; p.leftMargin = x; p.topMargin = y; view.layoutParams = p
            }
        }
        val layout = view.isLayoutRequested || view.left != x || view.top != y || view.width != width || view.height != height
        if (view.isLayoutRequested || view.measuredWidth != width || view.measuredHeight != height)
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        if (layout) view.layout(x, y, x + width, y + height)
        view.visibility = View.VISIBLE
    }
    private fun properties(view: View) = states.getOrPut(view) { Properties(view) }
    private inner class Properties(private val view: View) : AutoCloseable {
        var changed = false
        private val owned = LinkedHashMap<String, OwnedHostProperty<Any?>>()
        private var queueGradientFailed = false
        private val queueFadeAccess by lazy {
            runCatching {
                val type = view.javaClass
                check(type.name == "com.apple.android.music.common.views.AlphaGradientFrameLayout")
                checkNotNull(AlphaGradientEdgeFieldProfiles.resolve(type, targetBuild(view.context)))
                // Positions may be null (the native shader then distributes stops evenly).
                // Its two-array setter requires non-null, equally sized arrays. Keep the
                // verified positions field only to restore that original null exactly.
                val positions = type.getDeclaredField("f").apply { isAccessible = true }
                check(positions.type == FloatArray::class.java)
                listOf(type.getMethod("getFadeSizes"), type.getMethod("getBottomFadeColors"),
                    type.getMethod("getBottomFadePositions"), type.getMethod("d", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                    type.getMethod("b", IntArray::class.java, FloatArray::class.java)) to positions
            }.onFailure { failQueueGradient(it) }.getOrNull()
        }
        private var maskTarget: Triple<Int, List<Int>, List<Float>>? = null
        private var maskFade = -1
        private var maskBoundary = -1
        private var maskHeight = -1
        private var maskClip: Rect? = null
        fun prepareQueueMask() { queueFadeAccess }
        fun queueMask(boundary: Int, fade: Int) {
            val clip = maskClip?.takeIf { it.right == view.width && it.bottom == boundary }
                ?: Rect(0, 0, view.width, boundary).also { maskClip = it }
            // Enforce the footer boundary even if the optional native fade is unavailable.
            // The native viewport stays full-height, so padding is not subtracted twice.
            set("queueClip", { view.clipBounds }, { view.clipBounds = it }, clip)
            if (queueGradientFailed) return
            val (access, positionsField) = queueFadeAccess ?: return
            if (maskTarget == null || maskBoundary != boundary || maskHeight != view.height || maskFade != fade) {
                val feather = minOf(fade, boundary).coerceAtLeast(1)
                val size = (view.height - boundary + feather).coerceAtLeast(1)
                maskTarget = Triple(size, listOf(Color.BLACK, Color.TRANSPARENT, Color.TRANSPARENT),
                    listOf(0f, (feather.toFloat() / size).coerceIn(0f, 1f), 1f))
                maskBoundary = boundary; maskHeight = view.height; maskFade = fade
            }
            val target = checkNotNull(maskTarget)
            runCatching {
                set("queueFadeSize", { (access[0].invoke(view) as Rect).bottom }, { value ->
                    val current = access[0].invoke(view) as Rect
                    access[3].invoke(view, current.top, value)
                    view.invalidate()
                }, target.first)
                // Colors and stops form one native ramp; publishing them separately can
                // combine a new three-stop array with a two-stop or null native array.
                val ramp: Pair<List<Int>, List<Float>?> = target.second to target.third
                set("queueFadeRamp", {
                    (access[1].invoke(view) as IntArray).toList() to (access[2].invoke(view) as FloatArray?)?.toList()
                }, { value ->
                    val colors = value.first.toIntArray()
                    val stops = value.second?.toFloatArray()
                        ?: FloatArray(colors.size) { it.toFloat() / (colors.size - 1) }
                    access[4].invoke(view, colors, stops)
                    if (value.second == null) positionsField.set(view, null)
                    view.invalidate()
                }, ramp)
            }.onFailure { failQueueGradient(it) }
        }
        private fun releaseQueueGradient() {
            listOf("queueFadeRamp", "queueFadeSize").forEach { key ->
                runCatching { owned.remove(key)?.close() }.onFailure {
                    ModernXposedRuntime.log("Tablet queue gradient restore failed", it)
                }
            }
        }
        private fun failQueueGradient(error: Throwable) {
            queueGradientFailed = true
            releaseQueueGradient()
            ModernXposedRuntime.log("Tablet queue fade unavailable; retaining footer clip and player layout", error)
        }
        private val params get() = view.layoutParams as ViewGroup.MarginLayoutParams
        private val anchors by lazy {
            listOf("leftToLeft", "leftToRight", "rightToLeft", "rightToRight", "startToStart", "startToEnd",
                "endToStart", "endToEnd", "topToTop", "topToBottom", "bottomToTop", "bottomToBottom").associateWith { name ->
                checkNotNull(ConstraintLayoutPane.constraintField(params.javaClass, name)) { "${params.javaClass.name}.$name missing" }
            }
        }
        private fun <T> set(key: String, read: () -> T, write: (T) -> Unit, value: T): Boolean {
            @Suppress("UNCHECKED_CAST")
            val property = owned.getOrPut(key) { OwnedHostProperty({ read() }, { write(it as T) }) }
            if (read() == value) return false
            property.set(value); changed = true; return true
        }
        fun bounds(x: Int, y: Int, width: Int, height: Int) {
            size(width, height)
            val anchor = if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) "endToEnd" else "startToStart"
            anchors.forEach { (name, field) ->
                set(name, { field.getInt(params) }, { field.setInt(params, it) }, if (name == anchor || name == "topToTop") 0 else -1)
            }
            set("margins", { listOf(params.leftMargin, params.topMargin, params.rightMargin, params.bottomMargin, params.marginStart, params.marginEnd) }, { v ->
                params.setMargins(v[0], v[1], v[2], v[3]); params.marginStart = v[4]; params.marginEnd = v[5]
            }, listOf(x, y, 0, 0, if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) 0 else x, if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) x else 0))
            if (changed) view.layoutParams = params
        }
        fun size(width: Int, height: Int) {
            if (set("size", { view.layoutParams.width to view.layoutParams.height }, {
                    view.layoutParams.width = it.first; view.layoutParams.height = it.second
                }, width to height)) view.layoutParams = view.layoutParams
        }
        fun frameBounds(width: Int, height: Int) {
            size(width, height)
            val p = params as FrameLayout.LayoutParams
            set("frameGravity", { p.gravity }, { p.gravity = it }, Gravity.TOP or Gravity.LEFT)
            set("frameMargins", { listOf(p.leftMargin, p.topMargin, p.rightMargin, p.bottomMargin, p.marginStart, p.marginEnd) }, { v ->
                p.setMargins(v[0], v[1], v[2], v[3]); p.marginStart = v[4]; p.marginEnd = v[5]
            }, List(6) { 0 })
            if (changed) view.layoutParams = p
        }
        fun padding(left: Int, top: Int, right: Int, bottom: Int) = set("padding",
            { listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom) },
            { view.setPadding(it[0], it[1], it[2], it[3]) }, listOf(left, top, right, bottom))
        fun bottomPadding(bottom: Int) = set("bottomPadding", { view.paddingBottom },
            { view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, it) }, bottom)
        fun verticalMargins(top: Int, bottom: Int) {
            if (set("verticalMargins", { params.topMargin to params.bottomMargin }, { params.topMargin = it.first; params.bottomMargin = it.second }, top to bottom))
                view.layoutParams = params
        }
        fun verticalStack() {
            val group = view as LinearLayout
            set("orientation", { group.orientation }, { group.orientation = it }, LinearLayout.VERTICAL)
            set("gravity", { group.gravity }, { group.gravity = it }, Gravity.TOP or Gravity.START)
        }
        fun minimumHeight(value: Int) = set("minimumHeight", { view.minimumHeight }, { view.minimumHeight = it }, value)
        fun emptySlot(empty: Boolean) {
            if (empty && view.visibility != View.GONE)
                set("emptyVisibility", { view.visibility }, { view.visibility = it }, View.INVISIBLE)
            else owned.remove("emptyVisibility")?.close()
        }
        /** Toggle a pane's presence without touching its constraints; restored to the native value on close. */
        fun visible(visibility: Int) = set("paneVisibility", { view.visibility }, { view.visibility = it }, visibility)
        fun progressThickness(height: Int) {
            val progress = view as? ProgressBar ?: return
            if (android.os.Build.VERSION.SDK_INT < 29) return
            set("progressMinHeight", { progress.minHeight }, { progress.minHeight = it }, height)
            set("progressMaxHeight", { progress.maxHeight }, { progress.maxHeight = it }, height)
        }
        fun allowOverflow() {
            val group = view as ViewGroup
            set("clipChildren", { group.clipChildren }, { group.clipChildren = it }, false)
            set("clipToPadding", { group.clipToPadding }, { group.clipToPadding = it }, false)
        }
        fun text(sizePx: Float) {
            val text = view as TextView
            set("textSize", { text.textSize }, { text.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }, sizePx)
            set("fontPadding", { text.includeFontPadding }, { text.includeFontPadding = it }, false); minimumHeight(0)
        }
        fun gravity(g: Int) {
            val text = view as? TextView ?: return
            set("textGravity", { text.gravity }, { text.gravity = it }, g)
        }
        fun clearBackground() {
            set("viewBackground", { view.background }, { view.background = it }, null)
        }
        fun image(sizePx: Int) {
            val image = view as ImageView
            set("scaleType", { image.scaleType }, { image.scaleType = it }, ImageView.ScaleType.MATRIX)
            set("cropToPadding", { image.cropToPadding }, { image.cropToPadding = it }, false); padding(0, 0, 0, 0)
            imageMatrix(image, sizePx)?.let { values ->
                set("imageMatrix", { matrixValues(image) }, { image.imageMatrix = Matrix().apply { setValues(it.toFloatArray()) } }, values)
            }
        }
        override fun close() {
            releaseQueueGradient()
            owned.values.toList().asReversed().forEach { it.close() }; owned.clear(); view.layoutParams = view.layoutParams
        }
    }
    override fun close() {
        if (closed) return
        fullscreen(false)
        closed = true; volume.close(); player.removeView(overlay)
        TranslationsPopupOffsetHook.customAnchorProvider = null
        lyricsHost?.let { if (it.visibility == View.INVISIBLE) it.visibility = View.VISIBLE }
        states.values.toList().asReversed().forEach { it.close() }; states.clear(); columns.clear(); queueColumns.clear(); queueGradients.clear()
        current = null; currentQueue = null; frame = null
    }
}
