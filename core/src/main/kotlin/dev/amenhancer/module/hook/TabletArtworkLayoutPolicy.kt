package dev.amenhancer.module.hook

data class TabletArtworkLayout(
    val sizePx: Float,
    val edgeGapPx: Float,
)

/** Resting arrangement of the song column: where the cover sits and what shares its row. */
enum class SongColumnVariant {
    /** Upstream layout: the centered cover stacks above the controls inside the 45% column. */
    CENTERED,
    /** Cover plus title/album on the left, with a lyrics/queue switcher. */
    COVER_LEFT_SWITCHER,
}

/**
 * Live-tunable knobs of the cover-left variants, exercised by the desktop layout
 * preview. Defaults reproduce the compiled constants exactly; the module always
 * builds with the defaults, and a tuned copy is pasted back as new constants.
 */
data class SongColumnTuning(
    /** Cover side against the track height; replaces every height branch when changed. */
    val coverHeightFraction: Float = 0.6f,
    /** Height of the stacked metadata/progress/media/volume block, as a fraction of the viewport. */
    val controlsRowFraction: Float = 0.25f,
    /** Width share of the song grid column for both cover-left variants. */
    val songWidthFraction: Float = 0.52f,
    /** Horizontal gap between the cover column and the middle controls column, in CSS px. */
    val coverControlsGapCss: Float = 16f,
    /** Padding between the column start and the cover's left edge, in CSS px. */
    val coverLeftInsetCss: Float = 0f,
    /** Middle switcher strip width, in CSS px. */
    val switcherStripCss: Float = 72f,
    /** Padding between the switcher strip and the right pane content, in CSS px. */
    val switcherPanePadCss: Float = 28f,
    /** Transport group width in CSS px; 0 keeps the 1.5 × cover default. */
    val transportSpreadCss: Float = 0f,
    /** Vertical bias of the centered controls block in CSS px; positive shifts it down. */
    val controlsCenterBiasCss: Float = 0f,
)

/**
 * Center the unchanged native cover inside the vertical interval between the
 * player top and Apple's metadata barrier, leaving equal edge gaps whenever
 * the native cover fits in that interval.
 */
object TabletArtworkLayoutPolicy {
    /** Resting square follows the measured progress track, capped by the space above metadata. */
    fun forProgress(availableHeightPx: Float, progressWidthPx: Float): TabletArtworkLayout? {
        if (!availableHeightPx.isFinite() || !progressWidthPx.isFinite() ||
            availableHeightPx <= 0f || progressWidthPx <= 0f) return null
        val size = minOf(availableHeightPx, progressWidthPx)
        return TabletArtworkLayout(size, (availableHeightPx - size) / 2f)
    }

    /** All positions are local to player_root; insets are resolved against its expanded viewport. */
    fun nativeTopMarginInPlayer(
        hostTopPx: Float,
        metadataTopPx: Float,
        parentTopPx: Float,
        nativeSizePx: Float,
        topInsetPx: Float,
    ): Int? {
        if (!topInsetPx.isFinite() || topInsetPx < 0f) return null
        return nativeTopMargin(maxOf(hostTopPx, topInsetPx), metadataTopPx, parentTopPx, nativeSizePx)
    }

    /** Express centering in layout space, visible to the native descendant-rectangle animator. */
    fun nativeTopMargin(
        playerTopPx: Float,
        metadataTopPx: Float,
        parentTopPx: Float,
        nativeSizePx: Float,
    ): Int? {
        if (!playerTopPx.isFinite() || !metadataTopPx.isFinite() || !parentTopPx.isFinite() || !nativeSizePx.isFinite()) return null
        val layout = resolve(metadataTopPx - playerTopPx, nativeSizePx) ?: return null
        val margin = playerTopPx + layout.edgeGapPx - parentTopPx
        return if (margin.isFinite()) kotlin.math.round(margin).toInt() else null
    }

    fun resolve(
        availableHeightPx: Float,
        nativeSizePx: Float,
    ): TabletArtworkLayout? {
        if (availableHeightPx <= 0f || nativeSizePx <= 0f) return null
        val edgeGapPx = ((availableHeightPx - nativeSizePx) / 2f).coerceAtLeast(0f)
        return TabletArtworkLayout(
            sizePx = nativeSizePx,
            edgeGapPx = edgeGapPx,
        )
    }

