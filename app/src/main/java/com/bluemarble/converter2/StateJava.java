package com.bluemarble.converter2;

import android.util.Log;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * State singleton
 */
public class StateJava {
    private static final double DEFAULT_RATE = 1.0;
    private static StateJava instance;

    private final Map<String, Double> ratesMap = new HashMap<String, Double>();
    private final Map<String, String> currencySymbolMap = new HashMap<>();
    private AtomicBoolean isLocked = new AtomicBoolean(false); // TODO: use the synchronized keyword isntaed
    private String focusedEditTextView = "upper";
    private String upperSelection = "CZK";
    private String lowerSelection = "EUR";
    private double lastResult = 0.0;


    private StateJava() {
        currencySymbolMap.put("USD", "$");
        currencySymbolMap.put("EUR", "€");
        currencySymbolMap.put("JPY", "¥");
        currencySymbolMap.put("GBP", "£");
        currencySymbolMap.put("AUD", "A$");
        currencySymbolMap.put("CAD", "C$");
        currencySymbolMap.put("CHF", "CHF");
        currencySymbolMap.put("CNY", "¥");
        currencySymbolMap.put("SEK", "kr");
        currencySymbolMap.put("NZD", "NZ$");
        currencySymbolMap.put("MXN", "$");
        currencySymbolMap.put("SGD", "S$");
        currencySymbolMap.put("HKD", "HK$");
        currencySymbolMap.put("NOK", "kr");
        currencySymbolMap.put("KRW", "₩");
        currencySymbolMap.put("TRY", "₺");
        currencySymbolMap.put("INR", "₹");
        currencySymbolMap.put("RUB", "₽");
        currencySymbolMap.put("ZAR", "R");
        currencySymbolMap.put("BRL", "R$");
        currencySymbolMap.put("TWD", "NT$");
        currencySymbolMap.put("PLN", "zł");
        currencySymbolMap.put("THB", "฿");
        currencySymbolMap.put("CZK", "Kč");
        currencySymbolMap.put("DKK", "kr");
        currencySymbolMap.put("HUF", "Ft");
        currencySymbolMap.put("ILS", "₪");
        currencySymbolMap.put("MYR", "RM");
        currencySymbolMap.put("PHP", "₱");
        currencySymbolMap.put("IDR", "Rp");
        currencySymbolMap.put("PKR", "₨");
        currencySymbolMap.put("VND", "₫");
        currencySymbolMap.put("NGN", "₦");
        currencySymbolMap.put("EGP", "E£");
        currencySymbolMap.put("KZT", "₸");
        currencySymbolMap.put("UAH", "₴");
    }

    /**
     * @return Always the same one instance of the class
     */
    public static StateJava getInstance() {
        if (instance == null) {
            instance = new StateJava();
        }
        return instance;
    }

    public void lock() {
        while (isLocked.get()) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Log.e("State", "Thread interrupted exception");
                continue;
            }
        }
        isLocked.set(true);
    }

    public void unlock() {
        isLocked.set(false);
    }

     public void setUpperSelection(String value) {
        if (value == null) return;
        lock();
        if (ratesMap.containsKey(value)) {
            upperSelection = value;
        } else {
            Log.v("Selection", "upperSelection cannot be " + value);
        }
        unlock();
    }

     public String getUpperSelection() {
        return upperSelection;
    }

     public void setLowerSelection(String value) {
        if (value == null) return;
        lock();
        if (ratesMap.containsKey(value)) {
            lowerSelection = value;
        } else {
            Log.v("Selection", "lowerSelection cannot be " + value);
        }
        unlock();
    }

     public String getLowerSelection() {
        return lowerSelection;
    }

     public void setFocusedEditTextView(String view) {// TODO: create enum
        if (!view.equals("upper") && !view.equals("lower")) {
            throw new RuntimeException("Invalid view value");
        }
        focusedEditTextView = view;
    }

     public String getFocusedEditTextView() {
        return focusedEditTextView;
    }

    /**
     * @return A read only view of the ratesMap for iterating
     */
    public Map<String, Double> getRatesMap() {
        return Collections.unmodifiableMap(ratesMap);
    }

     public void setRateFor(String currency, double rate) {
        ratesMap.put(currency, rate);
    }

     public Double getRateFor(String currency) {
        return ratesMap.get(currency);
    }

     public String getSymbolFor(String currency) {
        return currencySymbolMap.getOrDefault(currency, "");
    }

     public void setLastResult(double result) {
        lastResult = result;
    }

     public double getLastResult() {
        return lastResult;
    }


     double calculateExchangeRate() {
        if (upperSelection == null || lowerSelection == null) {
            return DEFAULT_RATE;
        }
        lock();
        Double upperValue = ratesMap.getOrDefault(upperSelection, 1.0);
        Double lowerValue = ratesMap.getOrDefault(lowerSelection, 1.0);
        unlock();
        if (upperValue == null || lowerValue == null) {
            return DEFAULT_RATE;
        }
        return upperValue / lowerValue;
    }
}
