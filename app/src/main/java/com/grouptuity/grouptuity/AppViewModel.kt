package com.grouptuity.grouptuity

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData


class Event<out T>(private val content: T) {
    //TODO does this need to be thread safe?

    private var unconsumed = true

    fun consume(): T? {
        if(unconsumed) {
            unconsumed = false
            return content
        }
        return null
    }
}

/**
 *
 */
class AppViewModel(app: Application): AndroidViewModel(app) {
    private val _darkThemeActive = MutableLiveData<Boolean>()
    val darkThemeActive: LiveData<Boolean> = _darkThemeActive

    private val _permissionGetAccounts = MutableLiveData<Event<Boolean>>()
    val permissionGetAccounts: LiveData<Event<Boolean>> = _permissionGetAccounts

    private val _permissionReadContacts = MutableLiveData<Event<Boolean>>()
    val permissionReadContacts: LiveData<Event<Boolean>> = _permissionReadContacts

    private val _voiceInputMutable = MutableLiveData<Event<String>>()
    val voiceInput: LiveData<Event<String>> = _voiceInputMutable

    init {
        _darkThemeActive.value = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    fun switchToDarkTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        _darkThemeActive.value = true
    }
    fun switchToLightTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        _darkThemeActive.value = false
    }
}