package com.rupio.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rupio.app.core.Fmt
import com.rupio.app.domain.CalorieEstimate
import com.rupio.app.ui.theme.RupioColors

/** v1.8 · Tarjeta de calorías estimadas; al tocarla explica cómo se calcula y qué tan precisa es. */
@Composable
fun CaloriesTile(r: CalorieEstimate.Result) {
    var info by remember { mutableStateOf(false) }
    StatTile("Calorías", "≈ ${r.kcal} kcal",
        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = "ver cómo se calcula") { info = true },
        sub = if (r.assumedWeight) "con 70 kg · toca para ver" else "estimado · toca para ver")
    if (info) CaloriesInfoDialog(r) { info = false }
}


@Composable
fun CaloriesInfoDialog(r: CalorieEstimate.Result, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("¿Cómo se calculan?") },
        text = {
            Text(buildString {
                if (r.strengthMinutes > 0) {
                    append("Con el método MET: ${Fmt.num(CalorieEstimate.MET)} MET × ${Fmt.num(r.bodyKg, 1)} kg × ${r.strengthMinutes} min de pesas. ")
                    append("3.5 MET es el valor para pesas con varios ejercicios de 8 a 15 repeticiones (Compendio de Actividades Físicas 2024, código ${CalorieEstimate.COMPENDIUM_CODE}). ")
                }
                if (r.cardioMinutes > 0) append("Tus ${r.cardioMinutes} min de cardio cuentan con el MET de cada máquina a esfuerzo moderado " +
                    "(caminadora 3.8, bicicleta fija 5.8, elíptica 5.0, escaladora 9.3; mismo Compendio). ")
                append("Incluye lo que gastarías en reposo y puede variar ±20–30 % según tu músculo, la intensidad y los descansos.")
                if (r.assumedWeight) append("\n\nNo tienes tu peso registrado: se usó 70 kg. Regístralo en la pestaña Peso para afinarla.")
                if (r.capped) append("\n\nSe contaron ${r.strengthMinutes} min de pesas (máximo 5 por serie) porque el entreno quedó abierto más tiempo.")
            }, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Entendido") } },
    )
}
