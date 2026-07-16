package com.droid.dolphy

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.zIndex
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.Extension
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.scale
import kotlin.math.sin










@Deprecated("Use MaterialCard for Material Design 3")
@Composable
fun VolumetricCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 8.dp,
    glowIntensity: Float = 0.3f,
    content: @Composable () -> Unit
) {

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = glowIntensity,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary

    val gradientColors = getGradientColors(currentAccent)

    Box(
        modifier = modifier

            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = ShadowDark,
                spotColor = ShadowDark.copy(alpha = 0.5f)
            )

            .shadow(
                elevation = elevation / 2,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (accentColor != Color.Unspecified) accentColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                spotColor = if (accentColor != Color.Unspecified) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SurfaceVolumetricLight,
                        SurfaceVolumetricDark
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )

            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        currentAccent.copy(alpha = 0.5f),
                        currentAccent.copy(alpha = 0.2f),
                        currentAccent.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )

            .drawBehind {

                drawIntoCanvas { canvas ->
                    val highlightBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f * glowPulse),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.4f
                    )
                    drawRect(
                        brush = highlightBrush,
                        topLeft = Offset.Zero,
                        size = size
                    )
                }
            }
            .padding(16.dp)
    ) {
        content()
    }
}




@Composable
fun VolumetricButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    isActive: Boolean = false,
    cornerRadius: Dp = 12.dp,
    icon: ImageVector? = null
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val gradientColors = getGradientColors(currentAccent)
    val interactionSource = remember { MutableInteractionSource() }


    val infiniteTransition = rememberInfiniteTransition(label = "button")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive) 0.6f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonGlow"
    )

    val currentGlow by animateFloatAsState(
        targetValue = if (isActive) glowPulse else 0.2f,
        animationSpec = tween(300),
        label = "glow"
    )

    Box(
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth()
            .shadow(
                elevation = if (enabled) 8.dp else 2.dp,
                shape = RoundedCornerShape(cornerRadius),
                ambientColor = if (enabled) currentAccent.copy(alpha = 0.3f) else Color.Transparent,
                spotColor = if (enabled) currentAccent.copy(alpha = 0.5f) else Color.Transparent
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                brush = Brush.horizontalGradient(
                    colors = if (enabled) {
                        if (isActive) {
                            listOf(currentAccent, gradientColors[2])
                        } else {
                            gradientColors
                        }
                    } else {
                        listOf(Color.Gray.copy(alpha = 0.5f), Color.Gray.copy(alpha = 0.3f))
                    }
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = if (enabled) {
                        listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    } else {
                        listOf(Color.Gray.copy(alpha = 0.3f), Color.Transparent)
                    }
                ),
                shape = RoundedCornerShape(cornerRadius)
            )

            .drawBehind {
                if (enabled) {
                    val highlightBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = currentGlow * 0.3f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.5f
                    )
                    drawRect(
                        brush = highlightBrush,
                        topLeft = Offset.Zero,
                        size = size
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) TextWhite else TextGray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) TextWhite else TextGray,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}









@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val gradientColors = getGradientColors(currentAccent)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientDarkStart,
                        GradientDarkMiddle,
                        GradientDarkEnd
                    )
                )
            )
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            gradientColors[1].copy(alpha = 0.15f),
                            gradientColors[0].copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )


        GridBackground(modifier = Modifier.fillMaxSize())

        content()
    }
}




@Composable
fun AnimatedGradientBackground(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")

    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val gradientColors = getGradientColors(currentAccent)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GradientDarkStart,
                        GradientDarkMiddle,
                        GradientDarkEnd
                    )
                )
            )
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            gradientColors[1].copy(alpha = 0.2f * (0.5f + 0.5f * sin(animatedOffset * 2 * Math.PI.toFloat()))),
                            gradientColors[0].copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )


        GridBackground(modifier = Modifier.fillMaxSize())

        content()
    }
}








@Composable
fun GlowingIcon(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    glowSize: Float = 20f,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowColor = currentAccent.copy(alpha = glowPulse * 0.5f)
            drawCircle(
                color = glowColor,
                radius = size.minDimension / 2 + glowSize,
                center = center
            )
        }

        content()
    }
}




