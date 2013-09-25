package com.jbidwatcher.util.db;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Record;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Wrap the auction information up in a database.
 */
public class Table
{
  private static boolean STATEMENT_DEBUG = false;

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
  private DateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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
    mDateFormat.setTimeZone(TimeZone.getDefault());

    PreparedStatement query = mDB.prepare("SELECT * FROM " + mTableName);

    establishMetadata(query.getMetaData());
    mS = mDB.getStatement();
  }

  /**
   * Close the statement, commit any last outstanding data (!?) and shut down the database.
   * @return The database that was shut down.
   */
  public Database shutdown() {
    synchronized (mS) {
      try {
        mS.close();
      } catch (SQLException e) {
        JConfig.log().handleException("Can't shut down database.", e);
      }
      mDB.commit();
      return mDB;
    }
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
    if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing: " + statement);
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
    if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing fF query: " + query);

    synchronized (mS) {
      try {
        ResultSet rs = mS.executeQuery(query);
        return getFirstResult(rs);
      } catch (SQLException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        return null;
      }
    }
  }

  public Record findFirstBy(String key, String value) {
    return findByColumn(key, value);
  }

  public Record findFirstBy(String query) {
    if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing fFB query: " + query);

    synchronized (mS) {
      try {
        ResultSet rs = mS.executeQuery(query);
        return getFirstResult(rs);
      } catch (SQLException e) {
        e.printStackTrace();
        return null;
      }
    }
  }

  public List<Record> findAllMulti(String[] keys, String[] values, String order) {
    return findAllMulti(keys, values, null, order);
  }

  public List<Record> findAllMulti(String[] keys, String[] values, String[] comparisons, String order) {
    if(keys != null && keys.length != values.length) {
      JConfig.log().logMessage("Multi-find with varying key and value lengths!");
      return null;
    }
    if(keys != null && comparisons != null && keys.length != comparisons.length) {
      JConfig.log().logMessage("Multi-find with varying key and comparisons lengths!");
    }

    StringBuffer statement = new StringBuffer("SELECT * FROM " + mTableName);
    if(keys != null && keys.length != 0) {
      statement.append(" WHERE ");
      boolean start = true;
      for (int i = 0; i < keys.length; i++) {
        String key = keys[i];

        if(!start) statement.append(" AND ");
        statement.append(key);
        if(comparisons == null) {
          statement.append('=');
        } else {
          statement.append(comparisons[i]);
        }
        statement.append('?');
        start = false;
      }
    }

    if(order != null) statement.append(" ORDER BY ").append(order);

    try {
      PreparedStatement ps = mDB.prepare(statement.toString());
      if(keys != null && keys.length != 0) {
        int colnum = 1;
        for(int i=0; i<values.length; i++) {
          setColumn(ps, colnum, keys[i], values[i]);
        }
      }
      if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing fAM query: " + statement);

      ResultSet rs = execute(ps);
      return getAllResults(rs);
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return null;
  }

  public List<Record> findAll(String key, String value, String order) {
    String[] keys = {key};
    String[] values = {value};

    return findAllMulti(keys, values, order);
  }

  public List<Record> findAllComparator(String key, String comparison, String value, String order) {
    String[] keys = {key};
    String[] values = {value};
    String[] comparisons = {comparison};
    return findAllMulti(keys, values, comparisons, order);
  }

  public List<Record> findAll() {
    return findAll("SELECT * FROM " + mTableName, 0);
  }

  public List<Record> findAll(String query, int count) {
    synchronized (mS) {
      try {
        mS.setMaxRows(count);
        if (STATEMENT_DEBUG) JConfig.log().logDebug("Executing fA query: " + query);
        ResultSet rs = mS.executeQuery(query);
        return getAllResults(rs);
      } catch (SQLException e) {
        JConfig.log().handleDebugException("Error running query: " + query, e);
        return null;
      }
    }
  }

  private Map<String,PreparedStatement> mStatementMap = new HashMap<String,PreparedStatement>();

  public List<Record> findAllPrepared(String query, int count, String... parameters) {
    try {
      PreparedStatement ps = mStatementMap.get(query);
      if(ps==null) {
        ps = mDB.prepare(query);
        mStatementMap.put(query, ps);
      }
      ps.setMaxRows(count);

      int paramIndex = 1;
      for(String param : parameters) {
        ps.setString(paramIndex++, param);
      }

      if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing fAP query: " + query);
      ResultSet rs = execute(ps);
      ps.clearParameters();
      return getAllResults(rs);
    } catch (SQLException e) {
      JConfig.log().handleDebugException("Error preparing query: " + query, e);
      return null;
    }
  }

  private ResultSet execute(PreparedStatement ps) throws SQLException {
    return ps.executeQuery();
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
    rs.close();
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
    rs.close();
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
      oldRow = getRow(tableName, columnKey, value, true);
    }
    newRow = cleanRow(newRow);

    if(value == null || oldRow == null) {
      //  Magic columns; created_at automatically gets set.
      if (mColumnMap.containsKey("created_at")) {
        newRow.put("created_at", mDateFormat.format(new Date()));
      }
      return storeMap(newRow);
    }

    //  Magic columns; updated_at automatically gets set.
    if(mColumnMap.containsKey("updated_at")) {
      newRow.put("updated_at", mDateFormat.format(new Date()));
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
        if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing update on: " + sql);
        ps.execute();
        mDB.commit();
        return findKeys(ps);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  private Record cleanRow(Record newRow) {
    Record cleanedNewRow = new Record();
    for(String column : newRow.keySet()) {
      if(hasColumn(column)) {
        cleanedNewRow.put(column, newRow.get(column));
      }
    }
    newRow = cleanedNewRow;
    return newRow;
  }

  public Record findByColumn(String columnKey, String value) {
    return findByColumn(columnKey, value, false);
  }

  public Record findByColumn(String columnKey, String value, boolean forUpdate) {
    return getRow(mTableName, columnKey, value, forUpdate);
  }

  private Record getRow(String tableName, String columnKey, String value, boolean forUpdate) {
    Record oldRow = null;

    try {
      String statement = "SELECT * FROM " + tableName;
      statement += " WHERE " + columnKey + " = ?";
      if (forUpdate) statement += " FOR UPDATE";
      PreparedStatement ps = mDB.prepare(statement);
      setColumn(ps, 1, columnKey, value);
      if(STATEMENT_DEBUG) JConfig.log().logDebug("Executing gR statement: " + statement);
      ResultSet rs = execute(ps);
      oldRow = getFirstResult(rs);
    } catch (SQLException e) {
      JConfig.log().handleException("Can't get row" + (forUpdate? " for update":"") + " (" + columnKey + " = '" + value +"').", e);
    }
    return oldRow;
  }

  public String storeMap(Record newRow) {
    String sql = createPreparedInsert(mTableName, newRow);
    if(sql == null) return null;
    StringBuffer values = new StringBuffer();

    try {
      PreparedStatement ps = mDB.prepare(sql);

      int column = 1;
      for(String key: newRow.keySet()) {
        if(key.equals("id")) continue;
        if(values.length() != 0) values.append(", ");
        if(!setColumn(ps, column++, key, newRow.get(key))) {
          JConfig.log().logDebug("Error from columns: (" + column + ", " + key + ", " + mColumnMap.get(key).getType() + ", " + newRow.get(key) + ")");
        }
        values.append(newRow.get(key));
      }
      if(STATEMENT_DEBUG) JConfig.log().logDebug("Storing map: " + sql);

      ps.execute();
      mDB.commit();
      return findKeys(ps);
    } catch (SQLException e) {
      System.err.println("Command: " + sql);
      System.err.println("Values:  " + values);
      JConfig.log().handleException("Can't store row in table.", e);
    }
    return null;
  }

  private String findKeys(PreparedStatement ps) throws SQLException {
    ResultSet rs = ps.getGeneratedKeys();
    if(rs != null) {
      Record insertMap = getFirstResult(rs);
      if (insertMap.containsKey("1")) {
        return insertMap.get("1");
      } else if(insertMap.containsKey("generated_key")) {
        return insertMap.get("generated_key");
      } else if(insertMap.values().size() == 1) {
        return insertMap.values().toArray()[0].toString();
      }
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
          update.append(key).append("=?");
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
            JConfig.log().logMessage("Error from columns: (" + column + "," + key + ", " + mColumnMap.get(key).getType() + ", " + newRow.get(key) + ")");
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
        else ps.setString(column, val.substring(0, Math.min(val.length(), 255)));
      } else if (type.equals("CHAR")) {
        if (val == null) ps.setNull(column, java.sql.Types.CHAR);
        else ps.setString(column, val.substring(0, Math.min(val.length(), 255)));
      } else if (type.equals("TIMESTAMP") || type.equals("DATETIME")) {
        if (val == null) ps.setNull(column, java.sql.Types.TIMESTAMP);
        else try {
          ps.setTimestamp(column, Timestamp.valueOf(val));
        } catch(SQLException e) {
          JConfig.log().logMessage("Failing to insert \"" + val + "\" into column " + column + " of table " + mTableName);
          throw e;
        } catch(RuntimeException e) {
          JConfig.log().logMessage("Failing to insert \"" + val + "\" into column " + key + " (" + column + ") of table " + mTableName);
          throw e;
        }
      } else if (type.equals("INTEGER") || type.equals("INT")) {
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
        JConfig.log().logDebug("WTF?!?! (" + type + ", " + key + ", " + val + ")");
      }
    } catch (SQLException e) {
      JConfig.log().logDebug("Failure with prepared statement: " + ps.toString());
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
      JConfig.log().handleException("Can't load metadata for table " + mTableName + ".", e);
    }
  }

  public int count() {
    return countBySQL("SELECT COUNT(*) AS count FROM " + mTableName);
  }

  public int countBy(String condition) {
    return countBySQL("SELECT COUNT(*) AS count FROM " + mTableName + " WHERE " + condition);
  }

  public int countBySQL(String sql) {
    Record rm = findFirst(sql);
    String count = (String) (rm.values().toArray()[0]);
    return Integer.parseInt(count);
  }

  public Database getDB() {
    return mDB;
  }

  public Set<String> getColumns() {
    return mColumnMap.keySet();
  }
}
