package com.jbidwatcher.util;

import com.jbidwatcher.TimerHandler;
import com.jbidwatcher.Constants;
import com.jbidwatcher.auction.server.AuctionServer;
import com.jbidwatcher.queue.AuctionQObject;
import com.jbidwatcher.queue.TimeQueueManager;
import com.jbidwatcher.config.JConfig;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Feb 4, 2008
 * Time: 1:00:19 AM
 *
 * A queue of events to be placed on other queues, at timed intervals or immediately.
 *
 * The backbone of the message/event based architecture of JBidwatcher.
 */
public class SuperQueue {
  private static final int ONE_SECOND = Constants.ONE_SECOND;
  private TimeQueueManager mTQM = new TimeQueueManager();
  private static SuperQueue mInstance = null;

  public static SuperQueue getInstance() {
    if(mInstance == null) mInstance = new SuperQueue();

    return mInstance;
  }

  public TimeQueueManager getQueue() { return mTQM; }

  public TimerHandler establishSuperQueue() {
    long now = System.currentTimeMillis();

    if(JConfig.queryConfiguration("updates.enabled", "true").equals("true")) {
      mTQM.add("CHECK", "update", now + (ONE_SECOND * 10));
    }
    //noinspection MultiplyOrDivideByPowerOfTwo
    if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
      mTQM.add("TIMECHECK", "auction_manager", now + (ONE_SECOND * 2), Constants.THIRTY_MINUTES);
    }
    mTQM.add(new AuctionQObject(AuctionQObject.MENU_CMD, AuctionServer.UPDATE_LOGIN_COOKIE, null), "ebay", now + ONE_SECOND*3, 240 * Constants.ONE_MINUTE);
    mTQM.add("ALLOW_UPDATES", "Swing", now + (ONE_SECOND * 2 * 10));
    mTQM.add("FLUSH", "dbflush", now+Constants.ONE_MINUTE, ONE_SECOND*15);

    //  Other interesting examples...
    //mTQM.add("This is a message for the display!", "Swing", System.currentTimeMillis()+Constants.ONE_MINUTE);
    //mTQM.add(JBidMouse.ADD_AUCTION + "5582606163", "user", System.currentTimeMillis() + (Constants.ONE_MINUTE / 2));
    //mTQM.add("http://www.jbidwatcher.com", "browse", System.currentTimeMillis() + (Constants.ONE_MINUTE / 4));
    //mTQM.add(new AuctionQObject(AuctionQObject.BID, new AuctionBid("5582606251", Currency.getCurrency("2.99"), 1), "none"), "ebay", System.currentTimeMillis() + (Constants.ONE_MINUTE*2) );

    TimerHandler timeQueue = new TimerHandler(mTQM);
    timeQueue.setName("SuperQueue");
    timeQueue.start();

    return timeQueue;
  }
}
