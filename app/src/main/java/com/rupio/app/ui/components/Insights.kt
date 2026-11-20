package com.rupio.app.ui.components


import com.rupio.app.core.tr
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
    StatTile(tr("Calorías", "Calories"), "≈ ${r.kcal} kcal",
        Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = tr("ver cómo se calcula", "see how it's calculated")) { info = true },
        sub = if (r.assumedWeight) tr("con 70 kg · toca para ver", "using 70 kg · tap to see") else tr("estimado · toca para ver", "estimate · tap to see"))
    if (info) CaloriesInfoDialog(r) { info = false }
}


@Composable
fun CaloriesInfoDialog(r: CalorieEstimate.Result, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("¿Cómo se calculan?", "How are they calculated?")) },
        text = {
            Text(buildString {
                if (r.strengthMinutes > 0) {
                    append(tr("Con el método MET: ${Fmt.num(CalorieEstimate.MET)} MET × ${Fmt.num(r.bodyKg, 1)} kg × ${r.strengthMinutes} min de pesas. ", "Using the MET method: ${Fmt.num(CalorieEstimate.MET)} MET × ${Fmt.num(r.bodyKg, 1)} kg × ${r.strengthMinutes} min of weights. "))
                    append(tr("3.5 MET es el valor para pesas con varios ejercicios de 8 a 15 repeticiones (Compendio de Actividades Físicas 2024, código ${CalorieEstimate.COMPENDIUM_CODE}). ", "3.5 MET is the value for weight training with several exercises of 8 to 15 reps (2024 Compendium of Physical Activities, code ${CalorieEstimate.COMPENDIUM_CODE}). "))
                }
                if (r.cardioMinutes > 0) append(tr("Tus ${r.cardioMinutes} min de cardio cuentan con el MET de cada máquina a esfuerzo moderado " +
                    "(caminadora 3.8, bicicleta fija 5.8, elíptica 5.0, escaladora 9.3; mismo Compendio). ",
                    "Your ${r.cardioMinutes} min of cardio count with each machine's MET at moderate effort " +
                        "(treadmill 3.8, stationary bike 5.8, elliptical 5.0, stair climber 9.3; same Compendium). "))
                append(tr("Incluye lo que gastarías en reposo y puede variar ±20–30 % según tu músculo, la intensidad y los descansos.", "It includes what you'd burn at rest and can vary ±20–30% depending on your muscle mass, intensity and rest periods."))
                if (r.assumedWeight) append(tr("\n\nNo tienes tu peso registrado: se usó 70 kg. Regístralo en la pestaña Peso para afinarla.", "\n\nYou haven't logged your weight, so 70 kg was used. Log it in the Weight tab to make it more accurate."))
                if (r.capped) append(tr("\n\nSe contaron ${r.strengthMinutes} min de pesas (máximo 5 por serie) porque el entreno quedó abierto más tiempo.", "\n\n${r.strengthMinutes} min of weights were counted (5 max per set) because the workout was left open longer."))
            }, style = MaterialTheme.typography.bodyMedium, color = RupioColors.TextDim)
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Entendido", "Got it")) } },
    )
}
