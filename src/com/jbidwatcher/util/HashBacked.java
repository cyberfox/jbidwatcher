package com.jbidwatcher.util;

import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.XMLSerializeSimple;
import com.jbidwatcher.util.db.DBRecord;

import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Sep 30, 2007
 * Time: 1:54:43 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class HashBacked extends XMLSerializeSimple {
  private static final DBRecord EMPTY = new DBRecord();
  private DBRecord mBacking = EMPTY;
  private SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private Map<String, String> mTranslationTable;
  private String mDefaultCurrency;

  public HashBacked() {
    mDateFormat.setTimeZone(TimeZone.getDefault());
    mBacking = new DBRecord();
    mDefaultCurrency = Currency.getCurrency("$1.00").fullCurrencyName();
  }

  public void setTranslationTable(Map<String, String> table) { mTranslationTable = table; }

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

  public void setDefaultCurrency(Currency sample) {
    mDefaultCurrency = sample.fullCurrencyName();
  }

  public Currency getMonetary(String key) {
    String result = get(key);
    try {
      double value = Double.parseDouble(result);
      return Currency.getCurrency(mDefaultCurrency, value);
    } catch (Exception e) {
      return Currency.NoValue();
    }
  }

  public void setMonetary(String key, Currency c) {
    if (c.isNull())
      set(key, null);
    else {
      //  Only set the default currency to some non-USD currency if a non-USD currency is being passed in.
      if(!c.fullCurrencyName().equals(mDefaultCurrency) && c.getCurrencyType() != Currency.US_DOLLAR) setDefaultCurrency(c);
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
    if (s_value == null)
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

  public String get(String key) {
    if (mTranslationTable != null && mTranslationTable.containsKey(key)) {
      key = mTranslationTable.get(key);
    }

    return mBacking.get(key);
  }

  public void set(String key, String value) {
    if (mTranslationTable != null && mTranslationTable.containsKey(key)) {
      key = mTranslationTable.get(key);
    }
    mBacking.put(key, value);
  }

  protected XMLElement addCurrencyChild(XMLElement parent, String name) {
    Currency value = getMonetary(name);
    XMLElement xadd = null;
    if (value != null && !value.isNull()) {
      xadd = new XMLElement(name);
      xadd.setProperty("currency", value.fullCurrencyName());
      xadd.setProperty("price", Double.toString(value.getValue()));
      xadd.setEmpty();
      parent.addChild(xadd);
    }

    return xadd;
  }

  protected XMLElement addStringChild(XMLElement parent, String name) {
    String value = getString(name);
    XMLElement xadd = null;
    if (value != null && value.length() != 0) {
      xadd = new XMLElement(name);
      xadd.setContents(getString(name));
      parent.addChild(xadd);
    }

    return xadd;
  }

  protected XMLElement addBooleanChild(XMLElement parent, String name) {
    boolean value = getBoolean(name);
    XMLElement xadd = null;
    if (value) {
      xadd = new XMLElement(name);
      xadd.setEmpty();
      parent.addChild(xadd);
    }

    return xadd;
  }

  protected DBRecord getBacking() { return mBacking; }
}
