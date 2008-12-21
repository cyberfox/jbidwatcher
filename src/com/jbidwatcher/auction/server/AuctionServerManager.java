package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLParseException;
import com.jbidwatcher.util.xml.XMLSerialize;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.AuctionServerInterface;

import java.util.*;

/**
 * Simplified on February 17, 2008 to be single-auction-site specific;
 * JBidwatcher is not used on any other auction sites than eBay, and hasn't
 * been for many years.
 */
public class AuctionServerManager implements XMLSerialize, MessageQueue.Listener, Resolver {
  private final static AuctionServerManager mInstance;
  private static EntryManager sEntryManager = null;
  private AuctionServer mServer = null;
  private AuctionServer mSecondary = null;
  private static SearchManager mSearcher;

  private static final boolean sUberDebug = false;

  static {
    mInstance = new AuctionServerManager();
    mSearcher = SearchManager.getInstance();

    MQFactory.getConcrete("auction_manager").registerListener(mInstance);
  }

  public static void setEntryManager(EntryManager newEM) { sEntryManager = newEM; }

  private AuctionServerManager() { }

  /**
   * @brief Load all the auction servers.
   *
   * BUGBUG - Refactor this to use the XMLSerializeSimple if at all possible!
   *
   * @param inXML - The XML source to load from.
   * @noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException,StringContatenationInLoop
   */
  public void fromXML(XMLElement inXML) {
    Iterator<XMLElement> serversStep = inXML.getChildren();

    while(serversStep.hasNext()) {
      XMLElement perServer = serversStep.next();
      //  Only process the 'server' entries.
      if(perServer.getTagName().equals("server")) {
        AuctionServer newServer = null;
        String serverName = perServer.getProperty("NAME", null);
        if(serverName != null) {
          newServer = getServer();
          if(newServer == null) {
            try {
              Class<?> newClass = Class.forName(serverName + "Server");
              newServer = (AuctionServer) newClass.newInstance();
              newServer = setServer(newServer);
            } catch(ClassNotFoundException cnfe) {
              ErrorManagement.handleException("Failed to load controller class for server " + serverName + '.', cnfe);
              throw new XMLParseException(inXML.getTagName(), "Failed to load controller class for server " + serverName + '.');
            } catch(InstantiationException ie) {
              ErrorManagement.handleException("Failed to instantiate server " + serverName + '.', ie);
              throw new XMLParseException(inXML.getTagName(), "Failed to instantiate server for " + serverName + '.');
            } catch(IllegalAccessException iae) {
              ErrorManagement.handleException("Illegal access when instantiating server for " + serverName + '.', iae);
              throw new XMLParseException(inXML.getTagName(), "Illegal access when instantiating server for " + serverName + '.');
            }
          }
        }

        if (newServer != null) {
          getServerAuctionEntries(newServer, perServer);
        }
      }
    }
  }

  public void loadAuctionsFromDB(AuctionServer newServer) {
    MQFactory.getConcrete("splash").enqueue("SET 0");
    int count = 0;

    List<AuctionEntry> entries = AuctionEntry.findAll();
    for(AuctionEntry ae : entries) {
      ae.setServer(newServer);

      if (ae.getAuction() == null) {
        ErrorManagement.logMessage("We lost the underlying auction for: " + ae.dumpRecord());
        if(ae.getIdentifier() != null) {
          ErrorManagement.logMessage("Trying to reload auction via its auction identifier.");
          MQFactory.getConcrete("drop").enqueue(ae);
        } else {
          ae.delete();
        }
      } else {
        sEntryManager.addEntry(ae);
      }
      MQFactory.getConcrete("splash").enqueue("SET " + count++);
    }

    List<AuctionEntry> sniped = AuctionEntry.findAllSniped();
    for(AuctionEntry snipable:sniped) {
      if(!snipable.isComplete()) {
        snipable.setServer(newServer);
        snipable.refreshSnipe();
      }
    }
  }

