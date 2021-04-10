package com.grouptuity.grouptuity.data

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*


class Converters {
    @TypeConverter
    fun jsonToPreferences(json: String?): PaymentPreferences = json?.let { PaymentPreferences.fromJson(it) } ?: PaymentPreferences()

    @TypeConverter
    fun preferencesToJson(preferences: PaymentPreferences?): String = preferences?.toJson() ?: ""
}


private fun nameToInitials(name: String?): String {
    if(name.isNullOrBlank())
        return "!"

    val words = name.trim().split("\\s+".toRegex()).map { word -> word.replace("""^[,.]|[,.]$""".toRegex(), "")}

    return when(words.size) {
        0 -> { "" }
        1 -> { words[0].first().toUpperCase().toString() }
        else -> { words[0].first().toUpperCase().toString() + words[1].toUpperCase(Locale.getDefault()).first() }
    }
}


//TODO add more payment options as needed
enum class PaymentMethod(val acceptedByRestaurant: Boolean, val peerToPeer: Boolean) {
    CASH(true, true),
    CREDIT_CARD_SPLIT(true, false),
    CREDIT_CARD_INDIVIDUAL(true, false),
    IOU_EMAIL(false, true),
    VENMO(false, true)
}


class PaymentPreferences(private val preferenceMap: Map<Long, PaymentMethod>) {
    constructor(): this(emptyMap())

    fun forRecipient(id: Long): PaymentMethod? = preferenceMap[id]

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("payeeIds", JSONArray(preferenceMap.keys))
        obj.put("methods", JSONArray(preferenceMap.values.map { it.name }))

        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): PaymentPreferences {
            val obj = JSONObject(json)
            val payeeIds = obj.getJSONArray("payeeIds")
            val methods = obj.getJSONArray("methods")

            val newMap = mutableMapOf<Long, PaymentMethod>()
            for (i in 0..payeeIds.length()) {
                newMap[payeeIds[i] as Long] = PaymentMethod.valueOf(methods[i] as String)
            }

            return PaymentPreferences(newMap)
        }
    }
}


/**
 * Contacts TODO
 */
@Entity(tableName = "contact_table", indices = [Index(value = ["lookupKey"], unique = true)])
data class Contact(@PrimaryKey val lookupKey: String,
                   val name: String,
                   val photoUri: String?,
                   val visibility: Int): Parcelable {

    fun getInitials() = nameToInitials(name)

    fun withVisibility(newVisibility: Int) = Contact(lookupKey, name, photoUri, newVisibility)

    companion object {
        const val VISIBLE = 0
        const val FAVORITE = 1
        const val HIDDEN = 2

        private var selfName: String = "You"
        private var selfPhotoUri: String? = null

        fun updateSelfContactData(name: String, photoUri: String?) {
            selfName = name
            selfPhotoUri = photoUri
        }

        val dummy = Contact("grouptuity_dummy_contact_lookupKey", "", null, VISIBLE)
        val restaurant = Contact("grouptuity_restaurant_contact_lookupKey", "restaurant", null, VISIBLE)
        val self: Contact get() = Contact("grouptuity_self_contact_lookupKey", selfName, selfPhotoUri, VISIBLE)

        @JvmField val CREATOR = object: Parcelable.Creator<Contact> {
            override fun createFromParcel(parcel: Parcel) = Contact(
                parcel.readString()?: "contact_lookupKey",
                parcel.readString()?: "contact",
                parcel.readString(),
                parcel.readInt())

            override fun newArray(size: Int): Array<Contact?> = arrayOfNulls(size)
        }
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeString(lookupKey)
            this.writeString(name)
            this.writeString(photoUri)
            this.writeInt(visibility)
        }
    }
}


/**
 * A Bill object represents the calculation of payments associated with a meal at a restaurant.
 *
 * Bill objects contain metadata relevant to the payment calculation, such as a timestamp for the
 * meal and rules for handling tax and tip. The {@code id} field is referenced in the other
 * datastore entities so that SQL queries can retrieve the objects associated with this Bill.
 */
@Entity(tableName = "bill_table")
data class Bill(@PrimaryKey(autoGenerate = true) val id: Long,
                val title: String,
                val timeCreated: Long,
                val tax: Double,
                val taxAsPercent: Boolean,
                val tip: Double,
                val tipAsPercent: Boolean,
                val isTaxTipped: Boolean?,
                val discountsReduceTip: Boolean?)