@Composable
fun GlowBox(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 16.dp,
    glowIntensity: Float = 0.4f,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = glowIntensity,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .drawBehind {

                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        currentAccent.copy(alpha = glowPulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.maxDimension
                )
                drawRoundRect(
                    brush = glowBrush,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
                )
            }
    ) {
        content()
    }
}








@Composable
fun ParticleEffect(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    particleCount: Int = 20
) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    val particles = remember {
        List(particleCount) { index ->
            DesignParticle(
                x = Math.random().toFloat(),
                y = Math.random().toFloat(),
                size = 2f + Math.random().toFloat() * 4f,
                speed = 0.5f + Math.random().toFloat() * 1f,
                delay = index * 100
            )
        }
    }

    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val animatedY = (particle.y + (System.currentTimeMillis() / 1000f * particle.speed)) % 1f
            val alpha = 0.3f + 0.3f * sin(animatedY * Math.PI.toFloat() * 2)

            drawCircle(
                color = currentAccent.copy(alpha = alpha),
                radius = particle.size,
                center = Offset(particle.x * size.width, animatedY * size.height)
            )
        }
    }
}

private data class DesignParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val speed: Float,
    val delay: Int
)








@Composable
fun VolumetricText(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val gradientColors = getGradientColors(currentAccent)

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            brush = Brush.horizontalGradient(gradientColors)
        )
    )
}










@Composable
fun SettingsItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun DeviceItem(
    device: BluetoothDevice,
    modifier: Modifier = Modifier,
    onClick: (BluetoothDevice) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(device) })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = device.name ?: device.address,
            modifier = Modifier.weight(1f)
        )
    }
}












@Composable
fun VolumetricTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        SurfaceVolumetricDark,
                        SurfaceVolumetricLight
                    )
                ),
                shape = RoundedCornerShape(50.dp)
            )
            .border(
                width = 1.dp,
                color = currentAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selectedTabIndex

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        if (isSelected) {
                            Brush.horizontalGradient(getGradientColors(currentAccent))
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                        }
                    )
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (isSelected) TextWhite else TextGray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}










@Composable
fun MaterialCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 2.dp,
    containerType: String = "surface",
    contentPadding: Dp = 16.dp,
    shape: Shape? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val expressive = LocalExpressiveEnabled.current
    val baseRadius = if (cornerRadius < 20.dp) 20.dp else cornerRadius
    val targetRadius = if (expressive) baseRadius * 1.6f else baseRadius
    val animatedRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = targetRadius,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "card_radius"
    )

    val resolvedShape = shape ?: RoundedCornerShape(animatedRadius)

    Card(
        modifier = (if (expressive) modifier.animateContentSize() else modifier)
            .border(1.dp, (if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary).copy(alpha = 0.4f), resolvedShape),
        shape = resolvedShape,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}





@Composable
fun MaterialCardWithBorder(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 1.dp,
    containerType: String = "surface",
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val resolvedRadius = if (cornerRadius < 20.dp) 20.dp else cornerRadius

    val containerColor = when (containerType) {
        "primary" -> colorScheme.primaryContainer
        "secondary" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceVariant
    }

    val outlineColor = colorScheme.outline

    Card(
        modifier = modifier
            .border(1.dp, (if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f), RoundedCornerShape(resolvedRadius)),
        shape = RoundedCornerShape(resolvedRadius),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}




@Composable
fun MaterialButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    enabled: Boolean = true,
    isActive: Boolean = false
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val containerColor = if (isActive) GreenSuccess else currentAccent
    val expressive = LocalExpressiveEnabled.current
    val targetRadius = if (expressive) 30.dp else 24.dp
    val animatedRadius by animateDpAsState(
        targetValue = targetRadius,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 320f),
        label = "button_radius"
    )

    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(animatedRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.Black,
            disabledContainerColor = Color.Gray.copy(alpha = 0.5f),
            disabledContentColor = TextGray
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}




