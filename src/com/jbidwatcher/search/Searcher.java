package com.jbidwatcher.search;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLSerializeSimple;
import com.jbidwatcher.util.Constants;

public abstract class Searcher extends XMLSerializeSimple {
  protected long _id;             /**< The unique id for this search. */
  protected String _name="";      /**< The users name for this search. */
  protected String _search="";    /**< Any needed data to search for. */
  protected String _server="";    /**< The auction server the search is for. */
  protected String _category;     /**< The target tab for this search. */
  protected String _currency;     /**< The currency to limit to for this search. */
  protected int _period;          /**< How often to run this search, in hours. */
  protected long _last;           /**< The last time this search ran. */
  protected boolean _enabled;     /**< Is this search enabled for periodic updating? */
  protected boolean _skip_deleted; /**< Should this search add items irregardless of their deleted status? */
  protected boolean _initialized; /**< Has this search been filled out? */

  //  SHOULD only be used for imports, and the like.
  public Searcher() {
    _id = 0;
    _last   = 0;
    _initialized = false;
    _skip_deleted = true;
  }

  public Searcher(String name, String search, String server, int period) {
    _id = System.currentTimeMillis();
    _name = name;
    _search = search;
    _period = period;
    _server = server;
    _enabled = (period != -1);
    _initialized = true;
    _skip_deleted = true;
    _last   = 0;
  }

  public boolean isInitialized() { return _initialized; }
  public boolean shouldSkipDeleted() { return _skip_deleted; }
  public void setSkipDeleted(boolean skip) { _skip_deleted = skip; }

  public String getName() { return _name; }
  public void setName(String newName) { _name = newName; _initialized = true; }

  public String getSearch() { return _search; }
  public void setSearch(String newSearch) { _search = newSearch; _initialized = true; }

  public boolean hasCategory() { return _category != null; }
  public String getCategory() { return _category == null?getName():_category; }
  public void setCategory(String category) { _category = category; }

  public String getServer() { return _server; }
  public void setServer(String newServer) { _server = newServer; _initialized = true; }

  public String getCurrency() { return _currency; }
  public void setCurrency(String currency) { _currency = currency; _initialized = true; }

  public int getPeriod() { return _period; }
  public void setPeriod(int newPeriod) { _period = newPeriod; _initialized = true; }

  public void setId(long id) { _id = id; _initialized = true; }
  public long getId() { return _id; }

  public boolean isEnabled() { return _enabled; }
  public void enable() { _enabled = true; }
  public void disable() { _enabled = false; }

  public long getLastRun() { return _last; }
  protected void setLastRun() { _last = System.currentTimeMillis(); }
  protected void setLastRun(long last) { _last = last; }

  public boolean shouldExecute() {
    if(_enabled && _period != -1) {
      long curTime = System.currentTimeMillis();

      //  If the last time we ran, plus the repeat period is earlier or
      //  exactly at the current time, then it's time to run the search.
      if( (_last + (_period * Constants.ONE_HOUR)) <= curTime) return true;
    }

    return false;
  }

  public abstract String getTypeName();
  public void execute() { setLastRun(); fire(); }
  protected abstract void fire();

  /** @noinspection FeatureEnvy*/
  public XMLElement toXML() {
    XMLElement search = new XMLElement("search");

    search.setProperty("type", getTypeName());
    search.setProperty("id", Long.toString(getId()));

    XMLElement xname = new XMLElement("name");
    xname.setContents(getName());
    search.addChild(xname);

    XMLElement xsearch = new XMLElement("search");
    xsearch.setContents(getSearch());
    search.addChild(xsearch);

    XMLElement xperiod = new XMLElement("period");
    xperiod.setContents(Integer.toString(getPeriod()));
    search.addChild(xperiod);

    XMLElement xserver = new XMLElement("server");
    xserver.setContents(getServer());
    search.addChild(xserver);

    XMLElement xcurrency = new XMLElement("currency");
    xcurrency.setContents(getCurrency());
    search.addChild(xcurrency);

    XMLElement xlast = new XMLElement("last");
    xlast.setContents(Long.toString(getLastRun()));
    search.addChild(xlast);

    if(_enabled) {
      XMLElement xenabled = new XMLElement("enabled");
      xenabled.setEmpty();
      search.addChild(xenabled);
    }

    if(!_skip_deleted) {
      XMLElement xfinddeleted = new XMLElement("finddeleted");
      xfinddeleted.setEmpty();
      search.addChild(xfinddeleted);
    }

    XMLElement xcat = new XMLElement("category");
    if(_category == null) {
      xcat.setContents(getName());
    } else {
      xcat.setContents(_category);
    }
    search.addChild(xcat);

    _initialized = true;

    return search;
  }

  protected String[] infoTags = { "name", "search", "period", "server", "last", "enabled", "category", "currency", "finddeleted" };
  protected String[] getTags() { return infoTags; }

  protected void handleTag(int i, XMLElement curElement) {
    String contents = curElement.getContents();

    switch(i) {
      case 0: // Search name
        setName(contents);
        break;
      case 1:
        setSearch(contents);
        break;
      case 2:
        setPeriod(Integer.parseInt(contents));
        break;
      case 3:
        setServer(contents);
        break;
      case 4:
        setLastRun(Long.parseLong(contents));
        break;
      case 5:
        enable();
        break;
      case 6:
        setCategory(contents);
        break;
      case 7:
        setCurrency(contents);
        break;
      case 8:
        setSkipDeleted(false);
        break;
      default:
        //  For forwards compatibility, ignore unrecognized fields.
        break;
    }
  }
}
