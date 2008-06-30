package com.jbidwatcher.ui;

import com.jbidwatcher.auction.Auctions;

import javax.swing.*;
import java.awt.Color;

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
  private static JButton sCornerButton;

  public boolean isDeletable() {
    return mDeletable;
  }

  public void setDeletable(boolean deletable) {
    mDeletable = deletable;
  }

  AuctionListHolder(String name) {
    this(name, false, false, true);
  }

  AuctionListHolder(String name, Color presetBackground) {
    this(name, false, false, true);
    mAuctionUI.setBackground(presetBackground);
  }

  AuctionListHolder(String name, boolean _completed, boolean _selling, boolean deletable) {
    mAuctionList = new Auctions(name);
    if(_completed) mAuctionList.setComplete();
    if(_selling) mAuctionList.setSelling();
    mAuctionUI = new AuctionsUIModel(mAuctionList, sTableContext, sFrameContext, sCornerButton);
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

  public static void setCornerButton(JButton cornerButton) {
    sCornerButton = cornerButton;
  }
}
