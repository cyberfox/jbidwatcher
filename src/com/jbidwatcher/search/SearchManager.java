package com.jbidwatcher.search;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.AuctionQObject;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.XMLParseException;
import com.jbidwatcher.xml.XMLSerializeSimple;
import com.jbidwatcher.TimerHandler;
import com.jbidwatcher.Constants;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.auction.server.AuctionServerManager;

import java.io.*;
import java.util.List;
import java.util.ArrayList;

public class SearchManager extends XMLSerializeSimple implements SearchManagerInterface, TimerHandler.WakeupProcess {
  private List<Searcher> _searches = new ArrayList<Searcher>();
  private static SearchManager _instance = null;

  public void addSearch(Searcher newSearch) {
    _searches.add(newSearch);
  }

  public Searcher getSearchByName(String name) {
    for (Searcher search : _searches) {
      if (name.equals(search.getName())) return search;
    }

    return null;
  }

  private static class StringSearcher extends Searcher {
    public String getTypeName() { return "Text"; }
    protected void fire() { MQFactory.getConcrete(_server).enqueue(new AuctionQObject(AuctionQObject.LOAD_SEARCH, this, getCategory())); }
  }

  private static class TitleSearcher extends Searcher {
    public String getTypeName() { return "Title"; }
    protected void fire() { MQFactory.getConcrete(_server).enqueue(new AuctionQObject(AuctionQObject.LOAD_TITLE, this, getCategory())); }
  }

  private static class SellerSearcher extends Searcher {
    public String getTypeName() { return "Seller"; }
    protected void fire() { MQFactory.getConcrete(_server).enqueue(new AuctionQObject(AuctionQObject.LOAD_SELLER, this, getCategory())); }
  }

  private static class URLSearcher extends Searcher {
    public String getTypeName() { return "URL"; }
    protected void fire() { MQFactory.getConcrete(_server).enqueue(new AuctionQObject(AuctionQObject.LOAD_URL, this, getCategory())); }
  }

  private static class MyItemSearcher extends Searcher {
    public String getTypeName() { return "My Items"; }
    protected void fire() { MQFactory.getConcrete(_server).enqueue(new AuctionQObject(AuctionQObject.LOAD_MYITEMS, null, null)); }
  }

  public Searcher getSearchByIndex(int i) { if(i < _searches.size()) return _searches.get(i); else return null; }

  public int findSearch(Searcher s) { return _searches.indexOf(s); }

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

    saveFile = JConfig.getCanonicalFile(saveFile, "jbidwatcher", false);
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
      ErrorManagement.handleException("Failed to save searches.", e);
      saveDone = false;
    }

    return saveDone;
  }

  public void loadSearches() {
    XMLElement xmlFile = new XMLElement(true);
    String loadFile = JConfig.queryConfiguration("search.savefile", "searches.xml");
    String oldLoad = loadFile;

    loadFile = JConfig.getCanonicalFile(loadFile, "jbidwatcher", true);

    if(!loadFile.equals(oldLoad)) {
      JConfig.setConfiguration("search.savefile", loadFile);
    }

    try {
      InputStreamReader isr = new InputStreamReader(new FileInputStream(loadFile));

      xmlFile.parseFromReader(isr);

      if(!xmlFile.getTagName().equals("searches")) {
        throw new XMLParseException(xmlFile.getTagName(), "AuctionsManager only recognizes <searches> tag!");
      } else {
        fromXML(xmlFile);
      }
    } catch(IOException ioe) {
      ErrorManagement.logDebug("JBW: Failed to load saved searches, the search file is probably not there yet.");
      ErrorManagement.logDebug("JBW: This is not an error, unless you are consistently getting it.");
    } catch(Exception e) {
      ErrorManagement.handleException("JBW: Failed to load saved searches, file exists but can't be loaded!", e);
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
      ErrorManagement.logMessage("Failed to create searcher for: " + type);
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

  private SearchManager() {
    AuctionServerManager.getInstance().addSearches(this);
  }

  public static SearchManager getInstance() {
    if(_instance == null) _instance = new SearchManager();

    return _instance;
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
}
