package com.grouptuity.grouptuity.data.entities

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.grouptuity.grouptuity.data.Converters
import java.util.*


private fun nameToInitials(name: String?): String {
    if(name.isNullOrBlank())
        return "!"

    val words = name.trim().split("\\s+".toRegex()).map {
            word -> word.replace("""^[,.]|[,.]$""".toRegex(), "")
    }

    return when(words.size) {
        0 -> { "" }
        1 -> { words[0].first().uppercaseChar().toString() }
        else -> {
            words[0].first().uppercaseChar().toString() +
                    words[1].uppercase(Locale.getDefault()).first()
        }
    }
}


/**
 * Contacts TODO
 */
@Entity(tableName = "contact_table", indices = [Index(value = ["lookupKey"], unique = true)])
class Contact(
    @PrimaryKey val lookupKey: String,
    var name: String,
    var visibility: Int,
    val addresses: MutableMap<PaymentMethod, String> = mutableMapOf()
): Parcelable {

    @Ignore var photoUri: String? = null

    constructor(name: String, defaults: Map<PaymentMethod, String>): this(
        GROUPTUITY_LOOKUPKEY_PREFIX + UUID.randomUUID(),
        name,
        VISIBLE,
        defaults.toMutableMap())

    // Function used to get a user Contact object with updated name bypassing the preference store
    fun withName(newName: String) = Contact(lookupKey, newName, visibility, addresses).also {
        it.photoUri = photoUri
    }

    fun getInitials() = nameToInitials(name)

    fun setDefaultAddressForMethod(method: PaymentMethod, alias: String?) {
        if (alias == null) {
            addresses.remove(method)
        } else {
            addresses[method] = alias
        }
    }

    companion object {
        const val VISIBLE = 0
        const val FAVORITE = 1
        const val HIDDEN = 2
        const val GROUPTUITY_LOOKUPKEY_PREFIX = "grouptuity_lookupkey_"
        const val GROUPTUITY_CASH_POOL_LOOKUPKEY = "grouptuity_cash_pool_contact_lookupKey"
        const val GROUPTUITY_RESTAURANT_LOOKUPKEY = "grouptuity_restaurant_contact_lookupKey"
        const val GROUPTUITY_USER_CONTACT_LOOKUPKEY = "grouptuity_user_contact_lookupKey"

        val cashPool = Contact(GROUPTUITY_CASH_POOL_LOOKUPKEY, "cash pool", VISIBLE)
        val restaurant = Contact(GROUPTUITY_RESTAURANT_LOOKUPKEY, "restaurant",VISIBLE)
        var user = Contact(GROUPTUITY_USER_CONTACT_LOOKUPKEY, "You", VISIBLE)

        @JvmField val CREATOR = object: Parcelable.Creator<Contact> {
            override fun createFromParcel(parcel: Parcel) = Contact(
                parcel.readString()?: "contact_lookupKey",
                parcel.readString()?: "contact_name",
                parcel.readInt(),
                Gson().fromJson(parcel.readString(), Converters.aliasMapType)).also {
                it.photoUri = parcel.readString()
            }

            override fun newArray(size: Int): Array<Contact?> = arrayOfNulls(size)
        }
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeString(lookupKey)
            this.writeString(name)
            this.writeInt(visibility)
            this.writeString(Gson().toJson(addresses))
            this.writeString(photoUri)
        }
    }
}