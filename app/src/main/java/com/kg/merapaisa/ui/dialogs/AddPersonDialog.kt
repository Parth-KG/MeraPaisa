package com.kg.merapaisa.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kg.merapaisa.CurrencyStore
import com.kg.merapaisa.LocalAppTheme
import com.kg.merapaisa.data.SUPPORTED_CURRENCIES
import com.kg.merapaisa.data.currencySymbol
import com.kg.merapaisa.deleteProfilePhoto
import com.kg.merapaisa.saveProfilePhoto
import com.kg.merapaisa.ui.AVATAR_HUES
import com.kg.merapaisa.ui.avatarInk
import com.kg.merapaisa.ui.theme.MeraPaisaType
import com.kg.merapaisa.ui.theme.Shapes
import com.kg.merapaisa.ui.theme.Spacing
import com.kg.merapaisa.ui.SheetFrame
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight

/**
 * A name, a face, and the currency this person's balance is kept in.
 *
 * It was an AlertDialog wrapped around five fields, which is a box sized for a question holding a
 * form. A dialog is for a decision; a short form goes on a sheet that can take the height and be
 * scrolled, so this is one now.
 *
 * The currency is the last one used rather than a fixed rupee, because somebody who has just added
 * two people in dollars is almost certainly adding a third.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String) -> Unit,
    /** Everyone already in the ledger, to say so when a name is taken. */
    existingNames: List<String> = emptyList()
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var pfpType by remember { mutableStateOf("initials") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var emoji by remember { mutableStateOf("") }
    // Until a colour is picked by hand, the name picks one. Defaulting to the first swatch gave
    // every person added without opening the picker the same indigo, which is the all-green
    // problem the eight hues were meant to end. A name always lands on the same hue, so the
    // swatch does not flicker between visits, and two people rarely share one.
    var pickedColour by remember { mutableStateOf<String?>(null) }
    val selectedColour = pickedColour ?: hueForName(name)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            // Downscaled and written off the main thread; the old file is replaced.
            saveProfilePhoto(context, uri, photoPath)?.let {
                photoPath = it
                pfpType = "photo"
            }
        }
    }

    val lastCurrency by CurrencyStore.getLastCurrency(context).collectAsState(initial = "INR")
    var selectedCurrency by remember { mutableStateOf("INR") }
    LaunchedEffect(lastCurrency) { selectedCurrency = lastCurrency }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val dismiss = {
        // Nothing was saved, so the picked file has no owner.
        deleteProfilePhoto(context, photoPath)
        onDismiss()
    }

    SheetFrame(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "New person",
                style = MeraPaisaType.screenTitle,
                color = theme.textPrimary,
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
            Spacer(Modifier.height(Spacing.lg))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("John Doe") },
                // Allowed, but said: two people with one name read the same on every list, and a
                // link is filed against whoever has the sender's name.
                supportingText = if (existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) } && name.isNotBlank()) {
                    { Text("You already have someone called ${name.trim()}. Add a surname or an initial to tell them apart.") }
                } else null,
                textStyle = MeraPaisaType.body,
                singleLine = true,
                shape = Shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )

            AvatarPicker(
                pfpType = pfpType,
                onTypeChange = { pfpType = it },
                emoji = emoji,
                // One character: typing another replaces it rather than adding to it.
                onEmojiChange = { emoji = com.kg.merapaisa.data.firstCharacter(it.removePrefix(emoji).ifEmpty { it }) },
                hasPhoto = photoPath != null,
                selectedColour = selectedColour,
                onColourChange = { pickedColour = it },
                onPickPhoto = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            SheetHeading("Currency")
            CurrencyChips(selected = selectedCurrency, onSelect = { selectedCurrency = it })
        }

        Row(
            modifier = Modifier.height(IntrinsicSize.Min)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() } },
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                border = BorderStroke(1.dp, theme.outline),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textPrimary)
            ) {
                Text("Don't add", style = MeraPaisaType.action)
            }
            Button(
                onClick = {
                    val personName = name.trim()
                    val type = avatarTypeFor(pfpType, emoji, photoPath)
                    val value = avatarValueFor(type, personName, emoji, photoPath)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onAdd(personName, type, value, selectedColour, selectedCurrency)
                    }
                },
                enabled = name.isNotBlank(),
                shape = Shapes.small,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp).fillMaxHeight(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = theme.background
                )
            ) {
                Text("Add person", style = MeraPaisaType.action)
            }
        }
    }
}

/**
 * What the avatar ends up being, once the picker's state is read honestly.
 *
 * A type is only kept if it has something to draw with. An emoji nobody typed and a photo that was
 * never chosen both used to save as their own type and leave a blank tile in the list, so both
 * fall back to initials, which every person has.
 */
internal fun avatarTypeFor(pfpType: String, emoji: String, photoPath: String?): String = when {
    pfpType == "emoji" && emoji.isBlank() -> "initials"
    pfpType == "photo" && photoPath == null -> "initials"
    else -> pfpType
}

/** The value that goes with [avatarTypeFor]: the emoji, the file, or the first two letters. */
internal fun avatarValueFor(
    type: String,
    personName: String,
    emoji: String,
    photoPath: String?
): String = when (type) {
    "emoji" -> emoji.trim()
    "photo" -> photoPath.orEmpty()
    else -> com.kg.merapaisa.data.initialsOf(personName)
}

