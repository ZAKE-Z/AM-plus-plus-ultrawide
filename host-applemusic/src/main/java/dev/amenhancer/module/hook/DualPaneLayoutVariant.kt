package dev.amenhancer.module.hook

import dev.amenhancer.host.applemusic.BuildConfig

/**
 * Build-time dual-pane arrangement, injected per product flavor: v1 keeps lyrics on the
 * right with the cover moved to the far left; v2 additionally hands the right pane to
 * either lyrics or the playback controls through the middle switcher.
 */
internal object DualPaneLayoutVariant {
    val current: SongColumnVariant = when (BuildConfig.DUAL_PANE_VARIANT) {
        2 -> SongColumnVariant.COVER_LEFT_SWITCHER
        else -> SongColumnVariant.COVER_LEFT
    }

    /** True when the widened song host must let the native controls occupy the right pane. */
    val switcher get() = current == SongColumnVariant.COVER_LEFT_SWITCHER
}
