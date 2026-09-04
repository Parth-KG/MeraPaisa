package com.kg.merapaisa.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import com.kg.merapaisa.AppTheme
import com.kg.merapaisa.MainActivity
import com.kg.merapaisa.ThemeStore
import com.kg.merapaisa.data.AppDatabase
import com.kg.merapaisa.data.PersonWithBalance
import com.kg.merapaisa.repository.PersonRepository
import com.kg.merapaisa.data.CurrencyTotal
import com.kg.merapaisa.data.formatSignedAmount
import com.kg.merapaisa.data.netTotalsByCurrency
import com.kg.merapaisa.getThemeByName
import com.kg.merapaisa.repository.LedgerChangeNotifier
import kotlinx.coroutines.flow.first

/**
 * The widget sits on the home screen, which has its own light/dark mode independent of the
 * app. So it takes the user's chosen palette for whichever mode matches that palette, and a
 * legible counterpart for the other, instead of being dark-only.
 */
data class WidgetPalette(val day: AppTheme, val night: AppTheme) {
    fun of(pick: (AppTheme) -> Color) = ColorProvider(day = pick(day), night = pick(night))
}

private fun paletteFor(themeName: String): WidgetPalette {
    val selected = getThemeByName(themeName)
    return if (selected.isDark) {
        WidgetPalette(day = getThemeByName("Paper"), night = selected)
    } else {
        WidgetPalette(day = selected, night = getThemeByName("Midnight"))
    }
}

class DebtWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = PersonRepository(AppDatabase.getDatabase(context).personDao())
        val persons = repository.personsWithBalances().first()
            .filter { !it.isSettled && it.balanceMinor != 0L }
        val palette = paletteFor(ThemeStore.getTheme(context).first())

        provideContent {
            WidgetContent(
                persons = persons,
                totals = netTotalsByCurrency(persons),
                palette = palette
            )
        }
    }
}

@Composable
fun WidgetContent(
    persons: List<PersonWithBalance>,
    totals: List<CurrencyTotal>,
    palette: WidgetPalette
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.of { it.surface })
            .padding(12.dp)
            .clickable(onClick = actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mera Paisa",
                style = TextStyle(
                    color = palette.of { it.textPrimary },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            // Where you stand overall, one figure per currency — never added together.
            totals.take(2).forEach { total ->
                Text(
                    formatSignedAmount(total.amountMinor, total.currency),
                    style = TextStyle(
                        color = if (total.amountMinor > 0) {
                            palette.of { it.positive }
                        } else {
                            palette.of { it.negative }
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.padding(start = 8.dp)
                )
            }
        }

        if (persons.isEmpty()) {
            Text(
                "No active debts",
                style = TextStyle(
                    color = palette.of { it.textSecondary },
                    fontSize = 12.sp
                )
            )
        } else {
            persons.take(4).forEach { person ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // PFP initials box
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .background(ColorProvider(day = avatarTint(person), night = avatarTint(person))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            person.name.take(2).uppercase(),
                            style = TextStyle(
                                color = palette.of { it.textPrimary },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        person.name,
                        style = TextStyle(
                            color = palette.of { it.textPrimary },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        // Signed, so the direction of the debt does not rest on colour alone.
                        formatSignedAmount(person.balanceMinor, person.currency),
                        style = TextStyle(
                            color = if (person.balanceMinor > 0) {
                                palette.of { it.positive }
                            } else {
                                palette.of { it.negative }
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

private fun avatarTint(person: PersonWithBalance): Color = try {
    Color(android.graphics.Color.parseColor(person.person.pfpColor)).copy(alpha = 0.3f)
} catch (e: Exception) {
    Color(0xFF2ECC71).copy(alpha = 0.3f)
}

class DebtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DebtWidget()
}

/**
 * Pushes the widget when the ledger changes, replacing the hand-rolled
 * ACTION_APPWIDGET_UPDATE broadcast the ViewModel used to send from an Activity Context.
 */
class WidgetLedgerNotifier(private val appContext: Context) : LedgerChangeNotifier {
    override suspend fun onLedgerChanged() {
        DebtWidget().updateAll(appContext)
    }
}
