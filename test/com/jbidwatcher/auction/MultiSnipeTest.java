package com.jbidwatcher.auction;

import junit.framework.TestCase;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.JConfig;
import com.cyberfox.util.config.ErrorManagement;
import com.jbidwatcher.Upgrader;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jan 16, 2009
 * Time: 2:43:15 AM
 *
 * Attempt to test the MultiSnipe class.
 */
public class MultiSnipeTest extends TestCase {
  public MultiSnipeTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    JConfig.setLogger(new ErrorManagement());
//    ActiveRecord.disableDatabase();
    JConfig.setConfiguration("db.user", "test1");
    Upgrader.upgrade();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  private static class MockSnipeable implements Snipeable {
    private MultiSnipe mGroup;
    private Date mEndDate;
    private long mSnipeTime = 30L * 1000L;
    private boolean mCancelled;
    private String mIdentifier;

    public MockSnipeable(Integer msId, long hours_out, String ident) {
      mGroup = MultiSnipe.find(msId);
      mGroup.add(getIdentifier());
      mEndDate = new Date(System.currentTimeMillis() + hours_out * 60L * 60L * 1000L);
      mIdentifier = ident;
    }

    public void win() {
      mGroup.setWonAuction();
    }

    public void setSnipeTime(long newSnipeTime) {
      mSnipeTime = newSnipeTime;
    }

    public void cancelSnipe(boolean after_end) {
      mCancelled = true;
      mGroup.remove(getIdentifier());
    }

    public Date getEndDate() {
      return mEndDate;
    }

    public long getSnipeTime() {
      return mSnipeTime;
    }

    public boolean isCancelled() {
      return mCancelled;
    }

    public boolean hasDefaultSnipeTime() {
      return mSnipeTime == 30L * 1000L;
    }

    public MultiSnipe getMultiSnipe() {
      return mGroup;
    }

    public String getIdentifier() {
      return mIdentifier;
    }

    public boolean reload() {
      return true;
    }

    public boolean isComplete() {
      return false;
    }
  }

  public void testWinAuction() throws Exception {
    MultiSnipe ms = new MultiSnipe("a0c0c0", Currency.getCurrency("$1.23"), 75, false);
    MockSnipeable toWin = new MockSnipeable(ms.getId(), 1, "12345");
    MockSnipeable[] entries = new MockSnipeable[10];
    entries[0] = toWin;

    for(int i=2; i <= 10; i++) {
      entries[i-1] = new MockSnipeable(ms.getId(), i, Integer.toString(12344+i));
    }
//    MultiSnipeManager msm = new MultiSnipeManager();
//    ms = MultiSnipeManager.getInstance().getForAuctionIdentifier(toWin.getIdentifier());

    assertEquals("There should be 10 active entries in the multisnipe after the setup", 10, ms.activeEntries());
    toWin.win();
    // Sleep 2 seconds, waiting for the won processing queue to finish.
    Thread.sleep(2000);
    for (MockSnipeable entry : entries) {
      assertTrue("Each entry should have been cancelled but " + entry.getIdentifier() + " wasn't", entry.isCancelled());
//      ms = MultiSnipeManager.getInstance().getForAuctionIdentifier(entry.getIdentifier());
      assertEquals("Each entry's multisnipe should have 0 active entries", 0, ms.activeEntries());
    }
  }
}
