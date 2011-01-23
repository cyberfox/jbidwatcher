package com.jbidwatcher.ui.commands;

import com.jbidwatcher.auction.AuctionEntry;

import java.awt.Component;

/**
 * Allow commands to exist on their own, as individual classes.
 *
 * User: mrs
 * Date: Jun 23, 2010
 * Time: 3:25:50 PM
 */
public abstract class AbstractCommand {
  protected Component mSrc;
  protected AuctionEntry mAuction;
  protected int[] mRows;

  abstract protected String getCommand();

  public void setSource(Component src) {
    mSrc = src;
  }

  public void setAuction(AuctionEntry auction) {
    mAuction = auction;
  }

  public void setSelected(int[] rowlist) {
    mRows = rowlist.clone();
  }

  public abstract void execute();
}
