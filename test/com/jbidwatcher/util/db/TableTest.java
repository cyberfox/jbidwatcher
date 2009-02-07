package com.jbidwatcher.util.db;

import junit.framework.TestCase;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.Record;
import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.Upgrader;

import java.util.List;
import java.sql.Savepoint;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Jan 31, 2009
 * Time: 5:54:49 PM
 *
 *
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
public class TableTest extends TestCase {
  Savepoint mSavepoint = null;
  public TableTest(String name) {
    super(name);
  }

  public void setUp() throws Exception {
    super.setUp();
    JConfig.setLogger(new ErrorManagement());
    JConfig.setConfiguration("db.user", "test_tables");
    JConfig.setConfiguration("db.autocommit", "false");
    Upgrader.upgrade();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }

  public void testCountEntries() throws Exception {
    Table t = new Table("entries");
    assertEquals(0, t.count());
  }

  public void testInsertData() throws Exception {
    Table t = new Table("categories");
    mSavepoint = t.getDB().getConnection().setSavepoint();
    assert(t.execute("DELETE FROM categories"));
    Record r = new Record();
    r.put("id", "1");
    r.put("name", "zarf");
    t.storeMap(r);
    List<Record> recs = t.findAll();
    for(Record rec : recs) {
      HashBacked h = new HashBacked(rec);
      System.err.println(h.dumpRecord());
    }
    assertEquals(1, t.count());
    t.getDB().getConnection().rollback(mSavepoint);
  }

  public void testDeleteData() throws Exception {
    Table t = new Table("categories");
    mSavepoint = t.getDB().getConnection().setSavepoint();
    assert(t.execute("DELETE FROM categories"));
    t.getDB().getConnection().rollback(mSavepoint);
    t.commit();
  }
}
