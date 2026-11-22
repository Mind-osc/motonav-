package app.motonav.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.motonav.ui.theme.LocalAppColors
import app.motonav.ui.theme.MotoType
import app.motonav.ui.theme.Radius
import app.motonav.ui.theme.Tokens
import app.motonav.ui.theme.Touch

enum class ButtonKind { PRIMARY, SECONDARY, DANGER, STOP }

/** Τετράγωνο κουμπί εικονιδίου για γάντια (72dp). */
@Composable
fun RideIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.SECONDARY,
    size: Dp = Touch.ride,
) {
    val c = LocalAppColors.current
    val haptic = LocalHapticFeedback.current
    val (bg, fg, border) = when (kind) {
        ButtonKind.PRIMARY -> Triple(c.accent, c.onAccent, c.accent)
        ButtonKind.SECONDARY -> Triple(c.base.surface2, c.base.text, c.outline)
        ButtonKind.DANGER -> Triple(c.base.danger, c.base.onDanger, c.base.danger)
        ButtonKind.STOP -> Triple(Tokens.stop, Tokens.onStop, Tokens.stopBorder)
    }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(Radius.md),
        color = bg,
        contentColor = fg,
        border = BorderStroke(if (kind == ButtonKind.SECONDARY) 1.dp else 2.dp, border),
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(32.dp))
        }
    }
}

/** Κουμπί με κείμενο, ύψος ≥ 56dp. */
@Composable
fun TextChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        color = if (selected) c.accent else c.base.surface2,
        contentColor = if (selected) c.onAccent else c.base.text,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.outline),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = Touch.min)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MotoType.label)
        }
    }
}

/** Κύκλος χρώματος για επιλογή accent. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    Box(
        modifier = modifier
            .size(Touch.min)
            .clip(CircleShape)
            .border(if (selected) 4.dp else 1.dp, if (selected) c.base.text else c.outline, CircleShape)
            .background(color, CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
