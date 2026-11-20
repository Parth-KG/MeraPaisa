package com.kg.merapaisa.ui.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.ui.PrimaryAction
import com.kg.merapaisa.ui.SecondaryAction
import com.kg.merapaisa.ui.SheetFoot
import com.kg.merapaisa.ui.SheetFrame
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * The first open after an update: that version's headline changes, or what matters across the
 * versions skipped, in one sheet, once.
 *
 * Existing users get this and never the beginner walkthrough. For the release that brought the
 * walkthrough it offers it, as the way out rather than the way forward, so nobody is steered into
 * it (Parth, 20 Nov 2026).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(whatsNew: WhatsNew, onClose: () -> Unit, onShowWalkthrough: () -> Unit) {
    val theme = LocalAppTheme.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    // Once only: the buttons stay live while the sheet slides away.
    var closing by remember { mutableStateOf(false) }
    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    SheetFrame(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                "What's new in ${whatsNew.versionName}",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg).semantics { heading() }
            )
            whatsNew.lines.forEach { line ->
                Text(
                    line,
                    style = MeraPaisaType.body,
                    color = theme.textPrimary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
                )
            }
            Spacer(Modifier.height(Spacing.xs))
        }

        SheetFoot(above = scroll) {
            if (whatsNew.offersWalkthrough) {
                SecondaryAction("Show me how it works", enabled = true) { close(onShowWalkthrough) }
            }
            PrimaryAction("Back to your ledger", enabled = true) { close(onClose) }
        }
    }
}
