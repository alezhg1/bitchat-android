package com.neon.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.neon.android.identity.UserRole

internal fun roleMarkerDrawable(context: Context, role: UserRole): Drawable {
    val density = context.resources.displayMetrics.density
    val sizePx = (14f * density).toInt().coerceAtLeast(12)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = roleMarkerColor(role).toArgb()
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.12f
    }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius * 0.88f, paint)
    canvas.drawCircle(radius, radius, radius * 0.88f, stroke)
    return BitmapDrawable(context.resources, bitmap)
}

private fun roleMarkerColor(role: UserRole): Color = when (role) {
    UserRole.ADMIN -> Color(0xFFE53935)
    UserRole.TEACHER -> Color(0xFF1E88E5)
    UserRole.STUDENT -> Color(0xFF43A047)
}
