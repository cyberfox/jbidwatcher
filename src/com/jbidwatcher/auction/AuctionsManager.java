package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*!@class AuctionsManager
 * @brief AuctionsManager abstracts group functionality for all
 * managed groups of auctions
 *
 * So, for example, it supports searching all groups of auctions for
 * outstanding snipes, for snipes that need to fire, for removing,
 * verifying, adding, and retrieving auctions, and similar features
 */

import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.queue.*;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.xml.XMLParseException;
import com.jbidwatcher.*;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServerInterface;
import com.jbidwatcher.auction.server.AuctionStats;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/** @noinspection Singleton*/
public class AuctionsManager implements com.jbidwatcher.util.queue.TimerHandler.WakeupProcess,EntryManager {
  private static AuctionsManager _instance = null;
  private DeletedManager _deleted = null;
  private int _auctionCount = 0;
  private FilterManager _filter;

  //  Checkpoint (save) every N minutes where N is configurable.
  private long _checkpointFrequency;
  private long _lastCheckpointed = 0;
  private static final int AUCTIONCOUNT = 100;
  private static final int MAX_PERCENT = AUCTIONCOUNT;
  private boolean mDoSplash = false;

  /**
   * @brief AuctionsManager is a singleton, there should only be one
   * in the system.
   */
  private AuctionsManager() {
    //  This should be loaded from the configuration settings.
    _checkpointFrequency = 10 * Constants.ONE_MINUTE;
    _lastCheckpointed = System.currentTimeMillis();
    _deleted = new DeletedManager();

    _filter = FilterManager.getInstance();
  }

  static {
    _instance = new AuctionsManager();
  }

  /**
   * @brief The means of getting access to the functions of
   * AuctionsManager, as a Singleton.
   * 
   * @return The one reference to this object.
   */
  public static AuctionsManager getInstance() {
    return _instance;
  }

  /////////////////////////////////////////////////////////
  //  Mass-equivalents for Auction-list specific operations

  /**
   * @brief Determine if any snipes are pending in any of the auctions
   * groups handled.
   * 
   * @return True if any Auctions have pending snipes.
   */
  public boolean anySnipes() {
    for(int i = 0; i<_filter.listLength(); i++) {
      if(_filter.getList(i).anySnipes()) return true;
    }

    return false;
  }

  /**
   * @brief Wake up all Auctions and check to see if anything needs to
   * be done.
   * 
   * @return True if anything needs to be done in any auctions.
   */
  private boolean checkAuctions() {
    boolean retval = false;

    for(int i = 0; i<_filter.listLength(); i++) {
      if(_filter.getList(i).check()) retval = true;
    }

    return retval;
  }

  /**
   * @brief Check if it's time to save the auctions out yet.
   */
  private void checkSnapshot() {
    if( (_lastCheckpointed + _checkpointFrequency) < System.currentTimeMillis() ) {
      _lastCheckpointed = System.currentTimeMillis();
      saveAuctions();
      System.gc();
    }
  }

  /**
   * @brief Check all the auctions for active events, and check if we
   * should snapshot the auctions off to disk.
   * 
   * @return True if it's time to update in one of the auction
   * collections.
   */
  public boolean check() {
    //  The auctions themselves will decide which action this is,
    //  snipe-checks, or updating.
    boolean retval = checkAuctions();

    checkSnapshot();

    return retval;
  }

  /**
   * @brief Verify that an auction entry exists.
   *
   * This should query the filter manager instead of doing it itself.
   * This would let FilterManager handle all this, and AuctionsManager
   * wouldn't need to know anything too much about the items in the
   * auction lists.
   *
   * @note Both Verify and Get should proxy to FilterManager!
   * FUTURE FEATURE -- mrs: 29-September-2001 14:59
   * 
   * @param id - The auction id to search for.
   * 
   * @return - True if the item exists someplace in our list of Auctions.
   */
  public boolean verifyEntry(String id) {
    Auctions whereIs = _filter.whereIsAuction(id);
    return whereIs != null;
  }

  /**
   * @brief Add a new auction entry to the set.
   *
   * This is complex mainly because the splash screen needs to be
   * updated if we're loading from XML at startup, and because the new
   * auction type needs to be split across the hardcoded auction
   * collection types.
   *
   * @param ae - The auction entry to add.
   */
  public void addEntry(AuctionEntry ae) {
    if(mDoSplash) MQFactory.getConcrete("splash").enqueue("SET " + Integer.toString(++_auctionCount));

    FilterManager.getInstance().addAuction(ae);
  }

