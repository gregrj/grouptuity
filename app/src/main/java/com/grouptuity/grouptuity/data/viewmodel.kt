package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import com.grouptuity.grouptuity.GrouptuityApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


fun <T> Flow<T>.asLiveData(lock: Flow<Boolean>): LiveData<T> {
    return combineTransform(lock) { data, locked ->
        if(!locked)
            emit(data)
    }.asLiveData()
}


class Event<out T>(private val value: T) {
    data class EventValue<out T>(val value: T)

    @Volatile
    private var unconsumed = true

    fun consume(): EventValue<T>? {
        if(unconsumed) {
            unconsumed = false
            return EventValue(value)
        }
        return null
    }
}


abstract class UIViewModel<I, O>(app: GrouptuityApplication): AndroidViewModel(app) {
    protected val repository = Repository.getInstance(app)

    protected val context: Context
        get() = getApplication<Application>().applicationContext

    protected val resources: Resources
        get() = getApplication<Application>().resources

    // TODO Convert to mediators
    private var inputLockCoroutineScope: CoroutineScope? = null
    private val inputLocks = mutableListOf<Flow<Boolean>>()
    private val _isInputLocked = MutableStateFlow(false)
    private val transitionInputLock = createInputLock(false)

    // TODO Convert to mediators
    private var outputLockCoroutineScope: CoroutineScope? = null
    private val outputLocks = mutableListOf<Flow<Boolean>>()
    private val _isOutputLocked = MutableStateFlow(false)
    private val transitionOutputLock = createOutputLock(false)

    private val _finishFragmentEvent = MutableLiveData<Event<O>>()

    val isInputLocked: StateFlow<Boolean> = _isInputLocked
    val isOutputLocked: StateFlow<Boolean> = _isOutputLocked
    val finishFragmentEvent: LiveData<Event<O>> = _finishFragmentEvent

    fun addInputLock(lock: Flow<Boolean>) {
        inputLocks.add(lock)

        inputLockCoroutineScope?.apply { cancel() }
        inputLockCoroutineScope = CoroutineScope(Dispatchers.Unconfined).also { scope ->
            scope.launch {
                combine(inputLocks) { locks ->
                    locks.any { it }
                }.collect {
                    _isInputLocked.value = it
                }
            }
        }
    }
    fun createInputLock(initialState: Boolean = false) =
        MutableStateFlow(initialState).also { addInputLock(it) }
    fun unlockInput() { inputLocks.forEach { if(it is MutableStateFlow) it.value = false } }

    fun addOutputLock(lock: Flow<Boolean>) {
        outputLocks.add(lock)

        outputLockCoroutineScope?.apply { cancel() }
        outputLockCoroutineScope = CoroutineScope(Dispatchers.Unconfined).also { scope ->
            scope.launch {
                combine(outputLocks) { locks ->
                    locks.any { it }
                }.collect {
                    _isOutputLocked.value = it
                }
            }
        }
    }
    fun createOutputLock(initialState: Boolean = false) =
        MutableStateFlow(initialState).also { addOutputLock(it) }
    fun unlockOutput() { outputLocks.forEach { if(it is MutableStateFlow) it.value = false } }

    open fun notifyTransitionStarted() {
        // Disable user input and UI updates during transition
        transitionInputLock.value = true
        transitionOutputLock.value = true
    }
    open fun notifyTransitionFinished() {
        // Enable user input and UI updates after transition finishes
        transitionInputLock.value = false
        transitionOutputLock.value = false
    }

    fun isPermissionGranted(permissionString: String) = getApplication<Application>().checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED

    fun finishFragment(output: O) {
        notifyTransitionStarted()

        // Fire event to navigate from or close fragment
        _finishFragmentEvent.value = Event(output)
    }

    fun initialize(input: I) {
        unlockOutput()
        _finishFragmentEvent.value?.consume()
        onInitialize(input)
    }

    abstract fun onInitialize(input: I)

    abstract fun handleOnBackPressed()
}


abstract class BaseUIViewModel(app: GrouptuityApplication): UIViewModel<Unit?, Unit?>(app) {
    override fun onInitialize(input: Unit?) { }

    override fun handleOnBackPressed() { }
}