    // YouLyPlus' horizontal.module.css resolved in its browser's CSS coordinate space.
    // The reference runs at Windows 225% scaling and browser 100% zoom. Android dp
    // is a separate input space: using its density changes both CSS constants and
    // media-query branches even when the two player viewports have identical pixels.
    // Keep one CSS-to-physical scale for geometry, glyphs and supplemental drawing.
    const val REFERENCE_PIXEL_SCALE = 2.25f
    private const val COMPACT_HEIGHT_CSS_PX = 768f
    private const val CHROME_HEIGHT_CSS_PX = 1000f
    private const val TALL_HEIGHT_CSS_PX = 1080f
    private const val SHORT_HEIGHT_CSS_PX = 640f
    private const val TINY_HEIGHT_CSS_PX = 512f
    private const val COMPACT_WIDTH_CSS_PX = 1600f
    private const val PHONE_WIDTH_CSS_PX = 480f

    private const val ROOT_EM_CSS_PX = 16f
    private const val COMPACT_ROOT_EM_CSS_PX = 12.8f

    private const val WIDE_ROW_GAP_CSS_PX = 8
    private const val COMPACT_ROW_GAP_CSS_PX = 2
    private const val ROW_GAP_COUNT = 5
    private const val COLUMN_GAP_COUNT = 2

    const val SONG_WIDTH_FRACTION = 0.45f
    /**
     * The cover-left variants follow the 1800x720 TV/CarPlay reference: the song host
     * spans the 500px cover column plus the 580px controls column (60% of the width),
     * leaving the 720px right pane to lyrics or the playlist.
     */
    fun songWidthFraction(variant: SongColumnVariant): Float =
        if (variant == SongColumnVariant.CENTERED) SONG_WIDTH_FRACTION else 0.6f
    private const val CONTROLS_ROW_FRACTION = 0.25f
    private const val DRAG_ROW_FRACTION = 0.45f
    private const val DRAG_MIN_CSS_PX = 30
    private const val BOTTOM_ROW_FRACTION = 0.3f
    private const val COMPACT_BOTTOM_ROW_FRACTION = 0.2f
    private const val BOTTOM_TOOL_EM = 4f
    private const val COMPACT_BOTTOM_TOOL_EM = 3f

    private const val COVER_HEIGHT_FRACTION = 0.6f
    private const val CHROME_COVER_HEIGHT_FRACTION = 0.55f
    private const val TINY_COVER_HEIGHT_FRACTION = 0.5f
    private const val COVER_WIDTH_FRACTION = 0.38f

    private const val THUMB_BAND_VH = 0.04f
    private const val THUMB_HANDLE_CSS_PX = 50
    private const val THUMB_HANDLE_HEIGHT_CSS_PX = 8

    private const val CONTROLS_PAD_EM = 1.75f
    private const val CONTROLS_PAD_OFFSET_CSS_PX = 8f

    private const val METADATA_FONT_VH = 0.02f
    private const val METADATA_LINE_HEIGHT_EM = 1.25f
    private const val METADATA_LINE_COUNT = 2f
    /** Artist line relative to the title line (increased to 28/38 per user request). */
    private const val ARTIST_FONT_RATIO = 28f / 38f
    private const val METADATA_PAD_TOP_VH = 0.02f
    private const val METADATA_PAD_BOTTOM_VH = 0.01f

    private const val LABEL_FONT_VH = 0.012f
    private const val LABEL_FONT_EM = 0.8f
    private const val LABEL_LINE_HEIGHT = 1.2f
    private const val LABEL_MARGIN_CSS_PX = 4f
    private const val PROGRESS_TRACK_MIN_CSS_PX = 24f

    private const val VOLUME_ICON_CSS_PX = 40f
    private const val COMPACT_VOLUME_ICON_CSS_PX = 24f

    private const val MENU_SIZE_VH = 0.035f
    private const val MENU_QUERY_WIDTH_CSS_PX = 1000f
    private const val MENU_MIN_EM = 2f
    private const val MENU_MAX_VW = 0.05f
    private const val MENU_MAX_VH = 0.04f

