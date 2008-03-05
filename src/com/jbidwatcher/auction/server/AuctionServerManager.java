package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.*;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLParseException;
import com.jbidwatcher.util.xml.XMLSerialize;

import java.net.URL;
import java.util.*;

/**
 * Simplified on February 17, 2008 to be single-auction-site specific;
 * JBidwatcher is not used on any other auction sites than eBay, and hasn't
 * been for many years.
 */
public class AuctionServerManager implements XMLSerialize, MessageQueue.Listener {
  private final List<AuctionEntry> mEntryList = Collections.synchronizedList(new ArrayList<AuctionEntry>());
  private final static AuctionServerManager mInstance;
  private static EntryManager sEntryManager = null;
  private AuctionServer mServer;

  private static final boolean sUberDebug = false;

  static {
    mInstance = new AuctionServerManager();
    MQFactory.getConcrete("auction_manager").registerListener(mInstance);
  }

  public static void setEntryManager(EntryManager newEM) { sEntryManager = newEM; }

  public List<AuctionEntry> allSniped() {
    if(mEntryList == null) return null;

    List<AuctionEntry> sniped = new ArrayList<AuctionEntry>();

    synchronized (mEntryList) {
      for(AuctionEntry ae : mEntryList) {
        if(ae.isSniped()) sniped.add(ae);
      }
    }
    return sniped;
  }

