package com.jbidwatcher;

import com.jbidwatcher.util.Database;
import com.jbidwatcher.util.ErrorManagement;

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
        mS.execute("CREATE TABLE auctions(" +
            "id INT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)," +
            "auction_id VARCHAR(40)," +
            "title VARCHAR(255)," +
            "start_time TIMESTAMP," +
            "end_time TIMESTAMP," +
            "seller VARCHAR(200)," +
            "high_bidder VARCHAR(200)," +
            "high_bidder_email VARCHAR(255)," +
            "quantity INT,bid_count INT," +
            "insurance_optional CHAR," +
            "fixed_price CHAR," +
            "no_thumbnail CHAR," +
            "dutch CHAR," +
            "reserve CHAR," +
            "private CHAR," +
            "reserve_met CHAR," +
            "has_thumbnail CHAR," +
            "outbid CHAR," +
            "paypal CHAR," +
            "feedback INT," +
            "feedback_percent DECIMAL(5,2)," +
            "currency VARCHAR(10)," +
            "current_bid DECIMAL(10,2)," +
            "minimum_bid DECIMAL(10,2)," +
            "shipping DECIMAL(10,2)," +
            "insurance DECIMAL(10,2)," +
            "buy_now DECIMAL(10,2)," +
            "usd_current DECIMAL(10,2)," +
            "usd_buy_now DECIMAL(10,2))");
        mS.execute("CREATE INDEX IDX_AuctionId ON auctions(auction_id)");
        mS.execute("CREATE INDEX IDX_EndTime ON auctions(end_time)");
        mS.execute("CREATE INDEX IDX_Seller ON auctions(seller)");
        ErrorManagement.logDebug("Created table auctions");
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