    /** Middle strip that hosts the stacked play/lyrics switcher buttons. */
    private const val SWITCHER_STRIP_CSS_PX = 72f
    /** Breathing room between the switcher strip and the right pane content. */
    private const val SWITCHER_PANE_PAD_CSS_PX = 28f

    // TV/CarPlay reference (1800x720): 500 / 580 / 720 columns, cover 440.
    private const val COVER_COLUMN_FRACTION = 500f / 1800f
    private const val CENTER_COLUMN_FRACTION = 580f / 1800f
    private const val CENTER_COLUMN_PAD_LEFT_FRACTION = 20f / 1800f
    private const val CENTER_COLUMN_PAD_RIGHT_FRACTION = 46f / 1800f
    private const val TV_COVER_HEIGHT_FRACTION = 440f / 720f
    private const val TV_COVER_COLUMN_FILL = 0.88f
    /** Title size of the TV reference (enlarged 20% per user request: 38 * 1.20 = 45.6 / 720). */
    private const val TV_TITLE_FONT_VH = 45.6f / 720f
    /** Gap between stacked rows of the middle column (tightened slightly per request). */
    private const val TV_ROW_GAP_VH = 16f / 720f
    /** Extra gap before the bottom switcher row (tightened slightly). */
    private const val TV_SWITCHER_TOP_GAP_VH = 22f / 720f
    /** Transport row height, shrunk 10% per user request (78 * 1.8 * 0.9 / 720 = 126.36 / 720). */
    private const val MEDIA_ROW_FRACTION = (78f * 1.8f * 0.90f) / 720f
    /** Secondary actions row height, shrunk 10% (48 * 2.0 * 0.9 / 720 = 86.4 / 720). */
    private const val MODE_ROW_FRACTION = (48f * 2.0f * 0.90f) / 720f
    /** Lyrics/playlist switcher row height, shrunk 10% (64 * 0.9 / 720 = 57.6 / 720). */
    private const val SWITCH_ROW_FRACTION = (64f * 0.90f) / 720f
    private const val MEDIA_BUTTON_FRACTION = 0.18f
    // Inline style={iconStyle} overrides the mode SVG's intrinsic 56px width with 1.3em.
    private const val SKIP_ICON_CSS_PX = 32f
    private const val MODE_ICON_EM = 1.3f
    private const val PLAY_ICON_CSS_PX = 38f
    private const val PHONE_GLYPH_SCALE = 0.5f