  /**
   * @brief Delete from ALL auction lists!
   *
   * The FilterManager does this, as it needs to be internally
   * self-consistent.
   * 
   * @param ae - The auction entry to delete.
   */
  public void delEntry(AuctionEntry ae) {
    String id = ae.getIdentifier();
    _deleted.delete(id);
    ae.cancelSnipe(false);
    FilterManager.getInstance().deleteAuction(ae);
    //  TODO -- Actually delete the auction from the database.
    ae.delete();
  }

  /**
   * @brief Get (Retrieve) from ANY auction list an auction matching a
   * given ID.
   * 
   * @param id - The auction ID to search for, and return an AuctionEntry for.
   * 
   * @return An AuctionEntry corresponding to an ID that we found in
   * our list of auctions.
   */
  public AuctionEntry getEntry(String id) {
    Auctions located = _filter.whereIsAuction(id);
    if(located == null) return null;

    return located.getEntry(id);
  }

  /**
   * @brief Return an iterator useful for iterating over all the
   * auction lists managed.
   * 
   * @return An iterator pointing to the first auction list.
   */
  public static Iterator<AuctionEntry> getAuctionIterator() {
    return FilterManager.getInstance().getAuctionIterator();
  }

  public void loadAuctionsFromDB() {
    int auctionTotal = AuctionServerManager.getInstance().getDefaultServer().getCount();
    MQFactory.getConcrete("splash").enqueue("SET 0");
    MQFactory.getConcrete("splash").enqueue("WIDTH " + auctionTotal);

    MQFactory.getConcrete("splash").enqueue("SET 100");
  }

  /**
   * @brief Load auctions from a save file, with a pretty splash
   * screen and everything, if necessary.
   * 
   * I'd like to abstract this, and make it work with arbitrary
   * streams, so that we could send an XML file of auctions over a
   * network to sync between JBidwatcher instances.
   */
  public void loadAuctions() {
    mDoSplash = true;
    XMLElement xmlFile = new XMLElement(true);
    String loadFile = JConfig.queryConfiguration("savefile", "auctions.xml");
    String oldLoad = loadFile;

    loadFile = JConfig.getCanonicalFile(loadFile, "jbidwatcher", true);
    if(!loadFile.equals(oldLoad)) {
      JConfig.setConfiguration("savefile", loadFile);
    }

    File toLoad = new File(loadFile);
    if(toLoad.exists() && toLoad.length() != 0) {
      try {
        loadXMLFromFile(loadFile, xmlFile);
      } catch(IOException ioe) {
        com.jbidwatcher.util.config.ErrorManagement.handleException("A serious problem occurred trying to load from auctions.xml.", ioe);
        MQFactory.getConcrete("Swing").enqueue("ERROR Failure to load your saved auctions.  Some or all items may be missing.");
      } catch(XMLParseException xme) {
        com.jbidwatcher.util.config.ErrorManagement.handleException("Trying to load from auctions.xml.", xme);
        MQFactory.getConcrete("Swing").enqueue("ERROR Failure to load your saved auctions.  Some or all items may be missing.");
      }
    } else {
      //  This is a common thing, and we don't want to frighten new
      //  users, who are most likely to see it.
      com.jbidwatcher.util.config.ErrorManagement.logDebug("JBW: Failed to load saved auctions, the auctions file is probably not there yet.");
      com.jbidwatcher.util.config.ErrorManagement.logDebug("JBW: This is not an error, unless you're constantly getting it.");
    }
    mDoSplash = false;
  }

