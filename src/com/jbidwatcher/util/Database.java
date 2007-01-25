package com.jbidwatcher.util;

import com.jbidwatcher.config.JConfig;

import java.sql.*;
import java.util.Properties;

public class Database {
  /* the default framework is embedded*/
  private String framework = "embedded";
  private String driver = "org.apache.derby.jdbc.EmbeddedDriver";
  private String protocol = "jdbc:derby:";
  private Connection mConn;

  public static void main(String[] args) {
    try {
      Database db = new Database("/Users/mrs/.jbidwatcher");
      dbTest(db);
      db.shutdown();
    } catch(Exception e) {
      handleSQLException(e);
    }
  }

  public Database(String base) throws Exception {
    if(base == null) base = JConfig.getHomeDirectory("jbidwatcher");
    System.setProperty("derby.system.home", base);
    setup();
  }

  private void setup() throws ClassNotFoundException, IllegalAccessException, InstantiationException, SQLException {
    /*
       The driver is installed by loading its class.
       In an embedded environment, this will start up Derby, since it is not already running.
     */
    Class.forName(driver).newInstance();
    ErrorManagement.logDebug("Loaded the appropriate driver.");

    Properties props = new Properties();
    props.setProperty("user", "user1");
    props.setProperty("password", "user1");

    /*
       The connection specifies create=true to cause
       the database to be created. To remove the database,
       remove the directory derbyDB and its contents.
       The directory derbyDB will be created under
       the directory that the system property
       derby.system.home points to, or the current
       directory if derby.system.home is not set.
     */
    mConn = DriverManager.getConnection(protocol + "derbyDB;create=true", props);
    ErrorManagement.logDebug("Connected to and created database derbyDB");

    mConn.setAutoCommit(false);
  }

  public Statement getStatement() {
    Statement rval = null;

    try {
      if(mConn != null) rval = mConn.createStatement();
    } catch(SQLException squee) {
      handleSQLException(squee);
    }

    return rval;
  }

  private static boolean dbTest(Database db) {
    try {
      /*
         Creating a statement lets us issue commands against
         the connection.
       */
      Statement s = db.getStatement();

      /*
         We create a table, add a few rows, and update one.
       */
      s.execute("create table derbyDB(num int, addr varchar(40))");
      ErrorManagement.logDebug("Created table derbyDB");
      s.execute("insert into derbyDB values (1956,'Webster St.')");
      ErrorManagement.logDebug("Inserted 1956 Webster");
      s.execute("insert into derbyDB values (1910,'Union St.')");
      ErrorManagement.logDebug("Inserted 1910 Union");
      s.execute("update derbyDB set num=180, addr='Grand Ave.' where num=1956");
      ErrorManagement.logDebug("Updated 1956 Webster to 180 Grand");

      s.execute("update derbyDB set num=300, addr='Lakeshore Ave.' where num=180");
      ErrorManagement.logDebug("Updated 180 Grand to 300 Lakeshore");

      /*
         We select the rows and verify the results.
       */
      ResultSet rs = s.executeQuery("SELECT num, addr FROM derbyDB ORDER BY num");

      if (!rs.next())
      {
          throw new Exception("Wrong number of rows");
      }

      if (rs.getInt(1) != 300)
      {
          throw new Exception("Wrong row returned");
      }

      if (!rs.next())
      {
          throw new Exception("Wrong number of rows");
      }
      if (rs.getInt(1) != 1910)
      {
          throw new Exception("Wrong row returned");
      }

      if (rs.next())
      {
          throw new Exception("Wrong number of rows");
      }

      ErrorManagement.logDebug("Verified the rows");

      s.execute("drop table derbyDB");
      ErrorManagement.logDebug("Dropped table derbyDB");

      /*
         We release the result and statement resources.
       */
      rs.close();
      s.close();
      ErrorManagement.logDebug("Closed result set and statement");

      /*
         We end the transaction and the connection.
       */
      db.commit();
      ErrorManagement.logDebug("Committed transaction");
    } catch (Throwable e) {
      handleSQLException(e);
    }
    return true;
  }

  public void commit() {
    try {
      mConn.commit();
    } catch(SQLException squee) {
      handleSQLException(squee);
    }
  }

  public boolean shutdown() {
    try {
      mConn.close();
      ErrorManagement.logDebug("Closed connection");

      /*
         In embedded mode, an application should shut down Derby.
         If the application fails to shut down Derby explicitly,
         the Derby does not perform a checkpoint when the JVM shuts down, which means
         that the next connection will be slower.
         Explicitly shutting down Derby with the URL is preferred.
         This style of shutdown will always throw an "exception".
       */
      if (framework.equals("embedded")) {
        boolean gotSQLExc = false;
        try {
          DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException se) {
          gotSQLExc = true;
        }

        if (!gotSQLExc) {
          ErrorManagement.logMessage("Database did not shut down normally");
        } else {
          ErrorManagement.logDebug("Database shut down normally");
        }
      }
    } catch (Throwable e) {
      handleSQLException(e);
    }

    return true;
  }

  private static void handleSQLException(Throwable e) {
    ErrorManagement.logDebug("exception thrown:");

    if (e instanceof SQLException) {
      printSQLError((SQLException) e);
    } else {
      e.printStackTrace();
    }
  }


  static void printSQLError(SQLException e) {
    while (e != null) {
      ErrorManagement.logDebug(e.toString());
      e = e.getNextException();
    }
  }
}
