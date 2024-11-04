package com.bluemarble.converter2

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit


// VERCA <3


class MainActivity : AppCompatActivity() {

    class State {
        var upperSelection: String = "CZK"
        var lowerSelection: String = "EUR"
        var ratesMap = hashMapOf("EUR" to 1.0, "CZK" to 25.0)
        var exchangeRate: Double = 25.0 // default value when app is first installed

        private var locked: Boolean = false

        fun calculateExchangeRate() {
            lock()
            val upperValue = ratesMap[upperSelection]
            val lowerValue = ratesMap[lowerSelection]
            if (upperValue != null && lowerValue != null) {
                exchangeRate = upperValue / lowerValue
            }
            unlock()
        }

        fun unlock() {
            locked = false
        }

        fun lock() {
            while (locked) {
                Thread.sleep(50)
            }
            locked = true
        }
    }

    var state = State()


    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            convert(state)
        }
    }

    override fun onPause() {
        super.onPause()
        val leftCurrencySpinner: Spinner = findViewById(R.id.upperCurrencySpinner)
        val rightCurrencySpinner: Spinner = findViewById(R.id.lowerCurrencySpinner)

        val sharedPreferences: SharedPreferences =
            this.getSharedPreferences("ConverterPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("upperSelection", leftCurrencySpinner.selectedItemPosition)
        editor.putInt("lowerSelection", rightCurrencySpinner.selectedItemPosition)
        editor.apply()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val upperEditTextLayout: TextInputLayout = findViewById(R.id.editTextUpperLayout)
        val lowerEditTextLayout: TextInputLayout = findViewById(R.id.editTextLowerLayout)
        val lowerEditText: EditText = findViewById(R.id.editTextLower)
        val upperEditText: EditText = findViewById(R.id.editTextUpper)


        val leftCurrencySpinner: Spinner = findViewById(R.id.upperCurrencySpinner)
        val rightCurrencySpinner: Spinner = findViewById(R.id.lowerCurrencySpinner)


        loadData(state) // Load locally stored rates
        // Check for internet connectivity
        if (isNetworkAvailable(this)) {
            runBlocking {
                launch { getOnlineRates(state) }
            }
        }



        upperEditText.onFocusChangeListener = OnFocusChangeListener { _, b ->
            if (b) {
                lowerEditText.removeTextChangedListener(textWatcher)
                upperEditText.addTextChangedListener(textWatcher)
            }
        }
        lowerEditText.onFocusChangeListener = OnFocusChangeListener { _, a ->
            if (a) {
                upperEditText.removeTextChangedListener(textWatcher)
                lowerEditText.addTextChangedListener(textWatcher)
            }
        }
        upperEditText.requestFocus()

        leftCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                state.upperSelection =
                    parent?.getItemAtPosition(position).toString().slice((IntRange(0, 2)))
                // Use the selectedCurrency value to update your UI or perform other actions
                upperEditTextLayout.hint = state.upperSelection
                convert(state)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                leftCurrencySpinner.setSelection(0)
            }
        }
        rightCurrencySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                state.lowerSelection =
                    parent?.getItemAtPosition(position).toString().slice((IntRange(0, 2)))
                // Use the selectedCurrency value to update your UI or perform other actions
                lowerEditTextLayout.hint = state.lowerSelection
                convert(state)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                rightCurrencySpinner.setSelection(1)
            }
        }

        val exchangeArrows: ImageButton = findViewById(R.id.exchangeArrows)
        val animation: Animation = AnimationUtils.loadAnimation(this, R.anim.scale_animation)


        exchangeArrows.setOnClickListener { v ->
            v.startAnimation(animation)
            val tmp = rightCurrencySpinner.selectedItemId.toInt()
            rightCurrencySpinner.setSelection(leftCurrencySpinner.selectedItemId.toInt())
            leftCurrencySpinner.setSelection(tmp)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    // check if internet is available beforehand
    // tries to get exchange rate in the following order: last stored locally, online, some default value
    private fun getOnlineRates(state: State) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://openexchangerates.org/api/latest.json?app_id=bc4c542583c2493a92e07d2fc1f3c48b")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (responseData != null) {
                    val jsonObject = JSONObject(responseData)
                    val ratesObject = jsonObject.getJSONObject("rates")

                    state.lock()
                    for (key in ratesObject.keys()) {
                        state.ratesMap[key] = ratesObject.getDouble(key)
                    }
                    saveExchangeRates(ratesObject)
                    state.unlock()

                } else {
                    Log.e("API Response", "Null has been returned as a response")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Toast.makeText(applicationContext, "API call failure", Toast.LENGTH_LONG).show()
            }
        })
        updateTimeAgo(System.currentTimeMillis())
    }

    // stores all the rates
    fun saveExchangeRates(rates: JSONObject) {
        val sharedPreferences: SharedPreferences =
            this.getSharedPreferences("ConverterPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        // Saving a number to SharedPreferences
        for (key in rates.keys()) {
            editor.putFloat(key, rates.getDouble(key).toFloat())
        }
        val timeInMillis = System.currentTimeMillis()
        editor.putLong("lastUpdated", timeInMillis)
        editor.apply()
    }

    // tries to get the last stored exchange rate if not returns some default rate
    private fun loadData(state: State) {
        val sharedPreferences: SharedPreferences =
            this.getSharedPreferences("ConverterPrefs", Context.MODE_PRIVATE)

        val leftCurrencySpinner: Spinner = findViewById(R.id.upperCurrencySpinner)
        val rightCurrencySpinner: Spinner = findViewById(R.id.lowerCurrencySpinner)

        leftCurrencySpinner.setSelection(sharedPreferences.getInt("upperSelection", 0))
        rightCurrencySpinner.setSelection(sharedPreferences.getInt("lowerSelection", 1))

        updateTimeAgo(sharedPreferences.getLong("lastUpdated", -1L))
        state.lock()
        for ((key, value) in sharedPreferences.all) {
            if (value is Float) {
                state.ratesMap[key] = value.toDouble()
            }
        }
        state.unlock()

        state.calculateExchangeRate()


    }

    @SuppressLint("DefaultLocale")
    fun convert(state: State) {

        val upperView: EditText = findViewById(R.id.editTextUpper)
        val lowerView: EditText = findViewById(R.id.editTextLower)
        val upperVal = upperView.text.toString().toDoubleOrNull()
        val lowerVal = lowerView.text.toString().toDoubleOrNull()
        if (upperVal == null && lowerVal == null) {
            return
        }

        state.calculateExchangeRate()

        if (upperView.isFocused && upperVal != null) {
            lowerView.setText(String.format("%.2f", upperVal / state.exchangeRate))
        } else if (lowerView.isFocused && lowerVal != null) {
            upperView.setText(String.format("%.2f", lowerVal * state.exchangeRate))
        } else if (upperVal == null) {
            lowerView.text.clear()
        } else if (lowerVal == null) {
            upperView.text.clear()
        }

    }

    // format and display timeInMillis
    private fun updateTimeAgo(timeInMillis: Long) {

        runOnUiThread {
            val lastUpdatedView: TextView = findViewById(R.id.textViewUpdated)
            if (timeInMillis == -1L) {
                lastUpdatedView.text = getString(R.string.updated_never)
                return@runOnUiThread
            }

            val currentTime = System.currentTimeMillis()
            val timeDifference = currentTime - timeInMillis

            val seconds = TimeUnit.MILLISECONDS.toSeconds(timeDifference)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
            val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
            val days = TimeUnit.MILLISECONDS.toDays(timeDifference)

            lastUpdatedView.text = when {
                seconds < 1 -> "updated just now"
                seconds < 60 -> "updated $seconds seconds ago"
                minutes < 60 -> "updated $minutes minutes ago"
                hours < 24 -> "updated $hours hours ago"
                else -> "updated $days days ago"
            }
        }
    }
}
