package com.steeben.dial.tube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The "Tube" tab. Hosts YouTube (m.youtube.com) in a persistent WebView, NouTube-style.
 *
 * @param onEntered called once when the tab opens — use it to pause Dial's own player
 *                  so the two do not play over each other.
 */
@Composable
fun TubeScreen(
    modifier: Modifier = Modifier,
    onEntered: () -> Unit = {},
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onEntered()
        TubePlaybackService.start(context) // keeps the process alive for background audio
    }

    BackHandler(enabled = TubeWebHolder.isFullscreen) { TubeWebHolder.exitFullscreen() }
    BackHandler(enabled = !TubeWebHolder.isFullscreen && TubeWebHolder.canGoBack) { TubeWebHolder.goBack() }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> TubeWebHolder.attach(ctx) },
            onRelease = { TubeWebHolder.detach(context) },
        )
        if (TubeWebHolder.progress < 100) {
            LinearProgressIndicator(
                progress = { TubeWebHolder.progress / 100f },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            )
        }
    }
}
