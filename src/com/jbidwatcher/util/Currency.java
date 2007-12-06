package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.text.NumberFormat;
import java.util.Locale;

public class Currency implements Comparable {
  private static NumberFormat df = NumberFormat.getNumberInstance(Locale.US); // We create a lot of these, so minimizing memory usage is good.
  public static final int NONE=0, US_DOLLAR=1, UK_POUND=2, JP_YEN=3, GER_MARK=4, FR_FRANC=5, CAN_DOLLAR=6;
  public static final int EURO=7, AU_DOLLAR=8, CH_FRANC=9, NT_DOLLAR=10, TW_DOLLAR=10, HK_DOLLAR=11, MY_REAL=12;
  private static Currency _noValue = null;

  /** 
   * @brief This provides a concept of a currency value that is
   * invalid, not just 'zero' in some arbitrary currency.
   * 
   * @return A single, consistent, 'Empty Value', which indicates an
   * invalid currency.
   */
  public static Currency NoValue() {
    if(_noValue == null) _noValue = new Currency(NONE, 0.0);

    return _noValue;
  }

  private int _whatCurrency;
  private double _value;
  private static final char pound = '\u00A3';
  private static final Character objPound = new Character('\u00A3');

  public static Currency convertToUSD(Currency usd, Currency nonusd, Currency cvt) {
    if(cvt != null && !cvt.isNull() && cvt.getCurrencyType() != US_DOLLAR) {
      double multiple = usd.getValue() / nonusd.getValue();
      return getCurrency(US_DOLLAR, multiple*cvt.getValue());
    }

    return cvt;
  }

  /*!@class CurrencyTypeException
   *
   * @brief A class to yell about currency type comparison exceptions.
   *
   * This is used when comparing two currencies of disparate monies.
   */
  public class CurrencyTypeException extends Exception {
    String _associatedString;

    public CurrencyTypeException(String inString) {
      _associatedString = inString;
    }
    public String toString() {
      return _associatedString;
    }
  }

  private static final Integer CurDollar = new Integer(US_DOLLAR);  //  American Dollar
  private static final Integer CurPound = new Integer(UK_POUND);    //  British Pound
  private static final Integer CurYen = new Integer(JP_YEN);        //  Japanese Yen
  private static final Integer CurMark = new Integer(GER_MARK);     //  German Mark
  private static final Integer CurFranc = new Integer(FR_FRANC);    //  French Franc
  private static final Integer CurSwiss = new Integer(CH_FRANC);    //  Swiss Franc
  private static final Integer CurCan = new Integer(CAN_DOLLAR);    //  Canadian Dollar
  private static final Integer CurEuro = new Integer(EURO);         //  Euro
  private static final Integer CurAu = new Integer(AU_DOLLAR);      //  Australian Dollar
  private static final Integer CurTaiwan = new Integer(NT_DOLLAR);  //  New Taiwanese Dollar
  private static final Integer CurHK = new Integer(HK_DOLLAR);      //  Hong Kong Dollar
  private static final Integer CurMyr = new Integer(MY_REAL);       //  Malaysia Real(?)

  //  The fundamental list of the textual representation for different
  //  currencies, and the Currency type it translates to.
  private static final Object xlateTable[][] = {
    { "USD",    CurDollar },
    { "US $",   CurDollar },
    { "AU $",   CurAu },
    { "au$",    CurAu },
    { "AU",     CurAu },
    { "AUD",    CurAu },
    { "US",     CurDollar },
    { "USD $",  CurDollar },
    { "$",      CurDollar },
    { "C",      CurCan },
    { "C $",    CurCan },
    { "CAD",    CurCan },
    { "c$",     CurCan },
    { "GBP",    CurPound },
    { objPound.toString(), CurPound },
    { "pound", CurPound },
    { "\u00A3", CurPound },
    { "&pound", CurPound },
    { "Y",      CurYen },
    { "JPY",    CurYen },
    { "&yen",   CurYen },
    { "\u00A5", CurYen },
    { "DM",     CurMark },
    { "FRF",    CurFranc },
    { "fr",     CurFranc },
    { "CHF",    CurSwiss },
    { "chf",    CurSwiss },
    { "dm",     CurMark },
    { "\u20AC", CurEuro },
    { "eur",    CurEuro },
    { "EUR",    CurEuro },
    { "Eur",    CurEuro },
    { "NT$",    CurTaiwan },
    { "nt$",    CurTaiwan },
    { "NTD",    CurTaiwan },
    { "HK$",    CurHK },
    { "hk$",    CurHK },
    { "HKD",    CurHK },
    { "MYR",    CurMyr },
    { "myr",    CurHK }
  };

