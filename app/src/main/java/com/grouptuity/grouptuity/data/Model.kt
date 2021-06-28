package com.grouptuity.grouptuity.data
//
//import androidx.lifecycle.DefaultLifecycleObserver
//import kotlinx.coroutines.flow.MutableStateFlow
//import java.util.*
//import kotlin.properties.ReadWriteProperty
//import kotlin.reflect.KProperty
//
//fun <T> savedProperty(initialValue: T): ReadWriteProperty<ModelClass, T> =
//    object: ReadWriteProperty<ModelClass, T>, DefaultLifecycleObserver {
//        private var value = initialValue
//
//        override fun getValue(model: ModelClass, property: KProperty<*>): T { return value }
//
//        override fun setValue(thisRef: ModelClass, property: KProperty<*>, value: T) {
//            this.value = value
//
//
//        }
//    }
//
//abstract class ModelClass {
//    var flow = MutableStateFlow<Boolean>(false)
//}
//
//
//class Bill(
//    val id: String,
//    initialTitle: String,
//    val timeCreated: Long,
//    val tax: Double,
//    val taxAsPercent: Boolean,
//    val tip: Double,
//    val tipAsPercent: Boolean,
//    val isTaxTipped: Boolean,
//    val discountsReduceTip: Boolean): ModelClass() {
//
//    val title by savedProperty<String>(initialTitle)
//
//
//    val diners = mutableListOf<Diner>()
//
//    fun addDiner(diner: Diner) {
//        diners.add(diner)
//    }
//}
//
//class Diner(
//    val id: String,
//    val billId: String,
//    val contact: Contact,
//    var paymentPreferences: PaymentPreferences = PaymentPreferences()) {
//
//    val items: List<Long>
//
//    val debtsOwed: List<Long>
//
//    val debtsHeld: List<Long>
//
//    val discountsReceived: List<Long>
//
//    val discountsPurchased: List<Long>
//
//    val paymentsSent: List<Long>
//
//    val paymentsReceived: List<Long>
//}
//
//class Item {
//
//}
//
//class Debt {
//
//}
//
//class Discount {
//
//}
//
//class Payment {
//
//}
