package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*


fun <T> Flow<T>.withOutputSwitch(switch: Flow<Boolean>): Flow<T> { return combineTransform(switch) { data, enabled -> if(enabled) emit(data) } }


fun <T> Flow<T>.asLiveData(switch: Flow<Boolean>): LiveData<T> { return combineTransform(switch) { data, enabled -> if(enabled) emit(data) }.asLiveData() }


abstract class UIViewModel(app: Application): AndroidViewModel(app) {
    protected val repository = Repository.getInstance(app)

    protected val context: Context
        get() = getApplication<Application>().applicationContext

    private val transitionInputLocked = MutableStateFlow(false)
    private val inputLocks = mutableListOf<Flow<Boolean>>(transitionInputLocked)
    var isInputLocked: StateFlow<Boolean> = transitionInputLocked
        private set

    private val _isOutputFlowing = MutableStateFlow(true)
    protected val isOutputFlowing: Flow<Boolean> = _isOutputFlowing

    protected fun addInputLock(lock: Flow<Boolean>) {
        inputLocks.add(lock)
        isInputLocked = combine(inputLocks) { locks ->
            locks.any { it }
        }.stateIn(CoroutineScope(Dispatchers.Default), SharingStarted.Eagerly, true)
    }
    fun unLockInput() { inputLocks.forEach { if(it is MutableStateFlow) it.value = false } }

    fun freezeOutput() { _isOutputFlowing.value = false }
    fun unFreezeOutput() { _isOutputFlowing.value = true }

    open fun notifyTransitionStarted() { transitionInputLocked.value = true }
    open fun notifyTransitionFinished() { transitionInputLocked.value = false }

    fun isPermissionGranted(permissionString: String) = getApplication<Application>().checkSelfPermission(permissionString) == PackageManager.PERMISSION_GRANTED
}