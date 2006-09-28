package com.jbidwatcher.util;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;

/**
 * Currency Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>09/28/2006</pre>
 */
public class CurrencyTest extends TestCase {
  public CurrencyTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testNulls() throws Exception {
    Currency bad = Currency.getCurrency(null);
    assertSame(bad, Currency.NoValue());
  }

  public void testGetCurrency() throws Exception {
    Currency usd1 = Currency.getCurrency("$1.00");
    Currency usd2 = Currency.getCurrency(Currency.US_DOLLAR, 1.00);
    Currency usd3 = Currency.getCurrency("$", 1.00);
    Currency usd4 = Currency.getCurrency("$", "1.00");
    Currency usd5 = Currency.getCurrency("$1");
    Currency usd6 = Currency.getCurrency("US $", "1");

    assertEquals("The various currency constructions should all be equal.", usd1, usd2);
    assertEquals("The various currency constructions should all be equal.", usd2, usd3);
    assertEquals("The various currency constructions should all be equal.", usd3, usd4);
    assertEquals("The various currency constructions should all be equal.", usd4, usd5);
    assertEquals("The various currency constructions should all be equal.", usd5, usd6);
    assertEquals(Currency.getCurrency("1"), usd1);
  }

  public void testConvertToUSD() throws Exception {
    Currency usd1 = Currency.getCurrency("$1.00");
    Currency cad1 = Currency.getCurrency("c$1.23");
    Currency tst1 = Currency.getCurrency("c$4.92");
    Currency cvt1 = Currency.convertToUSD(usd1, cad1, tst1);
    assertEquals(cvt1, Currency.getCurrency("$4.00"));
  }

  public void testGetValue() throws Exception {
    //TODO: Test goes here...
  }

  public void testGetCurrencyType() throws Exception {
    //TODO: Test goes here...
  }

  public void testCompare() throws Exception {
    Currency usd1 = Currency.getCurrency("$1.00");

    //noinspection ObjectEqualsNull
    assertFalse(usd1.equals(null));
    assertFalse(usd1.equals("$1.00"));
    assertFalse(usd1.isNull());
    assertEquals(usd1.compareTo(Currency.NoValue()), 1);
    assertEquals(Currency.NoValue().compareTo(usd1), -1);
    assertEquals(usd1.compareTo(Currency.getCurrency("$2.34")), -1);
    assertEquals(usd1.compareTo(usd1), 0);
    assertEquals(usd1.compareTo(Currency.getCurrency("US $", "1")), 0);
  }

  public void testGetCurrencySymbol() throws Exception {
    assertEquals(Currency.getCurrency("USD", 1).getCurrencySymbol(), "$");
    assertEquals(Currency.getCurrency("NTD", 1).getCurrencySymbol(), "nt$");
    assertEquals(Currency.getCurrency("GBP", 1).getCurrencySymbol(), "\u00A3");
    assertEquals(Currency.getCurrency("JPY", 1).getCurrencySymbol(), "\u00A5");
    assertEquals(Currency.getCurrency("EUR", 1).getCurrencySymbol(), "\u20AC");
    assertEquals(Currency.getCurrency("CAD", 1).getCurrencySymbol(), "c$");
    assertEquals(Currency.getCurrency("AUD", 1).getCurrencySymbol(), "au$");
  }

  public void testGetFullCurrencyName() throws Exception {
    assertEquals("USD", Currency.getCurrency("$1").fullCurrencyName());
    assertEquals("NTD", Currency.getCurrency("nt$1").fullCurrencyName());
    assertEquals("GBP", Currency.getCurrency("GBP", 1).fullCurrencyName());
    assertEquals("JPY", Currency.getCurrency("JPY", 1).fullCurrencyName());
    assertEquals("EUR", Currency.getCurrency("EUR", 1).fullCurrencyName());
    assertEquals("CAD", Currency.getCurrency("C $1").fullCurrencyName());
    assertEquals("AUD", Currency.getCurrency("au$1").fullCurrencyName());
  }

  public void testGetValueString() throws Exception {
    //TODO: Test goes here...
  }

  public void testString() throws Exception {
    Currency usd1 = Currency.getCurrency("$1.00");
    assertEquals(usd1.toString(), "$1.00");
    assertEquals(Currency.NoValue().toString(), "null");
  }

  public void testFullCurrency() throws Exception {
    Currency usd1 = Currency.getCurrency("$123.45");
    assertEquals(usd1.fullCurrency(), "USD 123.45");
  }

  public void testAdd() throws Exception {
    Currency five_usd = Currency.getCurrency("$5.67");
    Currency four_usd = Currency.getCurrency("US $4.56");
    Currency compare_usd = Currency.getCurrency("USD", 10.23);
    Currency sum_usd = five_usd.add(four_usd);

    assertEquals("The sum should be 10.23!", sum_usd, compare_usd);
    assertEquals("The hashcodes should also be identical.", sum_usd.hashCode(), compare_usd.hashCode());
  }

  public void testSub() throws Exception {
    Currency five_usd = Currency.getCurrency("$5.67");
    Currency four_usd = Currency.getCurrency("US $4.56");
    Currency ten_usd = Currency.getCurrency("USD", 10.23);
    Currency sub_usd = ten_usd.subtract(four_usd);

    assertEquals("The hashcodes should be identical.", sub_usd.hashCode(), five_usd.hashCode());
    assertTrue(sub_usd.compareTo(Currency.getCurrency("$5.67")) == 0);
    assertEquals("The subtraction result should be $5.67!", five_usd, sub_usd);
  }

  public static Test suite() {
    return new TestSuite(CurrencyTest.class);
  }
}
