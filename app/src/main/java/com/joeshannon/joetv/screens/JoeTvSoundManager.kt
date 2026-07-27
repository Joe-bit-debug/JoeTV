package com.joeshannon.joetv.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.joeshannon.joetv.R

/**
 * SoundPool handles the tiny navigation tick.
 * MediaPlayer handles home/select because those sounds must play reliably
 * even while an app is opening or JoeTV is still starting.
 */
class JoeTvSoundManager(
    context: Context
) {
    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val moveSound: Int =
        soundPool.load(appContext, R.raw.joetv_move_clean, 1)

    private var moveLoaded = false
    private var homePlayer: MediaPlayer? = null
    private var selectPlayer: MediaPlayer? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == moveSound && status == 0) {
                moveLoaded = true
            }
        }
    }

    fun playMove() {
        if (!moveLoaded) return

        soundPool.play(
            moveSound,
            0.40f,
            0.40f,
            1,
            0,
            1.0f
        )
    }

    fun playSelect(onFinished: () -> Unit) {
        selectPlayer?.release()

        val player = MediaPlayer.create(
            appContext,
            R.raw.joetv_select_clean
        )

        if (player == null) {
            onFinished()
            return
        }

        selectPlayer = player

        player.setVolume(0.45f, 0.45f)
        player.setOnCompletionListener {
            it.release()

            if (selectPlayer === it) {
                selectPlayer = null
            }

            onFinished()
        }
        player.setOnErrorListener { mediaPlayer, _, _ ->
            mediaPlayer.release()

            if (selectPlayer === mediaPlayer) {
                selectPlayer = null
            }

            onFinished()
            true
        }
        player.start()
    }

    fun playHome() {
        homePlayer?.release()

        homePlayer = MediaPlayer.create(
            appContext,
            R.raw.joetv_home_clean
        )?.apply {
            setVolume(0.34f, 0.34f)
            setOnCompletionListener {
                it.release()

                if (homePlayer === it) {
                    homePlayer = null
                }
            }
            start()
        }
    }

    fun release() {
        soundPool.release()

        homePlayer?.release()
        homePlayer = null

        selectPlayer?.release()
        selectPlayer = null
    }
}