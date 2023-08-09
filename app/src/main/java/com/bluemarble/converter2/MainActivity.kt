package com.bluemarble.converter2

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View.OnFocusChangeListener
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject


class MainActivity : AppCompatActivity() {

    var exchangeRate = 24.281F // default value when app is first installed


    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            saveExchangeRate()
            convert()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val czkView: EditText = findViewById(R.id.editTextCzk)
        val eurView: EditText = findViewById(R.id.editTextEur)
        val roundUpSwitchView: SwitchMaterial = findViewById(R.id.round_up_switch)
        roundUpSwitchView.setOnClickListener {
            convert()
            saveExchangeRate()
        }

        val sharedPreferences: SharedPreferences =
            this.getSharedPreferences("ConverterPrefs", Context.MODE_PRIVATE)

        // Retrieving the number from SharedPreferences, if not found return default value
        exchangeRate = sharedPreferences.getFloat("exchangeRate", exchangeRate)

        exchangeRate = getRate()  // try to get online data if not found return default value


        val exchangeRateView: TextView =
            findViewById(R.id.exchangeRateTextView) // set the exchange rate textView
        exchangeRateView.text =
            String.format(getString(R.string.default_exchange_rate), exchangeRate)


        czkView.onFocusChangeListener = OnFocusChangeListener { _, b ->
            if (b) {
                eurView.removeTextChangedListener(textWatcher)
                czkView.addTextChangedListener(textWatcher)
            }
        }
        eurView.onFocusChangeListener = OnFocusChangeListener { _, a ->
            if (a) {
                czkView.removeTextChangedListener(textWatcher)
                eurView.addTextChangedListener(textWatcher)
            }
        }
        czkView.requestFocus()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return (activeNetworkInfo != null) && activeNetworkInfo.isConnected
    }


    private fun getRate(): Float {

        if (!isNetworkAvailable()) {
            Toast.makeText(applicationContext, "No internet connection", Toast.LENGTH_LONG).show()
            return exchangeRate
        }

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
                    val czkValue = ratesObject.getDouble("CZK")
                    val eurValue = ratesObject.getDouble("EUR")

                    exchangeRate = ((1 / eurValue) * czkValue).toFloat()
                } else {
                    Log.e("API Response", "Null has been returned")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                Toast.makeText(applicationContext, "API call failure", Toast.LENGTH_LONG).show()
            }
        })
        return exchangeRate
    }

    fun saveExchangeRate() {
        val sharedPreferences: SharedPreferences =
            this.getSharedPreferences("ConverterPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("exchangeRate", exchangeRate) // Saving a number to SharedPreferences
        editor.apply()
    }

    fun convert() {

        val exchangeRateView: TextView = findViewById(R.id.exchangeRateTextView)
        exchangeRateView.text =
            String.format(getString(R.string.default_exchange_rate), exchangeRate)

        val czkView: EditText = findViewById(R.id.editTextCzk)
        val eurView: EditText = findViewById(R.id.editTextEur)
        val roundUpSwitchView: SwitchMaterial = findViewById(R.id.round_up_switch)
        val czkVal = czkView.text.toString().toDoubleOrNull()
        val eurVal = eurView.text.toString().toDoubleOrNull()

        if (czkView.isFocused && czkVal != null) {
            eurView.setText(String.format("%.2f", (czkVal / exchangeRate)))
        } else if (eurView.isFocused && eurVal != null) {
            if (roundUpSwitchView.isChecked) {
                czkView.setText(String.format("%.0f", kotlin.math.ceil(eurVal * exchangeRate)))
            } else {
                czkView.setText(String.format("%.2f", eurVal * exchangeRate))
            }
        } else if (czkVal == null) {
            eurView.setText("")
        } else if (eurVal == null) {
            czkView.setText("")
        }
    }
}