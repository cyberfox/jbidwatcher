package com.jbidwatcher.auction.server;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.ebay.ebayServer;
import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.search.SearchManagerInterface;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.XMLParseException;
import com.jbidwatcher.xml.XMLSerialize;

import java.net.URL;
import java.util.*;

public class AuctionServerManager implements XMLSerialize, MessageQueue.Listener {
  private Map<AuctionServer, List<AuctionEntry>> mServerAuctionList;
  private List<AuctionServerListEntry> mServerList;
  private List<JConfigTab> mConfigTabList = null;
  private final static AuctionServerManager mInstance = new AuctionServerManager();
  private static EntryManager sEntryManager = null;

  private static final boolean sUberDebug = false;

  static {
    MQFactory.getConcrete("auction_manager").registerListener(mInstance);
  }

  public static void setEntryManager(EntryManager newEM) { sEntryManager = newEM; }

  public List<AuctionEntry> allSniped() {
    List<AuctionEntry> aucList = mServerAuctionList.get(getDefaultServer());
    if(aucList == null) return null;

    List<AuctionEntry> sniped = new ArrayList<AuctionEntry>();

    synchronized (aucList) {
      for(AuctionEntry ae : aucList) {
        if(ae.isSniped()) sniped.add(ae);
      }
    }
    return sniped;
  }

  private static class AuctionServerListEntry {
    private String _serverName;
    private AuctionServer _aucServ;

    private AuctionServerListEntry(String name, AuctionServer server) {
      _serverName = name;
      _aucServ = server;
    }

    public AuctionServer getAuctionServer() {
      return _aucServ;
    }

    public String getName() {
      return _serverName;
    }
  }

  public AuctionServer getServerByName(String name) {
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();
      if (as.getName().equals(name)) return as;
    }

    return null;
  }

  private AuctionServerManager() {
    mServerAuctionList = new HashMap<AuctionServer, List<AuctionEntry>>(2);
    mServerList = new ArrayList<AuctionServerListEntry>();
  }

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
              newServer = addServer(serverName, newServer);
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

        if(newServer != null) {
          int count = ActiveRecord.precache(AuctionInfo.class);
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
    ActiveRecord.precache(Seller.class);
    ActiveRecord.precache(Seller.class, "seller");
    ActiveRecord.precache(Category.class);
    ActiveRecord.precache(AuctionEntry.class, "auction_id");

    Map<String,ActiveRecord> entries = ActiveRecord.getCache(AuctionEntry.class);
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
      newServer.loadAuthorization(perServer);

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
        if(sUberDebug) ErrorManagement.logDebug("No Such Element caught.");
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

    for (AuctionServerListEntry asle : mServerList) {
      AuctionServer aucServ = asle.getAuctionServer();
      List<AuctionEntry> aucList = mServerAuctionList.get(aucServ);
      XMLElement serverChild = new XMLElement("server");

      serverChild.setProperty("name", aucServ.getName());
      aucServ.storeAuthorization(serverChild);

      if(aucList == null) return null;
      synchronized (aucList) {
        aucCount += aucList.size();

        for (AuctionEntry ae : aucList) {
          try {
            serverChild.addChild(ae.toXML());
          } catch(Exception e) {
            ErrorManagement.handleException("Exception trying to save auction " + ae.getIdentifier() + " (" + ae.getTitle() + ") -- Not saving", e);
          }
        }
      }
      xmlResult.addChild(serverChild);
    }

    xmlResult.setProperty("count", Integer.toString(aucCount));

    return xmlResult;
  }

  public void deleteEntry(AuctionEntry ae) {
    AuctionServer aucserv = ae.getServer();
    if(aucserv != null) {
      List<AuctionEntry> aucList = mServerAuctionList.get(aucserv);
      synchronized(aucList) { aucList.remove(ae); }
    }
  }

  public void addEntry(AuctionEntry ae) {
    AuctionServer aucserv = ae.getServer();
    if(aucserv != null) {
      List<AuctionEntry> aucList = mServerAuctionList.get(aucserv);
      synchronized (aucList) { aucList.add(ae); }
    }
  }

  public static AuctionServerManager getInstance() {
    return mInstance;
  }

  private AuctionServer addServerNoUI(String inName, AuctionServer aucServ) {
    for (AuctionServerListEntry asle : mServerList) {
      if (asle.getAuctionServer() == aucServ || inName.equals(asle.getName())) {
        return (asle.getAuctionServer());
      }
    }

    mServerList.add(new AuctionServerListEntry(inName, aucServ));
    mServerAuctionList.put(aucServ, Collections.synchronizedList(new ArrayList<AuctionEntry>()));
    return(aucServ);
  }

  public AuctionServer addServer(String inName, AuctionServer aucServ) {
    AuctionServer as = addServerNoUI(inName, aucServ);
    if(as == aucServ) {
      if(mConfigTabList != null) {
        mConfigTabList.add(aucServ.getConfigurationTab());
      }
    }

    return(as);
  }

  private AuctionServer addServer(AuctionServer aucServ) {
    return(addServer(aucServ.getName(), aucServ));
  }

  //  Handle the case of '198332643'.  (For 'paste auction').
  public AuctionServer getServerForIdentifier(String auctionId) {
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();

      if (as.checkIfIdentifierIsHandled(auctionId)) {
        return (addServer(as));
      }
    }

    return null;
  }

  public AuctionServer getServerForUrlString(String strURL) {
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();
      URL serverAddr = StringTools.getURLFromString(strURL);

      if (as.doHandleThisSite(serverAddr)) {
        return (addServer(as));
      }
    }

    ErrorManagement.logDebug("No matches for getServerForUrlString(" + strURL + ')');
    return null;
  }

  public void addAuctionServerMenus() {
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();
      as.establishMenu();
    }
  }

  public AuctionServer getDefaultServer() {
    Iterator<AuctionServerListEntry> it = mServerList.iterator();
    if(it.hasNext()) {
      return (it.next()).getAuctionServer();
    }

    return null;
  }

  public void addSearches(SearchManagerInterface searchManager) {
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();

      as.addSearches(searchManager);
    }
  }

  public void cancelSearches() {
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();
      as.cancelSearches();
    }
  }

  public List<JConfigTab> getServerConfigurationTabs() {
    //  Always rebuild, so as to fix a problem on first-startup.
    mConfigTabList = new ArrayList<JConfigTab>();
    for (AuctionServerListEntry a_serverList : mServerList) {
      AuctionServer as = (a_serverList).getAuctionServer();
      mConfigTabList.add(as.getConfigurationTab());
    }
    return mConfigTabList;
  }

  public AuctionStats getStats() {
    AuctionStats outStat = new AuctionStats();
    List<AuctionEntry> aucList = mServerAuctionList.get(getDefaultServer());

    if(aucList == null) return null;
    synchronized (aucList) {
      outStat._count = aucList.size();
      long lastUpdateTime = Long.MAX_VALUE;
      long lastEndedTime = Long.MAX_VALUE;
      long lastSnipeTime = Long.MAX_VALUE;
      for (AuctionEntry ae : aucList) {
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
