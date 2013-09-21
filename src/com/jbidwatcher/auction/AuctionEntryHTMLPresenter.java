package com.jbidwatcher.auction;

import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.awt.MediaTracker;
import java.net.MalformedURLException;
import java.net.URL;

public class AuctionEntryHTMLPresenter implements Presenter {
  private final AuctionEntry mAuctionEntry;
  public static final String newRow = "<tr><td>";
  public static final String newCol = "</td><td>";
  public static final String endRow = "</td></tr>";

  public AuctionEntryHTMLPresenter(AuctionEntry mAuctionEntry) {
    this.mAuctionEntry = mAuctionEntry;
  }

  public String buildInfo(boolean includeEvents) {
    String prompt = "";

    if (false) {
      prompt += "<b>" + StringTools.stripHigh(mAuctionEntry.getTitle()) + "</b> (" + mAuctionEntry.getIdentifier() + ")<br>";
    } else {
      prompt += "<b>" + mAuctionEntry.getTitle() + "</b> (" + mAuctionEntry.getIdentifier() + ")<br>";
    }
    prompt += "<table>";
    boolean addedThumbnail = false;
    if (mAuctionEntry.getThumbnail() != null) {
      String thumb = mAuctionEntry.getThumbnail();
      try {
        ImageIcon thumbnail = new ImageIcon(new URL(thumb));
        if (thumbnail.getImageLoadStatus() == MediaTracker.COMPLETE) {
          int h = thumbnail.getIconHeight();
          int w = thumbnail.getIconWidth();
          int long_side = Math.max(h, w);
          float scale = 192f / (float) long_side;
          h = (int) (((float) h) * scale);
          w = (int) (((float) w) * scale);
          prompt += newRow + "<img src=\"" + mAuctionEntry.getThumbnail() + "\" height=" + h + " width=" + w + ">" + newCol + "<table>";
          addedThumbnail = true;
        }
      } catch (MalformedURLException e) {
        JConfig.log().handleDebugException("Couldn't load the thumbnail image", e);
      }
    }
    prompt = buildInfoBody(prompt, includeEvents, addedThumbnail);

    return (prompt);
  }

  private String buildRow(String label, Object value) {
    return newRow + label + newCol + (value != null ? value.toString() : "null") + endRow;
  }

  private String buildInfoBody(String prompt, boolean includeEvents, boolean addedThumbnail) {
    if (!mAuctionEntry.isFixed()) {
      prompt += buildRow("Currently", mAuctionEntry.getCurrentPrice() + " (" + mAuctionEntry.getNumBidders() + " Bids)");
      String bidder = mAuctionEntry.getHighBidder();
      prompt += buildRow("High bidder", bidder == null ? "(n/a)" : bidder);
    } else {
      prompt += buildRow("Price", mAuctionEntry.getCurrentPrice());
    }
    if (mAuctionEntry.isFixed() && mAuctionEntry.getQuantity() > 1) {
      prompt += buildRow("Quantity", mAuctionEntry.getQuantity());
    }

    if (JConfig.debugging() && JConfig.scriptingEnabled()) {
      prompt += buildRow("Sticky", Boolean.toString(mAuctionEntry.isSticky()));
      prompt += buildRow("Category", mAuctionEntry.getCategory());
    }

    if (mAuctionEntry.isBidOn()) {
      prompt += buildRow("Your max bid", mAuctionEntry.getBid());
      if (mAuctionEntry.getBidQuantity() != 1) {
        prompt += buildRow("Quantity of", mAuctionEntry.getBidQuantity());
      }
    }

    if (mAuctionEntry.isSniped()) {
      prompt += buildRow("Sniped for", mAuctionEntry.getSnipeAmount());
      if (mAuctionEntry.getSnipeQuantity() != 1) {
        prompt += buildRow("Quantity of", mAuctionEntry.getSnipeQuantity());
      }
      prompt += newRow + "Sniping at " + (mAuctionEntry.getSnipeTime() / 1000) + " seconds before the end." + endRow;
    }

    if (mAuctionEntry.getShipping() != null && !mAuctionEntry.getShipping().isNull()) {
      prompt += buildRow("Shipping", mAuctionEntry.getShipping());
    }
    if (!mAuctionEntry.getInsurance().isNull()) {
      prompt += buildRow("Insurance (" + (mAuctionEntry.isInsuranceOptional() ? "optional" : "required") + ")", mAuctionEntry.getInsurance());
    }
    prompt += buildRow("Seller", mAuctionEntry.getSellerName());
    if (mAuctionEntry.isComplete()) {
      prompt += buildRow("Listing ended at ", mAuctionEntry.getEndDate());
    } else {
      prompt += buildRow("Listing ends at", mAuctionEntry.getEndDate());
    }
    if (mAuctionEntry.getLastUpdated() != null) {
      prompt += buildRow("Last updated at", mAuctionEntry.getLastUpdated());
    }

    if (addedThumbnail) {
      prompt += "</table>" + endRow;
    }
    prompt += "</table>";

    if (!mAuctionEntry.isFixed() && !mAuctionEntry.getBuyNow().isNull()) {
      if (mAuctionEntry.isComplete()) {
        prompt += "<b>You could have used Buy It Now for " + mAuctionEntry.getBuyNow() + "</b><br>";
      } else {
        prompt += "<b>Or you could buy it now, for " + mAuctionEntry.getBuyNow() + ".</b><br>";
        prompt += "Note: <i>To 'Buy Now' through this program,<br>      select 'Buy' from the context menu.</i><br>";
      }
    }

    if (mAuctionEntry.isComplete()) {
      prompt += "<i>Listing has ended.</i><br>";
    }

    if (mAuctionEntry.getComment() != null) {
      prompt += "<br><u>Comment</u><br>";

      prompt += "<b>" + mAuctionEntry.getComment() + "</b><br>";
    }

    if (includeEvents) prompt += "<b><u>Events</u></b><blockquote>" + mAuctionEntry.getStatusHistory() + "</blockquote>";
    return prompt;
  }

  public String buildComment(boolean showThumbnail) {
    boolean hasComment = (mAuctionEntry.getComment() != null);
    boolean hasThumb = showThumbnail && (mAuctionEntry.getThumbnail() != null);

    if (JConfig.queryConfiguration("display.thumbnail", "true").equals("false")) hasThumb = false;
    if (!hasComment && !hasThumb) return null;

    StringBuffer wholeHTML = new StringBuffer("<html><body>");
    if (hasThumb && hasComment) {
      wholeHTML.append("<table><tr><td><img src=\"").append(mAuctionEntry.getThumbnail()).append("\"></td><td>").append(mAuctionEntry.getComment()).append("</td></tr></table>");
    } else {
      if (hasThumb) {
        wholeHTML.append("<img src=\"").append(mAuctionEntry.getThumbnail()).append("\">");
      } else {
        wholeHTML.append(mAuctionEntry.getComment());
      }
    }
    wholeHTML.append("</body></html>");

    return wholeHTML.toString();
  }
}
