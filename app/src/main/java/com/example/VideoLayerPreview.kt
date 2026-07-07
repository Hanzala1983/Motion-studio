package com.example

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * Actually plays a video layer's frames, instead of showing a single static
 * thumbnail. Previously this app used Coil's AsyncImage with a
 * VideoFrameDecoder, which only ever decodes ONE frame from the video and
 * renders it like a still image — so playback looked frozen. This composable
 * uses a real ExoPlayer + PlayerView so the layer plays back like an actual
 * video, seeks with the timeline scrubber, and reports its real duration
 * once known (which is a far more reliable source of truth than
 * MediaMetadataRetriever for content:// URIs).
 *
 * @param uri the video content to play
 * @param playheadProgressSeconds the global timeline's current playhead position
 * @param layerStartSeconds where this clip begins on the timeline
 * @param isPlaying whether the global timeline is currently playing
 * @param onDurationKnownSeconds called once the player determines the real
 *   media duration, so the caller can size the clip correctly instead of
 *   leaving it at a default placeholder length
 */
@Composable
fun VideoLayerPreview(
    uri: Uri,
    playheadProgressSeconds: Float,
    layerStartSeconds: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onDurationKnownSeconds: (Float) -> Unit = {}
) {
    val context = LocalContext.current

    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            // Muted: this is a scrubbing preview inside an editor canvas, not
            // final playback, and multiple layers previewing audio at once
            // would be unpleasant. Audio is handled separately at export/mix time.
            volume = 0f
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
                ) {
                    val durationMs = player.duration
                    if (durationMs != androidx.media3.common.C.TIME_UNSET && durationMs > 0) {
                        onDurationKnownSeconds(durationMs / 1000f)
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Keep play/pause in sync with the global timeline state.
    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    // Seek the player to match the timeline scrubber whenever it moves by
    // more than a small threshold (avoids fighting the player's own
    // internal clock during normal playback, while still tracking drags).
    LaunchedEffect(playheadProgressSeconds) {
        val targetSeconds = (playheadProgressSeconds - layerStartSeconds).coerceAtLeast(0f)
        val targetMs = (targetSeconds * 1000).toLong()
        if (abs(exoPlayer.currentPosition - targetMs) > 150) {
            exoPlayer.seekTo(targetMs)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }
    )
}
