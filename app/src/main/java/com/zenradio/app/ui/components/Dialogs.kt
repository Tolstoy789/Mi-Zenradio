package com.zenradio.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zenradio.app.ui.theme.ZenColors
import com.zenradio.app.ui.theme.ZenPalette
import com.zenradio.app.ui.theme.ZenPalettes

private val SleepTimerOptions = listOf(0, 15, 30, 45, 60, 90)

/** Hoja inferior: temporizador de apagado, "reproducir al iniciar" y tema de color. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sleepTimerText: String?,
    autoPlay: Boolean,
    selectedThemeId: String,
    onSetSleepTimer: (Int) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onThemeChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = ZenColors
    var chosen by remember { mutableStateOf<Int?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.sheet,
        contentColor = palette.textPrimary,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.textSecondary.copy(alpha = 0.4f)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Temporizador de apagado", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Text(
                text = sleepTimerText?.let { "La radio se apagará en $it, bajando el volumen poco a poco." }
                    ?: "La radio sonará hasta que la pares.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SleepTimerOptions.forEach { minutes ->
                    val selected = chosen?.let { it == minutes } ?: (minutes == 0 && sleepTimerText == null)
                    ZenChip(
                        label = if (minutes == 0) "No" else "$minutes min",
                        selected = selected,
                        onClick = {
                            chosen = minutes
                            onSetSleepTimer(minutes)
                        },
                    )
                }
            }

            SheetDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Reproducir al iniciar", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                    Text(
                        "Suena la última emisora al abrir la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                Switch(
                    checked = autoPlay,
                    onCheckedChange = onAutoPlayChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (palette.isLight) Color.White else palette.textPrimary,
                        checkedTrackColor = palette.accentDeep,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = palette.textSecondary,
                        uncheckedTrackColor = palette.switchOff,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }

            SheetDivider()
            Text("Tema de color", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ZenPalettes.all.forEach { option ->
                    ThemeSwatch(
                        option = option,
                        selected = option.id == selectedThemeId,
                        onClick = { onThemeChange(option.id) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetDivider() {
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(ZenColors.outline)
    )
}

/** Muestra de un tema: círculo con su fondo y su acento; el elegido lleva aro y check. */
@Composable
private fun ThemeSwatch(option: ZenPalette, selected: Boolean, onClick: () -> Unit) {
    val palette = ZenColors
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(2.dp, if (selected) palette.accent else Color.Transparent, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(option.backgroundTop, option.accent)))
                .border(1.dp, option.textPrimary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = option.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = option.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) palette.textPrimary else palette.textSecondary,
        )
    }
}

/** Formulario para añadir una emisora propia. */
@Composable
fun AddStationDialog(
    onAdd: (name: String, url: String) -> String?,
    onDismiss: () -> Unit,
) {
    val palette = ZenColors
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.sheet,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        title = { Text("Añadir emisora", style = MaterialTheme.typography.headlineSmall) },
        text = {
            val currentError = error
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("URL del stream (MP3/AAC)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = currentError != null,
                    supportingText = if (currentError != null) {
                        { Text(currentError) }
                    } else {
                        null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onAdd(name, url)
                if (result == null) onDismiss() else error = result
            }) { Text("Añadir", color = palette.accentText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.textSecondary) }
        },
    )
}
