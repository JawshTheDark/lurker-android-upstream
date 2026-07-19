// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache

/**
 * Process-scoped owner of the [LurkerClient] and the shared Coil loader.
 *
 * Hoisting the client out of the Activity means the socket + all buffer state
 * survive Activity recreation (rotation, the tablet/phone layout switch) instead
 * of reconnecting from scratch, and it gives [LurkerConnectionService] the *same*
 * client to keep alive in the background (no second socket).
 *
 * As Coil's [ImageLoaderFactory] it also provides ONE app-wide image loader with
 * a 256 MB on-disk cache, so inline media survives scrolls, buffer switches, and
 * relaunches — and there's a single DiskCache per directory (Coil forbids more).
 */
class LurkerApp : Application(), ImageLoaderFactory {
    val client: LurkerClient by lazy { LurkerClient() }

    // Biometric-lock state kept here (not in the Activity) so it survives Activity
    // recreation — rotating the screen shouldn't re-prompt. backgroundedAt lets
    // onResume re-lock only after a real trip away, not a config change.
    var unlocked = false
    var backgroundedAt = 0L

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(ImageDecoderDecoder.Factory()) } // animated GIF/WebP
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
