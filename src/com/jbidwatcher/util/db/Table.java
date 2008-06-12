package com.jbidwatcher.util.db;

import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.Record;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Wrap the auction information up in a database.
 */
public class Table
{
  public boolean hasColumn(String colName) {
    return mColumnMap.containsKey(colName);
  }

  private static class TypeColumn {
    private String mType;
    private Integer mIndex;

    private TypeColumn(String type, Integer index) {
      mType = type;
      mIndex = index;
    }

    public String getType() {
      return mType;
    }

    public Integer getIndex() {
      return mIndex;
    }
  }

  private Database mDB;
  private Statement mS;
  private Map<String, TypeColumn> mColumnMap;
  private String mTableName;

  /**
   * Create or open a database for storing auction information.
   *
   * @param tablename - The name of the table this auctionDB reference will be talking to.
   *
   * @throws SQLException - If there's something wrong with the SQL to create the database.
   * @throws IllegalAccessException - If the database is not able to be accessed.
   * @throws InstantiationException - If we can't create the JDBC driver for the database.
   * @throws ClassNotFoundException - If we can't find the JDBC driver for the database at all.
   */
  public Table(String tablename) throws SQLException, IllegalAccessException, InstantiationException, ClassNotFoundException {
    mDB = new Database(null);
    mTableName = tablename;

    PreparedStatement query = mDB.prepare("SELECT * FROM " + mTableName);

    establishMetadata(query.getMetaData());
    mS = mDB.getStatement();
  }

  /**
   * Close the statement, commit any last outstanding data (!?) and shut down the database.
   */
  public void shutdown() {
    try {
      mS.close();
    } catch (SQLException e) {
      ErrorManagement.handleException("Can't shut down database.", e);
    }
    mDB.commit();
    mDB.shutdown();
  }

  public void commit() {
    mDB.commit();
  }

  public Record find(int id) {
    return findFirst("SELECT * FROM " + mTableName + " where id = " + id);
  }

  public boolean delete(int id) {
    try {
      PreparedStatement ps = mDB.prepare("DELETE FROM " + mTableName + " WHERE id = " + id);
      ps.execute();
      mDB.commit();
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return false;
    }
    return true;
  }

  public boolean deleteBy(String condition) {
    return execute("DELETE FROM " + mTableName + " WHERE " + condition);
  }

  public boolean execute(String statement) {
    try {
      PreparedStatement ps = mDB.prepare(statement);
      ps.execute();
      mDB.commit();
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return false;
    }
    return true;
  }

  public Record findFirst(String query) {
    try {
      ResultSet rs = mS.executeQuery(query);
      return getFirstResult(rs);
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }
  }

  public Record findFirstBy(String key, String value) {
    return findByColumn(key, value);
  }

  public Record findFirstBy(String conditions) {
    try {
      ResultSet rs = mS.executeQuery("select * FROM " + mTableName + " WHERE " + conditions);
      return getFirstResult(rs);
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }
  }

  public List<Record> findAll(String key, String value, String order) {
    String statement = "SELECT * FROM " + mTableName;
    statement += " WHERE " + key + " = ?";

    if(order != null) {
      statement += " ORDER BY " + order;
    }

    try {
      PreparedStatement ps = mDB.prepare(statement);
      setColumn(ps, 1, key, value);
      ResultSet rs = ps.executeQuery();
      return getAllResults(rs);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return null;
  }

  public List<Record> findAll() {
    return findAll("SELECT * FROM " + mTableName);
  }

  public List<Record> findAll(String query) {
    try {
      ResultSet rs = mS.executeQuery(query);
      return getAllResults(rs);
    } catch (SQLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      return null;
    }
  }

  private Record getFirstResult(ResultSet rs) throws SQLException {
    Record rval = new Record();
    ResultSetMetaData rsm = rs.getMetaData();
    if (rsm != null) {
      if (rs.next()) {
        for (int i = 1; i <= rsm.getColumnCount(); i++) {
          rval.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getString(i));
        }
      }
    }
    return rval;
  }