  /** 
   * @brief Convert from a string containing a recognized symbol into
   * a currency type.
   * 
   * @param symbol - The string representation of a currency.
   * 
   * @return - The integer value associated with the provided
   * currency, or NONE for unrecognized currencies.
   */
  private int xlateSymbolToType(String symbol) {
    for(int i=0; i<xlateTable.length; i++) {
      if(symbol.equals(xlateTable[i][0])) {
        Integer currency_value = (Integer)xlateTable[i][1];
        return currency_value.intValue();
      }
    }

    return NONE;
  }

  private boolean isDigit(char ch) {
    return(ch>='0' && ch<='9');
  }

  public static Currency getCurrency(String wholeValue) {
    if(wholeValue == null || wholeValue.equals("") || wholeValue.startsWith("UNK")) return NoValue();

    return new Currency(wholeValue);
  }

  public static Currency getCurrency(int whatType, double startValue) {
    if(whatType == NONE) return NoValue();

    return new Currency(whatType, startValue);
  }

  public static Currency getCurrency(String symbol, double startValue) {
    if(symbol == null || symbol.equalsIgnoreCase("UNK")) return NoValue();

    return new Currency(symbol, startValue);
  }

  public static Currency getCurrency(String symbol, String startValue) {
    if(symbol == null || symbol.equalsIgnoreCase("UNK")) return NoValue();

    return new Currency(symbol, startValue);
  }

  public Currency(String wholeValue) {
    setValues(wholeValue);
  }

  public Currency(int whatType, double startValue) {
    setValues(whatType, startValue);
  }

  public Currency(String symbol, double startValue) {
    setValues(symbol, startValue);
  }

  public Currency(String symbol, String startValue) {
    setValues(symbol, Double.parseDouble(startValue));
  }

  private int checkLengthMatchStart(String value, String currencyName) {
    String lowVal = value.toLowerCase();
    String curNam = currencyName.toLowerCase();
    if(lowVal.startsWith(curNam + " ")) {
      return currencyName.length()+1;
    }
    if(lowVal.startsWith(curNam)) {
      int len = currencyName.length();
      while(len < value.length() && !Character.isDigit(value.charAt(len))) len++;
      return len;
    }

    return 0;
  }

