package com.merapaisa.widget

import android.content.Context
import androidx.glance.action.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.color.ColorProvider
import com.merapaisa.MainActivity
import com.merapaisa.data.AppDatabase
import com.merapaisa.data.Person
import kotlinx.coroutines.flow.first

class DebtWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val persons = AppDatabase.getDatabase(context).personDao().getAllPersons().first()
            .filter { !it.isSettled && it.balance != 0.0 }

        provideContent {
            WidgetContent(persons = persons)
        }
    }
}

@Composable
fun WidgetContent(persons: List<Person>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = Color(0xFF111118), night = Color(0xFF111118)))
            .padding(12.dp)
            .clickable(onClick = actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "Mera Paisa",
            style = TextStyle(
                color = ColorProvider(day = Color.White, night = Color.White),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = GlanceModifier.padding(bottom = 8.dp)
        )

        if (persons.isEmpty()) {
            Text(
                "No active debts",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF555555), night = Color(0xFF555555)),
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
                            .background(
                                ColorProvider(
                                    day = try { Color(android.graphics.Color.parseColor(person.pfpColor)).copy(alpha = 0.3f) } catch (e: Exception) { Color(0xFF2ECC71).copy(alpha = 0.3f) },
                                    night = try { Color(android.graphics.Color.parseColor(person.pfpColor)).copy(alpha = 0.3f) } catch (e: Exception) { Color(0xFF2ECC71).copy(alpha = 0.3f) }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            person.name.take(2).uppercase(),
                            style = TextStyle(
                                color = ColorProvider(day = Color.White, night = Color.White),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        person.name,
                        style = TextStyle(
                            color = ColorProvider(day = Color.White, night = Color.White),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Text(
                        formatWidgetAmount(person.balance, person.currency),
                        style = TextStyle(
                            color = ColorProvider(
                                day = if (person.balance > 0) Color(0xFF2ECC71) else Color(0xFFE84B3A),
                                night = if (person.balance > 0) Color(0xFF2ECC71) else Color(0xFFE84B3A)
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
fun formatWidgetAmount(balance: Double, currency: String = "₹"): String {
    val abs = "%.0f".format(Math.abs(balance))
    return if (balance < 0) "-$currency$abs" else "$currency$abs"
}

class DebtWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DebtWidget()
}