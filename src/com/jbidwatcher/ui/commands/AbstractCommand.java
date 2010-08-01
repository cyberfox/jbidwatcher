package com.jbidwatcher.ui.commands;

import com.jbidwatcher.auction.AuctionEntry;

import java.awt.Component;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jun 23, 2010
 * Time: 3:25:50 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class AbstractCommand {
  private Component mSrc;
  private AuctionEntry mAuction;
  private int[] mRows;

  public void setSource(Component src) {
    mSrc = src;
  }

  public void setAuction(AuctionEntry auction) {
    mAuction = auction;
  }

  public void setSelected(int[] rowlist) {

  }

  public abstract void execute();
}
