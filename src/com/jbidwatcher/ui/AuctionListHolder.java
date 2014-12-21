package com.jbidwatcher.ui;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.jbidwatcher.auction.Auctions;
import com.jbidwatcher.auction.EntryCorral;
import com.jbidwatcher.auction.MultiSnipeManager;

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
public class AuctionListHolder {
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

  @Inject
  AuctionListHolder(JTabManager tabs, EntryCorral entryCorral, myTableCellRenderer cellRenderer, MultiSnipeManager multiManager,
                    @Assisted String name, @Assisted("completed") boolean _completed, @Assisted("deletable") boolean deletable) {
    mAuctionList = new Auctions(entryCorral, name);
    if(_completed) mAuctionList.setComplete();
    mAuctionUI = new AuctionsUIModel(mAuctionList, cellRenderer, multiManager, sTableContext, sFrameContext, sCornerButtonListener);
    mDeletable = deletable;
    tabs.add(name, mAuctionUI.getPanel(), mAuctionUI.getTable());
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
