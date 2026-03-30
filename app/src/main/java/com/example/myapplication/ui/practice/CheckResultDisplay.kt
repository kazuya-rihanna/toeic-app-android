package com.example.myapplication.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.model.CheckResult
import com.example.myapplication.data.model.WordMatch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@Composable
fun CheckResultDisplay(
    result: CheckResult,
    modifier: Modifier = Modifier
) {
    val wordMatches = result.wordLevelMatch?.wordMatches 
        ?: result.wordLevelDetails?.wordMatches // Fallback if name differs
        ?: emptyList()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Correction Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            mainAxisSpacing = 4.dp,
            crossAxisSpacing = 4.dp
        ) {
            wordMatches.forEach { match ->
                WordMatchItem(match)
            }
        }
    }
}

@Composable
fun WordMatchItem(match: WordMatch) {
    val isMatch = match.exactMatch == true || match.isMatch == true
    
    if (isMatch) {
        Text(
            text = match.inputWord ?: "",
            color = MaterialTheme.colorScheme.onSurface
        )
    } else {
        Row {
            if (!match.correctWord.isNullOrEmpty()) {
                Text(
                    text = match.correctWord,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.LineThrough
                )
            }
            if (!match.inputWord.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = match.inputWord,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

// FlowRow is available in foundation layout or accompanist, but for simplicity I'll use a wrap layout or just Row
// In Compose 1.4+, FlowRow is in androidx.compose.foundation.layout
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(mainAxisSpacing),
        verticalArrangement = Arrangement.spacedBy(crossAxisSpacing)
    ) {
        content()
    }
}
