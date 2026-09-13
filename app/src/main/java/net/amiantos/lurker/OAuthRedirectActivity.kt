// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Catches the browser's `<applicationId>:/oauth?code=…` redirect and hands it
 * to [MainActivity]. A separate, invisible activity rather than a filter on
 * MainActivity itself: the browser launches the redirect in its own task, and
 * forwarding with CLEAR_TOP | SINGLE_TOP lands it in ours as an onNewIntent —
 * without touching MainActivity's launch mode, which the share sheet and
 * notification taps depend on.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(data)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        }
        finish()
    }
}
