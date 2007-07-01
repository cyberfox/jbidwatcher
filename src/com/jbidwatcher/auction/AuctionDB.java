package com.jbidwatcher.auction;

import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.Database;

import java.sql.*;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;

/**
 * Wrap the auction information up in a database.
 */
public class AuctionDB {
  private Database mDB;
  private Statement mS;
  private Map<String, String> mTypeMap;

  /**
   * Create or open a database for storing auction information.
   *
   * @throws SQLException - If there's something wrong with the SQL to create the database.
   * @throws IllegalAccessException - If the database is not able to be accessed.
   * @throws InstantiationException - If we can't create the JDBC driver for the database.
   * @throws ClassNotFoundException - If we can't find the JDBC driver for the database at all.
   */
  public AuctionDB() throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException {
    mDB = new Database(null);
    PreparedStatement query = mDB.prepare("SELECT * FROM auctions");
    mTypeMap = getMetadata(query.getMetaData());
    mS = mDB.getStatement();
  }

  /**
   * Close the statement, commit any last outstanding data (!?) and shut down the database.
   */
  public void shutdown() {
    try {
      mS.close();
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    mDB.commit();
    mDB.shutdown();
  }

  public Map<String,String> loadMap(String tableName, int id) {
    Map<String,String> rval;
    try {
      ResultSet rs = mS.executeQuery("SELECT * FROM " + tableName);
      rval = makeMapFromResults(rs);
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }
    return rval;
  }

  public Map<String,String> loadQueryMap(String query) {
    Map<String,String> rval;
    try {
      ResultSet rs = mS.executeQuery(query);
      rval = makeMapFromResults(rs);
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }
    return rval;
  }

  private Map<String, String> makeMapFromResults(ResultSet rs) throws SQLException {
    Map<String, String> rval;
    rval = new HashMap<String, String>();
    for(int i=0; i < rs.getMetaData().getColumnCount(); i++) {
      rval.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getString(i));
    }
    return rval;
  }

  public boolean updateMap(String tableName, String columnKey, String value, Map<String,String> newRow) {
    Map<String, String> oldRow = getOldRow(tableName, columnKey, value);
    if(oldRow == null) return storeMap(tableName, newRow);

    String sql = createPreparedUpdate(tableName, oldRow, newRow);
    if(sql == null) return false;

    try {
      PreparedStatement ps = mDB.prepare(sql);

      int column = 1;
      for(String key: newRow.keySet()) {
        if (newRow.get(key) != null) {
          if(!setColumn(ps, column++, mTypeMap.get(key), newRow.get(key))) {
            System.err.println("Error from columns: (" + column + "," + key + ", " + mTypeMap.get(key) + ", " + newRow.get(key) + ")");
          }
        }
      }
      ps.execute();
      mDB.commit();
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private Map<String, String> getOldRow(String tableName, String columnKey, String value) {
    Map<String, String> oldRow = null;
    try {
      ResultSet rs = mS.executeQuery("SELECT * FROM " + tableName + " FOR UPDATE WHERE " + columnKey + "=" + value);
      oldRow = makeMapFromResults(rs);
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    return oldRow;
  }

  public boolean storeMap(String tableName, Map<String,String> newRow) {
    String sql = createPreparedInsert(tableName, newRow);
    if(sql == null) return false;

    try {
      PreparedStatement ps = mDB.prepare(sql);

      int column = 1;
      for(String key: newRow.keySet()) {
        if (newRow.get(key) != null) {
          if(!setColumn(ps, column++, mTypeMap.get(key), newRow.get(key))) {
            System.err.println("Error from columns: (" + column + "," + key + ", " + mTypeMap.get(key) + ", " + newRow.get(key) + ")");
          }
        }
      }
      ps.execute();
      mDB.commit();
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private String createPreparedInsert(String tableName, Map<String, String> newRow) {
    String sql = null;
    boolean anyKeys = false;
    StringBuffer insert = new StringBuffer("INSERT INTO " + tableName + " (");
    StringBuffer values = new StringBuffer('(');
    for (String key : newRow.keySet()) {
      if(newRow.get(key) != null) {
        if (anyKeys) {
          insert.append(',');
          values.append(',');
        }
        insert.append(key);
        values.append('?');
        anyKeys = true;
      }
    }
    if (anyKeys) {
      sql = insert + ") VALUES (" + values + ")";
    }
    return sql;
  }

  private String createPreparedUpdate(String tableName, Map<String, String> oldRow, Map<String, String> newRow) {
    boolean anyKeys = false;
    StringBuffer update = new StringBuffer("UPDATE " + tableName + " SET ");
    for (String key : newRow.keySet()) {
      String newVal = newRow.get(key);
      String oldVal = oldRow.get(key);
      if(!(newVal == null && oldVal == null)) {
        if (newVal == null || oldVal == null || !newVal.equals(oldVal)) {
          if (anyKeys) {
            update.append(',');
          }
          update.append(key).append('=').append('?');
          anyKeys = true;
        }
      }
    }
    return anyKeys?update.toString():null;
  }

  private boolean setColumn(PreparedStatement ps, int column, String type, String val) {
    try {
      if (type.equals("DECIMAL")) {
        if (val.length() == 0) {
          ps.setBigDecimal(column, null);
        } else {
          ps.setBigDecimal(column, BigDecimal.valueOf(Double.parseDouble(val)));
        }
      } else if (type.equals("VARCHAR")) {
        ps.setString(column, val);
      } else if (type.equals("CHAR")) {
        ps.setString(column, val);
      } else if (type.equals("TIMESTAMP")) {
        ps.setTimestamp(column, Timestamp.valueOf(val));
      } else if (type.equals("INTEGER")) {
        if(val.length()==0) {
          ps.setInt(column, -1);
        } else {
          ps.setInt(column, Integer.parseInt(val));
        }
      } else {
        System.err.println("WTF?!?!");
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private Map<String, String> getMetadata(ResultSetMetaData rsmd) {
    Map<String, String> typeMap = null;
    try {
      typeMap = new HashMap<String, String>();
      for (int i = 1; i <= rsmd.getColumnCount(); i++) {
        String key = rsmd.getColumnName(i).toLowerCase();
        String value = rsmd.getColumnTypeName(i);
        typeMap.put(key, value);
      }
    } catch (SQLException e) {
      //  TODO mrs -- Make this more JBidwatcher-y
      e.printStackTrace();
    }
    return typeMap;
  }

  public Map<String, String> loadMap(int id) {
    return loadMap("auctions", id);
  }

  public boolean storeMap(Map<String, String> newRow) {
    return storeMap("auctions", newRow);
  }

  class ResultMap extends HashMap<String, String> { };

  public int count() {
    Map<String, String> rm = loadQueryMap("SELECT COUNT(*) AS count FROM auctions");
    String count = rm.get("count");
    return Integer.parseInt(count);  //To change body of created methods use File | Settings | File Templates.
  }
}
