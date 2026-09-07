package com.example.ui.emulator

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SnesButton
import com.example.ui.theme.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchControls(
    opacity: Float,
    scale: Float,
    onButtonEvent: (SnesButton, Boolean) -> Unit,
    onOpenMenu: () -> Unit,
    onQuickReset: () -> Unit,
    isFastForward: Boolean = false,
    onToggleFastForward: () -> Unit = {},
    onQuickSave: () -> Unit = {},
    onQuickLoad: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(opacity)
            .scale(scale)
    ) {
        // Shoulder Buttons (L and R) at the top sides
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShoulderButton(
                label = "L",
                tag = "btn_l",
                onPressedChange = { onButtonEvent(SnesButton.L, it) }
            )

            // Center Top Quick Controls (FF, QS, QL, Reset, Menu)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fast-Forward Toggle Button
                IconButton(
                    onClick = onToggleFastForward,
                    modifier = Modifier
                        .testTag("btn_fast_forward")
                        .size(34.dp)
                        .background(
                            if (isFastForward) SnesPrimary.copy(alpha = 0.9f) else DarkSurfaceVariant.copy(alpha = 0.8f),
                            CircleShape
                        )
                ) {
                    Text(
                        text = if (isFastForward) "2x" else "1x",
                        color = if (isFastForward) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick Save
                IconButton(
                    onClick = onQuickSave,
                    modifier = Modifier
                        .testTag("btn_quick_save")
                        .size(34.dp)
                        .background(DarkSurfaceVariant.copy(alpha = 0.8f), CircleShape)
                ) {
                    Text(
                        text = "QS",
                        color = SnesSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick Load
                IconButton(
                    onClick = onQuickLoad,
                    modifier = Modifier
                        .testTag("btn_quick_load")
                        .size(34.dp)
                        .background(DarkSurfaceVariant.copy(alpha = 0.8f), CircleShape)
                ) {
                    Text(
                        text = "QL",
                        color = SnesSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onQuickReset,
                    modifier = Modifier
                        .testTag("btn_quick_reset")
                        .size(34.dp)
                        .background(DarkSurfaceVariant.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier
                        .testTag("btn_menu")
                        .size(34.dp)
                        .background(DarkSurfaceVariant.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ShoulderButton(
                label = "R",
                tag = "btn_r",
                onPressedChange = { onButtonEvent(SnesButton.R, it) }
            )
        }

        // Bottom Controls: D-Pad on Left, Action Diamond on Right, Select/Start in Center
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // D-Pad Left
            DPad(
                onButtonEvent = onButtonEvent,
                modifier = Modifier.testTag("dpad_controller")
            )

            // Select & Start Center
            Row(
                modifier = Modifier
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillButton(
                    label = "SELECT",
                    tag = "btn_select",
                    onPressedChange = { onButtonEvent(SnesButton.SELECT, it) }
                )
                PillButton(
                    label = "START",
                    tag = "btn_start",
                    onPressedChange = { onButtonEvent(SnesButton.START, it) }
                )
            }

            // Action Diamond Right (X, Y, A, B)
            ActionDiamond(
                onButtonEvent = onButtonEvent,
                modifier = Modifier.testTag("action_buttons")
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DPad(
    onButtonEvent: (SnesButton, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val size = 150.dp
    val armWidth = 48.dp

    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        // Cross background
        Box(
            modifier = Modifier
                .width(armWidth)
                .height(size)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.85f))
                .border(1.5.dp, DarkSurfaceHighlight, RoundedCornerShape(8.dp))
        )
        Box(
            modifier = Modifier
                .width(size)
                .height(armWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant.copy(alpha = 0.85f))
                .border(1.5.dp, DarkSurfaceHighlight, RoundedCornerShape(8.dp))
        )

        // Center hub
        Box(
            modifier = Modifier
                .size(armWidth)
                .background(DarkSurfaceHighlight.copy(alpha = 0.5f), CircleShape)
        )

        // Up Button
        TouchPadButton(
            label = "▲",
            tag = "btn_up",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(armWidth, 52.dp),
            onPressedChange = { onButtonEvent(SnesButton.UP, it) }
        )

        // Down Button
        TouchPadButton(
            label = "▼",
            tag = "btn_down",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(armWidth, 52.dp),
            onPressedChange = { onButtonEvent(SnesButton.DOWN, it) }
        )

        // Left Button
        TouchPadButton(
            label = "◀",
            tag = "btn_left",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(52.dp, armWidth),
            onPressedChange = { onButtonEvent(SnesButton.LEFT, it) }
        )

        // Right Button
        TouchPadButton(
            label = "▶",
            tag = "btn_right",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(52.dp, armWidth),
            onPressedChange = { onButtonEvent(SnesButton.RIGHT, it) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ActionDiamond(
    onButtonEvent: (SnesButton, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val diamondSize = 160.dp
    val btnSize = 52.dp

    Box(
        modifier = modifier
            .size(diamondSize),
        contentAlignment = Alignment.Center
    ) {
        // X Button (Top, Blue)
        ActionButton(
            label = "X",
            color = SnesBtnBlue,
            tag = "btn_x",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(btnSize),
            onPressedChange = { onButtonEvent(SnesButton.X, it) }
        )

        // Y Button (Left, Green)
        ActionButton(
            label = "Y",
            color = SnesBtnGreen,
            tag = "btn_y",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(btnSize),
            onPressedChange = { onButtonEvent(SnesButton.Y, it) }
        )

        // A Button (Right, Red)
        ActionButton(
            label = "A",
            color = SnesBtnRed,
            tag = "btn_a",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(btnSize),
            onPressedChange = { onButtonEvent(SnesButton.A, it) }
        )

        // B Button (Bottom, Yellow)
        ActionButton(
            label = "B",
            color = SnesBtnYellow,
            tag = "btn_b",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(btnSize),
            onPressedChange = { onButtonEvent(SnesButton.B, it) }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ActionButton(
    label: String,
    color: Color,
    tag: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(tag)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressedChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressedChange(false)
                        true
                    }
                    else -> false
                }
            }
            .clip(CircleShape)
            .background(
                if (isPressed) color.copy(alpha = 0.95f) else color.copy(alpha = 0.75f)
            )
            .border(
                2.dp,
                if (isPressed) Color.White else Color.White.copy(alpha = 0.4f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchPadButton(
    label: String,
    tag: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(tag)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressedChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressedChange(false)
                        true
                    }
                    else -> false
                }
            }
            .background(if (isPressed) DarkSurfaceHighlight else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) SnesPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ShoulderButton(
    label: String,
    tag: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(tag)
            .size(width = 80.dp, height = 38.dp)
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isPressed = true
                        onPressedChange(true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isPressed = false
                        onPressedChange(false)
                        true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPressed) SnesPurpleDark.copy(alpha = 0.9f) else DarkSurfaceVariant.copy(alpha = 0.75f)
            )
            .border(
                1.5.dp,
                if (isPressed) SnesPrimary else DarkSurfaceHighlight,
                RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PillButton(
    label: String,
    tag: String,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .testTag(tag)
                .size(width = 50.dp, height = 18.dp)
                .pointerInteropFilter { event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            isPressed = true
                            onPressedChange(true)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isPressed = false
                            onPressedChange(false)
                            true
                        }
                        else -> false
                    }
                }
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (isPressed) TextPrimary else DarkSurfaceHighlight.copy(alpha = 0.85f)
                )
                .border(
                    1.dp,
                    if (isPressed) Color.White else DarkSurfaceHighlight,
                    RoundedCornerShape(9.dp)
                )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
