package com.lightnet.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Android Settings-style grouped list shape: large corner radius on the outer edge of the
 * first/last item in a group, near-zero radius on inner edges (and middle items).
 */
@Composable
fun groupedListItemShape(index: Int, count: Int): Shape {
    val large = 16.dp
    val small = 4.dp
    val topRadius = if (index == 0) large else small
    val bottomRadius = if (index == count - 1) large else small
    return RoundedCornerShape(
        topStart = topRadius, topEnd = topRadius,
        bottomStart = bottomRadius, bottomEnd = bottomRadius,
    )
}
