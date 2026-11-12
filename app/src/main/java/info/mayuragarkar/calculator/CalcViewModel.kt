package info.mayuragarkar.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.mayuragarkar.calculator.engine.CalcEngine
import info.mayuragarkar.calculator.engine.CalcError
import info.mayuragarkar.calculator.engine.Op
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ResultState {
    data object Empty : ResultState
    data class Running(val op: Op) : ResultState
    data class Failed(val op: Op, val message: String) : ResultState
    data class Done(val op: Op, val text: String, val value: String?, val digits: Int?) : ResultState
}

class CalcViewModel : ViewModel() {
    var x by mutableStateOf("")
    var y by mutableStateOf("")
    var z by mutableStateOf("")
    var result by mutableStateOf<ResultState>(ResultState.Empty)
        private set

    private var job: Job? = null

    fun run(op: Op) {
        job?.cancel()
        val (a, b, c) = Triple(x, y, z)
        result = ResultState.Running(op)
        job = viewModelScope.launch {
            result = try {
                withContext(Dispatchers.Default) {
                    val text = CalcEngine.evaluate(op, a, b, c)
                    val first = text.substringBefore('\n')
                    val numeric = first.isNotEmpty() && first.all { it.isDigit() || it == '-' || it == '.' }
                    ResultState.Done(op, text, first.takeIf { numeric }, if (numeric) first.count(Char::isDigit) else null)
                }
            } catch (e: CalcError) {
                ResultState.Failed(op, e.message!!)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        CalcEngine.cancel()
        result = ResultState.Empty
    }

    fun useAsX(value: String) {
        x = value
    }

    fun clear() {
        cancel()
        x = ""
        y = ""
        z = ""
    }
}
