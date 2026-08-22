package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRailway
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var showContent by remember { mutableStateOf(false) }

    // Continuous animations
    val infiniteTransition = rememberInfiniteTransition(label = "train_infinite")
    
    // Train track movement offset
    val trackOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "track_motion"
    )

    // Train bouncing vibration effect
    val trainBounce by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "train_bounce"
    )

    // Glow pulse animation
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    // Steam smoke particles
    val steamAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "steam_alpha"
    )

    LaunchedEffect(Unit) {
        showContent = true
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2600, easing = FastOutSlowInEasing)
        )
        delay(200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030712), // Deepest dark midnight
                        Color(0xFF0F172A),
                        Color(0xFF022C22), // Deep Algerian emerald green
                        Color(0xFF030712)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background railway grid lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Draw subtle starry glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF059669).copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(width * 0.5f, height * 0.45f),
                    radius = width * 0.7f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BRANDING BADGE
            Spacer(modifier = Modifier.height(20.dp))
            
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(800)) + scaleIn(tween(800))
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF065F46).copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🇩🇿", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "الشبكة الوطنية للسكك الحديدية SNTF",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6EE7B7),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // CENTER ANIMATED TRAIN STAGE
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Train Motion Stage
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Headlight Glow Cone
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        val centerX = size.width * 0.5f
                        val centerY = size.height * 0.55f

                        // Headlight Beam projecting forward to the left
                        val beamPath = Path().apply {
                            moveTo(centerX - 40.dp.toPx(), centerY - 10.dp.toPx())
                            lineTo(centerX - 170.dp.toPx(), centerY - 45.dp.toPx())
                            lineTo(centerX - 170.dp.toPx(), centerY + 45.dp.toPx())
                            close()
                        }
                        drawPath(
                            path = beamPath,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF38BDF8).copy(alpha = 0.35f * glowPulse),
                                    Color(0xFF10B981).copy(alpha = 0.05f)
                                ),
                                startX = centerX,
                                endX = centerX - 180.dp.toPx()
                            )
                        )
                    }

                    // The Animated High-Speed Modern Train Body
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(0, trainBounce.dp.roundToPx()) },
                        contentAlignment = Alignment.Center
                    ) {
                        ModernTrainArtwork(glowPulse = glowPulse, steamAlpha = steamAlpha)
                    }

                    // Railway Tracks with moving ties underneath
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .align(Alignment.BottomCenter)
                    ) {
                        val trackY = size.height * 0.4f
                        val trackWidth = size.width

                        // Two main glowing rails
                        drawLine(
                            color = Color(0xFF0284C7),
                            start = Offset(0f, trackY),
                            end = Offset(trackWidth, trackY),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFF10B981),
                            start = Offset(0f, trackY + 12.dp.toPx()),
                            end = Offset(trackWidth, trackY + 12.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Moving railway ties / sleepers
                        val tieSpacing = 28.dp.toPx()
                        val animatedOffset = (trackOffset * 1.2f) % tieSpacing
                        var x = -tieSpacing + animatedOffset
                        while (x < trackWidth + tieSpacing) {
                            drawLine(
                                color = Color(0xFF64748B).copy(alpha = 0.7f),
                                start = Offset(x, trackY - 4.dp.toPx()),
                                end = Offset(x, trackY + 16.dp.toPx()),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            x += tieSpacing
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // APP TITLE & SLOGAN
                AnimatedVisibility(
                    visible = showContent,
                    enter = fadeIn(tween(1000, 200))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Win rah train",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🚂",
                                fontSize = 32.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "وين راه التران • رادار وتتبع قطارات الجزائر الحية",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF38BDF8),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "تتبع دقيق للضواحي • منبه النزول الذكي • تقارير الاكتظاظ",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // BOTTOM PROGRESS & STATUS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Sensors,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (progress.value < 0.5f) "جاري الاتصال بالأقمار الصناعية GPS..." else "جاري مزامنة خطوط ومحطات القطار...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Fast Skip / Enter Button
                Text(
                    text = "تخطي والدخول للرادار ←",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSplashFinished() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ModernTrainArtwork(glowPulse: Float, steamAlpha: Float) {
    Surface(
        modifier = Modifier
            .width(220.dp)
            .height(84.dp),
        shape = RoundedCornerShape(topStart = 40.dp, bottomStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF38BDF8), Color(0xFF10B981))
            )
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Train Cabin Top Streamline Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0284C7), Color(0xFF047857))
                        )
                    )
            )

            // Front Cockpit / Windshield Glass
            Surface(
                modifier = Modifier
                    .padding(start = 14.dp, top = 8.dp)
                    .size(width = 44.dp, height = 28.dp),
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 8.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                color = Color(0xFF0C4A6E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.8f))
            ) {}

            // Passenger Windows Row
            Row(
                modifier = Modifier
                    .padding(start = 66.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    Surface(
                        modifier = Modifier.size(width = 24.dp, height = 22.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0369A1).copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {}
                }
            }

            // Headlight Bulb at the very front
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8).copy(alpha = glowPulse))
            )

            // Bottom Green Stripe of SNTF Algeria
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF10B981))
            )

            // Red Emergency Flag accent
            Box(
                modifier = Modifier
                    .padding(start = 62.dp, bottom = 12.dp)
                    .size(width = 16.dp, height = 6.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0xFFEF4444), RoundedCornerShape(2.dp))
            )

            // Train Wheels
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155))
                            .border(2.dp, Color(0xFF64748B), CircleShape)
                    )
                }
            }
        }
    }
}
