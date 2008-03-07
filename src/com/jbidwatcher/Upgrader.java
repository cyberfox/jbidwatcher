package com.jbidwatcher;

import com.jbidwatcher.util.db.Database;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.config.JConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
    Database mDB = new Database(null);
    dbMake(mDB);
    mDB.commit();
    mDB.shutdown();
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
//        runFile(mS, "/upgrade.sql");
        ErrorManagement.logDebug("Auction information database already exists.");
      }
      rs.close();
    } catch (SQLException se) {
      System.err.println(se);
      return false;
    }
    return true;
  }

  private static void runFile(Statement mS, String filename) throws SQLException {
    String sql = StringTools.cat(JConfig.getResource(filename));
//    if(sql == null) sql = StringTools.cat(JConfig.getCanonicalFile("jbidwatcher.sql", "jbidwatcher", true));
    if(sql != null) {
      String[] statements = sql.split("(?m)^$");
      for (String statement : statements) {
        System.err.println("statement == " + statement);
        mS.execute(statement);
      }

      ErrorManagement.logDebug("Executed " + filename + ".");
      ErrorManagement.logDebug("Created database and various tables.");
    }
  }
}
