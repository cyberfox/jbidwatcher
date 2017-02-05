package com.jbidwatcher.auction;

import com.jbidwatcher.scripting.Scripting;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.awt.MediaTracker;
import java.net.MalformedURLException;
import java.net.URL;

public class AuctionEntryHTMLPresenter implements Presenter {
  private final AuctionEntry mAuctionEntry;

  public AuctionEntryHTMLPresenter(AuctionEntry mAuctionEntry) {
    this.mAuctionEntry = mAuctionEntry;
  }

  public String buildInfo(boolean includeEvents) {
    return (String)Scripting.rubyMethod("render_info", mAuctionEntry, includeEvents);
  }

  public String buildComment(boolean showThumbnail) {
    boolean hasComment = (mAuctionEntry.getComment() != null);
    boolean hasThumb = showThumbnail && (mAuctionEntry.getThumbnail() != null);

    if (JConfig.queryConfiguration("display.thumbnail", "true").equals("false")) hasThumb = false;
    if (!hasComment && !hasThumb) return null;

    return (String)Scripting.rubyMethod("render_comment", mAuctionEntry);
  }
}
