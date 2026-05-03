package com.cointracker.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import com.cointracker.mobile.data.ProfileEnvelope
import java.io.File
import java.io.FileOutputStream

object ProgressCardGenerator {

    /**
     * Generates a 900×500 progress card bitmap and shares it via system share sheet.
     * Call from a coroutine (file I/O).
     */
    fun shareProgressCard(ctx: Context, envelope: ProfileEnvelope) {
        val bitmap = generateBitmap(envelope)
        val file   = File(ctx.cacheDir, "cointracker_progress.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type  = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT,
                "My eFootball coin progress: ${envelope.balance}/${envelope.goal} coins — ${envelope.progress}% to goal! 🪙")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share Progress Card"))
    }

    private fun generateBitmap(envelope: ProfileEnvelope): Bitmap {
        val W = 900; val H = 500
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c   = Canvas(bmp)

        // ── Background gradient ───────────────────────────────────────────────
        val bgPaint = Paint()
        bgPaint.shader = LinearGradient(
            0f, 0f, W.toFloat(), H.toFloat(),
            intArrayOf(0xFF1E1B3A.toInt(), 0xFF0B3A5D.toInt(), 0xFF4A0F4B.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        // ── Subtle grid lines ─────────────────────────────────────────────────
        val gridPaint = Paint().apply {
            color = 0x15FFFFFF.toInt(); strokeWidth = 1f
        }
        for (x in 0 until W step 60) c.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), gridPaint)
        for (y in 0 until H step 60) c.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), gridPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBBFFFFFF.toInt(); typeface = Typeface.DEFAULT }

        // ── App label ─────────────────────────────────────────────────────────
        mutedPaint.textSize = 28f
        c.drawText("🪙  Coin Tracker  •  eFootball", 50f, 65f, mutedPaint)

        // ── Profile ───────────────────────────────────────────────────────────
        mutedPaint.textSize = 22f
        c.drawText("Profile: ${envelope.profile}", 50f, 100f, mutedPaint)

        // ── Balance ───────────────────────────────────────────────────────────
        textPaint.textSize = 72f
        textPaint.color = 0xFF60A5FA.toInt()    // blue
        c.drawText("${envelope.balance.formatCoins()} coins", 50f, 195f, textPaint)

        // ── Goal label ────────────────────────────────────────────────────────
        mutedPaint.textSize = 28f
        c.drawText("Goal: ${envelope.goal.formatCoins()}  •  ${envelope.progress}% complete", 50f, 240f, mutedPaint)

        // ── Progress bar ──────────────────────────────────────────────────────
        val barLeft = 50f; val barTop = 265f; val barRight = (W - 50).toFloat(); val barHeight = 24f
        val barRadius = 12f

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF.toInt() }
        c.drawRoundRect(barLeft, barTop, barRight, barTop + barHeight, barRadius, barRadius, trackPaint)

        // Fill
        val fillWidth = ((barRight - barLeft) * envelope.progress / 100f).coerceAtLeast(barRadius * 2)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(barLeft, 0f, barLeft + fillWidth, 0f,
                0xFF10B981.toInt(), 0xFF3B82F6.toInt(), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(barLeft, barTop, barLeft + fillWidth, barTop + barHeight, barRadius, barRadius, fillPaint)

        // ── Stats row ─────────────────────────────────────────────────────────
        val statY = 340f
        drawStat(c, "Today",    "+${envelope.dashboardStats.today}",  50f,  statY)
        drawStat(c, "This Week","+${envelope.dashboardStats.week}",  320f, statY)
        drawStat(c, "7d Rate",
            "${(envelope.analytics.dailyRate7d).toInt()}/day",         590f, statY)

        // ── Best week ────────────────────────────────────────────────────────
        val analytics = envelope.analytics
        if (analytics.bestWeekEarnings > 0) {
            mutedPaint.textSize = 22f
            c.drawText(
                "🏆 Best week: ${analytics.bestWeekLabel}  •  +${analytics.bestWeekEarnings.formatCoins()} coins",
                50f, 410f, mutedPaint
            )
        }

        // ── Estimated days ────────────────────────────────────────────────────
        textPaint.textSize = 26f; textPaint.color = 0xFF34D399.toInt()
        val estText = when (envelope.estimatedDays) {
            0    -> "🎉 Goal reached!"
            null -> "Keep earning to see estimate"
            else -> "~${envelope.estimatedDays} days to goal"
        }
        c.drawText(estText, 50f, 450f, textPaint)

        // ── Footer ────────────────────────────────────────────────────────────
        mutedPaint.textSize = 18f
        c.drawText("Generated by Coin Tracker for eFootball", W - 370f, H - 20f, mutedPaint)

        return bmp
    }

    private fun drawStat(c: Canvas, label: String, value: String, x: Float, y: Float) {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt(); textSize = 20f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 34f; typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText(label, x, y, labelPaint)
        c.drawText(value, x, y + 40f, valuePaint)
    }

    private fun Int.formatCoins(): String =
        toString().reversed().chunked(3).joinToString(",").reversed()
}
