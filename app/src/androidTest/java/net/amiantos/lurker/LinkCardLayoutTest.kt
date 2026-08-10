// Copyright (c) 2026 Brad Root
// SPDX-License-Identifier: MPL-2.0

package net.amiantos.lurker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The link-preview card sizes its thumbnail off the TEXT block's height
 * (`IntrinsicSize.Min` + `fillMaxHeight`) so the image sits flush in the card's
 * corner instead of floating vertically centred with dead gaps around it.
 *
 * That only works if every child supports intrinsic measurement. Coil's
 * `AsyncImage` does (its `SubcomposeAsyncImage` sibling does NOT) — this test
 * pins that, because getting it wrong throws at layout time and would break
 * every link card in the app rather than just looking wrong.
 */
class LinkCardLayoutTest {

    @get:Rule val rule = createComposeRule()

    @Test fun thumbnailStretchesToTheCardHeightWithoutThrowing() {
        rule.setContent {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = 76.dp)
                    .testTag("card"),
            ) {
                AsyncImage(
                    model = "https://example.invalid/og.png", // never loads; layout is the point
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxHeight().width(92.dp).testTag("thumb"),
                )
                Column(Modifier.weight(1f).padding(10.dp)) {
                    Text("GitHub")
                    Text("A title that runs onto a second line to make the card taller")
                    Text("And a description line under it as well")
                }
            }
        }
        rule.waitForIdle()

        val card = rule.onNodeWithTag("card").getBoundsInRoot()
        val thumb = rule.onNodeWithTag("thumb").getBoundsInRoot()

        // Flush to the top-left corner and as tall as the card — the actual bug was
        // a fixed-size square centred vertically, leaving gaps above and below.
        assertEquals(card.top.value, thumb.top.value, 0.5f)
        assertEquals(card.left.value, thumb.left.value, 0.5f)
        assertEquals((card.bottom - card.top).value, (thumb.bottom - thumb.top).value, 0.5f)
    }
}
