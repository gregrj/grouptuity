package com.grouptuity.grouptuity.data

import android.os.Parcel
import android.os.Parcelable
import android.util.Log
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


class PaymentPreferences(private val preferenceMap: Map<Long, Pair<PaymentMethod, Long?>>) {
    constructor(): this(emptyMap())

    fun forRecipient(id: Long): Pair<PaymentMethod, Long?>? = preferenceMap[id]

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("payeeIds", JSONArray(preferenceMap.keys))
        obj.put("methods", JSONArray(preferenceMap.values.map { it.first.name }))
        obj.put("surrogates", JSONArray(preferenceMap.values.map { it.second }))

        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): PaymentPreferences {
            val obj = JSONObject(json)
            val payeeIds = obj.getJSONArray("payeeIds")
            val methods = obj.getJSONArray("methods")
            val surrogates = obj.getJSONArray("surrogates")

            if (payeeIds.length() == 0) {
                return PaymentPreferences(emptyMap())
            }

            val newMap = mutableMapOf<Long, Pair<PaymentMethod, Long?>>()
            for (i in 0..payeeIds.length()) {
                newMap[payeeIds[i] as Long] = Pair(
                    PaymentMethod.valueOf(methods[i] as String),
                    surrogates[i] as Long,
                )
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
                val isTaxTipped: Boolean,
                val discountsReduceTip: Boolean)


@Entity(tableName = "diner_table",
    foreignKeys = [ ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Contact::class, parentColumns = ["lookupKey"], childColumns = ["contact_lookupKey"], onDelete = ForeignKey.NO_ACTION)],
    indices = [Index("billId"), Index("contact_lookupKey")]) //TODO is Index on contact_lookupkey useful?
data class Diner(@PrimaryKey(autoGenerate = true) val id: Long,
                 val billId: Long,
                 @Embedded(prefix = "contact_") val contact: Contact,
                 var paymentPreferences: PaymentPreferences = PaymentPreferences(emptyMap()),
                 @Ignore val items: List<Long> = emptyList(),
                 @Ignore val debtsOwed: List<Long> = emptyList(),
                 @Ignore val debtsHeld: List<Long> = emptyList(),
                 @Ignore val discountsReceived: List<Long> = emptyList(),
                 @Ignore val discountsPurchased: List<Long> = emptyList(),
                 @Ignore val paymentsSent: List<Long> = emptyList(),
                 @Ignore val paymentsReceived: List<Long> = emptyList()): Parcelable {

    @Ignore
    val lookupKey = contact.lookupKey

    @Ignore
    val name = contact.name

    @Ignore
    val photoUri = contact.photoUri

    fun getInitials() = nameToInitials(name)

    fun withLists(newItems: List<Long>?=null, newDebtsOwed: List<Long>?=null,
                  newDebtsHeld: List<Long>?=null, newDiscountsReceived: List<Long>?=null,
                  newDiscountsPurchased: List<Long>?=null, newPaymentsSent: List<Long>?=null,
                  newPaymentsReceived: List<Long>?=null) =
        Diner(id, billId, contact, paymentPreferences,
            newItems ?: items,
            newDebtsOwed ?: debtsOwed,
            newDebtsHeld ?: debtsHeld,
            newDiscountsReceived ?: discountsReceived,
            newDiscountsPurchased ?: discountsPurchased,
            newPaymentsSent ?: paymentsSent,
            newPaymentsReceived ?: paymentsReceived)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeLong(id)
            this.writeLong(billId)
            this.writeParcelable(contact, 0)
            this.writeString(paymentPreferences.toJson())
            this.writeLongArray(items.toLongArray())
            this.writeLongArray(debtsOwed.toLongArray())
            this.writeLongArray(debtsHeld.toLongArray())
            this.writeLongArray(discountsReceived.toLongArray())
            this.writeLongArray(discountsPurchased.toLongArray())
            this.writeLongArray(paymentsSent.toLongArray())
            this.writeLongArray(paymentsReceived.toLongArray())
        }
    }

    companion object CREATOR : Parcelable.Creator<Diner> {
        override fun createFromParcel(parcel: Parcel) = Diner(
            parcel.readLong(),
            parcel.readLong(),
            parcel.readParcelable(Contact::class.java.classLoader) ?: Contact.dummy,
            PaymentPreferences.fromJson(parcel.readString() ?: ""),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList()
        )

        override fun newArray(size: Int): Array<Diner?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "item_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
data class Item(@PrimaryKey(autoGenerate = true) val id: Long,
                val billId: Long,
                val price: Double,
                val name: String,
                @Ignore val diners: List<Long>,
                @Ignore val discounts: List<Long>): Parcelable {

    constructor(id: Long, billId: Long, price: Double, name: String): this(id, billId, price, name, emptyList(), emptyList())

    fun withLists(newDiners: List<Long>?=null, newDiscounts: List<Long>?=null) =
        Item(id, billId, price, name, newDiners ?: diners, newDiscounts ?: discounts)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeLong(id)
            this.writeLong(billId)
            this.writeDouble(price)
            this.writeString(name)
            this.writeLongArray(diners.toLongArray())
            this.writeLongArray(discounts.toLongArray())
        }
    }

    companion object CREATOR : Parcelable.Creator<Item> {
        override fun createFromParcel(parcel: Parcel) = Item(
            parcel.readLong(),
            parcel.readLong(),
            parcel.readDouble(),
            parcel.readString() ?: "Item",
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList())


        override fun newArray(size: Int): Array<Item?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "debt_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
data class Debt(@PrimaryKey(autoGenerate = true) val id: Long,
                val billId: Long,
                val amount: Double,
                @Ignore val debtors: List<Long>,
                @Ignore val creditors: List<Long>): Parcelable {

    constructor(id: Long, billId: Long, amount: Double): this(id, billId, amount, emptyList(), emptyList())

    fun withLists(newDebtors: List<Long>?=null, newCreditors: List<Long>?=null) =
        Debt(id, billId, amount, newDebtors ?: debtors, newCreditors ?: creditors)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeLong(id)
            this.writeLong(billId)
            this.writeDouble(amount)
            this.writeLongArray(debtors.toLongArray())
            this.writeLongArray(creditors.toLongArray())
        }
    }

    companion object CREATOR : Parcelable.Creator<Debt> {
        override fun createFromParcel(parcel: Parcel) = Debt(
            parcel.readLong(),
            parcel.readLong(),
            parcel.readDouble(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList())

        override fun newArray(size: Int): Array<Debt?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "discount_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
data class Discount(@PrimaryKey(autoGenerate = true) val id: Long,
                    val billId: Long,
                    val asPercent: Boolean,
                    val onItems: Boolean,
                    val value: Double,
                    val cost: Double?,
                    @Ignore val items: List<Long> = emptyList(),
                    @Ignore val recipients: List<Long> = emptyList(),
                    @Ignore val purchasers: List<Long> = emptyList()): Parcelable {

    constructor(id: Long, billId: Long, asPercent: Boolean, onItems: Boolean, value: Double, cost: Double?):
            this(id, billId, asPercent, onItems, value, cost, emptyList(), emptyList(), emptyList())

    fun withLists(newItems: List<Long>?=null, newRecipients: List<Long>?=null, newPurchasers: List<Long>?=null) =
        Discount(id, billId, asPercent, onItems, value, cost, newItems ?: items, newRecipients ?: recipients, newPurchasers ?: purchasers)

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeLong(id)
            this.writeLong(billId)
            this.writeInt(if (asPercent) 1 else  0)
            this.writeInt(if (onItems) 1 else  0)
            this.writeDouble(value)
            this.writeDouble(cost ?: -1.0)
            this.writeLongArray(items.toLongArray())
            this.writeLongArray(recipients.toLongArray())
            this.writeLongArray(purchasers.toLongArray())
        }
    }

    companion object CREATOR : Parcelable.Creator<Discount> {
        override fun createFromParcel(parcel: Parcel) = Discount(
            parcel.readLong(),
            parcel.readLong(),
            parcel.readInt() == 1,
            parcel.readInt() == 1,
            parcel.readDouble(),
            parcel.readDouble().let { if(it < 0.0) null else it },
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList(),
            parcel.createLongArray()?.toList() ?: emptyList())

        override fun newArray(size: Int): Array<Discount?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "payment_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["payerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["payeeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId"), Index("payerId"), Index("payeeId")])
