package com.example.alarm

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.data.model.AlarmSoundOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmAudioPlayer(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var loopJob: Job? = null

    private fun getToneType(option: AlarmSoundOption): Int = when (option) {
        AlarmSoundOption.SIREN -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
        AlarmSoundOption.MAYHEM -> ToneGenerator.TONE_CDMA_HIGH_PBX_SLS
        AlarmSoundOption.ROOSTER -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
        AlarmSoundOption.BABY_CRYING -> ToneGenerator.TONE_CDMA_PIP
        AlarmSoundOption.BUZZER -> ToneGenerator.TONE_SUP_BUSY
    }

    fun playPreview(option: AlarmSoundOption, scope: CoroutineScope) {
        stop()
        loopJob = scope.launch(Dispatchers.Default) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 85)
                toneGenerator?.startTone(getToneType(option), 750)
                vibrateOnce(180L)
                delay(850L)
                stop()
            } catch (e: Exception) {
                Log.w("AlarmAudioPlayer", "Preview tone unavailable: ${e.message}")
            }
        }
    }

    fun startAlarmLoop(option: AlarmSoundOption, scope: CoroutineScope) {
        stop()
        loopJob = scope.launch(Dispatchers.Default) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 95)
                val tone = getToneType(option)
                while (isActive) {
                    toneGenerator?.startTone(tone, 900)
                    vibrateOnce(350L)
                    delay(1250L)
                }
            } catch (e: Exception) {
                Log.w("AlarmAudioPlayer", "Alarm loop audio unavailable: ${e.message}")
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {
        } finally {
            toneGenerator = null
        }
    }

    private fun vibrateOnce(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }
}
