package com.jbidwatcher;

import com.jbidwatcher.util.Database;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.config.JConfig;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.*;

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
    Statement mS;

    try {
      /*
         Creating a statement lets us issue commands against
         the connection.
       */
      mS = db.getStatement();

      /*
       *  Drop the old database, just to be sure...
       */
      ResultSet rs = mS.getConnection().getMetaData().getTables(null, null, "AUCTIONS", null);
      if(!rs.next()) {
        /*
        * We create a table, add a few rows, and update one.
        */
        String sql = StringTools.cat(StringTools.class.getResource("/jbidwatcher.sql"));
        if(sql == null) sql = StringTools.cat(JConfig.getCanonicalFile("jbidwatcher.sql", "jbidwatcher", true));
        if(sql != null) {
          String[] statements = sql.split("(?m)^$");
          for (String statement : statements) {
            System.err.println("sql == " + statement);
            mS.execute(statement);
          }

          ErrorManagement.logDebug("Created table auctions");
        }
      } else {
        ErrorManagement.logDebug("Auctions table already exists.");
      }
      rs.close();
    } catch (SQLException se) {
      System.err.println(se);
      return false;
    }
    return true;
  }
}
