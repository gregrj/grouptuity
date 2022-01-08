package com.grouptuity.grouptuity.data

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.grouptuity.grouptuity.BuildConfig
import com.grouptuity.grouptuity.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.math.RoundingMode
import java.util.*


val Context.preferenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "grouptuity_preferences")


sealed class StoredPreference<T> {
    companion object {
        val keyStoredPreferenceMap = mutableMapOf<String, StoredPreference<*>>()
        lateinit var preferenceDataStore: DataStore<Preferences>

        lateinit var appVersion: NonNullable<String>
        lateinit var loadedBillId: Nullable<String>
        lateinit var userName: NonNullable<String>
        lateinit var userPhotoUri: Nullable<String>
        lateinit var userEmail: Nullable<String>
        lateinit var userVenmoAddress: Nullable<String>
        lateinit var userCashtag: Nullable<String>
        lateinit var userAlgorandAddress: Nullable<String>
        lateinit var defaultCurrencyCode: NonNullable<String>
        lateinit var defaultTaxPercent: NonNullable<String>
        lateinit var defaultTipPercent: NonNullable<String>
        lateinit var taxIsTipped: NonNullable<Boolean>
        lateinit var discountsReduceTip: NonNullable<Boolean>
        lateinit var itemRoundingMode: NonNullable<String>
        lateinit var discountRoundingMode: NonNullable<String>
        lateinit var taxRoundingMode: NonNullable<String>
        lateinit var tipRoundingMode: NonNullable<String>
        lateinit var autoAddUser: NonNullable<Boolean>
        lateinit var searchWithTypoAssist: NonNullable<Boolean>

        fun initialize(app: Application) {
            preferenceDataStore = app.preferenceDataStore

            appVersion = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_app_version)),
                BuildConfig.VERSION_NAME)
            loadedBillId = Nullable(
                stringPreferencesKey(app.getString(R.string.preference_key_loaded_bill_id))
            )
            userName = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_user_name)),
                app.getString(R.string.default_user_name))
            userPhotoUri = Nullable(
                stringPreferencesKey(app.getString(R.string.preference_key_user_photo_uri))
            )
            userEmail = Nullable(
                stringPreferencesKey(app.getString(R.string.preference_key_user_email))
            )
            userVenmoAddress = Nullable(
                stringPreferencesKey(app.getString(R.string.preference_key_user_venmo))
            )
            userCashtag = Nullable(
                stringPreferencesKey(app.getString(R.string.preference_key_user_cashtag))
            )
            userAlgorandAddress = Nullable(
                stringPreferencesKey(app.getString(R.string.preference_key_user_algorand))
            )
            defaultCurrencyCode = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_default_currency_code)),
                Currency.getInstance(Locale.getDefault()).currencyCode)
            defaultTaxPercent = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_default_tax_percent)),
                app.getString(R.string.default_default_tax_percent))
            defaultTipPercent = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_default_tip_percent)),
                app.getString(R.string.default_default_tip_percent))
            taxIsTipped = NonNullable(
                booleanPreferencesKey(app.getString(R.string.preference_key_tax_is_tipped)),
                app.resources.getBoolean(R.bool.default_tax_is_tipped))
            discountsReduceTip = NonNullable(
                booleanPreferencesKey(app.getString(R.string.preference_key_discounts_reduce_tip)),
                app.resources.getBoolean(R.bool.default_discounts_reduce_tip))
            itemRoundingMode = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_item_rounding_mode)),
                RoundingMode.HALF_UP.name)
            discountRoundingMode = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_discount_rounding_mode)),
                RoundingMode.HALF_UP.name)
            taxRoundingMode = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_tax_rounding_mode)),
                RoundingMode.HALF_UP.name)
            tipRoundingMode = NonNullable(
                stringPreferencesKey(app.getString(R.string.preference_key_tip_rounding_mode)),
                RoundingMode.HALF_UP.name)
            autoAddUser = NonNullable(
                booleanPreferencesKey(app.getString(R.string.preference_key_auto_add_user)),
                app.resources.getBoolean(R.bool.default_auto_add_user))
            searchWithTypoAssist = NonNullable(
                booleanPreferencesKey(app.getString(R.string.preference_key_contact_search_typo_assist)),
                app.resources.getBoolean(R.bool.default_contact_search_typo_assist))
        }
    }

    var isSet: Boolean? = null
        protected set

    abstract var value: T

    class NonNullable<T>(private val key: Preferences.Key<T>, private val defaultValue: T): StoredPreference<T>() {
        val stateFlow = preferenceDataStore.data.map { preferences ->
            preferences[key].also { isSet = true } ?: defaultValue.also { isSet = false }
        }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, defaultValue)

        init {
            keyStoredPreferenceMap[key.name] = this
        }

        override var value: T
            get() = runBlocking { preferenceDataStore.data.map { it[key] ?: defaultValue }.first() }
            set(newValue) {
                CoroutineScope(Dispatchers.IO).launch {
                    preferenceDataStore.edit { preferences -> preferences[key] = newValue }
                }
            }
    }

    class Nullable<T>(private val key: Preferences.Key<T>, private val defaultValue: T? = null): StoredPreference<T?>() {
        val stateFlow = preferenceDataStore.data.map { preferences ->
            preferences[key].also { isSet = true } ?: defaultValue.also { isSet = false }
        }.stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, defaultValue)

        init {
            keyStoredPreferenceMap[key.name] = this
        }

        override var value: T?
            get() = runBlocking { preferenceDataStore.data.map { it[key] ?: defaultValue }.first() }
            set(newValue) {
                CoroutineScope(Dispatchers.IO).launch {
                    preferenceDataStore.edit { preferences ->
                        if (newValue == null) {
                            preferences.remove(key)
                        } else {
                            preferences[key] = newValue
                        }
                    }
                }
            }
    }
}