  /** 
   * @brief Provided an entire string containing a currency prefix and
   * an amount, extract the two and set this object's value to equal
   * the result.
   *
   * Is there a reason this doesn't use xlateSymbolToType?
   * BUGBUG -- mrs: 03-January-2003 01:28
   * 
   * @param wholeValue - The string containing an entire currency+amount text.
   */
  private void setValues(String wholeValue) {
    if(wholeValue == null || wholeValue.equals("null")) {
      setValues(Currency.NONE, 0.0);
    } else {
      String parseCurrency, valuePortion;
      double actualValue;
      int euLen, gbpLen, frfLen, cdnLen, chfLen, ntdLen, audLen, usdLen;
      char firstChar;

      firstChar = wholeValue.charAt(0);

      euLen = checkLengthMatchStart(wholeValue, "EUR");
      gbpLen = checkLengthMatchStart(wholeValue, "GBP");
      frfLen = checkLengthMatchStart(wholeValue, "FRF");
      chfLen = checkLengthMatchStart(wholeValue, "CHF");
      cdnLen = checkLengthMatchStart(wholeValue, "CAD");
      ntdLen = checkLengthMatchStart(wholeValue, "NTD");
      audLen = checkLengthMatchStart(wholeValue, "AUD");
      usdLen = checkLengthMatchStart(wholeValue, "USD");

      if(wholeValue.startsWith("US $")) {
        parseCurrency = "US $";
        valuePortion = wholeValue.substring(4);
      } else if(wholeValue.startsWith("USD $")) {
        //  In case eBay ever corrects to the RIGHT currency code for USD.
        parseCurrency = "USD $";
        valuePortion = wholeValue.substring(5);
      } else if(wholeValue.startsWith("AU $")) {
        parseCurrency = "AU $";
        valuePortion = wholeValue.substring(4);
      } else if(usdLen != 0) {
        parseCurrency = "USD";
        valuePortion = wholeValue.substring(usdLen);
      } else if(euLen != 0) {
        parseCurrency = "EUR";
        valuePortion = wholeValue.substring(euLen);
      } else if(gbpLen != 0) {
        parseCurrency = "GBP";
        valuePortion = wholeValue.substring(gbpLen);
      } else if(frfLen != 0) {
        parseCurrency = "FRF";
        valuePortion = wholeValue.substring(frfLen);
      } else if(chfLen != 0) {
        parseCurrency = "CHF";
        valuePortion = wholeValue.substring(chfLen);
      } else if(cdnLen != 0) {
        parseCurrency = "CAD";
        valuePortion = wholeValue.substring(cdnLen);
      } else if(ntdLen != 0) {
        parseCurrency = "NTD";
        valuePortion = wholeValue.substring(ntdLen);
      } else if(audLen != 0) {
        parseCurrency = "AUD";
        valuePortion = wholeValue.substring(audLen);
      } else if(wholeValue.startsWith("NT$")) {
        parseCurrency = "NTD";
        valuePortion = wholeValue.substring(3);
      } else if(wholeValue.startsWith("nt$")) {
        parseCurrency = "NTD";
        valuePortion = wholeValue.substring(3);
      } else if(wholeValue.startsWith("au$")) {
        parseCurrency = "AUD";
        valuePortion = wholeValue.substring(3);
      } else if(wholeValue.startsWith("C $")) {
        parseCurrency = "C $";
        valuePortion = wholeValue.substring(3);
      } else if(wholeValue.charAt(0) == pound) {
        parseCurrency = "GBP";
        valuePortion = wholeValue.substring(1);
      } else {
        if(!isDigit(firstChar) && firstChar != '$') {
          int semiIndex = wholeValue.indexOf(";");
          if(semiIndex == -1) {
            semiIndex = wholeValue.indexOf(" ");
          }
          if(semiIndex != -1) {
            parseCurrency = wholeValue.substring(0, semiIndex);
            valuePortion = wholeValue.substring(parseCurrency.length()+1);
          } else {
            parseCurrency = "$";
            valuePortion = wholeValue;
          }
        } else {
          parseCurrency = "$";
          if(isDigit(firstChar)) {
            valuePortion = wholeValue;
          } else {
            valuePortion = wholeValue.substring(1);
          }
        }
      }

      //  Kill off non-digit characters.
      while(!valuePortion.equals("") && !Character.isDigit(valuePortion.charAt(0))) valuePortion = valuePortion.substring(1);

      //  If anything's left, try and parse it.
      if(!valuePortion.equals("")) {
        try {
          actualValue = df.parse(valuePortion).doubleValue();
        } catch(java.text.ParseException e) {
          ErrorManagement.handleException("currency parse!", e);
          actualValue = 0.0;
        }

        setValues(parseCurrency, actualValue);
      } else {
        setValues(null);
      }
    }
  }

  /** 
   * @brief If it's set as two seperate entries, then we use the MUCH
   * cleaner xlateSymbolToType function.
   *
   * This should be the basic method that setValues works also.
   * 
   * @param symbol - The string form of a currency symbol.
   * @param startValue - The amount associated with the currency.
   */
  private void setValues(String symbol, double startValue) {
    setValues(xlateSymbolToType(symbol), startValue);
  }

  /** 
   * @brief The underlying setter that assigns the currency and amounts.
   * 
   * @param whatType - The Currency type to set to.
   * @param startValue - The amount represented.
   */
  private void setValues(int whatType, double startValue) {
    _whatCurrency = whatType;
    _value = startValue;
    df.setMinimumFractionDigits(2);
    df.setMaximumFractionDigits(2);
  }

  /** 
   * @brief Get the full, storable textual name for the currency type
   * of this object.
   * 
   * @return A string containing a full ISO currency name.
   */
  public String fullCurrencyName() {
    switch(_whatCurrency) {
      case US_DOLLAR: return("USD");
      case AU_DOLLAR: return("AUD");
      case NT_DOLLAR: return("NTD");
      case HK_DOLLAR: return("HKD");
      case MY_REAL: return("MYR");
      case UK_POUND: return("GBP");
      case JP_YEN: return("JPY");
      case GER_MARK: return("DM");
      case FR_FRANC: return("FRF");
      case CH_FRANC: return("CHF");
      case CAN_DOLLAR: return("CAD");
      case EURO: return("EUR");
      default: return("UNK");
    }
  }

