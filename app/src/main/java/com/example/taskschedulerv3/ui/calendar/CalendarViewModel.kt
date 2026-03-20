package com.example.taskschedulerv3.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskschedulerv3.data.db.AppDatabase
import com.example.taskschedulerv3.data.model.Task
import com.example.taskschedulerv3.data.repository.TaskRepository
import com.example.taskschedulerv3.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TaskRepository(AppDatabase.getInstance(app).taskDao())

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _currentYear = MutableStateFlow(LocalDate.now().year)
    val currentYear: StateFlow<Int> = _currentYear.asStateFlow()

    private val _currentMonth = MutableStateFlow(LocalDate.now().monthValue)
    val currentMonth: StateFlow<Int> = _currentMonth.asStateFlow()

    val tasksForSelectedDate: StateFlow<List<Task>> = _selectedDate
        .flatMapLatest { date -> repo.getByDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = repo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String) { _selectedDate.value = date }

    fun previousMonth() {
        val date = LocalDate.of(_currentYear.value, _currentMonth.value, 1).minusMonths(1)
        _currentYear.value = date.year
        _currentMonth.value = date.monthValue
    }

    fun nextMonth() {
        val date = LocalDate.of(_currentYear.value, _currentMonth.value, 1).plusMonths(1)
        _currentYear.value = date.year
        _currentMonth.value = date.monthValue
    }

    fun deleteTask(task: Task) = viewModelScope.launch { repo.softDelete(task.id) }
    fun toggleComplete(task: Task) = viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
}
