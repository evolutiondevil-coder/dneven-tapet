package bg.dnevnitapet.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Everything chosen/switched on in the app is orange, so it is clear at a glance what is selected
 * (the phone's own theme colours made chosen and not chosen look almost the same).
 */
val SelectedOrange = Color(0xFFFF9800)

/** Text and icons on [SelectedOrange]. */
val OnSelectedOrange = Color(0xFF231400)

/** A choice chip: chosen = orange with a ✓, not chosen = only an outline. */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = SelectedOrange,
            selectedLabelColor = OnSelectedOrange,
            selectedLeadingIconColor = OnSelectedOrange,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = SelectedOrange,
        ),
    )
}

@Composable
fun choiceRadioColors(): RadioButtonColors = RadioButtonDefaults.colors(selectedColor = SelectedOrange)

@Composable
fun choiceSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = SelectedOrange,
    checkedBorderColor = SelectedOrange,
    checkedIconColor = SelectedOrange,
)
