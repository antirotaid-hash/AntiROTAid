package com.mindlift.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindlift.bridge.Course
import com.mindlift.bridge.DateInputPreview
import com.mindlift.data.EngineRepository
import com.mindlift.data.EngineResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Top-level app state: engine health, the in-session course list, and the
 * currently selected course. Courses are kept in memory for the session (the
 * engine persists them); a richer "list courses" call can be added later.
 */
class MainViewModel(private val repo: EngineRepository) : ViewModel() {

    data class State(
        val engineHealthy: Boolean? = null,
        val courses: List<Course> = emptyList(),
        val selected: Course? = null,
        val busy: Boolean = false,
        val message: String? = null,
        /** Live engine validation of the typed exam date (null when field empty). */
        val datePreview: DateInputPreview? = null,
        /** Cross-course "today" agenda headline (v10). */
        val agendaHeadline: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var dateJob: Job? = null

    /**
     * Asks the engine to normalize + validate a human-typed exam date. Debounced
     * so we don't hit the Python bridge on every keystroke. Clears the preview
     * when the field is empty (an unset exam date is allowed).
     */
    fun previewExamDate(raw: String, locale: String = "ro") {
        dateJob?.cancel()
        if (raw.isBlank()) {
            _state.update { it.copy(datePreview = null) }
            return
        }
        dateJob = viewModelScope.launch {
            delay(250)
            when (val r = repo.dateInputPreview(raw, locale)) {
                is EngineResult.Ok -> _state.update { it.copy(datePreview = r.value) }
                is EngineResult.Err -> _state.update { it.copy(datePreview = null) }
            }
        }
    }

    init {
        viewModelScope.launch {
            _state.update { it.copy(engineHealthy = repo.doctorOk()) }
            when (val r = repo.listCourses()) {
                is EngineResult.Ok -> _state.update { it.copy(courses = r.value) }
                is EngineResult.Err -> Unit
            }
            when (val a = repo.todayAgenda()) {
                is EngineResult.Ok -> _state.update {
                    it.copy(agendaHeadline = a.value.optString("headline").ifBlank { null })
                }
                is EngineResult.Err -> Unit
            }
        }
    }

    fun select(course: Course) = _state.update { it.copy(selected = course) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun createCourse(title: String, examDate: String?, onCreated: (Course) -> Unit) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            when (val r = repo.createCourse(title.trim(), examDate)) {
                is EngineResult.Ok -> {
                    _state.update {
                        it.copy(busy = false, courses = it.courses + r.value, selected = r.value)
                    }
                    onCreated(r.value)
                }
                is EngineResult.Err ->
                    _state.update { it.copy(busy = false, message = r.message) }
            }
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            repo.deleteCourse(courseId)
            _state.update { it.copy(courses = it.courses.filter { c -> c.id != courseId }) }
        }
    }

    companion object {
        fun factory(repo: EngineRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(repo) as T
        }
    }
}