    /**
     * Static geometry of the left column of YouLyPlus' horizontal player layout, in physical pixels.
     *
     * Every value is a top-left corner or a size inside the player root: x grows to the right and y
     * grows downwards, with the origin at the top-left of the viewport the caller measures. The
     * stylesheet is resolved in CSS px by dividing the measured viewport by [pixelScale].
     * Constants, em and viewport units use that same CSS space; the result is converted
     * once to physical pixels to place module views and the native cover slot directly.
     *
     * The control group always contains the metadata block, the progress track with its labels, the
     * media row and the volume row, in that order, so no rectangle overlaps its neighbour.
     * [metadataFontPx] and [labelFontPx] stay fractional so a caller can scale text without losing
     * the max(2vh, 1em)/max(1.2vh, .8em) breakpoint.
     */
    data class SongColumn(
        /** Left edge of the centered cover/controls column inside the 45% grid column. */
        val contentLeft: Int,
        /** Width of the cover and of the control group below it. */
        val contentWidth: Int,
        /** Left edge of the cover slot; equals [contentLeft] only in the centered variant. */
        val coverLeft: Int,
        /** Left edge of the metadata (title/subtitle) block. */
        val metadataLeft: Int,
        /** Width of the metadata (title/subtitle) block. */
        val metadataWidth: Int,
        /** Left edge of the queue pane column that shares the left host. */
        val queueLeft: Int,
        /** Width of the queue pane column that shares the left host. */
        val queueWidth: Int,
        /** Horizontal center of the drag handle in player coordinates. */
        val thumbCenterX: Int,
        /** Horizontal center of the middle switcher strip; 0 outside the switcher layout. */
        val switcherCenterX: Int,
        val coverTop: Int,
        val coverSize: Int,
        /** Vertical center of the drag handle, taken from the 4vh thumb band. */
        val thumbCenterY: Int,
        /** Resting width of the drag handle, 50 CSS px. */
        val thumbWidth: Int,
        /** Resting height of the drag handle, 8 CSS px. */
        val thumbHeight: Int,
        val controlsTop: Int,
        val controlsBottom: Int,
        /** Top of the first metadata text line, after every padding that goes before it. */
        val metadataTop: Int,
        /** Line box of a single metadata row. */
        val lineHeight: Int,
        val metadataFontPx: Float,
        /** Artist line size, one step below the title line. */
        val artistFontPx: Float,
        /** Side of the round menu button that shares the metadata row. */
        val menuSize: Int,
        /** Top of the secondary control row (shuffle/repeat/favorite/more). */
        val modeTop: Int,
        /** Height of the secondary control row. */
        val modeHeight: Int,
        /** Top of the lyrics/playlist switcher row; -1 outside the switcher variant. */
        val switchTop: Int,
        /** Height of the lyrics/playlist switcher row; 0 outside the switcher variant. */
        val switchHeight: Int,
        val progressTop: Int,
        /** Progress track plus its labels and the gap between them; at least the 24 CSS px track. */
        val progressHeight: Int,
        val progressTrackHeight: Int,
        val progressLabelGap: Int,
        val labelFontPx: Float,
        val mediaTop: Int,
        val mediaHeight: Int,
        /** Square media button side, capped by the media row height. */
        val mediaButtonSize: Int,
        /** Rewind/forward reference SVG box: viewBox 134 drawn at 32 CSS px before scaling. */
        val skipIconSize: Int,
        /** Play/pause reference SVG box: viewBox 38 drawn at 38 CSS px before scaling. */
        val playIconSize: Int,
        /** Shuffle/repeat SVG viewBox 56, drawn at the inline 1.3 button-em size before scaling. */
        val modeIconSize: Int,
        /** Transport group width inside the right pane; 0 leaves the caller's column width. */
        val transportSpreadWidth: Int,
        val volumeTop: Int,
        val volumeHeight: Int,
        /** Window-anchored output button in the reference bottom-controls row. */
        val outputLeft: Int,
        val outputTop: Int,
        val outputSize: Int,
        val gap: Int,
    )

