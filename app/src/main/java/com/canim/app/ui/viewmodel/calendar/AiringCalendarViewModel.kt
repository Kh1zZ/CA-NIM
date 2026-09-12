package com.canim.app.ui.viewmodel.calendar

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canim.app.data.model.AiringAnimeItem
import com.canim.app.data.model.UserMediaItem
import com.canim.app.domain.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class AiringCalendarUiState(
    val selectedDay: DayOfWeek = LocalDate.now().dayOfWeek,
    val filterOnlyWatching: Boolean = false,
    val isLoading: Boolean = false,
    val weekSchedule: Map<DayOfWeek, List<AiringAnimeItem>> = emptyMap(),
    val errorMessage: String? = null
) {
    fun currentDayItems(watchingMalIds: Set<Int>): List<AiringAnimeItem> {
        val allForDay = weekSchedule[selectedDay] ?: emptyList()
        return if (filterOnlyWatching) {
            allForDay.filter { it.malId != null && it.malId in watchingMalIds }
        } else {
            allForDay
        }
    }

    fun countForDay(day: DayOfWeek, watchingMalIds: Set<Int>): Int {
        val items = weekSchedule[day] ?: emptyList()
        return if (filterOnlyWatching) {
            items.count { it.malId != null && it.malId in watchingMalIds }
        } else {
            items.size
        }
    }
}

@HiltViewModel
class AiringCalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context? = null
) : ViewModel() {

    private val _calendarState = MutableStateFlow(AiringCalendarUiState())
    val calendarState: StateFlow<AiringCalendarUiState> = _calendarState.asStateFlow()

    init {
        loadAiringCalendar()
    }

    fun loadAiringCalendar(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _calendarState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val schedule = calendarRepository.getAiringCalendar(forceRefresh)
                _calendarState.update {
                    it.copy(
                        weekSchedule = schedule,
                        isLoading = false
                    )
                }
                context?.let { ctx ->
                    com.canim.app.widget.WidgetUpdateHelper.updateTodayAiringWidgets(ctx)
                }
            } catch (e: Exception) {
                _calendarState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Gagal memuat jadwal tayang: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectDay(day: DayOfWeek) {
        _calendarState.update { it.copy(selectedDay = day) }
    }

    fun toggleFilterOnlyWatching() {
        _calendarState.update { it.copy(filterOnlyWatching = !it.filterOnlyWatching) }
    }
}
