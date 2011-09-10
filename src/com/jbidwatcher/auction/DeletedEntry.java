package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.db.Table;
import com.jbidwatcher.util.config.JConfig;

import java.util.Date;
import java.util.List;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jun 12, 2008
 * Time: 12:35:22 AM
 *
 * Database-backed deleted-entry store.  Tracks the auction identifier of
 * auctions that have been deleted, so they don't get added back in when
 * automated searches load them up.
 */
public class DeletedEntry extends ActiveRecord {
  public DeletedEntry() { }

  public static DeletedEntry create(String identifier) {
    DeletedEntry d = new DeletedEntry(identifier);
    d.saveDB();
    return d;
  }

  private DeletedEntry(String identifier) {
    setString("identifier", identifier);
    setDate("created_at", new Date());
    killFiles(identifier);
  }

  private static Table sDB = null;
  protected static String getTableName() { return "deleted"; }
  protected Table getDatabase() {
    return getRealDatabase();
  }

  protected static Table getRealDatabase() {
    if (sDB == null) {
      sDB = openDB(getTableName());
    }
    return sDB;
  }

  private void killFiles(String id) {
    String imgPath = deleteThumbnails(id);
    if(imgPath == null) return;

    File html = new File(imgPath + ".html.gz");
    if (html.exists()) html.delete();

    File htmlBackup = new File(imgPath + ".html.gz~");
    if (htmlBackup.exists()) htmlBackup.delete();
  }

  public static String deleteThumbnails(String id) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if(outPath == null || outPath.length() == 0) return null;

    String imgPath = outPath + System.getProperty("file.separator") + id;

    File thumb = new File(imgPath + ".jpg");
    if (thumb.exists()) thumb.delete();

    File realThumb = new File(imgPath + "_t.jpg");
    if (realThumb.exists()) realThumb.delete();

    File badBlocker = new File(imgPath + "_b.jpg");
    if (badBlocker.exists()) badBlocker.delete();

    return imgPath;
  }

  public static DeletedEntry findByIdentifier(String identifier) {
    return (DeletedEntry)findFirstBy(DeletedEntry.class, "identifier", identifier);
  }

  public static int clear() {
    int total = getRealDatabase().count();
    if(getRealDatabase().deleteBy("1=1")) return total;
    return 0;
  }

  /**
   * Remove the given identifier from the list of 'deleted' entries.
   *
   * @param identifier - The auction identifier to 'undelete', effectively.
   */
  public static void remove(String identifier) {
    List<ActiveRecord> found = (List<ActiveRecord>) findAllBy(DeletedEntry.class, "identifier", identifier);
    if(found != null) {
      for(ActiveRecord entry : found) {
        entry.delete();
      }
    }
  }

  /**
   * Has the provided auction identifier ever been deleted by the user?
   *
   * @param identifier - The auction identifier to look up.
   *
   * @return - true if the user ever deleted the given auction.
   */
  public static boolean exists(String identifier) {
    return JConfig.queryConfiguration("deleted.ignore", "true").equals("true") && (DeletedEntry.findByIdentifier(identifier) != null);
  }
}
