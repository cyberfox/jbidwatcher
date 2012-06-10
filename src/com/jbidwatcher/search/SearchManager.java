package com.jbidwatcher.search;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.platform.Path;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.TimerHandler;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLParseException;
import com.jbidwatcher.util.xml.XMLSerializeSimple;
import com.jbidwatcher.util.Constants;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class SearchManager extends XMLSerializeSimple implements SearchManagerInterface, TimerHandler.WakeupProcess {
  private List<Searcher> _searches = new ArrayList<Searcher>();
  private static SearchManager _instance = null;
  private static TimerHandler sTimer;
  private String destinationQueue;

  private SearchManager() { }
  public static SearchManager getInstance() {
    if (_instance == null) _instance = new SearchManager();

    return _instance;
  }

  public void addSearch(Searcher newSearch) {
    _searches.add(newSearch);
  }

  public Searcher getSearchByName(String name) {
    for (Searcher search : _searches) {
      if (name.equals(search.getName())) return search;
    }

    return null;
  }

  public void setDestinationQueue(String dQueue) {
    destinationQueue = dQueue;
  }

  public class StringSearcher extends Searcher {
    public String getTypeName() { return "Text"; }
    protected void fire() {
      MQFactory.getConcrete(destinationQueue).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_SEARCH, getId(), getCategory()));
    }
  }

  public class TitleSearcher extends Searcher {
    public String getTypeName() { return "Title"; }
    protected void fire() {
      MQFactory.getConcrete(destinationQueue).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_TITLE, getId(), getCategory()));
    }
  }

  public class URLSearcher extends Searcher {
    public String getTypeName() { return "URL"; }
    protected void fire() {
      MQFactory.getConcrete(destinationQueue).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_URL, getId(), getCategory()));
    }
  }

  public class SellerSearcher extends Searcher {
    public String getTypeName() { return "Seller"; }
    protected void fire() {
      MQFactory.getConcrete(destinationQueue).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_SELLER, getId(), getCategory()));
    }
  }

  public class MyItemSearcher extends Searcher {
    public String getTypeName() { return "My Items"; }
    protected void fire() {
      MQFactory.getConcrete(destinationQueue).enqueueBean(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, hasCategory() ? getCategory() : null));
    }
  }

  public Searcher getSearchByIndex(int i) { if(i < _searches.size()) return _searches.get(i); else return null; }

  public int findSearch(Searcher s) { return _searches.indexOf(s); }

  public static Searcher getSearchById(Long id) {
    return getInstance().getSearchById(id.longValue());
  }

  public Searcher getSearchById(long id) {
    for (Searcher s : _searches) {
      if (id == s.getId()) return s;
    }

    return null;
  }

  public boolean check() {
    boolean fired=false;
    for (Searcher s : _searches) {
      if (s.shouldExecute()) {
        s.execute();
        fired = true;
      }
    }
    return fired;
  }

  /** 
   * @brief Save searches in a file.
   *
   * This is mostly for testing, although the same function will be
   * used by the 'shutdown' code.
   * 
   * @return - true indicates the file was successfully saved, false
   * indicates that an error occurred while trying to save the searches.
   */
  public boolean saveSearches() {
    String saveFile = JConfig.queryConfiguration("search.savefile", "searches.xml");
    String oldSave = saveFile;

    StringBuffer saveData = this.toXML().toStringBuffer();

    saveFile = Path.getCanonicalFile(saveFile, "jbidwatcher", false);
    if(!saveFile.equals(oldSave)) {
      JConfig.setConfiguration("search.savefile", saveFile);
    }

    boolean saveDone = true;
    try {
      PrintStream ps = new PrintStream(new FileOutputStream(saveFile));

      ps.println("<?xml version=\"1.0\"?>");
      ps.println("");
      ps.println(Constants.XML_SEARCHES_DOCTYPE);
      ps.println("");
      ps.println(saveData);
      ps.close();
    } catch(IOException e) {
      JConfig.log().handleException("Failed to save searches.", e);
      saveDone = false;
    }

    return saveDone;
  }

  public void loadSearches() {
    XMLElement xmlFile = new XMLElement(true);
    String loadFile = JConfig.queryConfiguration("search.savefile", "searches.xml");
    String oldLoad = loadFile;

    loadFile = Path.getCanonicalFile(loadFile, "jbidwatcher", true);

    if(!loadFile.equals(oldLoad)) {
      JConfig.setConfiguration("search.savefile", loadFile);
    }

    try {
      InputStreamReader isr = new InputStreamReader(new FileInputStream(loadFile));

      xmlFile.parseFromReader(isr);

      if(!xmlFile.getTagName().equals("searches")) {
        throw new XMLParseException(xmlFile.getTagName(), "SearchManager only recognizes <searches> tag!");
      } else {
        fromXML(xmlFile);
      }
    } catch(IOException ioe) {
      JConfig.log().logDebug("JBW: Failed to load saved searches, the search file is probably not there yet.");
      JConfig.log().logDebug("JBW: This is not an error, unless you are consistently getting it.");
    } catch(Exception e) {
      JConfig.log().handleException("JBW: Failed to load saved searches, file exists but can't be loaded!", e);
    }
  }

  public XMLElement toXML() {
    XMLElement allData = new XMLElement("searches");

    for (Searcher s : _searches) {
      XMLElement search = s.toXML();

      allData.addChild(search);
    }

    return allData;
  }

  protected String[] infoTags = { "search" };
  protected String[] getTags() { return infoTags; }

  protected void handleTag(int i, XMLElement curElement) {
    switch(i) {
      case 0:
        String type = curElement.getProperty("TYPE");
        long id = 0;
        try {
          id = Long.parseLong(curElement.getProperty("ID"));
        } catch(NumberFormatException nfe) {
          //  This is totally normal.
        }
        Searcher s = newSearch(type);
        s.setId(id);

        s.fromXML(curElement);
        if(s.getId() == 0) {
          if(s.getTypeName().equals("My Items")) {
            s.setId(1);
          } else {
            s.setId(s.getName().hashCode() + System.currentTimeMillis() + s.getSearch().hashCode());
          }
        }
        if(getSearchById(s.getId()) == null) {
          _searches.add(s);
        }
        break;
      default:
        break;
    }
  }

  public int getSearchCount() { return _searches.size(); }

  public void deleteSearch(Searcher s) {
    _searches.remove(s);
  }

  private Searcher newSearch(String type) {
    if(type.startsWith("Text")) {
      return new StringSearcher();
    } else if(type.startsWith("Title")) {
      return new TitleSearcher();
    } else if(type.startsWith("Seller")) {
      return new SellerSearcher();
    } else if(type.startsWith("URL")) {
      return new URLSearcher();
    } else if(type.equals("My Items")) {
      return new MyItemSearcher();
    } else {
      JConfig.log().logMessage("Failed to create searcher for: " + type);
    }

    return null;
  }

  public Searcher addSearch(String type, String name, String search, String server, int period, long id) {
    Searcher s = buildSearch(id, type, name, search, server, null, period);
    _searches.add(s);

    return s;
  }

  public Searcher buildSearch(long id, String type, String name, String search, String server, String currency, int period) {
    Searcher s = newSearch(type);
    s.setId(id);
    s.setName(name);
    s.setSearch(search);
    s.setServer(server);
    s.setPeriod(period);
    s.setCurrency(currency);
    return s;
  }

  public void saveSearchDisplay() {
    String xCfg = JConfig.queryAuxConfiguration("searches.x", JConfig.queryDisplayProperty("searches.x"));
    String yCfg = JConfig.queryAuxConfiguration("searches.y", JConfig.queryDisplayProperty("searches.y"));
    String wCfg = JConfig.queryAuxConfiguration("searches.width", JConfig.queryDisplayProperty("searches.width"));
    String hCfg = JConfig.queryAuxConfiguration("searches.height", JConfig.queryDisplayProperty("searches.height"));

    if (xCfg != null && yCfg != null && wCfg != null && hCfg != null) {
      JConfig.setAuxConfiguration("searches.x", xCfg);
      JConfig.setAuxConfiguration("searches.y", yCfg);
      JConfig.setAuxConfiguration("searches.width", wCfg);
      JConfig.setAuxConfiguration("searches.height", hCfg);
    }
  }

  public void deleteSearch(String searchName) {
    Searcher old = getSearchByName(searchName);
    if(old != null) deleteSearch(old);
  }

  //  This thread / timer handles the periodic searching that the
  //  search feature allows to be set up.  Check only once a minute,
  //  because searching isn't a very time-critical feature.
  public static void start() {
    if (sTimer == null) {
      sTimer = new TimerHandler(getInstance(), Constants.ONE_MINUTE);
      sTimer.setName("Searches");
      sTimer.start();
    }
  }
}
