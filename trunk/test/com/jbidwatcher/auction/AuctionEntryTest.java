package com.jbidwatcher.auction;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.framework.TestCase;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;

import java.util.Date;

/**
 * AuctionEntry Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>09/27/2006</pre>
 */
public class AuctionEntryTest extends TestCase {
  private AuctionEntry mAE = null;
  MockAuctionInfo mai;

  public AuctionEntryTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    mai = new MockAuctionInfo();
    mAE = AuctionEntry.buildEntry(mai.getIdentifier());
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testSetGetServer() throws Exception {
    assertEquals("testBay", mAE.getServer().getName());
  }

  public void testSetGetBid() throws Exception {
    assertSame(mAE.getBid(), Currency.NoValue());
    mAE.setBid(Currency.getCurrency("$9.99"));
    assertEquals(Currency.getCurrency("$9.99"), mAE.getBid());
  }

  public void testSetGetBidQuantity() throws Exception {
    assertEquals(1, mAE.getQuantity());
  }

  public void testGetSnipeBid() throws Exception {
    assertEquals(mAE.getSnipe(), Currency.NoValue());
  }

  public void testGetSnipeQuantity() throws Exception {
    assertEquals(1, mAE.getSnipeQuantity());
  }

  public void testSetGetMultiSnipe() throws Exception {
    assertNull(mAE.getMultiSnipe());
  }

  public void testSetGetDefaultSnipeTime() throws Exception {
    //TODO: Test goes here...
  }

  public void testSetGetSnipeTime() throws Exception {
    assertEquals(1000 * 30, mAE.getSnipeTime());
  }

  public void testGetJustAdded() throws Exception {
    assertTrue(mAE.getJustAdded() > System.currentTimeMillis());
  }

  public void testGetIdentifier() throws Exception {
    assertEquals("12345678", mAE.getIdentifier());
  }

  public void testGetNextUpdate() throws Exception {
    assertTrue(mAE.getNextUpdate() > System.currentTimeMillis());
  }

  public void testSetInvalid() throws Exception {
    assertFalse(mAE.isInvalid());
  }

  public void testSetGetComment() throws Exception {
    mAE.setComment("Test Comment");
    assertEquals("Test Comment", mAE.getComment());
  }

  public void testSetGetLastStatus() throws Exception {
    mAE.setLastStatus("Test Status-1");
    assertTrue(mAE.getLastStatus().indexOf("Test Status-1") != -1);
    mAE.setLastStatus("Test Status-2");
    assertTrue(mAE.getLastStatus().indexOf("Test Status-1") != -1 && mAE.getLastStatus().indexOf("Test Status-2") != -1);
  }

  public void testSetGetShipping() throws Exception {
    assertEquals(Currency.getCurrency("$1.99"), mAE.getShipping());
    mAE.setShipping(Currency.getCurrency("$2.99"));
    assertEquals(Currency.getCurrency("$2.99"), mAE.getShipping());
  }

  public void testGetStatusCount() throws Exception {
    mAE.setLastStatus("Test Status-1");
    mAE.setLastStatus("Test Status-2");
    assertEquals(2, mAE.getStatusCount());
  }

  public void testGetCancelledSnipe() throws Exception {
    assertNull(mAE.getCancelledSnipe());
  }

  public void testGetCancelledSnipeQuantity() throws Exception {
    assertEquals(1, mAE.getCancelledSnipeQuantity());
  }

  public void testSetNeedsUpdate() throws Exception {
    MQFactory.getConcrete("redraw").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        assertSame(deQ, mAE);
      }
    });
    assertTrue(mAE.checkUpdate());
    mAE.update();
    assertFalse(mAE.checkUpdate());
  }

  public void testSetGetCategory() throws Exception {
    assertNull(mAE.getCategory());
    mAE.setCategory("Test Category");
    assertEquals("Test Category", mAE.getCategory());
  }

  public void testSetSticky() throws Exception {
    assertFalse(mAE.isSticky());
    mAE.setSticky(true);
    assertTrue(mAE.isSticky());
  }

  public void testGetTimeLeft() throws Exception {
    Thread.sleep(500);
    assertEquals("23h, 59m", mAE.getTimeLeft());
  }

  public void testSetEnded() throws Exception {
    assertFalse(mAE.isComplete());
  }

  private static final int NOT_FIXED_PRICE=1;
  private static final int HIGH_BIDDER=2;
  private static final int WILL_WIN=4;
  private static final int HAS_BIN=8;
  private static final int RESERVE_AND_MET=16;
  private static final int RESERVE_NOT_MET=32;
  private static final int HAS_PAYPAL=64;

  public void testGetFlags() throws Exception {
    //  1 (Not fixed price), 2 (is High Bidder), 4 (Will win), 8 (Has BIN price),
    // 16 (Reserve & Reserve is met), 32 (Reserve & Reserve not met), 64 (Has PayPal)
    assertEquals(HAS_PAYPAL | HAS_BIN | NOT_FIXED_PRICE, mAE.getFlags());
  }

  public void testGetCurBid() throws Exception {
    assertEquals(Currency.getCurrency("$9.99"), mAE.getCurBid());
  }

  public void testGetUSCurBid() throws Exception {
    assertEquals(Currency.getCurrency("$9.99"), mAE.getUSCurBid());
  }

  public void testGetMinBid() throws Exception {
    assertEquals(Currency.getCurrency("$9.99"), mAE.getMinBid());
  }

  public void testGetInsurance() throws Exception {
    assertEquals(Currency.getCurrency("$0.99"), mAE.getInsurance());
  }

  public void testGetInsuranceOptional() throws Exception {
    assertFalse(mAE.getInsuranceOptional());
  }

  public void testGetBuyNow() throws Exception {
    assertEquals(Currency.getCurrency("$19.99"), mAE.getBuyNow());
  }

  public void testGetQuantity() throws Exception {
    assertEquals(1, mAE.getQuantity());
  }

  public void testGetNumBidders() throws Exception {
    assertEquals(1, mAE.getNumBidders());
  }

  public void testGetSeller() throws Exception {
    assertEquals("cyberfox", mAE.getSeller());
  }

  public void testGetHighBidder() throws Exception {
    assertEquals("test-jbidwatcher-bids", mAE.getHighBidder());
  }

  public void testGetHighBidderEmail() throws Exception {
    assertNull(mAE.getHighBidderEmail());
  }

  public void testGetTitle() throws Exception {
    assertEquals("A test auction.", mAE.getTitle());
  }

  public void testGetStartDate() throws Exception {
    assertEquals(mai.getStartDate(), mAE.getStartDate());
  }

  public void testGetEndDate() throws Exception {
    assertEquals(mai.getEndDate(), mAE.getEndDate());
  }

  public void testGetSnipeDate() throws Exception {
    assertEquals(new Date(mai.getEndDate().getTime() - 1000 * 30), mAE.getSnipeDate());
  }

  public void testGetThumbnail() throws Exception {
    assertNull(mAE.getThumbnail());
  }

  public void testGetItemLocation() throws Exception {
    assertEquals("Test County, USA", mAE.getItemLocation());
  }

  public void testGetPositiveFeedbackPercentage() throws Exception {
    assertEquals("99.6%", mAE.getPositiveFeedbackPercentage());
  }

  public void testGetFeedbackScore() throws Exception {
    assertEquals(139, mAE.getFeedbackScore());
  }

  public void testGetShippingWithInsurance() throws Exception {
    assertEquals(Currency.getCurrency("$2.98"), mAE.getShippingWithInsurance());
  }

  public static Test suite() {
    return new TestSuite(AuctionEntryTest.class);
  }
}
