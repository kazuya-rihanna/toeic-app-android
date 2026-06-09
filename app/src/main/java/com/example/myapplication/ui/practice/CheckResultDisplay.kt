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
import androidx.compose.material3.Surface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckResultDisplay(
    result: CheckResult,
    modifier: Modifier = Modifier
) {
    // Try to find word matches in various possible structures
    val wordMatches = (result.wordLevelMatch?.wordMatches)
        ?: result.wordLevelDetails
        ?: result.details
        ?: result.resultDetails
        ?: result.directWordMatches
        ?: emptyList()

    val correctText = result.exactMatch?.normalizedCorrect 
        ?: result.overallStats?.processedCorrect 
        ?: ""
    val inputText = result.exactMatch?.normalizedInput 
        ?: result.overallStats?.processedInput 
        ?: ""

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Correction Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Comparison Summary Section
            if (correctText.isNotEmpty() || inputText.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    ComparisonLine(label = "Example", text = correctText)
                    Spacer(modifier = Modifier.height(8.dp))
                    ComparisonLine(label = "Your Input", text = inputText)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            // Word-by-word Detail Section
            if (wordMatches.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    wordMatches.forEach { match ->
                        DetailedWordMatch(match)
                    }
                }
            } else {
                // FALLBACK: If no word matches, show the similarity and some raw info
                Column {
                    Text(
                        "No word-level details available in result.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (result.similarity != null) {
                        Text("Similarity: ${String.format("%.2f", result.similarity)}")
                    }
                    if (result.isCorrect == true) {
                        Text("Result: Correct", color = Color(0xFF4CAF50))
                    }
                    // Debug Raw Data (Simplified)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Raw Result Structure: ${result.toString().take(200)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun ComparisonLine(label: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DetailedWordMatch(match: WordMatch) {
    val isMatch = match.exactMatch == true || match.isMatch == true
    val isMissing = (match.status == "missing") || (match.correctWord != null && match.inputWord == null)
    val isExtra = (match.status == "extra") || (match.inputWord != null && match.correctWord == null)

    when {
        isMatch -> {
            // Correct word - show normally
            Text(
                text = match.inputWord ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        isMissing -> {
            // Missing word - strike through on surface-variant
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = match.correctWord ?: "",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = TextDecoration.LineThrough
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        isExtra -> {
            // Extra word - error container highlight
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = match.inputWord ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        else -> {
            // Incorrect word - show both
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = match.correctWord ?: "",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = TextDecoration.LineThrough
                    ),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = match.inputWord ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