  private void getServerAuctionEntries(AuctionServer newServer, XMLElement perServer) {
    try {
      Iterator<XMLElement> entryStep = perServer.getChildren();
      int count = 0;
      while (entryStep.hasNext()) {
        XMLElement perEntry = entryStep.next();
        AuctionEntry ae = new AuctionEntry();

        ae.setServer(newServer);
        ae.fromXML(perEntry);
        ae.saveDB();

        if (sEntryManager != null) {
          sEntryManager.addEntry(ae);
        }
        MQFactory.getConcrete("splash").enqueue("SET " + count++);
      }
    } catch(XMLParseException e) {
      ErrorManagement.handleException("Parse exception: ", e);
    }
  }

  /**
   * @brief Serialize access to the time updating function, so that
   * everybody in the world doesn't try to update the time all at
   * once, like they used to.  Four threads trying to update the time
   * all together caused some nasty errors.
   */
  public void messageAction(Object deQ) {
    String cmd = (String)deQ;

    if(cmd.equals("TIMECHECK")) {
      com.jbidwatcher.auction.AuctionServerInterface defaultServer = getServer();

      defaultServer.reloadTime();

      long servTime = defaultServer.getServerTimeDelta();
      Date now = new Date(System.currentTimeMillis() + servTime);
      MQFactory.getConcrete("Swing").enqueue("Server time is now: " + now);

      try {
        boolean done = false;
        final MessageQueue aucManagerQ = MQFactory.getConcrete("auction_manager");
        while(!done) {
          cmd = aucManagerQ.dequeue();
          if(!cmd.equals("TIMECHECK")) done=true;
        }
        //  Re-enqueue the last one, because it must not have been
        //  another TIMECHECK command!
        aucManagerQ.enqueue(cmd);
      } catch(NoSuchElementException nsee) {
        //  Nothing really to do, this just means we cleaned out the
        //  list before finding a non-timecheck value.
        if(sUberDebug) ErrorManagement.logDebug("No Such Element caught.");
      }
    }
  }

  public String getDefaultServerTime() {
    AuctionServerInterface defaultServer = getServer();
    return defaultServer.getTime();
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("auctions");
    XMLElement serverChild = new XMLElement("server");
    List<AuctionEntry> entryList = AuctionEntry.findAll();

    if (entryList == null || entryList.isEmpty()) return null;

    serverChild.setProperty("name", mServer.getName());

    int aucCount = 0;
    aucCount += entryList.size();

    for (AuctionEntry ae : entryList) {
      try {
        serverChild.addChild(ae.toXML());
      } catch (Exception e) {
        try {
        ErrorManagement.handleException("Exception trying to save auction " + ae.getIdentifier() + " (" + ae.getTitle() + ") -- Not saving", e);
        } catch(Exception e2) {
          ErrorManagement.handleException("Exception trying to save auction entry id " + ae.getId() + " -- Not saving", e);
        }
      }
    }

    xmlResult.addChild(serverChild);
    xmlResult.setProperty("count", Integer.toString(aucCount));

    return xmlResult;
  }

  public static AuctionServerManager getInstance() {
    return mInstance;
  }

  public AuctionServer setServer(AuctionServer aucServ) {
    if(mServer != null) {
      //noinspection ThrowableInstanceNeverThrown
      RuntimeException here = new RuntimeException("Trying to add a server, when we've already got one!");
      ErrorManagement.handleException("setServer error!", here);
      return mServer;
    }
    mServer = aucServ;

    mServer.addSearches(mSearcher);
    return(mServer);
  }

  public AuctionServer getServer() {
    return mServer;
  }

  public ServerMenu addAuctionServerMenus() {
    return mServer.establishMenu();
  }

  public void cancelSearches() {
    mServer.cancelSearches();
  }

  public AuctionStats getStats() {
    AuctionStats outStat = new AuctionStats();

    outStat._count = AuctionEntry.count();
    outStat._completed = AuctionEntry.completedCount();
    outStat._snipes = AuctionEntry.snipedCount();
    outStat._nextSnipe = AuctionEntry.nextSniped();
    outStat._nextEnd = null;
    outStat._nextUpdate = null;

    return outStat;
  }
}