@Composable
fun MaterialOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    enabled: Boolean = true
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = currentAccent
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}





@Composable
fun MaterialBackground(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme
    val animatedBackgroundEnabled = LocalAnimatedBackgroundEnabled.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (animatedBackgroundEnabled) {
            MatrixIconField(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(0.42f),
                accentColor = currentAccent,
                glyphCount = 90,
                columns = 12,
                minSizeDp = 8,
                sizeVarianceDp = 7,
                minAlpha = 0.16f,
                alphaVariance = 0.34f
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colorScheme.background.copy(alpha = 0.72f))
            )
        }
        content()
    }
}




@Composable
fun MaterialBackgroundWithAccent(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    accentAlpha: Float = 0.05f,
    content: @Composable BoxScope.() -> Unit
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentAccent.copy(alpha = accentAlpha))
        )
        content()
    }
}




@Composable
fun MaterialTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, currentAccent.copy(alpha = 0.35f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedTabIndex,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f),
                label = "tabIndicator"
            )


            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .background(currentAccent, CircleShape)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTabIndex == index
                    val textColor by animateColorAsState(
                        targetValue = if (selected) Color.Black else Color.White.copy(alpha = 0.6f),
                        label = "tab_text"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}




















@Composable
fun MaterialSettingsItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}




fun getSegmentedShape(index: Int, count: Int, cornerRadius: Dp = 28.dp, innerRadius: Dp = 8.dp): RoundedCornerShape {
    return when {
        count == 1 -> RoundedCornerShape(cornerRadius)
        index == 0 -> RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius, bottomStart = innerRadius, bottomEnd = innerRadius)
        index == count - 1 -> RoundedCornerShape(topStart = innerRadius, topEnd = innerRadius, bottomStart = cornerRadius, bottomEnd = cornerRadius)
        else -> RoundedCornerShape(innerRadius)
    }
}




@Composable
fun MaterialSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    DolphySwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colorScheme.onPrimary,
            checkedTrackColor = colorScheme.primary,
            uncheckedThumbColor = colorScheme.outline,
            uncheckedTrackColor = colorScheme.surfaceVariant,
            checkedIconColor = colorScheme.onPrimary,
            uncheckedIconColor = colorScheme.onSurfaceVariant
        )
    )
}




@Composable
fun MaterialTonalContainer(
    modifier: Modifier = Modifier,
    containerType: String = "surface",
    cornerRadius: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor = when (containerType) {
        "primary" -> colorScheme.primaryContainer
        "secondary" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(cornerRadius))
            .padding(16.dp),
        content = content
    )
}




@Composable
fun MaterialOutlinedContainer(
    modifier: Modifier = Modifier,
    containerType: String = "surface",
    cornerRadius: Dp = 12.dp,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor = when (containerType) {
        "primary" -> colorScheme.primaryContainer
        "secondary" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceVariant
    }

    Box(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(cornerRadius))
            .border(borderWidth, colorScheme.outline, RoundedCornerShape(cornerRadius))
            .padding(16.dp),
        content = content
    )
}




@Composable
fun MaterialProfileCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.border(1.dp, currentAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}




@Composable
fun MaterialThemeSelectorCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (isSelected) colorScheme.primary else currentAccent.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Box(modifier = Modifier.padding(16.dp), content = content)
    }
}




@Composable
fun MaterialDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(colorScheme.outlineVariant)
    )
}




@Composable
fun SignalRow(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(currentAccent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = currentAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
            )
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextGray.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}




@Composable
fun ExpressiveSegmentedCardList(
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val currentAccent = if (accentColor != Color.Unspecified) accentColor else MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme
    val expressive = LocalExpressiveEnabled.current
    val cornerRadius = if (expressive) 32.dp else 16.dp

    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, currentAccent.copy(alpha = 0.4f), RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}




