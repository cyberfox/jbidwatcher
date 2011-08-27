package com.jbidwatcher.auction;

import com.jbidwatcher.util.CreationObserver;
import com.jbidwatcher.util.xml.XMLElement;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 7/11/11
 * Time: 1:14 AM
 * To change this template use File | Settings | File Templates.
 */
public class EntryFactory implements CreationObserver<AuctionEntry> {
  private static Resolver sResolver = null;
  private static EntryFactory instance;

  public static EntryFactory getInstance() {
    if(instance == null) instance = new EntryFactory();
    return instance;
  }

  public AuctionEntry constructEntry() {
    AuctionServerInterface server = sResolver.getServer();
    AuctionEntry ae = AuctionEntry.construct(server);
    ae.setPresenter(new AuctionEntryHTMLPresenter(ae));

    return ae;
  }

  public AuctionEntry constructEntry(String auctionId) {
    AuctionServerInterface server = sResolver.getServer();
    String strippedId = server.stripId(auctionId);

    AuctionEntry ae = AuctionEntry.construct(strippedId, server);
    if(ae != null) ae.setPresenter(new AuctionEntryHTMLPresenter(ae));

    return ae;
  }

  public static void setResolver(Resolver resolver) {
    sResolver = resolver;
  }

  public XMLElement retrieveAuctionXML(String identifier) {
    AuctionEntry ae = constructEntry(identifier);
    if (ae != null) {
      return ae.toXML(); //  TODO -- Check high bidder (a separate request).
    }

    return null;
  }

  public StringBuffer retrieveAuctionXMLString(String identifier) {
    XMLElement xe = retrieveAuctionXML(identifier);

    return xe != null ? xe.toStringBuffer() : null;
  }

  public void onCreation(AuctionEntry auctionEntry) {
    if(auctionEntry.getServer() == null) {
      auctionEntry.setServer(sResolver.getServer());
    }
    if(auctionEntry.getPresenter() == null) {
      auctionEntry.setPresenter(new AuctionEntryHTMLPresenter(auctionEntry));
    }
  }
}