data class Payment(@PrimaryKey(autoGenerate = true) val id: Long,
                   val billId: Long,
                   val amount: Double,
                   val method: String,
                   val committed: Boolean,
                   val payerId: Long,
                   val payeeId: Long)


@Entity(tableName = "diner_item_join_table",
    primaryKeys = ["dinerId", "itemId"],
    foreignKeys = [ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class,  parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class DinerItemJoin(val dinerId: Long, val itemId: Long)


@Entity(tableName = "debt_debtor_join_table",
    primaryKeys = ["debtId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Debt::class,  parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DebtDebtorJoin(val debtId: Long, val dinerId: Long)


@Entity(tableName = "debt_creditor_join_table",
    primaryKeys = ["debtId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Debt::class,  parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DebtCreditorJoin(val debtId: Long, val dinerId: Long)


@Entity(tableName = "discount_recipient_join_table",
    primaryKeys = ["discountId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DiscountRecipientJoin(val discountId: Long, val dinerId: Long)


@Entity(tableName = "discount_purchaser_join_table",
    primaryKeys = ["discountId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DiscountPurchaserJoin(val discountId: Long, val dinerId: Long)


@Entity(tableName = "discount_item_join_table",
    primaryKeys = ["discountId", "itemId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class DiscountItemJoin(val discountId: Long, val itemId: Long)


@Entity(tableName = "payment_payer_join_table",
    primaryKeys = ["paymentId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Payment::class,  parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class PaymentPayerJoin(val paymentId: Long, val dinerId: Long)


@Entity(tableName = "payment_payee_join_table",
    primaryKeys = ["paymentId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Payment::class,  parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class PaymentPayeeJoin(val paymentId: Long, val dinerId: Long)
