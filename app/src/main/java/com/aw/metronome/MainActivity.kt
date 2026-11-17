package com.aw.metronome


import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.isActive
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.DisposableEffect
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(WindowInsetsCompat.Type.systemBars())

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            MetronomeScreen(
                onRunningChanged = { running ->
                    if (running) {
                        window.addFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        )
                    } else {
                        window.clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun MetronomeScreen(
    onRunningChanged: (Boolean) -> Unit
) {

    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            "metronome_settings",
            android.content.Context.MODE_PRIVATE
        )
    }

    var bpm by rememberSaveable {
        mutableIntStateOf(
            preferences.getInt("last_bpm", 120)
        )
    }

    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        onRunningChanged(running)
    }
    var beat by remember { mutableIntStateOf(0) }
    var flashing by remember { mutableStateOf(false) }
    var editingTempo by remember { mutableStateOf(false) }
    var tempoInput by remember { mutableStateOf("") }

    var soundEnabled by remember { mutableStateOf(false) }

    val toneGenerator = remember {
        ToneGenerator(AudioManager.STREAM_MUSIC, 50)
    }

    var continuousMode by remember { mutableStateOf(false) }

    DisposableEffect(toneGenerator) {
        onDispose {
            toneGenerator.release()
        }
    }

//    val beatColours = listOf(
//        Color(0xFFFF3030), // Beat 1 - vivid red
//        Color(0xFF00BFFF), // Beat 2 - bright blue
//        Color(0xFFFFD000), // Beat 3 - bright amber
//        Color(0xFFB0B8C8)  // Beat 4 - pale grey-blue
//    )

    val beatColours = listOf(
        Color(0xFFFF3030), // Beat 1 - vivid red
        Color(0xFFFFD000), // Beat 2 - bright blue
        Color(0xFFFFD000), // Beat 3 - bright amber
        Color(0xFFFFD000)  // Beat 4 - pale grey-blue
    )

    LaunchedEffect(running, bpm) {

        if (!running) {
            flashing = false
            beat = 0
            return@LaunchedEffect
        }

        // Nanoseconds between beats, calculated without
        // rounding to whole milliseconds.
        val intervalNanos = 60_000_000_000L / bpm

        // Flash occupies 20% of each beat.
        val flashNanos = intervalNanos / 5

        // Fixed starting point for the entire sequence.
        val startTime = SystemClock.elapsedRealtimeNanos()

        var beatNumber = 0L

        while (isActive) {

            val scheduledTime =
                startTime + beatNumber * intervalNanos

            // Wait until the scheduled beat time.
            while (isActive) {
                val remaining =
                    scheduledTime -
                            SystemClock.elapsedRealtimeNanos()

                if (remaining <= 0L) break

                delay(
                    (remaining / 1_000_000L)
                        .coerceAtLeast(1L)
                )
            }

            if (!isActive) break

            // If Android has delayed us by a whole beat
            // or more, skip ahead rather than bunch beats.
            val now = SystemClock.elapsedRealtimeNanos()

            if (now - scheduledTime >= intervalNanos) {
                beatNumber =
                    (now - startTime) / intervalNanos
                continue
            }

            // Display this beat.
            beat = (beatNumber % 4).toInt() + 1

            if (soundEnabled) {
                toneGenerator.startTone(
                    if (beat == 1)
                        ToneGenerator.TONE_DTMF_0
                    else
                        ToneGenerator.TONE_DTMF_1,
                    if (beat == 1) 80 else 40
                )
            }

            flashing = true

            // End the flash at its scheduled time,
            // not a fixed delay after actual execution.
            val flashEnd = scheduledTime + flashNanos

            val remainingFlash =
                flashEnd - SystemClock.elapsedRealtimeNanos()

            if (remainingFlash > 0L) {
                delay(
                    (remainingFlash / 1_000_000L)
                        .coerceAtLeast(1L)
                )
            }

            flashing = false
            beatNumber++
        }
    }


    if (editingTempo) {

        TempoKeypad(
            input = tempoInput,
            onDigit = { digit ->
                if (tempoInput.length < 3) {
                    tempoInput += digit
                }
            },
            onDelete = {
                tempoInput = tempoInput.dropLast(1)
            },
            onEnter = {
                val newTempo = tempoInput.toIntOrNull()
                if (newTempo != null && newTempo in 20..300) {
                    bpm = newTempo
                    preferences.edit()
                        .putInt("last_bpm", newTempo)
                        .apply()
                    editingTempo = false
                }
            },
            onCancel = {
                editingTempo = false
            }
        )

    } else {
        val backgroundColour = when {
            !running || beat == 0 -> Color.Black

            continuousMode ->
                if (beat == 1)
                    beatColours[0]
                else
                    beatColours[2]

            flashing -> beatColours[beat - 1]

            else -> Color.Black
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColour)
                .clickable {
                    running = !running
                },
            contentAlignment = Alignment.Center
        ) {


            if (running && beat > 0 && (flashing || continuousMode)) {
                // Large beat number during each colour flash
                Text(
                    text = beat.toString(),
                    color = Color.Black,
                    fontSize = 320.sp,
                    fontWeight = FontWeight.Bold
                )

            } else if (!running) {

                // Tempo display when stopped
                Text(
                    text = bpm.toString(),
                    color = Color.White,
                    fontSize = 144.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        tempoInput = ""
                        editingTempo = true
                    }
                )
            }


            if (!running) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (soundEnabled) "SOUND ON" else "SOUND OFF",
                        color = if (soundEnabled) Color.Green else Color.LightGray,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { soundEnabled = !soundEnabled }
                            .padding(12.dp)
                    )

                    Text(
                        text = if (continuousMode) "CONTINUOUS" else "FLASH",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { continuousMode = !continuousMode }
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TempoKeypad(
    input: String,
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    onCancel: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {

        Text(
            text = "SET TEMPO",
            color = Color.LightGray,
            fontSize = 24.sp
        )

        Text(
            text = input.ifEmpty { "—" },
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Bold
        )

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )

        rows.forEach { row ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                row.forEach { key ->

                    Button(
                        onClick = {
                            when (key) {
                                "C" -> onCancel()
                                "⌫" -> onDelete()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(75.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray
                        )
                    ) {
                        Text(
                            text = key,
                            fontSize = 30.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Button(
            onClick = onEnter,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF237A44)
            )
        ) {
            Text(
                text = "ENTER",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