  private void loadXMLFromFile(String loadFile, XMLElement xmlFile) throws IOException {
    InputStreamReader isr = new InputStreamReader(new FileInputStream(loadFile));
    MQFactory.getConcrete("splash").enqueue("WIDTH " + MAX_PERCENT);
    MQFactory.getConcrete("splash").enqueue("SET " + MAX_PERCENT / 2);

    xmlFile.parseFromReader(isr);
    MQFactory.getConcrete("splash").enqueue("SET " + MAX_PERCENT);

    String formatVersion = xmlFile.getProperty("FORMAT", "0101");
    XMLElement auctionsXML = xmlFile.getChild("auctions");
    JConfig.setConfiguration("savefile.format", formatVersion);
    //  set the width of the splash progress bar based on the number
    //  of auctions that will be loaded!
    if (auctionsXML == null) {
      throw new XMLParseException(xmlFile.getTagName(), "AuctionsManager requires an <auctions> tag!");
    }
    String auctionQuantity = auctionsXML.getProperty("COUNT", null);

    int auctionTotal = 0;
    if(auctionQuantity != null) {
      auctionTotal = Integer.parseInt(auctionQuantity);
      MQFactory.getConcrete("splash").enqueue("SET 0");
      MQFactory.getConcrete("splash").enqueue("WIDTH " + auctionTotal);

      _auctionCount = 0;
    }

    AuctionServerManager.setEntryManager(this);
    AuctionServerManager.getInstance().fromXML(auctionsXML);

    AuctionStats as = AuctionServerManager.getInstance().getStats();

    //  TODO -- Do something more valuable than just notify, when the auction counts are off.
    int savedCount = Integer.parseInt(JConfig.queryConfiguration("last.auctioncount", "-1"));
    if(as != null) {
      if(as.getCount() != auctionTotal || (savedCount != -1 && as.getCount() != savedCount)) {
        MQFactory.getConcrete("Swing").enqueue("NOTIFY Failed to load all auctions.");
      }
    }
    _deleted.fromXML(xmlFile.getChild("deleted"));
  }

  public AuctionEntry newAuctionEntry(String id) {
    String strippedId = stripId(id);

    if(!_deleted.isDeleted(strippedId) && !verifyEntry(strippedId)) {
      return AuctionEntry.buildEntry(id);
    }

    return null;
  }

  public static String stripId(String id) {
    String strippedId = id;
    if(id.startsWith("http")) {
      AuctionServerInterface aucServ = AuctionServerManager.getInstance().getServerForUrlString(id);
      strippedId = aucServ.extractIdentifierFromURLString(id);
    }

    return strippedId;
  }

  public void undelete(String id) {
    _deleted.undelete(id);
  }

  public boolean isDeleted(String id) {
    return _deleted.isDeleted(id);
  }

  private static final int ONEK = 1024;
  private static final StringBuffer _saveBuf = new StringBuffer(AUCTIONCOUNT *ONEK);

  /**
   * @brief Save auctions out to the savefile, in XML format.
   *
   * Similar to the loadAuctions code, this would be nice if it were
   * abstracted to write to any outputstream, allowing us to write to
   * a remote node to update it with our auctions and snipes.
   * 
   * @return - true if it successfully saved, false if an error occurred.
   */
  public boolean saveAuctions() {
    XMLElement auctionsData = AuctionServerManager.getInstance().toXML();
    XMLElement deletedData = _deleted.toXML();
    String oldSave = JConfig.queryConfiguration("savefile", "auctions.xml");
    String saveFilename = JConfig.getCanonicalFile(JConfig.queryConfiguration("savefile", "auctions.xml"), "jbidwatcher", false);
    String newSave=saveFilename;

    ensureDirectories(saveFilename);

    boolean swapFiles = needSwapSaves(saveFilename);

    if(!saveFilename.equals(oldSave)) {
      JConfig.setConfiguration("savefile", saveFilename);
    }

    //  If we already have a save file, preserve its name, and write
    //  the new one to '.temp'.
    if(swapFiles) {
      newSave = saveFilename + ".temp";
      File newSaveFile = new File(newSave);
      if(newSaveFile.exists()) newSaveFile.delete();
    }

    buildSaveBuffer(auctionsData, deletedData);
    boolean saveDone = true;

    //  Dump the save file out!
    try {
      PrintStream ps = new PrintStream(new FileOutputStream(newSave));
      ps.println(_saveBuf);
      ps.close();
    } catch(IOException e) {
      com.jbidwatcher.util.config.ErrorManagement.handleException("Failed to save auctions.", e);
      saveDone = false;
    }

    //  If the save was complete, and we have to swap old/new files,
    //  then [remove prior '.old' file if necessary], save current XML
    //  as '.old', and move most recent save file to be just a normal
    //  save file.
    if(saveDone && swapFiles) {
      preserveFiles(saveFilename);
    }

    return saveDone;
  }