@Composable
fun ExpressiveSplitButton(
    primaryText: String,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    secondaryIcon: ImageVector = Icons.Default.KeyboardArrowDown
) {
    val expressive = LocalExpressiveEnabled.current
    val shape = RoundedCornerShape(if (expressive) 32.dp else 24.dp)

    Surface(
        modifier = modifier.height(60.dp),
        shape = shape,
        color = if (enabled) accentColor else Color.Gray.copy(alpha = 0.5f),
        contentColor = if (enabled) Color.Black else Color.White
    ) {
        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = enabled, onClick = onPrimaryClick)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }


            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.5f)
                    .background(Color.White.copy(alpha = 0.3f))
            )


            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.9f)
                    .clickable(enabled = enabled, onClick = onSecondaryClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = secondaryIcon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}




@Composable
fun MaterialIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerType: String = "surface",
    content: @Composable () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    val containerColor = when (containerType) {
        "primary" -> colorScheme.primaryContainer
        "secondary" -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.background(containerColor, CircleShape)
    ) {
        content()
    }
}

@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    fillColor: Color = Color.Transparent,
    strokeWidth: Dp = 6.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_circular")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "phase"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing)
        ),
        label = "rotation"
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = (minOf(size.width, size.height) - strokeWidth.toPx() * 2f) / 2f
        val amplitude = strokeWidth.toPx() * 0.5f
        val frequency = 8f


        drawCircle(color = trackColor, radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx() / 2f))

        val path = androidx.compose.ui.graphics.Path()
        val segments = 100
        val sweepAngle = progress ?: 1f

        if (sweepAngle > 0f) {
            val maxSegments = (segments * sweepAngle).toInt().coerceAtLeast(1)
            for (i in 0..maxSegments) {
                val angle = (i.toFloat() / segments) * 2f * Math.PI.toFloat()
                val r = radius + kotlin.math.sin(angle * frequency + phase) * amplitude
                val x = center.x + r * kotlin.math.cos(angle).toFloat()
                val y = center.y + r * kotlin.math.sin(angle).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (fillColor != Color.Transparent) {
                drawPath(path = path, color = fillColor, style = androidx.compose.ui.graphics.drawscope.Fill)
            }
            drawPath(path = path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun ExpressiveBounceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val expressive = LocalExpressiveEnabled.current
    val baseRadius = if (shape is RoundedCornerShape) 28.dp else 28.dp

    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 12.dp else 28.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "corner"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "scale"
    )

    val contentColorFinal = if (containerColor == MaterialTheme.colorScheme.primary) Color.Black else contentColor

    val buttonShape = shape ?: RoundedCornerShape(cornerRadius)

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .height(56.dp),
        enabled = enabled,
        interactionSource = interactionSource,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColorFinal),
        contentPadding = PaddingValues(horizontal = 24.dp),
        content = content
    )
}

@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    accentColor: Color,
    bluetoothSelected: Boolean,
    otherSelected: Boolean,
    settingsSelected: Boolean,
    onBluetoothClick: () -> Unit,
    onOtherClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = when {
        bluetoothSelected -> 0
        otherSelected -> 1
        else -> 2
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .height(72.dp)
                .wrapContentWidth(),
            shape = CircleShape,
            color = accentColor,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                val itemWidth = 64.dp
                val spacing = 10.dp


                val indicatorOffset = (itemWidth + spacing) * selectedIndex


                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .padding(vertical = 10.dp)
                        .size(itemWidth, 52.dp)
                        .clip(CircleShape)
                        .background(Color.Black, CircleShape)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    FloatingToolbarItem(
                        selected = bluetoothSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBluetoothClick()
                        },
                        icon = Icons.Default.Bluetooth,
                        accentColor = accentColor
                    )
                    FloatingToolbarItem(
                        selected = otherSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOtherClick()
                        },
                        icon = Icons.Default.Extension,
                        accentColor = accentColor
                    )
                    FloatingToolbarItem(
                        selected = settingsSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSettingsClick()
                        },
                        icon = Icons.Default.Settings,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingToolbarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    accentColor: Color
) {
    val iconColor by animateColorAsState(
        if (selected) accentColor else Color.Black,
        label = "icon"
    )
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}



@Composable
fun ExpressiveDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)) {
                    title()
                }
            }
        },
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton
    )
}