  private List<Record> getAllResults(ResultSet rs) throws SQLException {
    ArrayList<Record> rval = new ArrayList<Record>();
    ResultSetMetaData rsm = rs.getMetaData();
    if (rsm != null) {
      while(rs.next()) {
        Record row = new Record();
        for (int i = 1; i <= rsm.getColumnCount(); i++) {
          row.put(rs.getMetaData().getColumnName(i).toLowerCase(), rs.getString(i));
        }
        rval.add(row);
      }
    }
    return rval;
  }

  public String insertOrUpdate(Record row) {
    String value = row.get("id");
    if(value != null && value.length() == 0) value = null;
    return updateMap(mTableName, "id", value, row);
  }

  public String updateMap(String tableName, String columnKey, String value, Record newRow) {
    Record oldRow = null;
    if(value != null) {
      oldRow = getOldRow(tableName, columnKey, value, true);
    }
    if(value == null || oldRow == null) {
      return storeMap(newRow);
    }

    String sql = createPreparedUpdate(tableName, oldRow, newRow);
    if(sql == null) return null;

    sql += " WHERE " + columnKey + " = ?";
    try {
      PreparedStatement ps = mDB.prepare(sql);

      int colCount = setPreparedUpdate(ps, oldRow, newRow);
      if(colCount != -1) {
        //  Set the 'WHERE' value.
        setColumn(ps, colCount, columnKey, value);
        ps.execute();
        mDB.commit();
        return findKeys(ps);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  public Record findByColumn(String columnKey, String value) {
    return findByColumn(columnKey, value, false);
  }

  public Record findByColumn(String columnKey, String value, boolean forUpdate) {
    return getOldRow(mTableName, columnKey, value, forUpdate);
  }

  private Record getOldRow(String tableName, String columnKey, String value, boolean forUpdate) {
    Record oldRow = null;

    try {
      String statement = "SELECT * FROM " + tableName;
      statement += " WHERE " + columnKey + " = ?";
      if (forUpdate) statement += " FOR UPDATE";
      PreparedStatement ps = mDB.prepare(statement);
      setColumn(ps, 1, columnKey, value);
      ResultSet rs = ps.executeQuery();
      oldRow = getFirstResult(rs);
    } catch (SQLException e) {
      ErrorManagement.handleException("Can't get row" + (forUpdate? " for update":"") + " (" + columnKey + " = '" + value +"').", e);
    }
    return oldRow;
  }

  public String storeMap(Record newRow) {
    String sql = createPreparedInsert(mTableName, newRow);
    if(sql == null) return null;

    try {
      PreparedStatement ps = mDB.prepare(sql);

      int column = 1;
      for(String key: newRow.keySet()) {
        if(key.equals("id")) continue;
        if(!setColumn(ps, column++, key, newRow.get(key))) {
          ErrorManagement.logDebug("Error from columns: (" + column + ", " + key + ", " + mColumnMap.get(key).getType() + ", " + newRow.get(key) + ")");
        }
      }
      ps.execute();
      mDB.commit();
      return findKeys(ps);
    } catch (SQLException e) {
      ErrorManagement.handleException("Can't store row in table.", e);
    }
    return null;
  }

  private String findKeys(PreparedStatement ps) throws SQLException {
    ResultSet rs = ps.getGeneratedKeys();
    if(rs != null) {
      Record insertMap = getFirstResult(rs);
      if (insertMap.containsKey("1")) return insertMap.get("1");
    }
    return "";
  }

  private String createPreparedInsert(String tableName, Record newRow) {
    boolean anyKeys = false;
    StringBuffer insert = new StringBuffer("INSERT INTO " + tableName + " (");
    StringBuffer values = new StringBuffer();
    for (String key : newRow.keySet()) {
      if(key.equals("id")) continue;
      if (anyKeys) {
        insert.append(',');
        values.append(',');
      }
      insert.append(key);
      values.append('?');
      anyKeys = true;
    }
    String sql = null;
    if (anyKeys) {
      sql = insert + ") VALUES (" + values + ")";
    }
    return sql;
  }

  private String createPreparedUpdate(String tableName, Record oldRow, Record newRow) {
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

  private int setPreparedUpdate(PreparedStatement ps, Record oldRow, Record newRow) {
    boolean errors = false;
    int column = 1;
    for (String key : newRow.keySet()) {
      String newVal = newRow.get(key);
      String oldVal = oldRow.get(key);
      if (!(newVal == null && oldVal == null)) {
        if (newVal == null || oldVal == null || !newVal.equals(oldVal)) {
          if(!setColumn(ps, column++, key, newRow.get(key))) {
            ErrorManagement.logMessage("Error from columns: (" + column + "," + key + ", " + mColumnMap.get(key).getType() + ", " + newRow.get(key) + ")");
            errors = true;
          }
        }
      }
    }
    return errors ? -1 : column;
  }

  private boolean setColumn(PreparedStatement ps, int column, String key, String val) {
    String type = mColumnMap.get(key).getType();
    try {
      if (type.equals("DECIMAL")) {
        if(val == null) ps.setNull(column, java.sql.Types.DECIMAL);
        else {
          if (val.length() == 0) {
            ps.setBigDecimal(column, null);
          } else {
            ps.setBigDecimal(column, BigDecimal.valueOf(Double.parseDouble(val)));
          }
        }
      } else if (type.equals("VARCHAR")) {
        if (val == null) ps.setNull(column, java.sql.Types.VARCHAR);
        else ps.setString(column, val);
      } else if (type.equals("CHAR")) {
        if (val == null) ps.setNull(column, java.sql.Types.CHAR);
        else ps.setString(column, val);
      } else if (type.equals("TIMESTAMP")) {
        if (val == null) ps.setNull(column, java.sql.Types.TIMESTAMP);
        else ps.setTimestamp(column, Timestamp.valueOf(val));
      } else if (type.equals("INTEGER")) {
        if (val == null) ps.setNull(column, java.sql.Types.INTEGER);
        else if(val.length()==0) {
          ps.setInt(column, -1);
        } else {
          ps.setInt(column, Integer.parseInt(val));
        }
      } else if (type.equals("SMALLINT")) {
        if (val == null) ps.setNull(column, java.sql.Types.SMALLINT);
        else if(val.equals("Y")) ps.setShort(column, (short) 1);
        else if(val.equals("N")) ps.setShort(column, (short) 0);
        else {
          try {
            ps.setShort(column, Short.parseShort(val));
          } catch (NumberFormatException nfe) {
            ps.setNull(column, java.sql.Types.SMALLINT);
          }
        }
      } else {
        ErrorManagement.logDebug("WTF?!?!");
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void establishMetadata(ResultSetMetaData rsmd) {
    try {
      mColumnMap = new HashMap<String, TypeColumn>();
      for (int i = 1; i <= rsmd.getColumnCount(); i++) {
        String key = rsmd.getColumnName(i).toLowerCase();
        String value = rsmd.getColumnTypeName(i);

        mColumnMap.put(key, new TypeColumn(value, i));
      }
    } catch (SQLException e) {
      ErrorManagement.handleException("Can't load metadata for table " + mTableName + ".", e);
    }
  }

  public int count() {
    Record rm = findFirst("SELECT COUNT(*) AS count FROM " + mTableName);
    String count = rm.get("count");
    return Integer.parseInt(count);
  }

  public int count_by(String condition) {
    Record rm = findFirst("SELECT COUNT(*) AS count FROM " + mTableName + " WHERE " + condition);
    String count = rm.get("count");
    return Integer.parseInt(count);
  }

  public Database getDB() {
    return mDB;
  }

  public Set<String> getColumns() {
    return mColumnMap.keySet();
  }
}
