package ai.gyango.chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Year

private val MONTH_ABBREVS = listOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

fun birthMonthAbbrev(month1Based: Int): String =
    if (month1Based in 1..12) MONTH_ABBREVS[month1Based - 1] else ""

/** Birth year options for dropdowns: newest first, down to [oldestYear]. */
fun birthYearDropdownOptions(oldestYear: Int = Year.now().value - 100): List<Int?> {
    val y = Year.now().value
    return listOf<Int?>(null) + (y downTo oldestYear.coerceAtMost(y)).toList()
}

/**
 * Month and year as Material exposed dropdowns (JAN–DEC + optional year list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthMonthYearFields(
    month: Int?,
    onMonthChange: (Int?) -> Unit,
    year: Int?,
    onYearChange: (Int?) -> Unit,
    monthSectionLabel: @Composable () -> Unit,
    monthDropdownLabel: String,
    yearDropdownLabel: String,
    notSetLabel: String,
    modifier: Modifier = Modifier,
) {
    var monthMenu by remember { mutableStateOf(false) }
    var yearMenu by remember { mutableStateOf(false) }
    val monthDisplay = when (month) {
        null -> notSetLabel
        in 1..12 -> MONTH_ABBREVS[month - 1]
        else -> notSetLabel
    }
    val yearOptions = remember(notSetLabel) { birthYearDropdownOptions() }
    val yearDisplay = year?.toString() ?: notSetLabel

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        monthSectionLabel()
        ExposedDropdownMenuBox(
            expanded = monthMenu,
            onExpandedChange = { monthMenu = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = monthDisplay,
                onValueChange = {},
                label = { Text(monthDropdownLabel) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenu) },
                shape = EntryScreenFieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                ),
            )
            ExposedDropdownMenu(
                expanded = monthMenu,
                onDismissRequest = { monthMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(notSetLabel) },
                    onClick = {
                        onMonthChange(null)
                        monthMenu = false
                    },
                )
                for (m in 1..12) {
                    DropdownMenuItem(
                        text = { Text(MONTH_ABBREVS[m - 1]) },
                        onClick = {
                            onMonthChange(m)
                            monthMenu = false
                        },
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = yearMenu,
            onExpandedChange = { yearMenu = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = yearDisplay,
                onValueChange = {},
                label = { Text(yearDropdownLabel) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearMenu) },
                shape = EntryScreenFieldShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                ),
            )
            ExposedDropdownMenu(
                expanded = yearMenu,
                onDismissRequest = { yearMenu = false },
            ) {
                for (opt in yearOptions) {
                    val label = opt?.toString() ?: notSetLabel
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onYearChange(opt)
                            yearMenu = false
                        },
                    )
                }
            }
        }
    }
}
