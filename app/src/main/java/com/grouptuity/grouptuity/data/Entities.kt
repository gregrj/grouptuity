package com.grouptuity.grouptuity.data

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import com.grouptuity.grouptuity.R
import java.lang.reflect.Type


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


class Converters {
    companion object {
        val aliasMapType: Type = object : TypeToken<MutableMap<PaymentMethod, String>>() {}.type
        val paymentTemplateMapType: Type = object : TypeToken<MutableMap<String, PaymentTemplate>>() {}.type
    }

    @TypeConverter
    fun aliasMapToJSON(aliasMap: MutableMap<PaymentMethod, String>) = Gson().toJson(aliasMap)

    @TypeConverter
    fun jsonToAddressMap(json: String): MutableMap<PaymentMethod, String> = Gson().fromJson(json, aliasMapType)

    @TypeConverter
    fun paymentTemplateMapToJSON(template: MutableMap<String, PaymentTemplate>) = Gson().toJson(template)

    @TypeConverter
    fun jsonToPaymentTemplate(json: String): MutableMap<String, PaymentTemplate> = Gson().fromJson(json, paymentTemplateMapType)
}


enum class PaymentMethod(val acceptedByRestaurant: Boolean,
                         val processedWithinApp: Boolean,
                         val addressCodeScannable: Boolean,
                         val addressCanBeEmail: Boolean,
                         val addressCanBePhone: Boolean,
                         val displayNameStringId: Int,
                         val addressNameStringId: Int,
                         val paymentInstructionStringId: Int,
                         val addressSelectionStringId: Int,
                         val paymentIconId: Int,
                         val isIconColorless: Boolean) {
    CASH(
        true,
        false,
        false,
        false,
        false,
        R.string.payment_method_display_name_cash,
        R.string.payment_method_address_name_cash_address,
        R.string.payments_instruction_cash,
        R.string.placeholder_text,
        R.drawable.ic_payment_cash,
        true),
    CREDIT_CARD_SPLIT(
        true,
        false,
        false,
        false,
        false,
        R.string.payment_method_display_name_credit_card_split,
        R.string.payment_method_address_name_credit_card_split,
        R.string.payments_instruction_credit_card_split,
        R.string.placeholder_text,
        R.drawable.ic_payment_credit_card_split,
        true),
    CREDIT_CARD_INDIVIDUAL(
        true,
        false,
        false,
        false,
        false,
        R.string.payment_method_display_name_credit_card_individual,
        R.string.payment_method_address_name_credit_card_individual,
        R.string.payments_instruction_credit_card_individual,
        R.string.placeholder_text,
        R.drawable.ic_payment_credit_card,
        true),
    PAYBACK_LATER(
        false,
        true,
        false,
        true,
        true,
        R.string.payment_method_display_name_payback_later,
        R.string.payment_method_address_name_payback_later,
        R.string.payments_instruction_payback_later,
        R.string.payments_address_entry_iou_email,
        R.drawable.ic_payment_payback_later,
        true),
    VENMO(
        false,
        true,
        true,
        true,
        true,
        R.string.payment_method_display_name_venmo,
        R.string.payment_method_address_name_venmo,
        R.string.payments_instruction_venmo,
        R.string.payments_address_entry_venmo,
        R.drawable.ic_payment_venmo,
        false),
    CASH_APP(
        false,
        true,
        true,
        true,
        true,
        R.string.payment_method_display_name_cash_app,
        R.string.payment_method_address_name_cash_app,
        R.string.payments_instruction_cash_app,
        R.string.payments_address_entry_cash_app,
        R.drawable.ic_payment_cash_app,
        false),
    ALGO(
        false,
        true,
        true,
        false,
        false,
        R.string.payment_method_display_name_algorand,
        R.string.payment_method_address_name_algorand,
        R.string.payments_instruction_algorand,
        R.string.payments_address_entry_algorand,
        R.drawable.ic_payment_algorand,
        true)
}


/**
 * Contacts TODO
 */
