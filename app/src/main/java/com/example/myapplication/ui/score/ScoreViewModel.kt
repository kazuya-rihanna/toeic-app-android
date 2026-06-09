package com.example.myapplication.ui.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.model.Progress
import com.example.myapplication.domain.ToeicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import com.example.myapplication.data.remote.ChartEntry
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ScoreViewModel @Inject constructor(
    private val repository: ToeicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScoreUiState>(ScoreUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var currentUserId: String = ""
    private var displayMonth = YearMonth.now()

    fun loadProgress(userId: String) {
        currentUserId = userId
        fetchData()
    }

    fun nextMonth() {
        displayMonth = displayMonth.plusMonths(1)
        fetchData()
    }

    fun prevMonth() {
        displayMonth = displayMonth.minusMonths(1)
        fetchData()
    }

    private fun fetchData() {
        if (currentUserId.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.value = ScoreUiState.Loading
            try {
                val startDate = displayMonth.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                val endDate = displayMonth.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)

                val (progressResponse, chartResponse) = supervisorScope {
                    val progressDeferred = async { repository.getProgress(currentUserId) }
                    val chartDataDeferred = async { repository.getChartData(currentUserId, startDate, endDate) }
                    progressDeferred.await() to chartDataDeferred.await()
                }

                if (progressResponse.isSuccessful) {
                    val progress = progressResponse.body()?.progress ?: emptyMap()
                    val chartData = if (chartResponse.isSuccessful) {
                        chartResponse.body()?.chartData ?: emptyList()
                    } else {
                        emptyList()
                    }
                    _uiState.value = ScoreUiState.Success(
                        progress = progress,
                        chartData = chartData,
                        displayMonth = displayMonth
                    )
                } else {
                    _uiState.value = ScoreUiState.Error("Failed to fetch progress: ${progressResponse.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = ScoreUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class ScoreUiState {
    object Loading : ScoreUiState()
    data class Success(
        val progress: Map<String, Progress>,
        val chartData: List<ChartEntry> = emptyList(),
        val displayMonth: YearMonth = YearMonth.now()
    ) : ScoreUiState()
    data class Error(val message: String) : ScoreUiState()
}