/**
 * How a person will look in the list, chosen here and drawn the same way here as there.
 *
 * Shared with the edit sheet on purpose: two forms describing the same avatar have to offer the
 * same choices, or the same person changes appearance depending on which one you happened to open.
 *
 * The swatches are drawn through `avatarInk`, not by parsing the stored hex. The stored value is a
 * choice of hue and the ink is re-lit for the theme, so a picker showing the raw hex would be
 * showing a colour that never appears on screen: on Paper every swatch would sit lighter than the
 * avatar it makes, and on Amoled darker.
 */
@Composable
internal fun AvatarPicker(
    pfpType: String,
    onTypeChange: (String) -> Unit,
    emoji: String,
    onEmojiChange: (String) -> Unit,
    hasPhoto: Boolean,
    selectedColour: String,
    onColourChange: (String) -> Unit,
    onPickPhoto: () -> Unit
) {
    val theme = LocalAppTheme.current

    SheetHeading("Shown as")
    Row(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        AVATAR_TYPES.forEach { (type, label) ->
            FilterChip(
                selected = pfpType == type,
                onClick = {
                    // Photo only becomes the type once a file is actually saved. Setting it on the
                    // tap left an avatar with no image and no fallback whenever somebody opened
                    // the picker and backed out of it.
                    if (type == "photo") onPickPhoto() else onTypeChange(type)
                },
                shape = Shapes.small,
                modifier = Modifier.heightIn(min = 48.dp),
                label = { Text(label, style = MeraPaisaType.action) }
            )
        }
    }

    if (pfpType == "emoji") {
        Spacer(Modifier.height(Spacing.md))
        OutlinedTextField(
            value = emoji,
            onValueChange = onEmojiChange,
            label = { Text("Emoji") },
            supportingText = {
                Text(
                    "One character from your keyboard. If you leave it empty, their initials show.",
                    style = MeraPaisaType.label
                )
            },
            textStyle = MeraPaisaType.body,
            singleLine = true,
            shape = Shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
        )
    }

    if (pfpType == "photo") {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (hasPhoto) "Tap Photo again to choose a different one."
            else "No photo chosen yet, so their initials will show.",
            style = MeraPaisaType.label,
            color = theme.textSecondary,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
    } else {
        // Offered for an emoji as well as for initials, because the tile an emoji sits on is
        // washed in this same ink. Only a photo covers the tile completely, and picking a colour
        // nobody will ever see is a control that does nothing.
        SheetHeading("Colour")
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            AVATAR_HUES.forEach { hue ->
                ColourSwatch(
                    hue = hue,
                    selected = selectedColour == hue,
                    onSelect = { onColourChange(hue) }
                )
            }
        }
    }
}

/**
 * One colour on offer, at the size it will be drawn and in the ink it will be drawn with.
 *
 * The tap target is the full 48dp square; the ink inside it is smaller, so eight of them fit two
 * to a line on a narrow phone without any of them becoming a target you have to aim at.
 */
@Composable
private fun ColourSwatch(hue: String, selected: Boolean, onSelect: () -> Unit) {
    val theme = LocalAppTheme.current
    val ink = remember(hue, theme.isDark) { avatarInk(hue, theme.isDark) }
    val name = AVATAR_HUE_NAMES[hue] ?: "Avatar colour"

    Box(
        modifier = Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(Shapes.circle)
                .background(ink)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) theme.textPrimary else theme.outline,
                    shape = Shapes.circle
                )
        )
    }
}

/** The currencies a balance can be kept in. Symbols, because that is what the amounts will wear. */
@Composable
internal fun CurrencyChips(selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        SUPPORTED_CURRENCIES.forEach { code ->
            FilterChip(
                selected = selected == code,
                enabled = enabled,
                onClick = { onSelect(code) },
                shape = Shapes.small,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = code },
                label = { Text(currencySymbol(code), style = MeraPaisaType.body) }
            )
        }
    }
}

/** A quiet label over a block of a form, with air above it and none below. */
@Composable
internal fun SheetHeading(text: String) {
    val theme = LocalAppTheme.current
    Text(
        text,
        style = MeraPaisaType.sectionTitle,
        color = theme.textSecondary,
        modifier = Modifier.padding(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.xl,
            bottom = Spacing.sm
        )
    )
}

/** The three kinds of avatar, with the word each one is offered under. */
private val AVATAR_TYPES = listOf(
    "initials" to "Initials",
    "emoji" to "Emoji",
    "photo" to "Photo"
)

/**
 * What to call each hue out loud.
 *
 * A swatch is a coloured square with no text in it, so without these TalkBack reads eight
 * identical unlabelled controls and the picker becomes unusable for anyone who cannot see which
 * one is which. The names are the ones the palette itself is written with.
 */
private val AVATAR_HUE_NAMES = mapOf(
    "#3F6DA8" to "Indigo",
    "#2E8079" to "Teal",
    "#6E8C3A" to "Olive",
    "#A8802E" to "Ochre",
    "#A65A2E" to "Rust",
    "#A6423F" to "Brick",
    "#8C4A7D" to "Plum",
    "#5B5F8C" to "Slate"
)

/** The hue a name lands on when nobody has picked one. Stable: String.hashCode is specified. */
internal fun hueForName(name: String): String =
    AVATAR_HUES[Math.floorMod(name.trim().lowercase().hashCode(), AVATAR_HUES.size)]
