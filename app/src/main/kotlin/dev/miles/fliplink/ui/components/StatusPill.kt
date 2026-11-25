package dev.miles.fliplink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.ui.theme.FlipLinkSpacing
import dev.miles.fliplink.ui.theme.FlipLinkTheme

@Composable
fun DomainStatusPill(
    status: DomainStatus,
    modifier: Modifier = Modifier,
) {
    val colors = FlipLinkTheme.colors
    when (status) {
        DomainStatus.Verified -> StatusPill(
            text = "Verified",
            containerColor = colors.statusActiveSurface,
            contentColor = colors.statusActive,
            dotColor = colors.statusActive,
            modifier = modifier,
        )

        DomainStatus.Approved -> StatusPill(
            text = "Approved",
            containerColor = colors.statusActiveSurface,
            contentColor = colors.statusActive,
            modifier = modifier,
        )

        DomainStatus.Selected -> StatusPill(
            text = "Selected",
            containerColor = colors.statusActiveSurface,
            contentColor = colors.statusActive,
            modifier = modifier,
        )

        DomainStatus.Disabled -> StatusPill(
            text = "Disabled",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
            modifier = modifier,
        )

        DomainStatus.Denied -> StatusPill(
            text = "Denied",
            containerColor = colors.statusErrorSurface,
            contentColor = colors.statusError,
            modifier = modifier,
        )

        DomainStatus.Declared -> StatusPill(
            text = "Declared",
            containerColor = colors.surfaceSubtle,
            contentColor = colors.textSecondary,
            modifier = modifier,
        )
    }
}

@Composable
fun ConflictPill(
    count: Int = 1,
    modifier: Modifier = Modifier,
) {
    val colors = FlipLinkTheme.colors
    val label = if (count <= 1) "Conflict" else "$count conflicts"
    StatusPill(
        text = label,
        containerColor = colors.statusWarningSurface,
        contentColor = colors.statusWarning,
        modifier = modifier,
    )
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val pillContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(containerColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = contentColor,
                )
            } else if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                text = text,
                style = FlipLinkTheme.typography.pill,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (onClick != null) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = FlipLinkSpacing.minTouchTarget, minHeight = FlipLinkSpacing.minTouchTarget)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            pillContent()
        }
    } else {
        Box(modifier = modifier) {
            pillContent()
        }
    }
}
