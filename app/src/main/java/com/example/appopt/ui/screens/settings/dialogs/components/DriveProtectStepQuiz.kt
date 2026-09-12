package com.example.appopt.ui.screens.settings.dialogs.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.example.appopt.R
import com.example.appopt.security.MnemonicManager
import com.example.appopt.ui.theme.Dimensions

/**
 * Sub-componente para el Paso 3 del diálogo de protección: Cuestionario interactivo de verificación de palabras mnemónicas.
 *
 * Muestra las preguntas generadas aleatoriamente para validar que el usuario respaldó las palabras en su orden correcto.
 *
 * @param questions Lista de preguntas del cuestionario de verificación.
 * @param selectedAnswers Mapa asociativo con las respuestas seleccionadas por índice de pregunta.
 * @param isError Indica si la última validación falló para resaltar bordes en color de error.
 * @param onSelectAnswer Callback invocado al seleccionar una opción de palabra.
 * @param modifier Modificador de diseño Compose opcional.
 */
@Composable
fun DriveProtectStepQuiz(
    questions: List<MnemonicManager.MnemonicQuizQuestion>,
    selectedAnswers: Map<Int, String>,
    isError: Boolean,
    onSelectAnswer: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.md)
    ) {
        Text(
            text = stringResource(R.string.settings_drive_quiz_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        questions.forEachIndexed { qIdx, q ->
            Surface(
                shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    Dimensions.Stroke.thin,
                    if (isError && selectedAnswers[qIdx] == null) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Dimensions.Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.settings_drive_quiz_question_label, q.position),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.xs)
                    ) {
                        q.options.forEach { option ->
                            val isSelected = selectedAnswers[qIdx] == option
                            Surface(
                                onClick = { onSelectAnswer(qIdx, option) },
                                shape = RoundedCornerShape(Dimensions.CornerRadius.small),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    Dimensions.Stroke.thin,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(
                                        vertical = Dimensions.Spacing.sm,
                                        horizontal = Dimensions.Spacing.xs
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

