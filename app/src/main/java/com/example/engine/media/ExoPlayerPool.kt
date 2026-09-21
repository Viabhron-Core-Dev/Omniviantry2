package com.example.engine.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.example.utils.LogKeeper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Lifecycle-safe, singleton shared media player pool.
 * Prevents AudioTrack and MediaCodec exhaustion / collision across multiple in-chat
 * audio/video artifact cards. Ensures at most one audio stream is actively playing at any time.
 */
object ExoPlayerPool {

    private const val TAG = "ExoPlayerPool"

    data class PlaybackState(
        val activeMediaUri: String? = null,
        val isPlaying: Boolean = false,
        val currentPositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val speed: Float = 1.0f
    )

    private var mediaPlayer: MediaPlayer? = null
    private var activeUriString: String? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var playbackSpeed: Float = 1.0f

    @Synchronized
    fun play(context: Context, uriString: String) {
        try {
            if (activeUriString == uriString && mediaPlayer != null) {
                if (!mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.start()
                    _playbackState.value = _playbackState.value.copy(isPlaying = true)
                    LogKeeper.log(TAG, "Resume", "Resumed playback for: $uriString")
                }
                return
            }

            // Stop and release previous playback
            stopAndReleaseInternal()

            val uri = if (uriString.startsWith("/") || uriString.startsWith("file://")) {
                val cleanPath = uriString.removePrefix("file://")
                val f = File(cleanPath)
                if (!f.exists()) {
                    LogKeeper.log(TAG, "FileNotFound", "Media file does not exist: $cleanPath")
                    return
                }
                Uri.fromFile(f)
            } else {
                Uri.parse(uriString)
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                setOnPreparedListener { mp ->
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                        }
                    } catch (e: Exception) {
                        LogKeeper.log(TAG, "SpeedError", "Failed to apply speed $playbackSpeed: ${e.message}")
                    }
                    mp.start()
                    activeUriString = uriString
                    _playbackState.value = PlaybackState(
                        activeMediaUri = uriString,
                        isPlaying = true,
                        currentPositionMs = 0L,
                        durationMs = mp.duration.toLong().coerceAtLeast(0L),
                        speed = playbackSpeed
                    )
                    LogKeeper.log(TAG, "PlayStarted", "Started playback (${mp.duration}ms): $uriString")
                }
                setOnCompletionListener {
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = it.duration.toLong().coerceAtLeast(0L)
                    )
                    LogKeeper.log(TAG, "PlayCompleted", "Completed playback for: $uriString")
                }
                setOnErrorListener { _, what, extra ->
                    LogKeeper.log(TAG, "PlaybackError", "MediaPlayer error what=$what extra=$extra on $uriString")
                    stopAndReleaseInternal()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            LogKeeper.log(TAG, "PlayException", "Failed to play $uriString: ${e.message}")
            stopAndReleaseInternal()
        }
    }

    @Synchronized
    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = it.currentPosition.toLong()
                    )
                    LogKeeper.log(TAG, "Pause", "Paused: $activeUriString")
                }
            }
        } catch (e: Exception) {
            LogKeeper.log(TAG, "PauseError", "Error pausing: ${e.message}")
        }
    }

    @Synchronized
    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.let {
                it.seekTo(positionMs.toInt())
                _playbackState.value = _playbackState.value.copy(
                    currentPositionMs = positionMs
                )
            }
        } catch (e: Exception) {
            LogKeeper.log(TAG, "SeekError", "Error seeking to $positionMs: ${e.message}")
        }
    }

    @Synchronized
    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.playbackParams = it.playbackParams.setSpeed(speed)
                    }
                }
            }
            _playbackState.value = _playbackState.value.copy(speed = speed)
        } catch (e: Exception) {
            LogKeeper.log(TAG, "SpeedError", "Error setting speed $speed: ${e.message}")
        }
    }

    @Synchronized
    fun togglePlayPause(context: Context, uriString: String) {
        if (activeUriString == uriString && _playbackState.value.isPlaying) {
            pause()
        } else {
            play(context, uriString)
        }
    }

    fun getCurrentPosition(): Long {
        return try {
            mediaPlayer?.currentPosition?.toLong() ?: _playbackState.value.currentPositionMs
        } catch (e: Exception) {
            _playbackState.value.currentPositionMs
        }
    }

    @Synchronized
    fun release() {
        stopAndReleaseInternal()
        LogKeeper.log(TAG, "Released", "Media pool released")
    }

    private fun stopAndReleaseInternal() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            LogKeeper.log(TAG, "ReleaseError", "Error releasing player: ${e.message}")
        } finally {
            mediaPlayer = null
            activeUriString = null
            _playbackState.value = PlaybackState()
        }
    }
}
