package com.example.myapplication.ui.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.model.Progress
import com.example.myapplication.data.remote.ChartEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreScreen(
    onBack: () -> Unit,
    viewModel: ScoreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProgress("VnocHGzzyhNUbkx2YVw9qi1GtFe2")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Score Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is ScoreUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ScoreUiState.Error -> {
                    Text(
                        state.message, 
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp).align(Alignment.Center)
                    )
                }
                is ScoreUiState.Success -> {
                    ScoreDashboard(
                        state.progress, 
                        state.chartData, 
                        state.displayMonth,
                        onNextMonth = { viewModel.nextMonth() },
                        onPrevMonth = { viewModel.prevMonth() }
                    )
                }
            }
        }
    }
}

val categoryDisplayNames = mapOf(
    "nakamura_vocabulary_1_part_1" to "Nakamura Vocab 1 (Part 1)",
    "nakamura_vocabulary_1_part_2_3" to "Nakamura Vocab 1 (Part 2-3)",
    "nakamura_vocabulary_1_part_4" to "Nakamura Vocab 1 (Part 4)",
    "nakamura_vocabulary_1_part_5_6" to "Nakamura Vocab 1 (Part 5-6)",
    "nakamura_vocabulary_1_part_7" to "Nakamura Vocab 1 (Part 7)",
    "nakamura_vocabulary_2" to "Nakamura Vocab 2",
    "gold_phrases" to "Gold Phrases",
    "gold_sentences" to "Gold Sentences",
    "gold_collocations" to "Gold Collocations",
    "toeic_official_vocabulary" to "TOEIC Official Vocabulary",
    "black_phrases" to "Black Phrases"
)

@Composable
fun ScoreDashboard(
    progressMap: Map<String, Progress>, 
    chartData: List<ChartEntry>,
    displayMonth: YearMonth,
    onNextMonth: () -> Unit,
    onPrevMonth: () -> Unit
) {
    val scrollState = rememberScrollState()
    val totalScore = progressMap.values.sumOf { p -> p.score.values.sum() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Total Score Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.MilitaryTech, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Column {
                    Text("Total Score", style = MaterialTheme.typography.titleMedium)
                    Text(
                        totalScore.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "UID: VnocHGzzyhNUbkx2YVw9qi1GtFe2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Daily Stats Header with Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Daily Stats", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.Default.NavigateBefore, contentDescription = "Previous Month")
                }
                Text(
                    displayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(min = 120.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.NavigateNext, contentDescription = "Next Month")
                }
            }
        }

        if (chartData.isNotEmpty()) {
            ActivityChart(chartData)
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Score by Category Grid
        Text("Score by Category", style = MaterialTheme.typography.titleLarge)
        GridScoreDisplay(progressMap)

        // Detailed Completion
        Text("Completed Pages", style = MaterialTheme.typography.titleLarge)
        progressMap.forEach { (id, progress) ->
            CategoryDetailItem(id, progress)
        }
    }
}

@Composable
fun GridScoreDisplay(progressMap: Map<String, Progress>) {
    // Since we are inside a vertical scrollable Column, we use a custom non-scrollable grid or flow layout
    // for simplicity, let's use chunks of lists.
    val items = progressMap.keys.toList().sorted()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { id ->
                    val progress = progressMap[id] ?: Progress()
                    CategorySummaryCard(id, progress, Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CategorySummaryCard(id: String, progress: Progress, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                categoryDisplayNames[id] ?: id,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                MethodScore("Typing", progress.score["typing"] ?: 0)
                MethodScore("STT", progress.score["stt"] ?: 0)
                MethodScore("Sync", progress.score["livescribe"] ?: 0)
            }
        }
    }
}

