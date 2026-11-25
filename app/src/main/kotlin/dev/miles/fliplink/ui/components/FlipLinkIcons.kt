package dev.miles.fliplink.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object FlipLinkIcons {
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(11f, 4f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 18f)
                arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = false, 11f, 4f)
                close()
                moveTo(20f, 20f)
                lineTo(16.1f, 16.1f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(18f, 6f)
                lineTo(6f, 18f)
                moveTo(6f, 6f)
                lineTo(18f, 18f)
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18.36f, 5.64f)
                moveTo(21f, 3f)
                verticalLineTo(8f)
                horizontalLineTo(16f)
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 9f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 15f)
                arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 9f)
                close()
                moveTo(19.4f, 15f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.73f, 16.82f)
                lineTo(19.79f, 16.88f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 16.96f, 19.71f)
                lineTo(16.9f, 19.65f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 19.4f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 14f, 20.91f)
                verticalLineTo(21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 10f, 21f)
                verticalLineTo(20.91f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 19.4f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 7.18f, 19.73f)
                lineTo(7.12f, 19.79f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4.29f, 16.96f)
                lineTo(4.35f, 16.9f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.6f, 15f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 3.09f, 14f)
                horizontalLineTo(3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 10f)
                horizontalLineTo(3.09f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.6f, 9f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 4.27f, 7.18f)
                lineTo(4.21f, 7.12f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 7.04f, 4.29f)
                lineTo(7.1f, 4.35f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 9f, 4.6f)
                horizontalLineTo(9.09f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 10f, 3.09f)
                verticalLineTo(3f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 14f, 3f)
                verticalLineTo(3.09f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 15f, 4.6f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 16.82f, 4.27f)
                lineTo(16.88f, 4.21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 19.71f, 7.04f)
                lineTo(19.65f, 7.1f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.4f, 9f)
                verticalLineTo(9.09f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 20.91f, 10f)
                horizontalLineTo(21f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 14f)
                horizontalLineTo(20.91f)
                arcTo(1.65f, 1.65f, 0f, isMoreThanHalf = false, isPositiveArc = false, 19.4f, 15f)
                close()
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(19f, 12f)
                horizontalLineTo(5f)
                moveTo(12f, 19f)
                lineTo(5f, 12f)
                lineTo(12f, 5f)
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 6f)
                lineTo(9f, 17f)
                lineTo(4f, 12f)
            }
        }.build()
    }
}
