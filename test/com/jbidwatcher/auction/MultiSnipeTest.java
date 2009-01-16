package com.jbidwatcher.auction;

import junit.framework.TestCase;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;
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

    public MockSnipeable(Integer msId, long hours_out) {
      mGroup = MultiSnipe.find(msId);
      mGroup.add(this);
      mEndDate = new Date(System.currentTimeMillis() + hours_out * 60L * 60L * 1000L);
    }

    public void win() {
      mGroup.setWonAuction();
    }

    public void setSnipeTime(long newSnipeTime) {
      mSnipeTime = newSnipeTime;
    }

    public void cancelSnipe(boolean after_end) {
      mCancelled = true;
      mGroup.remove(this);
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

    public MultiSnipe getGroup() {
      return mGroup;
    }
  }

  public void testWinAuction() throws Exception {
    MultiSnipe ms = new MultiSnipe("a0c0c0", Currency.getCurrency("$1.23"), 75, false);
    MockSnipeable toWin = new MockSnipeable(ms.getId(), 1);
    MockSnipeable[] entries = new MockSnipeable[10];
    entries[0] = toWin;

    for(int i=2; i <= 10; i++) {
      entries[i-1] = new MockSnipeable(ms.getId(), i);
    }
    assertEquals("There should be 10 active entries in the multisnipe after the setup", 10, toWin.getGroup().activeEntries());
    toWin.win();
    for (MockSnipeable entry : entries) {
      assertTrue("Each entry should have been cancelled but " + entry + " wasn't", entry.isCancelled());
      assertEquals("Each entry's multisnipe should have 0 active entries", 0, entry.getGroup().activeEntries());
    }
  }
}