  public double getValue() { return _value; }

  public String fullCurrency() {
    return fullCurrencyName() + " " + getValueString();
  }

  /**
   * @brief Add two currencies and return a new currency containing
   * the result of the two added together.
   * 
   * @param addValue - The currency value/amount to add.  It must be
   * of the same currency type as 'this'.
   * 
   * @return A new currency object containing the sum of the two
   *         amounts provided, with the same currency type as them.
   *
   * @throws CurrencyTypeException if the two objects are of different currencies.
   */
  public Currency add(Currency addValue) throws CurrencyTypeException {
    if(addValue == null) throw new CurrencyTypeException("Cannot add null Currency.");

    if(addValue.getCurrencyType() == _whatCurrency) {
      return new Currency(_whatCurrency, _value + addValue.getValue());
    }

    //  If only one currency is known, return the result as the known currency.
    //  TODO mrs -- validate that this is a good idea, and all the places that use this are safely going to do the right thing. 
    if (_whatCurrency == NONE) return new Currency(addValue.getCurrencyType(), _value + addValue.getValue());
    if (addValue.getCurrencyType() == NONE) return new Currency(_whatCurrency, _value + addValue.getValue());

    throw new CurrencyTypeException("Cannot add " + fullCurrencyName() + " to " + addValue.fullCurrencyName() + ".");
  }

  /**
   * @brief Subtract two currencies and return a new currency containing
   * the result of the passed value subtracted from this objects value.
   *
   * @param subValue - The currency value/amount to subtract.  It must be
   * of the same currency type as 'this'.
   *
   * @return A new currency object containing the difference of the two
   *         amounts provided, with the same currency type as them.
   *
   * @throws CurrencyTypeException if the two objects are of different currencies.
   */
  public Currency subtract(Currency subValue) throws CurrencyTypeException {
    if(subValue == null) throw new CurrencyTypeException("Cannot subtract null Currency.");

    if(subValue.getCurrencyType() == _whatCurrency) {
      return new Currency(_whatCurrency, _value - subValue.getValue());
    }

    //  If only one currency is known, return the result as the known currency.
    if(_whatCurrency == NONE) return new Currency(subValue.getCurrencyType(), _value - subValue.getValue());
    if(subValue.getCurrencyType() == NONE) return new Currency(_whatCurrency, _value - subValue.getValue());

    throw new CurrencyTypeException("Cannot subtract " + fullCurrencyName() + " from " + subValue.fullCurrencyName() + ".");
  }

  public int getCurrencyType() { return _whatCurrency; }

  public String getCurrencySymbol() {
    switch(_whatCurrency) {
      case US_DOLLAR: return("$");
      case NT_DOLLAR: return("nt$");
      case HK_DOLLAR: return("hk$");
      case MY_REAL: return("myr");
      case UK_POUND: return(objPound.toString());
      case JP_YEN: return("\u00A5"); //  HACKHACK
      case FR_FRANC: return("fr");
      case CH_FRANC: return("chf");
      case GER_MARK: return("dm");
      case CAN_DOLLAR: return("c$");
      case AU_DOLLAR: return("au$");
      case EURO: return("\u20AC");
      default: return("unk");
    }
  }

  /** 
   * @brief Format the currency and amount as appropriate for the
   * current locale.
   *
   * This is kind of interesting, because it will display in one
   * fashion, but when it snipes or bids, it's all against the
   * US sites, so it's all operating in US forms at that point.
   * 
   * @return A nicely formatted, locale-correct money value, prefixed
   * with the best currency symbol for the currency type.
   */
  public String toString() {
    if(isNull()) {
      return("null");
    } else {
      String cvtToString = getCurrencySymbol();

      cvtToString += df.format(_value);

      return(cvtToString);
    }
  }

  /** 
   * @brief Format the amount as appropriate for the current locale.
   *
   * This is kind of interesting, because it will display in one
   * fashion, but when it snipes or bids, it's all against the
   * US sites, so it's all operating in US forms at that point.
   * 
   * @return A nicely formatted, locale-correct money value, prefixed
   * with the best currency symbol for the currency type.
   */
  public String getValueString() {
    if(isNull()) {
      return("null");
    } else {
      return df.format(_value);
    }
  }

