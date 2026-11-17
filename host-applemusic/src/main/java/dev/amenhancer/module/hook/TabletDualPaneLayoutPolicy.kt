package dev.amenhancer.module.hook

/** Native host boundaries stay stable; songColumn independently positions the visible left content. */
internal object TabletDualPaneLayoutPolicy {
    const val SONG_WIDTH_FRACTION = TabletArtworkLayoutPolicy.SONG_WIDTH_FRACTION
    fun songWidthFraction(variant: SongColumnVariant = SongColumnVariant.CENTERED): Float =
        TabletArtworkLayoutPolicy.songWidthFraction(variant)
    fun songColumnWidth(playerWidth: Int, variant: SongColumnVariant = SongColumnVariant.CENTERED): Int =
        (playerWidth * songWidthFraction(variant)).toInt()

    // Original host compensation, not the new visible cover/metadata inset.
    const val CONTENT_SIDE_INSET_DP = 50
    // Keep the lyrics viewport and its drawing/word-width insets unchanged.
    const val LYRICS_TEXT_START_INSET_DP = 16 + 20 + 12
    // Native metadata uses a 16dp guideline plus a 16dp margin; progress uses 32dp.
    private const val NATIVE_CONTENT_SIDE_INSET_DP = 32
    const val SONG_HOST_SIDE_MARGIN_DP = CONTENT_SIDE_INSET_DP - NATIVE_CONTENT_SIDE_INSET_DP

    fun songHostWidth(playerWidth: Int, hostSideMargin: Int, lyricsTextStartInset: Int): Int =
        (songColumnWidth(playerWidth) + lyricsTextStartInset - 2 * hostSideMargin).coerceAtLeast(1)

    /**
     * V2 hands the right pane to either lyrics or the relocated native controls, so the
     * song host must span the full width underneath the lyrics pane for the controls to
     * be visible and hit-testable there. V1 widens its song column to narrow the lyrics.
     */
    fun songHostWidth(playerWidth: Int, hostSideMargin: Int, lyricsTextStartInset: Int,
        variant: SongColumnVariant): Int =
        if (variant == SongColumnVariant.COVER_LEFT_SWITCHER)
            (playerWidth - 2 * hostSideMargin).coerceAtLeast(1)
        else (songColumnWidth(playerWidth, variant) + lyricsTextStartInset - 2 * hostSideMargin).coerceAtLeast(1)
}