    /**
     * Resolve the resting geometry of the horizontal player's left column.
     *
     * [playerWidthPx] and [playerHeightPx] are the visible viewport in physical pixels and
     * [pixelScale] the Android density. All CSS comparisons use that logical viewport; only the
     * final output is rounded back to physical pixels.
     *
     * Preserve the source's fixed cover, 25% control track and fr/auto sizing. The media flex item
     * may reach zero in a very short window; it does not borrow height by shrinking the cover.
     * Invalid viewport inputs return null so the caller can keep the native presentation.
     */
    fun songColumn(
        playerWidthPx: Int,
        playerHeightPx: Int,
        pixelScale: Float = REFERENCE_PIXEL_SCALE,
        nativeProgressLabelHeightPx: Int = 0,
        variant: SongColumnVariant = SongColumnVariant.CENTERED,
        tuning: SongColumnTuning? = null,
    ): SongColumn? {
        if (!pixelScale.isFinite() || pixelScale <= 0f) return null
        if (playerWidthPx <= 0 || playerHeightPx <= 0) return null
        // TV geometry uses a 720-unit viewport, independent of Android display DPI.
        // Normalize once so fixed gaps, type and glyph breakpoints scale together.
        val viewportScale = playerHeightPx / 720f
        if (variant != SongColumnVariant.CENTERED && pixelScale != viewportScale) {
            return songColumn(playerWidthPx, playerHeightPx, viewportScale,
                nativeProgressLabelHeightPx, variant, tuning)
        }
        val width = playerWidthPx / pixelScale
        val height = playerHeightPx / pixelScale
        if (!width.isFinite() || !height.isFinite()) return null
        val compact = height <= COMPACT_HEIGHT_CSS_PX
        val gap = if (compact) COMPACT_ROW_GAP_CSS_PX else WIDE_ROW_GAP_CSS_PX
        val rootEm = if (compact) COMPACT_ROOT_EM_CSS_PX else ROOT_EM_CSS_PX
        // Browser buttons keep the UA 13 1/3px font, rather than inheriting the 16px div font.
        val buttonEm = ROOT_EM_CSS_PX * 5f / 6f

        val coverFraction = tuning?.coverHeightFraction ?: when {
            variant != SongColumnVariant.CENTERED -> TV_COVER_HEIGHT_FRACTION
            height <= TINY_HEIGHT_CSS_PX -> TINY_COVER_HEIGHT_FRACTION
            height <= CHROME_HEIGHT_CSS_PX -> CHROME_COVER_HEIGHT_FRACTION
            else -> COVER_HEIGHT_FRACTION
        }
        val infoColumn = if (variant == SongColumnVariant.CENTERED) SONG_WIDTH_FRACTION * (width - COLUMN_GAP_COUNT * gap)
            else (tuning?.songWidthFraction ?: songWidthFraction(variant)) * (width - COLUMN_GAP_COUNT * gap)
        // The centered variant keeps the reference 38vw cap; the cover-left variants
        // size the cover inside its own TV-style left column.
        val coverWidthCap = if (variant == SongColumnVariant.CENTERED) COVER_WIDTH_FRACTION * width
            else COVER_COLUMN_FRACTION * width * TV_COVER_COLUMN_FILL
        val coverSize = minOf(coverFraction * height, coverWidthCap)
        val thumbBase = THUMB_BAND_VH * height
        val controlsPadTop = if (height <= SHORT_HEIGHT_CSS_PX) 0f
            else CONTROLS_PAD_EM * rootEm - CONTROLS_PAD_OFFSET_CSS_PX
        val metadataFont = if (variant == SongColumnVariant.CENTERED)
            maxOf(METADATA_FONT_VH * height, rootEm)
        else maxOf(TV_TITLE_FONT_VH * height, rootEm * 1.6f)
        val metadataLine = METADATA_LINE_HEIGHT_EM * metadataFont
        val paddedMetadata = height >= CHROME_HEIGHT_CSS_PX
        val metadataPadTopCss = if (paddedMetadata) METADATA_PAD_TOP_VH * height else 0f
        val metadataPadBottomCss = if (paddedMetadata) METADATA_PAD_BOTTOM_VH * height else 0f
        val metadata = METADATA_LINE_COUNT * metadataLine + metadataPadTopCss + metadataPadBottomCss
        val labelFont = if (variant == SongColumnVariant.CENTERED)
            maxOf(LABEL_FONT_VH * height, LABEL_FONT_EM * rootEm)
        else 19.2f * height / 720f
        // Native audio-quality badges keep their intrinsic size and padding. Let
        // that row grow within the existing flex controls rather than squash them.
        val labelHeight = maxOf((if (variant == SongColumnVariant.CENTERED) LABEL_LINE_HEIGHT else 1.5f) * labelFont,
            nativeProgressLabelHeightPx.coerceAtLeast(0) / pixelScale)
        val progress = PROGRESS_TRACK_MIN_CSS_PX + (if (compact) 0f else LABEL_MARGIN_CSS_PX) + labelHeight
        val volumeHeight = if (height <= SHORT_HEIGHT_CSS_PX) COMPACT_VOLUME_ICON_CSS_PX else VOLUME_ICON_CSS_PX
        val controlsRow = (tuning?.controlsRowFraction ?: CONTROLS_ROW_FRACTION) * height
        val compactBottomTools = height <= CHROME_HEIGHT_CSS_PX || width <= COMPACT_WIDTH_CSS_PX
        val bottomTool = if (compactBottomTools)
            COMPACT_BOTTOM_TOOL_EM * buttonEm else BOTTOM_TOOL_EM * buttonEm
        val bottomInset = (if (compactBottomTools) 2f else 4f) * rootEm

        // CSS Grid's flexible-track algorithm uses max(1, sum(fr)). The 0fr bottom
        // tool track freezes at its min-content base. A drag track below 30px freezes
        // too; resolve the trailing fraction again against the remaining free space.
        val free = height - ROW_GAP_COUNT * gap - thumbBase - coverSize - controlsRow - bottomTool
        val bottomFraction = if (compact) COMPACT_BOTTOM_ROW_FRACTION else BOTTOM_ROW_FRACTION
        val dragFrozen = free * DRAG_ROW_FRACTION < DRAG_MIN_CSS_PX
        val dragRow = if (dragFrozen) DRAG_MIN_CSS_PX.toFloat() else free * DRAG_ROW_FRACTION
        val tailRow = maxOf(0f, if (dragFrozen) free - dragRow else free) * bottomFraction
        // align-content:normal stretches only the two auto tracks, equally. The
        // fixed cover stays centered inside its enlarged track; the thumb uses its
        // 2vh top margin and absolute child, not the track's center.
        val autoExtra = maxOf(0f, free - dragRow - tailRow) / 2f
        val thumbRow = thumbBase + autoExtra
        val coverRow = coverSize + autoExtra
        // Reference 1800x720 TV/CarPlay layout: 500px cover column, 580px stacked
        // controls column, 720px right pane. All values below are fractions of the
        // viewport so any tablet resolution keeps the same proportions.
        val coverColumn = COVER_COLUMN_FRACTION * width
        val centeredColumn = (infoColumn - coverSize) / 2f
        val coverLeft: Float
        val contentLeft: Float
        val contentWidth: Float
        val metadataLeft: Float
        val metadataWidth: Float
        val queueLeft: Float
        val queueWidth: Float
        val thumbCenterX: Float
        val switcherCenterX: Float
        when (variant) {
            SongColumnVariant.CENTERED -> {
                coverLeft = centeredColumn
                contentLeft = centeredColumn; contentWidth = coverSize
                metadataLeft = centeredColumn; metadataWidth = coverSize
                queueLeft = centeredColumn; queueWidth = coverSize
                thumbCenterX = centeredColumn + coverSize / 2f
                switcherCenterX = 0f
            }
            SongColumnVariant.COVER_LEFT_SWITCHER -> {
                // Cover centered in the left column; metadata + controls stack in the
                // middle column; the right pane keeps its own native viewport.
                // Center the cover inside coverColumn so it never collapses to 0 margin.
                val coverColumnLeft = (coverColumn - coverSize) / 2f
                coverLeft = maxOf(coverColumnLeft, tuning?.coverLeftInsetCss ?: 0f)
                contentLeft = coverColumn + (tuning?.coverControlsGapCss ?: (CENTER_COLUMN_PAD_LEFT_FRACTION * width))
                contentWidth = (CENTER_COLUMN_FRACTION * width - (contentLeft - coverColumn) -
                    CENTER_COLUMN_PAD_RIGHT_FRACTION * width).coerceAtLeast(1f)
                // Center metadata across the combined cover and playback-controls region.
                metadataLeft = coverLeft
                metadataWidth = (contentLeft + contentWidth - metadataLeft).coerceAtLeast(1f)
                // The switcher shows the playlist in the right pane.
                queueLeft = 0f; queueWidth = coverColumn
                thumbCenterX = coverColumn / 2f
                switcherCenterX = 0f
            }
        }
        // Vertical stack of the middle column: title/artist, progress, transport,
        // secondary actions and (switcher variant) the lyrics/playlist row, all
        // centered as one block like the reference. Volume slider is removed.
        val rowGapV = if (variant == SongColumnVariant.CENTERED) gap * 2f else maxOf(gap * 2f, TV_ROW_GAP_VH * height)
        val metaBlock = METADATA_LINE_COUNT * metadataLine + metadataPadTopCss + metadataPadBottomCss
        val mediaBlock = if (variant == SongColumnVariant.CENTERED) MEDIA_BUTTON_FRACTION * coverSize else MEDIA_ROW_FRACTION * height
        val modeBlock = if (variant == SongColumnVariant.CENTERED) 0f else MODE_ROW_FRACTION * height
        val switchGapV = maxOf(gap * 2f, TV_SWITCHER_TOP_GAP_VH * height)
        val switchBlock = if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) SWITCH_ROW_FRACTION * height else 0f
        val volumeBlock = if (variant == SongColumnVariant.CENTERED)
            (if (height <= SHORT_HEIGHT_CSS_PX) COMPACT_VOLUME_ICON_CSS_PX else VOLUME_ICON_CSS_PX) else 0f
        val stackBottomExtra = if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) switchBlock + switchGapV else 0f
        val stackVolumeExtra = if (variant == SongColumnVariant.CENTERED) rowGapV + volumeBlock else 0f
        val stackHeight = metaBlock + rowGapV + progress + rowGapV + mediaBlock + rowGapV + modeBlock +
            stackBottomExtra + stackVolumeExtra
        val thumbTop = dragRow + gap
        val thumbCenterY = thumbTop + thumbBase / 2f
        val coverTrackTop = thumbTop + thumbRow + gap
        val stackTop: Float
        var coverTop: Float
        var metadataTop: Float
        var progressTop: Float
        var mediaTop: Float
        var mediaHeight: Float
        var modeTop: Float
        var switchTop: Float
        var volumeTop: Float
        var controlsBottom: Float
        if (variant == SongColumnVariant.CENTERED) {
            coverTop = coverTrackTop + autoExtra / 2f
            metadataTop = coverTrackTop + coverRow + gap + controlsPadTop + metadataPadTopCss
            progressTop = metadataTop + metadata
            mediaTop = progressTop + progress
            mediaHeight = maxOf(0f, (coverTrackTop + coverRow + gap + controlsRow) - volumeBlock - mediaTop)
            modeTop = mediaTop + mediaHeight
            switchTop = -1f
            volumeTop = mediaTop + mediaHeight
            controlsBottom = coverTrackTop + coverRow + gap + controlsRow
        } else {
            // 1. Shift album cover downwards per user request
            val coverDownShift = 55f * (height / 720f)
            coverTop = ((height - coverSize) / 2f) + coverDownShift

            // Raise the original metadata position slightly, without moving it over the cover.
            metadataTop = (thumbTop + thumbBase + gap * 2f - 16f * height / 720f).coerceAtLeast(0f)

            // 3. Middle playback controls (progress, media, secondary, switcher) align with cover [coverTop, coverTop + coverSize]
            val totalBlocks = progress + mediaBlock + modeBlock +
                (if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) switchBlock else 0f)
            val gapCount = if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) 3 else 2
            val alignedGap = maxOf(4f, (coverSize - totalBlocks) / gapCount)
            val totalControlsHeight = totalBlocks + alignedGap * gapCount
            val verticalShift = (coverSize - totalControlsHeight) / 2f

            progressTop = coverTop + verticalShift
            mediaTop = progressTop + progress + alignedGap
            mediaHeight = mediaBlock
            modeTop = mediaTop + mediaHeight + alignedGap
            switchTop = if (variant == SongColumnVariant.COVER_LEFT_SWITCHER) modeTop + modeBlock + alignedGap else -1f
            volumeTop = -1f
            controlsBottom = if (switchTop >= 0f) switchTop + switchBlock else modeTop + modeBlock
        }
        val controlsTop = progressTop
        val mediaButton = if (variant == SongColumnVariant.CENTERED) minOf(MEDIA_BUTTON_FRACTION * coverSize, mediaHeight)
            else mediaHeight
        val mediaScale = glyphScale(height, width, tall = 3f, standard = 2f, short = 1.5f, tiny = 1f)
        val playScale = glyphScale(height, width, tall = 2f, standard = 1.1f, short = 0.8f, tiny = PHONE_GLYPH_SCALE)
        // Transport shrunk 10%: 1.8 * 0.9 = 1.62; Mode shrunk 10%: 2.0 * 0.9 = 1.8
        val playIconFactor = if (variant == SongColumnVariant.CENTERED) 1f else 1.62f
        val skipIconFactor = if (variant == SongColumnVariant.CENTERED) 1f else 1.62f
        val modeIconFactor = if (variant == SongColumnVariant.CENTERED) 1f else 1.80f
        val menu = if (variant == SongColumnVariant.CENTERED) {
            if (width <= MENU_QUERY_WIDTH_CSS_PX)
                maxOf(MENU_MIN_EM * buttonEm, minOf(MENU_MAX_VW * width, MENU_MAX_VH * height))
            else MENU_SIZE_VH * height
        } else {
            (48f * 2.0f * 0.90f / 720f) * height
        }

        return SongColumn(
            contentLeft = px(contentLeft, pixelScale),
            contentWidth = px(contentWidth, pixelScale),
            coverLeft = px(coverLeft, pixelScale),
            metadataLeft = px(metadataLeft, pixelScale),
            metadataWidth = px(metadataWidth, pixelScale),
            queueLeft = px(queueLeft, pixelScale),
            queueWidth = px(queueWidth, pixelScale),
            thumbCenterX = px(thumbCenterX, pixelScale),
            switcherCenterX = px(switcherCenterX, pixelScale),
            coverTop = px(coverTop, pixelScale),
            coverSize = px(coverSize, pixelScale),
            thumbCenterY = px(thumbCenterY, pixelScale),
            thumbWidth = px(THUMB_HANDLE_CSS_PX, pixelScale),
            thumbHeight = px(THUMB_HANDLE_HEIGHT_CSS_PX, pixelScale),
            controlsTop = px(controlsTop, pixelScale),
            controlsBottom = px(controlsBottom, pixelScale),
            metadataTop = px(metadataTop, pixelScale),
            lineHeight = px(metadataLine, pixelScale),
            metadataFontPx = metadataFont * pixelScale,
            artistFontPx = metadataFont * ARTIST_FONT_RATIO * pixelScale,
            menuSize = px(menu, pixelScale),
            modeTop = px(modeTop, pixelScale),
            modeHeight = px(modeBlock, pixelScale),
            switchTop = if (switchTop < 0f) -1 else px(switchTop, pixelScale),
            switchHeight = px(switchBlock, pixelScale),
            progressTop = px(progressTop, pixelScale),
            progressHeight = px(progress, pixelScale),
            progressTrackHeight = px(PROGRESS_TRACK_MIN_CSS_PX, pixelScale),
            progressLabelGap = px(if (compact) 0f else LABEL_MARGIN_CSS_PX, pixelScale),
            labelFontPx = labelFont * pixelScale,
            mediaTop = px(mediaTop, pixelScale),
            mediaHeight = px(mediaHeight, pixelScale),
            mediaButtonSize = px(mediaButton, pixelScale),
            skipIconSize = px(SKIP_ICON_CSS_PX * mediaScale * skipIconFactor, pixelScale),
            playIconSize = px(PLAY_ICON_CSS_PX * playScale * playIconFactor, pixelScale),
            modeIconSize = px(MODE_ICON_EM * buttonEm * mediaScale * modeIconFactor, pixelScale),
            transportSpreadWidth = if (variant == SongColumnVariant.COVER_LEFT_SWITCHER)
                px(if (tuning != null && tuning.transportSpreadCss > 0f) tuning.transportSpreadCss
                    else coverSize * 3f / 2f, pixelScale) else 0,
            volumeTop = if (volumeTop < 0f) -1 else px(volumeTop, pixelScale),
            volumeHeight = if (volumeTop < 0f) 0 else px(volumeBlock, pixelScale),
            outputLeft = px(bottomInset, pixelScale),
            outputTop = px(controlsBottom + gap, pixelScale),
            outputSize = px(bottomTool, pixelScale),
            gap = px(gap, pixelScale),
        )
    }

    /** Glyph scale ladder of the media buttons; the 480 CSS px width query overrides the height. */
    private fun glyphScale(
        heightCss: Float,
        widthCss: Float,
        tall: Float,
        standard: Float,
        short: Float,
        tiny: Float,
    ): Float = when {
        widthCss <= PHONE_WIDTH_CSS_PX -> PHONE_GLYPH_SCALE
        heightCss > TALL_HEIGHT_CSS_PX -> tall
        heightCss > COMPACT_HEIGHT_CSS_PX -> standard
        heightCss > TINY_HEIGHT_CSS_PX -> short
        else -> tiny
    }

    private fun whole(value: Float): Int = kotlin.math.round(value).toInt()

    /** The column is resolved in CSS px and reported in the physical pixels of the measured viewport. */
    private fun px(cssPixels: Int, pixelScale: Float): Int = whole(cssPixels * pixelScale)
    private fun px(cssPixels: Float, pixelScale: Float): Int = whole(cssPixels * pixelScale)
}
