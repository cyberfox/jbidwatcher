package com.jbidwatcher.ui;

import com.jbidwatcher.auction.Auctions;

import java.awt.Color;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Jun 30, 2008
* Time: 1:32:50 AM
*
* A simple class to hold an auctions list and the UI model that goes along with it.
*/
class AuctionListHolder {
  private Auctions mAuctionList;
  private AuctionsUIModel mAuctionUI;
  private boolean mDeletable = true;
  private static JBidContext sFrameContext;
  private static JBidContext sTableContext;
  private static ActionListener sCornerButtonListener;

  public boolean isDeletable() {
    return mDeletable;
  }

  void setBackground(Color presetBackground) {
    mAuctionUI.setBackground(presetBackground);
  }

  AuctionListHolder(String name, boolean _completed, boolean deletable) {
    mAuctionList = new Auctions(name);
    if(_completed) mAuctionList.setComplete();
    mAuctionUI = new AuctionsUIModel(mAuctionList, sTableContext, sFrameContext, sCornerButtonListener);
    mDeletable = deletable;
    JTabManager.getInstance().add(name, mAuctionUI.getPanel(), mAuctionUI.getTableSorter());
  }

  public Auctions getList() { return mAuctionList; }
  public AuctionsUIModel getUI() { return mAuctionUI; }

  public static void setFrameContext(JBidContext frameContext) {
    sFrameContext = frameContext;
  }

  public static void setTableContext(JBidContext tableContext) {
    sTableContext = tableContext;
  }

  public static void setCornerButtonListener(ActionListener listener) {
    sCornerButtonListener = listener;
  }
}
