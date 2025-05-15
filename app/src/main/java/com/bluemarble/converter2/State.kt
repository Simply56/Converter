package com.bluemarble.converter2

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class State {
    private var locked: AtomicBoolean = AtomicBoolean(false)
    var focusedEditTextView: String = "upper"
    var upperSelection: String = "CZK"
        set(value) {
            lock()
            if (ratesMap.containsKey(value)) {
                field = value
            } else {
                Log.v("Selection", "upperSelection cannot be $value")
            }
            unlock()
        }
    var lowerSelection: String = "EUR"
        set(value) {
            lock()
            if (ratesMap.containsKey(value)) {
                field = value
            } else {
                Log.v("Selection", "lowerSelection cannot be $value")
            }
            unlock()
        }
    val currencyMap = hashMapOf(
        "USD" to "$",
        "EUR" to "€",
        "JPY" to "¥",
        "GBP" to "£",
        "AUD" to "A$",
        "CAD" to "C$",
        "CHF" to "CHF",
        "CNY" to "¥",
        "SEK" to "kr",
        "NZD" to "NZ$",
        "MXN" to "$",
        "SGD" to "S$",
        "HKD" to "HK$",
        "NOK" to "kr",
        "KRW" to "₩",
        "TRY" to "₺",
        "INR" to "₹",
        "RUB" to "₽",
        "ZAR" to "R",
        "BRL" to "R$",
        "TWD" to "NT$",
        "PLN" to "zł",
        "THB" to "฿",
        "CZK" to "Kč",
        "DKK" to "kr",
        "HUF" to "Ft",
        "ILS" to "₪",
        "MYR" to "RM",
        "PHP" to "₱",
        "IDR" to "Rp",
        "PKR" to "₨",
        "VND" to "₫",
        "NGN" to "₦",
        "EGP" to "E£",
        "KZT" to "₸",
        "UAH" to "₴"
    )
    var ratesMap = hashMapOf("EUR" to 1.0, "CZK" to 25.0)
    var calculatedResult: Double = 0.0


    fun exchangeRate(): Double {
        lock()
        val upperValue: Double? = ratesMap[upperSelection]
        val lowerValue: Double? = ratesMap[lowerSelection]
        unlock()

        if (upperValue != null && lowerValue != null) {
            return upperValue / lowerValue
        }
        return 25.0
    }

    fun unlock() {
        locked.set(false)
    }

    /**
     * Locks this entire class preventing it from modification
     */
    fun lock() {
        while (locked.get()) {
            Thread.sleep(50)
        }
        locked.set(true)
    }
}
