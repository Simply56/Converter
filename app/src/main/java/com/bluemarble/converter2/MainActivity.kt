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
import android.view.KeyEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import net.objecthunter.exp4j.ExpressionBuilder
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit


import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class MainActivity : AppCompatActivity() {

    val state = State()

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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)


        val upperEditTextLayout: TextInputLayout = findViewById(R.id.editTextUpperLayout)
        val lowerEditTextLayout: TextInputLayout = findViewById(R.id.editTextLowerLayout)
        val lowerEditText: EditText = findViewById(R.id.editTextLower)
        val upperEditText: EditText = findViewById(R.id.editTextUpper)

        initializeCustomKeyboard()
        findViewById<Button>(R.id.button_delete).setOnLongClickListener {
            lowerEditText.text.clear()
            upperEditText.text.clear()
            true
        }


        lowerEditText.showSoftInputOnFocus = false
        upperEditText.showSoftInputOnFocus = false

        val leftCurrencySpinner: Spinner = findViewById(R.id.upperCurrencySpinner)
        val rightCurrencySpinner: Spinner = findViewById(R.id.lowerCurrencySpinner)


        loadData(state) // Load locally stored rates
        if (isNetworkAvailable(this)) {
            lifecycleScope.launch {
                getOnlineRates(state) // Run asynchronously without blocking the main thread
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

    private fun initializeCustomKeyboard() {
        val buttons: Array<Button> = arrayOf(
            findViewById(R.id.button_delete),
            findViewById(R.id.button_bracket_l),
            findViewById(R.id.button_bracket_r),
            findViewById(R.id.button_division),
            findViewById(R.id.button_multiply),
            findViewById(R.id.button_subtraction),
            findViewById(R.id.button_addition),
            findViewById(R.id.button_equals),
            findViewById(R.id.button_answer),
            findViewById(R.id.button_dot),
            findViewById(R.id.button_0),
            findViewById(R.id.button_1),
            findViewById(R.id.button_2),
            findViewById(R.id.button_3),
            findViewById(R.id.button_4),
            findViewById(R.id.button_5),
            findViewById(R.id.button_6),
            findViewById(R.id.button_7),
            findViewById(R.id.button_8),
            findViewById(R.id.button_9)
        )
        for (b in buttons) {
            b.setOnClickListener { press(b) }
        }
    }

    // check if internet is available beforehand
    // geta exchange rate in the following order: last stored locally, online, some default value
    private fun getOnlineRates(state: State) {
        val client = OkHttpClient()
        val request =
            Request.Builder().url("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    if (responseData == null) {
                        Log.e("API Response", "Null has been returned as a response")
                        return
                    }
                    // Create XML pull parser
                    val factory = XmlPullParserFactory.newInstance()
                    val parser = factory.newPullParser()
                    parser.setInput(StringReader(responseData))

                    var eventType = parser.eventType
                    state.lock()

                    // Parse XML
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "Cube") {
                            val currency = parser.getAttributeValue(null, "currency")
                            val rate = parser.getAttributeValue(null, "rate")

                            if (currency != null && rate != null) {
                                state.ratesMap[currency] = rate.toDouble()
                            }
                        }
                        eventType = parser.next()
                    }

                    // Save the rates to SharedPreferences
                    val ratesObject = JSONObject()
                    for ((key, value) in state.ratesMap) {
                        ratesObject.put(key, value)
                    }
                    saveExchangeRates(ratesObject)
                    state.unlock()

                } catch (e: Exception) {
                    Log.e("XML Parsing", "Error parsing XML response", e)
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext, "Error parsing exchange rates", Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext, "Failed to fetch exchange rates", Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
        updateTimeAgo(System.currentTimeMillis())
    }


    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
    }

    @SuppressLint("DefaultLocale")
    fun convert(state: State) {
        val exchangeRateText: TextView = findViewById(R.id.exchangeRateText)
        exchangeRateText.text = getString(
            R.string.exchange_rate_format,
            state.currencyMap[state.upperSelection],
            String.format("%.5f", 1 / state.exchangeRate()),
            state.currencyMap[state.lowerSelection]
        )


        val upperView: EditText = findViewById(R.id.editTextUpper)
        val lowerView: EditText = findViewById(R.id.editTextLower)

        upperView.showSoftInputOnFocus = false
        lowerView.showSoftInputOnFocus = false

        val upperVal: Double? = evaluateExpression(upperView.text.toString())
        val lowerVal: Double? = evaluateExpression(lowerView.text.toString())
        if (upperVal == null && lowerVal == null) {
            return
        }


        if (upperView.isFocused && upperVal != null) {
            lowerView.setText(String.format("%.2f", upperVal / state.exchangeRate()))
        } else if (lowerView.isFocused && lowerVal != null) {
            upperView.setText(String.format("%.2f", lowerVal * state.exchangeRate()))
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

    private fun evaluateExpression(expression: String): Double? {
        return try {
            val exp = ExpressionBuilder(expression).build()
            exp.evaluate()
        } catch (e: Exception) {
            return null
        }
    }


    private fun getFocusedEditText(): EditText? {
        val lowerEditText: EditText = findViewById(R.id.editTextLower)
        val upperEditText: EditText = findViewById(R.id.editTextUpper)
        if (lowerEditText.hasFocus()) {
            return lowerEditText
        } else if (upperEditText.hasFocus()) {
            return upperEditText
        }
        return null
    }


    private fun press(button: Button) {
        val focusedEditText: EditText = getFocusedEditText() ?: return
        val buttonKeyMap: HashMap<String, Int> = hashMapOf(
            "del" to KeyEvent.KEYCODE_DEL,
            "(" to KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN,
            ")" to KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN,
            "÷" to KeyEvent.KEYCODE_SLASH,
            "×" to KeyEvent.KEYCODE_NUMPAD_MULTIPLY,
            "-" to KeyEvent.KEYCODE_MINUS,
            "+" to KeyEvent.KEYCODE_PLUS,
            "." to KeyEvent.KEYCODE_PERIOD,
            "0" to KeyEvent.KEYCODE_0,
            "1" to KeyEvent.KEYCODE_1,
            "2" to KeyEvent.KEYCODE_2,
            "3" to KeyEvent.KEYCODE_3,
            "4" to KeyEvent.KEYCODE_4,
            "5" to KeyEvent.KEYCODE_5,
            "6" to KeyEvent.KEYCODE_6,
            "7" to KeyEvent.KEYCODE_7,
            "8" to KeyEvent.KEYCODE_8,
            "9" to KeyEvent.KEYCODE_9
        )


        when (button.text.toString()) {
            "ans" -> {
                focusedEditText.text.append(state.calculatedResult.toString())
                return
            }

            "=" -> {
                val result = evaluateExpression(focusedEditText.text.toString())
                if (result != null) {
                    state.calculatedResult = result
                    focusedEditText.setText("$result")
                    val textLength = focusedEditText.text.length
                    focusedEditText.setSelection(textLength)
                }
                return
            }
        }
        if (focusedEditText.text.isNotEmpty()) {
            if ((focusedEditText.text.last() == '/' && button.text[0] == '÷') || (focusedEditText.text.last() == '*' && button.text[0] == '×') || (focusedEditText.text.last() == '+' && button.text[0] == '+')) {
                return
            }
        }
        simulateKeyPress(focusedEditText, buttonKeyMap[button.text.toString()]!!)
    }

    private fun simulateKeyPress(editText: EditText, keyCode: Int) {
        editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