  /** 
   * @brief Implementing equals means I should implement hashCode().
   * 
   * @return - The hash code of the string consisting of the full
   * currency named followed by the value as a string.  Null/invalid
   * currency entries return 0.
   */
  public int hashCode() {
    if(isNull()) return 0;

    String tmp = fullCurrencyName() + getValueString();
    return tmp.hashCode();
  }

  /** 
   * @brief Must be able to compare currency values for equality.
   * 
   * @param inValue - The value to compare against.
   * 
   * @return True if the two values are the same, or the currency and
   * amount are the same.  False otherwise, including false if it is
   * an entirely different class.  Differing currencies are always
   * unequal.
   */
  public boolean equals(Object inValue) {
    boolean sameCurrency, sameValue;
    Currency otherValue;

    //  Be careful not to compare with null.
    if(inValue == null) return false;
    //  Shortcut for this.equals(this)
    if(inValue == this) return true;
    //  Is it this class even?
    if(!(inValue instanceof Currency)) return false;
    //  Okay, now cast it because it's safe.
    otherValue = (Currency)inValue;

    sameCurrency = (otherValue.getCurrencyType() == _whatCurrency);
    sameValue = ((int)(otherValue.getValue()*1000)) == ((int)(_value*1000));

    return(sameCurrency && sameValue);
  }

  /** 
   * @brief Determine if (this < otherValue).
   *
   * This only works for items of the same currency type.
   * 
   * @param otherValue - The value to compare against.
   * 
   * @return - True if this amount is less than the otherValue amount
   * and both currency types are equal.  If the otherValue is null,
   * the same object as this (this.less(this)), or this amount is
   * actually less, then it returns false.
   *
   * @throws CurrencyTypeException if you try to compare different currencies.
   */
  public boolean less(Currency otherValue) throws CurrencyTypeException {
    boolean sameCurrency, lowerValue;

    //  Be careful
    if(otherValue == null) return false;
    //  Shortcut
    if(otherValue == this) return false;

    sameCurrency = (otherValue.getCurrencyType() == _whatCurrency);
    if(!sameCurrency) {
      throw new CurrencyTypeException("Cannot compare different currencies.");
    }

    lowerValue = Double.compare( (double)((int)(otherValue.getValue()*1000)), (double)(int)(_value*1000)) == 1;

    return(lowerValue);
  }

  /** 
   * @brief Utility function to check if this is a purely invalid currency.
   *
   * It should probably check against the invalid currency object first...
   * 
   * @return True if this is a 'null currency' object.
   */
  public boolean isNull() {
    return(_value == 0.0 && _whatCurrency == NONE);
  }

  /** 
   * @brief The comparable interface defines this, and so I'm
   * comparing using the well defined set of rules for Comparables.
   *
   * Defined with 'equals' and less', but both should be special cases
   * of this, since some checks are duplicated.
   * 
   * @param o - The object to compare against.
   * 
   * @return -1 if o's class is Currency, it's the same currency type,
   *         and the amount of this is less than o's amount.
   *          0 if o's class is Currency, it's the same currency type,
   *         and the amount of this is the same as o's amount.
   *          1 if o's class is Currency, it's the same currency type,
   *         and the amount of this is greater than o's amount.
   *
   * @throws ClassCastException if you try to compareTo non-Currency classes.
   */
  public int compareTo(Object o) {
    Currency otherValue;

    //  We are always greater than null
    if(o == null) return 1;
    //  We are always equal to ourselves
    if(o == this) return 0;
    //  This is an incorrect usage and should be caught.
    if(!(o instanceof Currency)) throw new ClassCastException("Currency cannot compareTo different classes!");

    //  Okay, now cast it because it's safe.
    otherValue = (Currency)o;

    if(otherValue.isNull()) return 1;
    if(isNull()) return -1;
    try {
      if(less(otherValue)) return -1;
    } catch(ClassCastException e) {
      /* This should be impossible */
      throw new ClassCastException("Currency cannot compareTo different classes!\n" + e);
    } catch (CurrencyTypeException e) {
        //  Can't re-throw (or not catch!) because Object.compareTo doesn't throw CurrencyTypeException!
        throw new ClassCastException("Currency cannot compareTo different currencies!\n" + e);
    }
    if(equals(otherValue)) return 0;
    return 1;
  }
}
