package com.example.alerts

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.WinRahApplication
import com.example.audio.TrainSoundSynthesizer
import com.example.audio.trainSoundById
import com.example.design.PrimaryButton
import com.example.design.TrainGlyph
import com.example.design.WinRahTheme
import com.example.design.Wr
import com.example.design.WrType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Full-screen "wake up, your stop is next" alarm, shown over the lock screen. */
class WakeAlarmActivity : ComponentActivity() {
    companion object { const val EXTRA_STATION = "station" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        enableEdgeToEdge()
        val station = intent.getStringExtra(EXTRA_STATION) ?: ""
        val prefs = (application as WinRahApplication).container.prefs

        // Ring until dismissed, capped at two minutes.
        val ringing = lifecycleScope.launch {
            val p = prefs.prefs.first()
            if (p.vibrate) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator().vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator().vibrate(longArrayOf(0, 600, 400), 0)
                }
            }
            val end = System.currentTimeMillis() + 120_000
            while (isActive && System.currentTimeMillis() < end) {
                TrainSoundSynthesizer.playSound(trainSoundById(p.alarmSoundId), p.soundVolume / 100f, alarm = true)
                delay(1_200)
            }
            vibrator().cancel()
        }

        setContent {
            WinRahTheme(darkTheme = true) {
                val c = Wr.colors
                val pulse by rememberInfiniteTransition(label = "pulse")
                    .animateFloat(1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
                Column(
                    Modifier.fillMaxSize().background(c.hero).safeDrawingPadding().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    Box(Modifier.size(140.dp).scale(pulse).clip(CircleShape).background(c.signal), contentAlignment = Alignment.Center) {
                        Icon(TrainGlyph, null, tint = c.onSignal, modifier = Modifier.size(72.dp))
                    }
                    Text("استيقظ!", style = WrType.hero.copy(fontSize = 40.sp), color = c.heroText)
                    Text("اقتربت من $station\nاستعد للنزول", style = WrType.title, color = c.heroMuted, textAlign = TextAlign.Center)
                    PrimaryButton("أنا مستيقظ", onClick = {
                        ringing.cancel()
                        vibrator().cancel()
                        getSystemService(NotificationManager::class.java).cancel(WakeAlarmService.NOTIF_ALARM)
                        WakeAlarmService.stop(this@WakeAlarmActivity)
                        finish()
                    })
                }
            }
        }
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)

    override fun onDestroy() {
        vibrator().cancel()
        super.onDestroy()
    }
}
