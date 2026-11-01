package com.jahid.together.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jahid.together.ui.theme.*
import java.util.concurrent.TimeUnit

object BengaliFormatter {
    private val bengaliDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun toBengaliDigits(input: Any): String {
        val str = input.toString()
        val sb = StringBuilder()
        for (ch in str) {
            if (ch in '0'..'9') {
                sb.append(bengaliDigits[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${toBengaliDigits(meters.toInt())} মিটার"
        } else {
            val km = String.format("%.1f", meters / 1000.0)
            "${toBengaliDigits(km)} কিমি"
        }
    }

    fun formatElapsedTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        if (diff < 30_000) return "এইমাত্র"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        if (minutes < 60) return "${toBengaliDigits(minutes)} মিনিট আগে"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        if (hours < 24) return "${toBengaliDigits(hours)} ঘণ্টা আগে"
        val days = TimeUnit.MILLISECONDS.toDays(diff)
        return "${toBengaliDigits(days)} দিন আগে"
    }
}

@Composable
fun ProfileAvatar(
    photoBase64: String,
    displayName: String,
    size: Dp = 56.dp,
    borderColor: Color = RosePrimary,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(photoBase64) {
        if (photoBase64.isNotBlank()) {
            try {
                val decoded = Base64.decode(photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .background(RosePrimaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$displayName ছবি",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = displayName.take(1).ifBlank { "❤️" },
                color = RosePrimary,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45).sp
            )
        }
    }
}

@Composable
fun TogetherButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    testTag: String = "together_action_btn"
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RosePrimary,
            contentColor = Color.White,
            disabledContainerColor = LightOutline
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TogetherCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun StatusBadge(
    isOnline: Boolean,
    isMoving: Boolean,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text) = when {
        !isOnline -> Triple(Color(0xFFF1F5F9), StatusOffline, "অফলাইন")
        isMoving -> Triple(Color(0xFFEFF6FF), StatusMoving, "চলন্তাবস্থায় 🚗")
        else -> Triple(Color(0xFFECFDF5), StatusOnline, "একস্থানে স্থির 🟢")
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BatteryBadge(
    batteryPct: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val color = when {
        batteryPct <= 20 -> BatteryLow
        batteryPct <= 50 -> BatteryMedium
        else -> BatteryGood
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
            contentDescription = "ব্যাটারি",
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${BengaliFormatter.toBengaliDigits(batteryPct)}%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