  public int clearDeleted() {
    int rval = _deleted.clearDeleted();

    saveAuctions();
    System.gc();

    return rval;
  }

  private static void ensureDirectories(String saveFilename) {
    //  Thanks to Gabor Liptak for this recommendation...
    File saveParent = new File(saveFilename);
    saveParent = saveParent.getParentFile();
    if(!saveParent.exists()) saveParent.mkdirs(); //  This can fail, but we don't mind.
  }

  public static StringBuffer buildSaveBuffer(XMLElement auctionsData, XMLElement deletedData) {
    synchronized(_saveBuf) {
      _saveBuf.setLength(0);
      _saveBuf.append("<?xml version=\"1.0\"?>\n\n");
      _saveBuf.append(Constants.XML_SAVE_DOCTYPE);
      _saveBuf.append('\n');
      _saveBuf.append("<jbidwatcher format=\"0101\">\n");
      auctionsData.toStringBuffer(_saveBuf, 1);
      if(deletedData != null) {
        deletedData.toStringBuffer(_saveBuf, 1);
      }
      _saveBuf.append("</jbidwatcher>");
    }
    return _saveBuf;
  }

  private static boolean needSwapSaves(String saveName) {
    File oldFile = new File(saveName);
    return oldFile.exists();
  }

  private static void preserveFiles(String filename) {
    File oldFile = new File(filename);
    File saveFile = new File(filename + ".temp");
    SimpleDateFormat sdf = new SimpleDateFormat("ddMMMyy_HHmm");
    String nowStr = sdf.format(new Date());
    String retainFilename = makeBackupFilename(filename, nowStr);
    File retainFile = new File(retainFilename);
    if(retainFile.exists()) retainFile.delete();

    String oldestSave = JConfig.queryConfiguration("save.file.4", "");
    if(oldestSave.length() != 0) {
      File oldest = new File(oldestSave);
      if(oldest.exists()) {
        backupByDate(filename, oldest);
      }
    }

    for(int i=4; i>0; i--) {
      JConfig.setConfiguration("save.file." + i, JConfig.queryConfiguration("save.file." + (i-1), ""));
    }

    File keepFile = new File(retainFilename);
    if(!oldFile.renameTo(keepFile)) {
      com.jbidwatcher.util.config.ErrorManagement.logDebug("Renaming the old file (" + oldFile + ") to the retain file (" + keepFile + ") failed!");
    }
    JConfig.setConfiguration("save.file.0", retainFilename);

    File standard = new File(filename);
    if(!saveFile.renameTo(standard)) {
      com.jbidwatcher.util.config.ErrorManagement.logDebug("Renaming the new file (" + saveFile + ") to the standard filename (" + standard + ") failed!");
    }
  }

  private static void backupByDate(String filename, File oldest) {
    SimpleDateFormat justDateFmt = new SimpleDateFormat("ddMMMyy");
    String justDate = justDateFmt.format(new Date());
    String oldBackup = makeBackupFilename(filename, justDate);
    File oldDateBackup = new File(oldBackup);
    if(oldDateBackup.exists()) {
      oldDateBackup.delete();
      File newDateBackup = new File(oldBackup);
      oldest.renameTo(newDateBackup);
    } else {
      oldest.renameTo(oldDateBackup);
      String oldestByDate = JConfig.queryConfiguration("save.bydate.4", "");
      for(int i=4; i>0; i--) {
        JConfig.setConfiguration("save.bydate." + i, JConfig.queryConfiguration("save.bydate." + (i-1), ""));
      }
      JConfig.setConfiguration("save.bydate.0", oldBackup);
      File deleteMe = new File(oldestByDate);
      deleteMe.delete();
    }
  }

  private static String makeBackupFilename(String filename, String toInsert) {
    int lastSlash = filename.lastIndexOf(System.getProperty("file.separator"));
    if(lastSlash == -1) {
      com.jbidwatcher.util.config.ErrorManagement.logDebug("Filename has no separators: " + filename);
      lastSlash = 0;
    }
    int firstDot = filename.indexOf('.', lastSlash);
    if(firstDot == -1) {
      com.jbidwatcher.util.config.ErrorManagement.logDebug("Filename has no dot/extension: " + filename);
      firstDot = filename.length();
    }

    return filename.substring(0, firstDot) + '-' + toInsert + filename.substring(firstDot);
  }
}
