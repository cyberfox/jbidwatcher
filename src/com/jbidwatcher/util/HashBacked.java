package com.jbidwatcher.util;

import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

/**
 * Hash map acting as the backing store for the table information.
 *
 * User: Morgan
 * Date: Sep 30, 2007
 * Time: 1:54:43 PM
 */
public class HashBacked {
  private static final Record EMPTY = new Record();
  private Record mBacking = EMPTY;
  public static String DB_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
  private SimpleDateFormat mDateFormat = new SimpleDateFormat(DB_DATE_FORMAT);
  private Map<String, String> mTranslationTable;
  private String mDefaultCurrency;
  private boolean mDirty = false;
  private static final Currency ONE_DOLLAR = Currency.getCurrency("$1.00");

  public HashBacked() {
    this(new Record());
  }

  public HashBacked(Record data) {
    mDateFormat.setTimeZone(TimeZone.getDefault());
    mBacking = data;
    if(data.get("currency") == null) mDefaultCurrency = ONE_DOLLAR.fullCurrencyName();
    else mDefaultCurrency = get("currency");
  }

  public void setTranslationTable(Map<String, String> table) { if(mTranslationTable == null) mTranslationTable = table; }

  public boolean isDirty() { return mDirty; }
  protected void clearDirty() { mDirty = false; }
  protected void setDirty() { mDirty = true; }

  public Currency getMonetary(String key, int currencyType) {
    String result = get(key);
    try {
      double value = Double.parseDouble(result);
      return Currency.getCurrency(currencyType, value);
    } catch (Exception e) {
      return Currency.NoValue();
    }
  }

  public Currency getMonetary(String key, Currency fallback) {
    Currency rval = getMonetary(key);
    if (rval == null || rval.isNull())
      return fallback;
    else
      return rval;
  }

  protected void setDefaultCurrency(Currency sample) {
    mDefaultCurrency = sample.fullCurrencyName();
  }

  public Currency getDefaultCurrency() {
    return Currency.getCurrency(mDefaultCurrency, 1.0);
  }

  public Currency getMonetary(String key) {
    String result = get(key);
    if(result == null) return Currency.NoValue();
    try {
      double value = Double.parseDouble(result);
      return Currency.getCurrency(mDefaultCurrency, value);
    } catch (Exception e) {
      return Currency.NoValue();
    }
  }

  public void setMonetary(String key, Currency c) {
    setMonetary(key, c, true);
  }

  public void setMonetary(String key, Currency c, boolean updateDefault) {
    if (c == null || c.isNull())
      set(key, null);
    else {
      //  Only set the default currency to some non-USD currency if a non-USD currency is being passed in.
      if(updateDefault &&
          !c.fullCurrencyName().equals(mDefaultCurrency) &&
          c.getCurrencyType() != Currency.US_DOLLAR &&
          c.getCurrencyType() != Currency.NONE) setDefaultCurrency(c);
      set(key, Double.toString(c.getValue()));
    }
  }

  public void setBoolean(String key, boolean value) {
    set(key, value ? "1" : "0");
  }

  public void setDate(String key, Date date) {
    if (date == null || date.getTime() < 0) {
      set(key, null);
    } else {
      set(key, mDateFormat.format(date));
    }
  }

  public Date getDate(String key) {
    String s_value = get(key);
    if (s_value == null || s_value.length() == 0) {
      return null;
    } else {
      try {
        return mDateFormat.parse(s_value);
      } catch (Exception e) {
        return null;
      }
    }
  }

  public Integer getInteger(String key, Integer fallback) {
    Integer result = getInteger(key);
    if(result == null) return fallback;
    return result;
  }
  public Integer getInteger(String key) {
    String s_value = get(key);
    if (s_value == null || s_value.length() == 0)
      return null;
    else
      return Integer.parseInt(s_value);
  }

  public void setInteger(String key, Integer value) {
    if (value == null)
      set(key, null);
    else
      set(key, Integer.toString(value));
  }

  public boolean getBoolean(String key) {
    String result = get(key);
    return (result != null && "1".equals(result));
  }

  public boolean getBoolean(String key, boolean fallback) {
    String result = get(key);
    if(result == null) return fallback;
    return "1".equals(result);
  }

  public void setString(String key, String value) {
    set(key, value);
  }

  public String getString(String key) {
    return get(key);
  }

  public String getString(String key, String fallback) {
    String rval = get(key);
    if (rval == null)
      return fallback;
    else
      return rval;
  }

  private Record mSecondary = null;
  private boolean mSecondaryAttempted = false;

  protected void setSecondary(Record r) {
    mSecondary = r;
  }

  protected void loadSecondary() { }

  public String get(String key) {
    if (mTranslationTable != null && mTranslationTable.containsKey(key)) {
      key = mTranslationTable.get(key);
    }

    String result = mBacking.get(key);
    if (result == null && mBacking.get("id") != null) {
      if (mSecondary != null) {
        if (mSecondary.containsKey(key)) {
          result = mSecondary.get(key);
        }
      } else if (!mSecondaryAttempted) {
        mSecondaryAttempted = true;
        loadSecondary();
        if (mSecondary != null) {
          result = mSecondary.get(key);
        }
      }
    }
    return result;
  }

  public void set(String key, String value) {
    if (mTranslationTable != null && mTranslationTable.containsKey(key)) {
      key = mTranslationTable.get(key);
    }
    String prev = mBacking.put(key, value);
    if( (prev == null && value != null) ||
        (prev != null && !prev.equals(value))) setDirty();
  }

  public Record getBacking() { return mBacking; }
  public void setBacking(Record r) {
    mBacking = r;
    if(r.get("currency") == null) mDefaultCurrency = ONE_DOLLAR.fullCurrencyName();
    else mDefaultCurrency = get("currency");    
    clearDirty();
  }

  public String dumpRecord() {
    StringBuffer sb = new StringBuffer("<record>\n");
    for(String key : mBacking.keySet()) {
      sb.append("  <").append(key).append('>').append(mBacking.get(key)).append("</").append(key).append(">\n");
    }
    sb.append("</record>\n");

    return sb.toString();
  }
}
