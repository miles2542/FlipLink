package dev.miles.fliplink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme

enum class BannerTone {
    Neutral,
    Warning,
    Error,
}

@Composable
fun CompactBanner(
    message: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = FlipLinkTheme.colors
    val typography = FlipLinkTheme.typography

    val containerColor = when (tone) {
        BannerTone.Neutral -> colors.surfaceSubtle
        BannerTone.Warning -> colors.statusWarningSurface
        BannerTone.Error -> colors.statusErrorSurface
    }
    val contentColor = when (tone) {
        BannerTone.Neutral -> colors.textSecondary
        BannerTone.Warning -> colors.statusWarning
        BannerTone.Error -> colors.statusError
    }
    val borderColor = when (tone) {
        BannerTone.Neutral -> colors.border
        BannerTone.Warning -> colors.statusWarning.copy(alpha = 0.25f)
        BannerTone.Error -> colors.statusError.copy(alpha = 0.25f)
    }

    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(containerColor)
            .border(width = FlipLinkSpacing.hairline, color = borderColor, shape = shape)
            .then(
                if (onAction != null && actionLabel == null) {
                    Modifier.clickable(role = Role.Button, onClick = onAction)
                } else {
                    Modifier
                },
            )
            .padding(start = 12.dp, end = if (onDismiss != null) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = typography.secondary,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onSecondaryAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = secondaryActionLabel,
                    style = typography.inlineAction,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    style = typography.inlineAction,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (onDismiss != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Dismiss banner",
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = FlipLinkIcons.Close,
                    contentDescription = "Dismiss",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
