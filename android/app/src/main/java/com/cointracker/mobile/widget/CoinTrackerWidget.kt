package com.cointracker.mobile.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import android.content.res.Configuration
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cointracker.mobile.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.glance.appwidget.LinearProgressIndicator
import android.annotation.SuppressLint

// ── State keys ───────────────────────────────────────────────────────────────

private val KEY_BALANCE   = intPreferencesKey("w_balance")
private val KEY_GOAL      = intPreferencesKey("w_goal")
private val KEY_PROGRESS  = intPreferencesKey("w_progress")
private val KEY_RATE      = stringPreferencesKey("w_rate")
private val KEY_PROFILE   = stringPreferencesKey("w_profile")
private val KEY_IS_DARK   = stringPreferencesKey("w_is_dark")   // "true" / "false"

// ── App gradient palette (matches CoinTrackerTheme) ──────────────────────────

private val BG_DARK_START  = Color(0xFF1E1B3A)   // GradientDark1
private val BG_DARK_END    = Color(0xFF0B3A5D)   // GradientDark3
private val BG_LIGHT_START = Color(0xFFC3AED6)   // GradientLight1
private val BG_LIGHT_END   = Color(0xFFA1C4FD)   // GradientLight3
private val TEXT_DARK      = Color(0xFFE2E8F0)
private val TEXT_LIGHT     = Color(0xFF1E293B)
private val PRIMARY_DARK   = Color(0xFF60A5FA)
private val PRIMARY_LIGHT  = Color(0xFF3B82F6)
private val SUCCESS        = Color(0xFF10B981)
private val MUTED_DARK     = Color(0xFF94A3B8)
private val MUTED_LIGHT    = Color(0xFF64748B)
private val TRACK          = Color(0x33FFFFFF)

// ── Widget ───────────────────────────────────────────────────────────────────

class CoinTrackerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Allow any size — the layout adapts
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent(context) }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun WidgetContent(context: Context) {
        val prefs    = currentState<androidx.datastore.preferences.core.Preferences>()
        val balance  = prefs[KEY_BALANCE]  ?: 0
        val goal     = prefs[KEY_GOAL]     ?: 13500
        val progress = prefs[KEY_PROGRESS] ?: 0
        val rate     = prefs[KEY_RATE]     ?: "—"
        val profile  = prefs[KEY_PROFILE]  ?: "Default"
        val isDark   = prefs[KEY_IS_DARK]  != "false"   // default dark
        val pct      = progress.coerceIn(0, 100)

        val bgColor    = if (isDark) BG_DARK_START  else BG_LIGHT_START
        val textColor  = if (isDark) TEXT_DARK      else TEXT_LIGHT
        val primary    = if (isDark) PRIMARY_DARK   else PRIMARY_LIGHT
        val muted      = if (isDark) MUTED_DARK     else MUTED_LIGHT

        // Outer container mimics app gradient with rounded corners + padding
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(bgColor))
                .cornerRadius(20.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            // Subtle overlay for depth (dark strip at top-left)
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0x18FFFFFF)))
                    .cornerRadius(20.dp)
            ) {}

            Column(modifier = GlanceModifier.fillMaxSize()) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🪙 Coin Tracker",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(textColor)
                        )
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        profile,
                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(muted))
                    )
                }

                Spacer(GlanceModifier.height(6.dp))

                // ── Balance ───────────────────────────────────────────────────
                Text(
                    "${balance.formatCoins()} coins",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(primary)
                    )
                )

                Spacer(GlanceModifier.height(4.dp))

                // ── Goal + percent ────────────────────────────────────────────
                Text(
                    "Goal: ${goal.formatCoins()}  •  $pct%",
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(textColor))
                )

                Spacer(GlanceModifier.height(8.dp))

                // Progress bar — automatically handles fractional width for any widget size
                LinearProgressIndicator(
                    progress = pct / 100f,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .cornerRadius(4.dp),
                    color = ColorProvider(Color(0xFF10B981)),         // Your green fill
                    backgroundColor = ColorProvider(Color(0xFFE2E8F0)) // Your gray track
                )

                Spacer(GlanceModifier.height(8.dp))

                // ── 7-day rate ────────────────────────────────────────────────
                Text(
                    "7d avg: $rate coins/day",
                    style = TextStyle(fontSize = 11.sp, color = ColorProvider(muted))
                )
            }
        }
    }

    private fun Int.formatCoins(): String =
        toString().reversed().chunked(3).joinToString(",").reversed()
}

// ── Receiver ─────────────────────────────────────────────────────────────────

class CoinTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CoinTrackerWidget()
}

// ── Updater — call after every transaction save ───────────────────────────────

object WidgetUpdater {

    suspend fun update(ctx: Context) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val prefs = ctx.getSharedPreferences("cointracker_prefs", Context.MODE_PRIVATE)
        val profile = prefs.getString("last_profile", "Default") ?: "Default"
        val isDark  = prefs.getBoolean("is_dark_mode", true)

        try {
            val doc      = FirebaseFirestore.getInstance()
                .collection("user_data").document(uid).get().await()
            val profiles = doc.data?.get("profiles") as? Map<*, *> ?: return
            val pd       = profiles[profile] as? Map<*, *> ?: return
            val txns     = pd["transactions"] as? List<*> ?: emptyList<Any>()
            val settings = pd["settings"] as? Map<*, *>
            val goal     = (settings?.get("goal") as? Number)?.toInt() ?: 13500

            val balance = txns.sumOf {
                (it as? Map<*, *>)?.get("amount")?.let { a -> (a as? Number)?.toInt() } ?: 0
            }
            val progress = if (goal > 0) ((balance.toDouble() / goal) * 100).toInt().coerceIn(0, 100) else 0

            // 7-day rate
            val sevenAgo = LocalDate.now(ZoneOffset.UTC).minusDays(7).toString()
            val earnings7d = txns.sumOf { tx ->
                val m   = tx as? Map<*, *> ?: return@sumOf 0
                val amt = (m["amount"] as? Number)?.toInt() ?: 0
                val date = m["date"] as? String ?: return@sumOf 0
                if (amt > 0 && date >= sevenAgo) amt else 0
            }
            val rate = (earnings7d / 7.0).toInt()

            GlanceAppWidgetManager(ctx)
                .getGlanceIds(CoinTrackerWidget::class.java)
                .forEach { id ->
                    updateAppWidgetState(ctx, PreferencesGlanceStateDefinition, id) { p ->
                        p.toMutablePreferences().apply {
                            this[KEY_BALANCE]  = balance
                            this[KEY_GOAL]     = goal
                            this[KEY_PROGRESS] = progress
                            this[KEY_RATE]     = rate.toString()
                            this[KEY_PROFILE]  = profile
                            this[KEY_IS_DARK]  = isDark.toString()
                        }
                    }
                    CoinTrackerWidget().update(ctx, id)
                }
        } catch (e: Exception) {
            // Widget update failure is non-fatal
        }
    }
}