@Composable
fun MethodScore(label: String, score: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        Text(score.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ActivityChart(chartData: List<ChartEntry>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    
    val maxVal = chartData.maxOf { it.stt + it.livescribe + it.typing }.coerceAtLeast(10).toFloat()

    Column {
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LegendItem("STT", primaryColor)
            LegendItem("Sync", secondaryColor)
            LegendItem("Typing", tertiaryColor)
        }

        Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)) {
                val width = size.width
                val height = size.height - 40f // Margin for labels
                val barWidth = (width / chartData.size).coerceAtMost(40.dp.toPx()) * 0.7f
                val spacing = width / chartData.size

                chartData.forEachIndexed { index, entry ->
                    val x = index * spacing + (spacing - barWidth) / 2f
                    
                    var currentUpperY = height
                    val total = entry.stt + entry.livescribe + entry.typing
                    
                    // Typing (Bottom)
                    val typingHeight = (entry.typing / maxVal) * height
                    drawRect(
                        color = tertiaryColor,
                        topLeft = Offset(x, height - typingHeight),
                        size = Size(barWidth, typingHeight)
                    )
                    currentUpperY -= typingHeight

                    // Livescribe (Middle)
                    val liveHeight = (entry.livescribe / maxVal) * height
                    drawRect(
                        color = secondaryColor,
                        topLeft = Offset(x, currentUpperY - liveHeight),
                        size = Size(barWidth, liveHeight)
                    )
                    currentUpperY -= liveHeight

                    // STT (Top)
                    val sttHeight = (entry.stt / maxVal) * height
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(x, currentUpperY - sttHeight),
                        size = Size(barWidth, sttHeight)
                    )
                    currentUpperY -= sttHeight

                    // Draw Day Text
                    val day = entry.date.split("-").last()
                    drawContext.canvas.nativeCanvas.drawText(
                        day,
                        x + barWidth / 2f,
                        height + 30f,
                        android.graphics.Paint().apply {
                            color = onSurface.toArgb()
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 24f
                        }
                    )

                    // Draw Total Count on top if > 0
                    if (total > 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            total.toString(),
                            x + barWidth / 2f,
                            currentUpperY - 10f,
                            android.graphics.Paint().apply {
                                color = onSurface.toArgb()
                                textAlign = android.graphics.Paint.Align.CENTER
                                textSize = 20f
                                isFakeBoldText = true
                            }
                        )
                    }
                }
                
                // X-Axis line
                drawLine(
                    color = Color.Gray.copy(alpha = 0.5f),
                    start = Offset(0f, height),
                    end = Offset(width, height),
                    strokeWidth = 2f
                )
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(12.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun CategoryDetailItem(id: String, progress: Progress) {
    var expanded by remember { mutableStateOf(false) }
    val totalPages = progress.totalPages ?: 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryDisplayNames[id] ?: id, 
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                MethodProgressBar("Typing", progress.correct["typing"]?.size ?: 0, totalPages, MaterialTheme.colorScheme.primary)
                MethodProgressBar("STT", progress.correct["stt"]?.size ?: 0, totalPages, MaterialTheme.colorScheme.secondary)
                MethodProgressBar("Sync", progress.correct["livescribe"]?.size ?: 0, totalPages, MaterialTheme.colorScheme.tertiary)
                
                Spacer(Modifier.height(16.dp))
                Text("Completed Pages:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                
                // Wrap Flow for pages
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (p in 1..totalPages) {
                        PageStatusChip(p, progress)
                    }
                }
            }
        }
    }
}

@Composable
fun MethodProgressBar(label: String, count: Int, total: Int, color: Color) {
    val ratio = if (total > 0) count.toFloat() / total else 0f
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text("$count / $total", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun PageStatusChip(page: Int, progress: Progress) {
    val pageKey = "page_$page"
    val clearedTyping = progress.correct["typing"]?.get(pageKey) == true
    val clearedStt = progress.correct["stt"]?.get(pageKey) == true
    val clearedLive = progress.correct["livescribe"]?.get(pageKey) == true
    val isAny = clearedTyping || clearedStt || clearedLive

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(width = 40.dp, height = 32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(page.toString(), fontSize = 10.sp, color = if (isAny) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                Row {
                    if (clearedTyping) Icon(Icons.Default.Keyboard, null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.primary)
                    if (clearedStt) Icon(Icons.Default.Mic, null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.secondary)
                    if (clearedLive) Icon(Icons.Default.EditNote, null, modifier = Modifier.size(8.dp), tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
