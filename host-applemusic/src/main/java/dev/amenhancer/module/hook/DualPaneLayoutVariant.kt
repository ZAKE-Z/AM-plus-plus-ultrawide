package dev.amenhancer.module.hook

/** Ultrawide arrangement with lyrics and playback queue switching. */
internal object DualPaneLayoutVariant {
    val current: SongColumnVariant = SongColumnVariant.COVER_LEFT_SWITCHER

    /** True when the widened song host must let the native controls occupy the right pane. */
    val switcher get() = current == SongColumnVariant.COVER_LEFT_SWITCHER
}
