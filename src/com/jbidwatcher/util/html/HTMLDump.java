package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.AuctionEntry;
import com.jbidwatcher.auction.AuctionsManager;
import com.jbidwatcher.Constants;

import java.util.*;

public class HTMLDump {
  public String tableHeader() {
    return "<TABLE border=\"1\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" bgcolor=\"#CCCCFF\">" +
           "<TR>" +
           "<td align=\"center\" width=\"10%\"><font size=\"2\">Item</font></td><td align=\"center\" width=\"10%\"><font size=\"2\">Start Price</font></td><td align=\"center\" width=\"11%\"><font size=\"2\">Current Price</font></td><td align=\"center\" width=\"11%\"><font size=\"2\">My Max/Snipe Bid</font></td><td align=\"center\" width=\"5%\"><font size=\"2\">Quantity</font></td><td align=\"center\" width=\"8%\"><font size=\"2\"># of Bids</font></td><td align=\"center\" width=\"11%\"><font size=\"2\">Start Date</font></td><td align=\"center\" width=\"18%\"><font size=\"2\"><strong>End Date PDT</strong></font></td><td align=\"center\" width=\"9%\"><font size=\"2\">Time Left</font></td></TR>" +
           "</TABLE>";
  }

  //  This should be an external doc of some sort, that gets filled
  //  out by the same means as the FAQ and stuff.  I.e. a template.
  public String auctionName(String auctionTitle, String auctionURL, AuctionEntry aeEntry) {
    return "<TABLE border=\"0\" cellpadding=\"1\" cellspacing=\"0\" width=\"100%\" bgcolor=\"#CCCCFF\">" +
           "<TR>" +
           "<TD width=\"100%\" colspan=\"9\" bgcolor=\"#EFEFEF\">&nbsp;<FONT size=\"3\"><strong>" +
           "<A HREF=\"" + auctionURL + "\">" +
           (aeEntry.isInvalid()?"<strike>":"") +
           auctionTitle +
           (aeEntry.isInvalid()?"</strike>":"") +
           "</A>" +
           "</strong></FONT></TD>" +
           "</TR>" +
           "</TABLE>";
  }

  public String createTD(String width, String align, int valueToFormat, String highlightColor) {
    return(createTD(width, align, Integer.toString(valueToFormat), highlightColor));
  }

  public String createTD(String width, String align, String valueToFormat, String highlightColor) {
    return "<TD width=\"" + width + "\" align=\"" + align + "\" bgcolor=\"#EFEFEF\"><FONT size=\"2\" color=\"" + highlightColor + "\">" + valueToFormat + "</FONT></TD>";
  }

  public String createValueTable(AuctionEntry ae) {
    StringBuffer outValue = new StringBuffer();
    boolean isHighBidder = ae.isHighBidder();
    String color = isHighBidder?"green":"red";

    if(ae.getNumBidders() < 1) color = "black";

    outValue.append("<TABLE border=\"0\" cellpadding=\"1\" cellspacing=\"0\" width=\"100%\" bgcolor=\"#CCCCFF\">");
    outValue.append("<TR>");
    outValue.append(createTD("10%", "center", ae.getIdentifier(), color));
    outValue.append(createTD("10%", "right", ae.getMinBid().toString(), color));
    outValue.append(createTD("11%", "right", ae.getCurBid().toString(), color));
    String savecolor = color;
    String highBid;
    if(ae.isSniped()) {
      highBid = "<a href=\"cancelSnipe?id=" + ae.getIdentifier() + "\">" + ae.getSnipeBid().toString() + "</a>";
      color = "blue";
    } else {
      if(ae.isBidOn()) {
        highBid = ae.getBid().toString();
      } else highBid = "--";

      if(highBid.equals("null")) {
        highBid = "--";
      }
    }
    outValue.append(createTD("11%", "right", highBid, color));
    color = savecolor;
    outValue.append(createTD("5%", "center", ae.getBidQuantity(), color));
    if(ae.isFixed()) {
      outValue.append(createTD("8%", "center", "FP", color));
    } else {
      outValue.append(createTD("8%", "center", ae.getNumBidders(), color));
    }
    outValue.append(createTD("11%", "center", ae.getStartDate().toString().substring(4), color));
    outValue.append(createTD("18%", "center", ae.getEndDate().toString().substring(4), color));
    String snipeLink = ae.getTimeLeft();
    if(!snipeLink.equals("Auction ended.")) {
      snipeLink = "<a href=\"snipe?id=" + ae.getIdentifier() + "\">" + ae.getTimeLeft() + "</a>";
    }
    outValue.append(createTD("9%", "center", snipeLink, color));
    outValue.append("</TR></TABLE>");

    return outValue.toString();
  }

  public StringBuffer createFullTable(Iterator<AuctionEntry> aucIterate) {
    StringBuffer sb = new StringBuffer();

    sb.append(addAuctionLink());
    sb.append(tableHeader());
    while(aucIterate.hasNext()) {
      AuctionEntry ae = aucIterate.next();
      sb.append(auctionName(ae.getTitle(), '/' + ae.getIdentifier(), ae));
      sb.append(createValueTable(ae));
    }
    return sb;
  }

  /**
   * Method addAuctionLink.
   * @return Object
   */
  private String addAuctionLink() {
    JHTMLOutput jho = new JHTMLOutput("Add Auction",
                                      new JHTMLDialog("Add Auction", "./addAuction", "GET",
                                                      "Add Auction", "Auction Id:",
                                                      "id", 20, "").toString());
    return jho.getStringBuffer().toString();
  }

  public StringBuffer createFullTable() {
    StringBuffer sb = createFullTable(AuctionsManager.getAuctionIterator());

    return new JHTMLOutput(Constants.PROGRAM_NAME + " Auctions", sb).getStringBuffer();
  }
}
