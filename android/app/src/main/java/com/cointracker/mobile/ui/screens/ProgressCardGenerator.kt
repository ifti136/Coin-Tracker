package com.cointracker.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import com.cointracker.mobile.data.ProfileEnvelope
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ProgressCardGenerator {

    /**
     * Generates a branded 900×500 progress card bitmap and shares it via system share sheet.
     * Aligned with Web v4.0.4 canvas design tokens.
     */
    fun shareProgressCard(ctx: Context, envelope: ProfileEnvelope) {
        val bitmap = generateBitmap(envelope)
        val file   = File(ctx.cacheDir, "cointracker_progress.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "My Coin Tracker savings: ${envelope.balance.formatCoins()} coins (${envelope.progress}% of goal)! 🪙"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Share Progress Card"))
    }

    private fun generateBitmap(envelope: ProfileEnvelope): Bitmap {
        val W = 900
        val H = 500
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // ── Palette from design-tokens.json ──
        val colorBg          = 0xFF11151B.toInt() // Dark Canvas
        val colorSurface     = 0xFF191F27.toInt() // Dark Surface
        val colorBox         = 0xFF151B22.toInt() // Dark Surface Subtle
        val colorBorder      = 0xFF303A46.toInt() // Dark Border
        val colorBrand       = 0xFFE8B84A.toInt() // Brand Gold
        val colorIncome      = 0xFF47D7A0.toInt() // Income Emerald
        val colorTextPrimary = 0xFFF1F3F6.toInt() // Text Primary
        val colorTextSec     = 0xFFA7B0BC.toInt() // Text Secondary
        val colorTextMuted   = 0xFF7F8A98.toInt() // Text Muted

        val pad = 40f

        // ── Background ────────────────────────────────────────────────────────
        val bgPaint = Paint().apply { color = colorBg; style = Paint.Style.FILL }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)

        // ── Dot grid ──────────────────────────────────────────────────────────
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBorder }
        var dx = pad
        while (dx < W - pad) {
            var dy = pad
            while (dy < H - pad) {
                c.drawCircle(dx, dy, 1.2f, dotPaint)
                dy += 32f
            }
            dx += 32f
        }

        // ── Left accent bar ───────────────────────────────────────────────────
        val accentPaint = Paint().apply { color = colorBrand; style = Paint.Style.FILL }
        c.drawRect(0f, 0f, 6f, H.toFloat(), accentPaint)

        // ── App name & profile ────────────────────────────────────────────────
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBrand
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("Coin Tracker", pad + 8f, pad + 18f, brandPaint)

        val profilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextSec
            textSize = 14f
            typeface = Typeface.DEFAULT
        }
        c.drawText(envelope.profile, pad + 8f, pad + 40f, profilePaint)

        // ── Balance ───────────────────────────────────────────────────────────
        val balPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextPrimary
            textSize = 58f
            typeface = Typeface.DEFAULT_BOLD
        }
        val balText = envelope.balance.formatCoins()
        c.drawText(balText, pad + 8f, 138f, balPaint)

        val coinLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextSec
            textSize = 14f
            typeface = Typeface.DEFAULT
        }
        val balWidth = balPaint.measureText(balText)
        c.drawText("coins", pad + 16f + balWidth, 138f, coinLabelPaint)

        // ── Progress bar ──────────────────────────────────────────────────────
        val barY = 170f
        val barH = 14f
        val barW = (W - pad * 2 - 16f)
        val barRadius = barH / 2f

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBox }
        c.drawRoundRect(pad + 8f, barY, pad + 8f + barW, barY + barH, barRadius, barRadius, trackPaint)

        // Fill
        val prog = envelope.progress.coerceIn(0, 100)
        val fillW = if (prog > 0) ((prog / 100f) * barW).coerceAtLeast(barH) else 0f
        if (fillW > 0) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBrand }
            c.drawRoundRect(pad + 8f, barY, pad + 8f + fillW, barY + barH, barRadius, barRadius, fillPaint)
        }

        // Milestone ticks (25%, 50%, 75%)
        val tickPaint = Paint().apply { color = colorBg }
        listOf(0.25f, 0.5f, 0.75f).forEach { pct ->
            val tickX = pad + 8f + pct * barW
            c.drawRect(tickX - 1f, barY + 2f, tickX + 1f, barY + barH - 2f, tickPaint)
        }

        // Progress label & goal text
        val progLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBrand
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("$prog%", pad + 8f + barW + 8f, barY + 11f, progLabelPaint)

        val goalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextSec
            textSize = 13f
            typeface = Typeface.DEFAULT
        }
        val goalStr = if (envelope.goal > 0) "Goal: ${envelope.goal.formatCoins()} coins" else "No goal set"
        c.drawText(goalStr, pad + 8f, barY + barH + 20f, goalTextPaint)

        // ── Stats grid (6 boxes, 2 rows of 3) ─────────────────────────────────
        val stats = envelope.dashboardStats
        val dailyRate = envelope.analytics.dailyRate7d.toInt()
        val bestWeek = envelope.analytics.bestWeekEarnings
        val estDays = envelope.estimatedDays

        data class StatCard(val label: String, val value: String, val color: Int)
        val cards = listOf(
            StatCard("Today", "+${stats.today.formatCoins()}", colorIncome),
            StatCard("This week", "+${stats.week.formatCoins()}", colorIncome),
            StatCard("This month", "+${stats.month.formatCoins()}", colorIncome),
            StatCard("7-day rate", "${dailyRate.formatCoins()}/day", colorBrand),
            StatCard("Best week", if (bestWeek > 0) "+${bestWeek.formatCoins()}" else "—", colorBrand),
            StatCard("Est. days", when (estDays) {
                0 -> "Done!"
                null -> "—"
                else -> "$estDays days"
            }, colorTextPrimary)
        )

        val colW = barW / 3f
        val boxY = 240f
        val boxH = 92f
        val boxRadius = 10f

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorBox }
        val boxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        cards.forEachIndexed { i, card ->
            val row = i / 3
            val col = i % 3
            val bx = pad + 8f + col * colW
            val by = boxY + row * (boxH + 12f)
            val bw = colW - 12f

            // Box Background & border
            c.drawRoundRect(bx, by, bx + bw, by + boxH, boxRadius, boxRadius, boxPaint)
            c.drawRoundRect(bx, by, bx + bw, by + boxH, boxRadius, boxRadius, boxBorderPaint)

            // Label
            val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorTextSec
                textSize = 12f
                typeface = Typeface.DEFAULT
            }
            c.drawText(card.label, bx + 14f, by + 26f, lblPaint)

            // Value
            val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = card.color
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
            }
            c.drawText(card.value, bx + 14f, by + 64f, valPaint)
        }

        // ── Footer ────────────────────────────────────────────────────────────
        val footerDate = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorTextMuted
            textSize = 12f
            typeface = Typeface.DEFAULT
        }
        c.drawText("Generated $footerDate • Coin Tracker", pad + 8f, H - 20f, footerPaint)

        return bmp
    }

    private fun Int.formatCoins(): String =
        toString().reversed().chunked(3).joinToString(",").reversed()
}
