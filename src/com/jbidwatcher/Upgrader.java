package com.jbidwatcher;

import com.jbidwatcher.util.db.Database;
import com.jbidwatcher.util.db.Table;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.Record;
import com.jbidwatcher.util.HashBacked;
import com.jbidwatcher.util.config.JConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.text.NumberFormat;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 24, 2007
 * Time: 5:32:20 AM
 *
 * Centralize all code to do upgrading-related operations in this class.
 */
public class Upgrader {
  public static void upgrade() throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException {
    Database db = new Database(null);
    if(dbMake(db)) {
      dbMigrate(db);
    }
    db.commit();
    db.shutdown();
  }

  private static void dbMigrate(Database db) throws IllegalAccessException, SQLException, ClassNotFoundException, InstantiationException {
    Table schemaInfo = new Table("SCHEMA_INFO");
    List<Record> info = schemaInfo.findAll();
    if(info != null) {
      Record first = info.get(0);
      HashBacked record = new HashBacked(first);
      int version = record.getInteger("version", -1);
      if(version != -1) {
        int last_version = version;
        version++;
        NumberFormat nf = NumberFormat.getIntegerInstance();
        nf.setMinimumIntegerDigits(3);
        Statement s = db.getStatement();
        while(runFile(s, "/db/" + nf.format(version) + ".sql")) {
          record.setInteger("version", version);
          schemaInfo.updateMap("SCHEMA_INFO", "version", Integer.toString(last_version), record.getBacking());
          last_version = version;
          version++;
        }
      }
    }
  }

  private static boolean dbMake(Database db) {
    try {
      /*
         Creating a statement lets us issue commands against
         the connection.
       */
      Statement mS = db.getStatement();

      ResultSet rs = mS.getConnection().getMetaData().getTables(null, null, "SCHEMA_INFO", null);
      if(!rs.next()) {
        runFile(mS, "/jbidwatcher.sql");
        JConfig.setConfiguration("jbidwatcher.created_db", "true");
      } else {
        ErrorManagement.logDebug("Auction information database already exists.");
      }
      rs.close();
    } catch (SQLException se) {
      System.err.println(se);
      return false;
    }
    return true;
  }

  private static boolean runFile(Statement mS, String filename) throws SQLException {
    String sql = StringTools.cat(JConfig.getResource(filename));
    if(sql != null && sql.length() != 0) {
      String[] statements = sql.split("(?m)^$");
      for (String statement : statements) {
        System.err.println("statement == " + statement);
        mS.execute(statement);
      }

      ErrorManagement.logDebug("Executed " + filename + ".");
      ErrorManagement.logDebug("Created database and various tables.");
      return true;
    } else {
      return false;
    }
  }
}