@Entity(tableName = "contact_table", indices = [Index(value = ["lookupKey"], unique = true)])
class Contact(@PrimaryKey val lookupKey: String,
              var name: String,
              var visibility: Int,
              val paymentAddressDefaults: MutableMap<PaymentMethod, String> = mutableMapOf()): Parcelable {

    @Ignore var photoUri: String? = null

    constructor(name: String, defaults: Map<PaymentMethod, String>): this(
        GROUPTUITY_LOOKUPKEY_PREFIX + UUID.randomUUID(),
        name,
        VISIBLE,
        defaults.toMutableMap())

    fun getInitials() = nameToInitials(name)

    companion object {
        const val VISIBLE = 0
        const val FAVORITE = 1
        const val HIDDEN = 2
        const val GROUPTUITY_LOOKUPKEY_PREFIX = "grouptuity_lookupkey_"

        private var selfName: String = "You" // Value overwritten from xml during database creation
        private var selfPhotoUri: String? = null

        fun updateSelfContactData(name: String, photoUri: String?) {
            selfName = name
            selfPhotoUri = photoUri
        }

        val dummy = Contact("grouptuity_dummy_contact_lookupKey", "", VISIBLE)
        val cashPool = Contact("grouptuity_cash_pool_contact_lookupKey", "cash pool", VISIBLE)
        val restaurant = Contact("grouptuity_restaurant_contact_lookupKey", "restaurant",VISIBLE)
        val self: Contact get() = Contact("grouptuity_self_contact_lookupKey", selfName, VISIBLE).also {
            it.photoUri = selfPhotoUri
        }

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
            this.writeString(Gson().toJson(paymentAddressDefaults))
            this.writeString(photoUri)
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
class Bill(@PrimaryKey val id: String,
           val title: String,
           val timeCreated: Long,
           val tax: Double,
           val taxAsPercent: Boolean,
           val tip: Double,
           val tipAsPercent: Boolean,
           val isTaxTipped: Boolean,
           val discountsReduceTip: Boolean) {

    fun withTitle(title: String) =
        Bill(this.id, title, this.timeCreated, this.tax, this.taxAsPercent, this.tip, this.tipAsPercent, this.isTaxTipped, this.discountsReduceTip)
    fun withTaxPercent(taxPercent: Double) =
        Bill(this.id, this.title, this.timeCreated, taxPercent, true, this.tip, this.tipAsPercent, this.isTaxTipped, this.discountsReduceTip)
    fun withTaxAmount(taxAmount: Double) =
        Bill(this.id, this.title, this.timeCreated, taxAmount, false, this.tip, this.tipAsPercent, this.isTaxTipped, this.discountsReduceTip)
    fun withTipPercent(tipPercent: Double) =
        Bill(this.id, this.title, this.timeCreated, this.tax, this.taxAsPercent, tipPercent, true, this.isTaxTipped, this.discountsReduceTip)
    fun withTipAmount(tipAmount: Double) =
        Bill(this.id, this.title, this.timeCreated, this.tax, this.taxAsPercent, tipAmount, false, this.isTaxTipped, this.discountsReduceTip)
    fun withTaxTipped(taxTipped: Boolean) =
        Bill(this.id, this.title, this.timeCreated, this.tax, this.taxAsPercent, this.tip, this.tipAsPercent, taxTipped, this.discountsReduceTip)
    fun withDiscountsReduceTip(reduceTip: Boolean) =
        Bill(this.id, this.title, this.timeCreated, this.tax, this.taxAsPercent, this.tip, this.tipAsPercent, this.isTaxTipped, reduceTip)
}


data class PaymentTemplate(val method: PaymentMethod,
                           val payerId: String,
                           val payeeId: String,
                           val surrogateId: String? = null,
                           val payerAddress: String? = null,
                           val payeeAddress: String? = null,
                           val surrogateAddress: String? = null)


@Entity(tableName = "diner_table",
    foreignKeys = [ ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Diner(@PrimaryKey val id: String,
            val billId: String,
            val listIndex: Int,
            val lookupKey: String,
            var name: String,
            val paymentAddressDefaults: MutableMap<PaymentMethod, String> = mutableMapOf(),
            val paymentTemplateMap: MutableMap<String, PaymentTemplate> = mutableMapOf()): Parcelable {

    constructor(id: String, billId: String, listIndex: Int, contact: Contact):
            this(id, billId, listIndex, contact.lookupKey, contact.name, contact.paymentAddressDefaults) {
        photoUri = contact.photoUri
    }

    @Ignore var photoUri: String? = null
    @Ignore var emailAddresses: List<String> = emptyList()

    fun asContact() = Contact(lookupKey, name, Contact.VISIBLE, paymentAddressDefaults).also { it.photoUri = photoUri }

    @Ignore private val itemIdsMutable = mutableListOf<String>()
    @Ignore private val debtOwedIdsMutable = mutableListOf<String>()
    @Ignore private val debtHeldIdsMutable = mutableListOf<String>()
    @Ignore private val discountReceivedIdsMutable = mutableListOf<String>()
    @Ignore private val discountPurchasedIdsMutable = mutableListOf<String>()
    @Ignore private val paymentSentIdsMutable = mutableListOf<String>()
    @Ignore private val paymentReceivedIdsMutable = mutableListOf<String>()
    @Ignore val itemIds: List<String> = itemIdsMutable
    @Ignore val debtOwedIds: List<String> = debtOwedIdsMutable
    @Ignore val debtHeldIds: List<String> = debtHeldIdsMutable
    @Ignore val discountReceivedIds: List<String> = discountReceivedIdsMutable
    @Ignore val discountPurchasedIds: List<String> = discountPurchasedIdsMutable
    @Ignore val paymentSentIds: List<String> = paymentSentIdsMutable
    @Ignore val paymentReceivedIds: List<String> = paymentReceivedIdsMutable

    @Ignore private val itemsMutable = mutableListOf<Item>()
    @Ignore private val debtsOwedMutable = mutableListOf<Debt>()
    @Ignore private val debtsHeldMutable = mutableListOf<Debt>()
    @Ignore private val discountsReceivedMutable = mutableListOf<Discount>()
    @Ignore private val discountsPurchasedMutable = mutableListOf<Discount>()
    @Ignore private val paymentsSentMutable = mutableListOf<Payment>()
    @Ignore private val paymentsReceivedMutable = mutableListOf<Payment>()
    @Ignore val items: List<Item> = itemsMutable
    @Ignore val debtsOwed: List<Debt> = debtsOwedMutable
    @Ignore val debtsHeld: List<Debt> = debtsHeldMutable
    @Ignore val discountsReceived: List<Discount> = discountsReceivedMutable
    @Ignore val discountsPurchased: List<Discount> = discountsPurchasedMutable
    @Ignore val paymentsSent: List<Payment> = paymentsSentMutable
    @Ignore val paymentsReceived: List<Payment> = paymentsReceivedMutable

    fun isCashPool() = lookupKey == Contact.cashPool.lookupKey
    fun isRestaurant() = lookupKey == Contact.restaurant.lookupKey
    fun isSelf() = lookupKey == Contact.self.lookupKey

    fun getDefaultAddressForMethod(method: PaymentMethod) = paymentAddressDefaults[method]

    fun setDefaultAddressForMethod(method: PaymentMethod, alias: String?) {
        if (alias == null) {
            paymentAddressDefaults.remove(method)
        } else {
            paymentAddressDefaults[method] = alias
        }
    }

    fun getPaymentTemplate(payee: Diner) = paymentTemplateMap[payee.id]

    fun setPaymentTemplate(template: PaymentTemplate) {
        paymentTemplateMap[template.payeeId] = template
    }

    fun addItem(item: Item) {
        itemIdsMutable.add(item.id)
        itemsMutable.add(item)
    }
    fun addOwedDebt(debt: Debt) {
        debtOwedIdsMutable.add(debt.id)
        debtsOwedMutable.add(debt)
    }
    fun addHeldDebt(debt: Debt) {
        debtHeldIdsMutable.add(debt.id)
        debtsHeldMutable.add(debt)
    }
    fun addReceivedDiscount(discount: Discount) {
        discountReceivedIdsMutable.add(discount.id)
        discountsReceivedMutable.add(discount)
    }
    fun addPurchasedDiscount(discount: Discount) {
        discountPurchasedIdsMutable.add(discount.id)
        discountsPurchasedMutable.add(discount)
    }
    fun addSentPayment(payment: Payment) {
        paymentSentIdsMutable.add(payment.id)
        paymentsSentMutable.add(payment)
    }
    fun addReceivedPayment(payment: Payment) {
        paymentReceivedIdsMutable.add(payment.id)
        paymentsReceivedMutable.add(payment)
    }

    fun removeItem(item: Item) {
        itemIdsMutable.remove(item.id)
        itemsMutable.remove(item)
    }
    fun removeOwedDebt(debt: Debt) {
        debtOwedIdsMutable.remove(debt.id)
        debtsOwedMutable.remove(debt)
    }
    fun removeHeldDebt(debt: Debt) {
        debtHeldIdsMutable.remove(debt.id)
        debtsHeldMutable.remove(debt)
    }
    fun removeReceivedDiscount(discount: Discount) {
        discountReceivedIdsMutable.remove(discount.id)
        discountsReceivedMutable.remove(discount)
    }
    fun removePurchasedDiscount(discount: Discount) {
        discountPurchasedIdsMutable.remove(discount.id)
        discountsPurchasedMutable.remove(discount)
    }
    fun removeSentPayment(payment: Payment) {
        paymentSentIdsMutable.remove(payment.id)
        paymentsSentMutable.remove(payment)
    }
    fun removeReceivedPayment(payment: Payment) {
        paymentReceivedIdsMutable.remove(payment.id)
        paymentsReceivedMutable.remove(payment)
    }

    fun withIdLists(newItemIds: List<String>,
                    newDebtOwedIds: List<String>,
                    newDebtHeldIds: List<String>,
                    newDiscountReceivedIds: List<String>,
                    newDiscountPurchasedIds: List<String>,
                    newPaymentSentIds: List<String>,
                    newPaymentReceivedIds: List<String>) = this.also {
        itemIdsMutable.addAll(newItemIds)
        debtOwedIdsMutable.addAll(newDebtOwedIds)
        debtHeldIdsMutable.addAll(newDebtHeldIds)
        discountReceivedIdsMutable.addAll(newDiscountReceivedIds)
        discountPurchasedIdsMutable.addAll(newDiscountPurchasedIds)
        paymentSentIdsMutable.addAll(newPaymentSentIds)
        paymentReceivedIdsMutable.addAll(newPaymentReceivedIds)
    }

    fun populateEntityLists(itemMap: Map<String, Item>,
                            debtMap: Map<String, Debt>,
                            discountMap: Map<String, Discount>,
                            paymentMap: Map<String, Payment>) {
        itemsMutable.addAll(itemIds.mapNotNull { itemMap[it] })
        debtsOwedMutable.addAll(debtOwedIds.mapNotNull { debtMap[it] })
        debtsHeldMutable.addAll(debtHeldIds.mapNotNull { debtMap[it] })
        discountsReceivedMutable.addAll(discountReceivedIds.mapNotNull { discountMap[it] })
        discountsPurchasedMutable.addAll(discountPurchasedIds.mapNotNull { discountMap[it] })
        paymentsSentMutable.addAll(paymentSentIds.mapNotNull { paymentMap[it] })
        paymentsReceivedMutable.addAll(paymentReceivedIds.mapNotNull { paymentMap[it] })
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeString(id)
            this.writeString(billId)
            this.writeInt(listIndex)
            this.writeString(lookupKey)
            this.writeString(name)
            this.writeString(Gson().toJson(paymentAddressDefaults))
            this.writeString(Gson().toJson(paymentTemplateMap))
            this.writeStringList(itemIds)
            this.writeStringList(debtOwedIds)
            this.writeStringList(debtHeldIds)
            this.writeStringList(discountReceivedIds)
            this.writeStringList(discountPurchasedIds)
            this.writeStringList(paymentSentIds)
            this.writeStringList(paymentReceivedIds)
        }
    }

    companion object CREATOR: Parcelable.Creator<Diner> {
        override fun createFromParcel(parcel: Parcel) = Diner(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readInt(),
            parcel.readString()!!,
            parcel.readString()!!,
            Gson().fromJson(parcel.readString(), Converters.aliasMapType),
            Gson().fromJson(parcel.readString(), Converters.paymentTemplateMapType))
            .withIdLists(
                parcel.createStringArrayList() ?: mutableListOf(),
                parcel.createStringArrayList() ?: mutableListOf(),
                parcel.createStringArrayList() ?: mutableListOf(),
                parcel.createStringArrayList() ?: mutableListOf(),
                parcel.createStringArrayList() ?: mutableListOf(),
                parcel.createStringArrayList() ?: mutableListOf(),
                parcel.createStringArrayList() ?: mutableListOf()
            )

        override fun newArray(size: Int): Array<Diner?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "item_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Item(@PrimaryKey val id: String,
           val billId: String,
           val listIndex: Int,
           val price: Double,
           val name: String): Parcelable {

    @Ignore private val dinerIdsMutable = mutableListOf<String>()
    @Ignore private val discountIdsMutable = mutableListOf<String>()
    @Ignore val dinerIds: List<String> = dinerIdsMutable
    @Ignore val discountIds: List<String> = discountIdsMutable

    @Ignore private val dinersMutable = mutableListOf<Diner>()
    @Ignore private val discountsMutable = mutableListOf<Discount>()
    @Ignore val diners: List<Diner> = dinersMutable
    @Ignore val discounts: List<Discount> = discountsMutable

    fun withIdLists(newDinerIds: List<String>, newDiscountIds: List<String>) = this.also {
        dinerIdsMutable.addAll(newDinerIds)
        discountIdsMutable.addAll(newDiscountIds)
    }

    fun populateEntityLists(dinerMap: Map<String, Diner>, discountMap: Map<String, Discount>) {
        dinersMutable.addAll(dinerIds.mapNotNull { dinerMap[it] })
        discountsMutable.addAll(discountIds.mapNotNull { discountMap[it] })
    }

    fun addDiner(diner: Diner) {
        dinerIdsMutable.add(diner.id)
        dinersMutable.add(diner)
    }
    fun addDiscount(discount: Discount) {
        discountIdsMutable.add(discount.id)
        discountsMutable.add(discount)
    }

    fun removeDiner(diner: Diner) {
        dinerIdsMutable.remove(diner.id)
        dinersMutable.remove(diner)
    }
    fun removeDiscount(discount: Discount) {
        discountIdsMutable.remove(discount.id)
        discountsMutable.remove(discount)
    }
    
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeString(id)
            this.writeString(billId)
            this.writeInt(listIndex)
            this.writeDouble(price)
            this.writeString(name)
            this.writeStringList(dinerIds)
            this.writeStringList(discountIds)
        }
    }

    companion object CREATOR : Parcelable.Creator<Item> {
        override fun createFromParcel(parcel: Parcel) = Item(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readInt(),
            parcel.readDouble(),
            parcel.readString() ?: "Item")
            .withIdLists(
                parcel.createStringArrayList() ?: emptyList(),
                parcel.createStringArrayList() ?: emptyList()
            )

        override fun newArray(size: Int): Array<Item?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "debt_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Debt(@PrimaryKey val id: String,
           val billId: String,
           val listIndex: Int,
           val amount: Double,
           val name: String): Parcelable {

    @Ignore private val debtorIdsMutable = mutableListOf<String>()
    @Ignore private val creditorIdsMutable = mutableListOf<String>()
    @Ignore val debtorIds: List<String> = debtorIdsMutable
    @Ignore val creditorIds: List<String> = creditorIdsMutable

    @Ignore private val debtorsMutable = mutableListOf<Diner>()
    @Ignore private val creditorsMutable = mutableListOf<Diner>()
    @Ignore val debtors: List<Diner> = debtorsMutable
    @Ignore val creditors: List<Diner> = creditorsMutable

    fun addDebtor(diner: Diner) {
        debtorIdsMutable.add(diner.id)
        debtorsMutable.add(diner)
    }
    fun addCreditor(diner: Diner) {
        creditorIdsMutable.add(diner.id)
        creditorsMutable.add(diner)
    }

    fun removeDebtor(diner: Diner) {
        debtorIdsMutable.remove(diner.id)
        debtorsMutable.remove(diner)
    }
    fun removeCreditor(diner: Diner) {
        creditorIdsMutable.remove(diner.id)
        creditorsMutable.remove(diner)
    }

    fun withIdLists(newDebtorIds: List<String>, newCreditorIds: List<String>) = this.also {
        debtorIdsMutable.addAll(newDebtorIds)
        creditorIdsMutable.addAll(newCreditorIds)
    }

    fun populateEntityLists(dinerMap: Map<String, Diner>) {
        debtorsMutable.addAll(debtorIds.mapNotNull { dinerMap[it] })
        creditorsMutable.addAll(creditorIds.mapNotNull { dinerMap[it] })
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeString(id)
            this.writeString(billId)
            this.writeInt(listIndex)
            this.writeDouble(amount)
            this.writeString(name)
            this.writeStringList(debtorIds)
            this.writeStringList(creditorIds)
        }
    }

    companion object CREATOR : Parcelable.Creator<Debt> {
        override fun createFromParcel(parcel: Parcel) = Debt(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readInt(),
            parcel.readDouble(),
            parcel.readString()!!)
            .withIdLists(
                parcel.createStringArrayList() ?: emptyList(),
                parcel.createStringArrayList() ?: emptyList()
            )

        override fun newArray(size: Int): Array<Debt?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "discount_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId")])
class Discount(@PrimaryKey val id: String,
               val billId: String,
               val listIndex: Int,
               val asPercent: Boolean,
               val onItems: Boolean,
               val value: Double,
               val cost: Double?): Parcelable {

    @Ignore private val itemIdsMutable = mutableListOf<String>()
    @Ignore private val recipientIdsMutable = mutableListOf<String>()
    @Ignore private val purchaserIdsMutable = mutableListOf<String>()
    @Ignore val itemIds: List<String> = itemIdsMutable
    @Ignore val recipientIds: List<String> = recipientIdsMutable
    @Ignore val purchaserIds: List<String> = purchaserIdsMutable

    @Ignore private val itemsMutable = mutableListOf<Item>()
    @Ignore private val recipientsMutable = mutableListOf<Diner>()
    @Ignore private val purchasersMutable = mutableListOf<Diner>()
    @Ignore val items = itemsMutable
    @Ignore val recipients = recipientsMutable
    @Ignore val purchasers = purchasersMutable

    fun addItem(item: Item) {
        itemIdsMutable.add(item.id)
        itemsMutable.add(item)
    }
    fun addRecipient(diner: Diner) {
        recipientIdsMutable.add(diner.id)
        recipientsMutable.add(diner)
    }
    fun addPurchaser(diner: Diner) {
        purchaserIdsMutable.add(diner.id)
        purchasersMutable.add(diner)
    }

    fun removeItem(item: Item) {
        itemIdsMutable.remove(item.id)
        itemsMutable.remove(item)
    }
    fun removeRecipient(diner: Diner) {
        recipientIdsMutable.remove(diner.id)
        recipientsMutable.remove(diner)
    }
    fun removePurchaser(diner: Diner) {
        purchaserIdsMutable.remove(diner.id)
        purchasersMutable.remove(diner)
    }

    fun withIdLists(newItemIds: List<String>, newRecipientIds: List<String>, newPurchaserIds: List<String>) = this.also {
        itemIdsMutable.addAll(newItemIds)
        recipientIdsMutable.addAll(newRecipientIds)
        purchaserIdsMutable.addAll(newPurchaserIds)
    }

    fun populateEntityLists(dinerMap: Map<String, Diner>, itemMap: Map<String, Item>) {
        itemsMutable.addAll(itemIds.mapNotNull { itemMap[it] })
        recipientsMutable.addAll(recipientIds.mapNotNull { dinerMap[it] })
        purchasersMutable.addAll(purchaserIds.mapNotNull { dinerMap[it] })
    }

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.apply {
            this.writeString(id)
            this.writeString(billId)
            this.writeInt(listIndex)
            this.writeInt(if (asPercent) 1 else  0)
            this.writeInt(if (onItems) 1 else  0)
            this.writeDouble(value)
            this.writeDouble(cost ?: -1.0)
            this.writeStringList(itemIds)
            this.writeStringList(recipientIds)
            this.writeStringList(purchaserIds)
        }
    }

    companion object CREATOR : Parcelable.Creator<Discount> {
        override fun createFromParcel(parcel: Parcel) = Discount(
            parcel.readString()!!,
            parcel.readString()!!,
            parcel.readInt(),
            parcel.readInt() == 1,
            parcel.readInt() == 1,
            parcel.readDouble(),
            parcel.readDouble().let { if(it < 0.0) null else it })
            .withIdLists(
                parcel.createStringArrayList() ?: emptyList(),
            parcel.createStringArrayList() ?: emptyList(),
            parcel.createStringArrayList() ?: emptyList()
            )

        override fun newArray(size: Int): Array<Discount?> = arrayOfNulls(size)
    }
}