  public AuctionServer getServerByName(String name) {
    if (mServer.getName().equals(name)) return mServer;

    return null;
  }

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
          newServer = getServerByName(serverName);
          if(newServer == null) {
            try {
              Class<?> newClass = Class.forName(serverName + "Server");
              newServer = (AuctionServer) newClass.newInstance();
              newServer = addServer(newServer);
            } catch(ClassNotFoundException cnfe) {
              ErrorManagement.handleException("Failed to load controller class for server " + serverName + '.', cnfe);
              throw new XMLParseException(inXML.getTagName(), "Failed to load controller class for server " + serverName + '.');
            } catch(InstantiationException ie) {
              ErrorManagement.handleException("Failed to instantiate server " + serverName + '.', ie);
              throw new XMLParseException(inXML.getTagName(), "Failed to instantiate server for " + serverName + '.');
            } catch(IllegalAccessException iae) {
              com.jbidwatcher.util.config.ErrorManagement.handleException("Illegal access when instantiating server for " + serverName + '.', iae);
              throw new XMLParseException(inXML.getTagName(), "Illegal access when instantiating server for " + serverName + '.');
            }
          }
        }

        if(newServer != null) {
          int count = com.jbidwatcher.util.db.ActiveRecord.precache(AuctionInfo.class);
          if (count == 0) {
            getServerAuctionEntries(newServer, perServer);
          } else {
            loadAuctionsFromDB(newServer);
          }
        }
      }
    }
  }

  private void loadAuctionsFromDB(AuctionServer newServer) {
    com.jbidwatcher.util.db.ActiveRecord.precache(Seller.class);
    com.jbidwatcher.util.db.ActiveRecord.precache(Seller.class, "seller");
    com.jbidwatcher.util.db.ActiveRecord.precache(Category.class);
    com.jbidwatcher.util.db.ActiveRecord.precache(AuctionEntry.class, "auction_id");

    Map<String, com.jbidwatcher.util.db.ActiveRecord> entries = com.jbidwatcher.util.db.ActiveRecord.getCache(AuctionEntry.class);
    for(String auction_id : entries.keySet()) {
      AuctionEntry ae = (AuctionEntry) entries.get(auction_id);
      ae.setServer(newServer);

      AuctionInfo ai = AuctionInfo.findFirstBy("id", ae.get("auction_id"));
      if(ai != null) {
        ae.setAuctionInfo(ai);
        sEntryManager.addEntry(ae);
      } else {
        System.err.println("CAN'T BRING IN AUCTION #: " + ae.get("auction_id"));
      }
    }
  }

  private void getServerAuctionEntries(AuctionServer newServer, XMLElement perServer) {
    try {
      Iterator<XMLElement> entryStep = perServer.getChildren();
      while (entryStep.hasNext()) {
        XMLElement perEntry = entryStep.next();
        AuctionEntry ae = new AuctionEntry();

        ae.setServer(newServer);
        ae.fromXML(perEntry);
        MQFactory.getConcrete("dbsave").enqueue(ae);

        if (sEntryManager != null) {
          sEntryManager.addEntry(ae);
        }
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
      AuctionServerInterface defaultServer = getDefaultServer();

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
        if(sUberDebug) com.jbidwatcher.util.config.ErrorManagement.logDebug("No Such Element caught.");
      }
    }
  }

  public String getDefaultServerTime() {
    AuctionServerInterface defaultServer = getDefaultServer();
    return defaultServer.getTime();
  }

  public XMLElement toXML() {
    XMLElement xmlResult = new XMLElement("auctions");
    int aucCount = 0;

    XMLElement serverChild = new XMLElement("server");

    serverChild.setProperty("name", mServer.getName());

    if (mEntryList == null) return null;
    synchronized (mEntryList) {
      aucCount += mEntryList.size();

      for (AuctionEntry ae : mEntryList) {
        try {
          serverChild.addChild(ae.toXML());
        } catch (Exception e) {
          com.jbidwatcher.util.config.ErrorManagement.handleException("Exception trying to save auction " + ae.getIdentifier() + " (" + ae.getTitle() + ") -- Not saving", e);
        }
      }
    }
    xmlResult.addChild(serverChild);

    xmlResult.setProperty("count", Integer.toString(aucCount));

    return xmlResult;
  }

  public void deleteEntry(AuctionEntry ae) {
    synchronized (mEntryList) { mEntryList.remove(ae); }
  }

  public void addEntry(AuctionEntry ae) {
    synchronized (mEntryList) { mEntryList.add(ae); }
  }

  public static AuctionServerManager getInstance() {
    return mInstance;
  }

  public AuctionServer addServer(AuctionServer aucServ) {
    if(mServer != null) {
      RuntimeException here = new RuntimeException("Trying to add a server, when we've already got one!");
      com.jbidwatcher.util.config.ErrorManagement.handleException("addServer error!", here);
      return mServer;
    }
    mServer = aucServ;

    return(mServer);
  }

  //  Handle the case of '198332643'.  (For 'paste auction').
  public AuctionServer getServerForIdentifier(String auctionId) {
    if (mServer.checkIfIdentifierIsHandled(auctionId)) return mServer;

    return null;
  }

  public AuctionServer getServerForUrlString(String strURL) {
    URL serverAddr = StringTools.getURLFromString(strURL);

    if (mServer.doHandleThisSite(serverAddr)) return mServer;

    com.jbidwatcher.util.config.ErrorManagement.logDebug("No matches for getServerForUrlString(" + strURL + ')');
    return null;
  }

  public void addAuctionServerMenus() {
    mServer.establishMenu();
  }

  /**
   * Returns the first server, which means it's the 'default'.
   *
   * @return - The first auction server in the list, or null if the list is empty.
   */
  public AuctionServer getDefaultServer() {
    return mServer;
  }

  public void addSearches(SearchManagerInterface searchManager) {
    mServer.addSearches(searchManager);
  }

  public void cancelSearches() {
    mServer.cancelSearches();
  }

  public List<JConfigTab> getServerConfigurationTabs() {
    return mServer.getConfigurationTabs();
  }

  public AuctionStats getStats() {
    AuctionStats outStat = new AuctionStats();

    if(mEntryList == null) return null;
    synchronized (mEntryList) {
      outStat._count = mEntryList.size();
      long lastUpdateTime = Long.MAX_VALUE;
      long lastEndedTime = Long.MAX_VALUE;
      long lastSnipeTime = Long.MAX_VALUE;
      for (AuctionEntry ae : mEntryList) {
        if (ae.isComplete()) {
          outStat._completed++;
        } else {
          long thisTime = ae.getEndDate().getTime();
          if (ae.isSniped()) {
            outStat._snipes++;
            if (thisTime < lastSnipeTime) {
              outStat._nextSnipe = ae;
              lastSnipeTime = thisTime;
            }
          }

          if (thisTime < lastEndedTime) {
            outStat._nextEnd = ae;
            lastEndedTime = thisTime;
          }

          long nextTime = ae.getNextUpdate();
          if (nextTime < lastUpdateTime) {
            outStat._nextUpdate = ae;
            lastUpdateTime = nextTime;
          }
        }
      }
    }
    return outStat;
  }
}