@Entity(tableName = "payment_table",
    foreignKeys = [ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["payerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["payeeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["surrogateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("billId"), Index("payerId"), Index("payeeId"), Index("surrogateId")])
class Payment(@PrimaryKey val id: String,
              val billId: String,
              val amount: Double,
              val method: PaymentMethod,
              val committed: Boolean,
              val payerId: String,
              val payeeId: String,
              val surrogateId: String?) {

    @Ignore lateinit var payer: Diner
    @Ignore lateinit var payee: Diner
    @Ignore var surrogate: Diner? = null

    fun unprocessed() = !committed && method.processedWithinApp

    fun populateEntities(dinerMap: Map<String, Diner>) {
        payer = dinerMap[payerId]!!
        payee = dinerMap[payeeId]!!
        surrogate = dinerMap[surrogateId]!!
    }
}


@Entity(tableName = "diner_item_join_table",
    primaryKeys = ["dinerId", "itemId"],
    foreignKeys = [ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class,  parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class DinerItemJoin(val dinerId: String, val itemId: String)


@Entity(tableName = "debt_debtor_join_table",
    primaryKeys = ["debtId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Debt::class,  parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DebtDebtorJoin(val debtId: String, val dinerId: String)


@Entity(tableName = "debt_creditor_join_table",
    primaryKeys = ["debtId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Debt::class,  parentColumns = ["id"], childColumns = ["debtId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DebtCreditorJoin(val debtId: String, val dinerId: String)


@Entity(tableName = "discount_recipient_join_table",
    primaryKeys = ["discountId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DiscountRecipientJoin(val discountId: String, val dinerId: String)


@Entity(tableName = "discount_purchaser_join_table",
    primaryKeys = ["discountId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class DiscountPurchaserJoin(val discountId: String, val dinerId: String)


@Entity(tableName = "discount_item_join_table",
    primaryKeys = ["discountId", "itemId"],
    foreignKeys = [ForeignKey(entity = Discount::class,  parentColumns = ["id"], childColumns = ["discountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")])
data class DiscountItemJoin(val discountId: String, val itemId: String)


@Entity(tableName = "payment_payer_join_table",
    primaryKeys = ["paymentId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Payment::class,  parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class PaymentPayerJoin(val paymentId: String, val dinerId: String)


@Entity(tableName = "payment_payee_join_table",
    primaryKeys = ["paymentId", "dinerId"],
    foreignKeys = [ForeignKey(entity = Payment::class,  parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Diner::class, parentColumns = ["id"], childColumns = ["dinerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dinerId")])
data class PaymentPayeeJoin(val paymentId: String, val dinerId: String)
