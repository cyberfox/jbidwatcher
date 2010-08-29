package com.jbidwatcher.util.db;

/**
* Created by IntelliJ IDEA.
* User: mrs
* Date: Aug 29, 2010
* Time: 1:41:48 AM
* To change this template use File | Settings | File Templates.
*/
public class Device extends ActiveRecord {
  public Device() { }
  public Device(String deviceId) {
    setString("device_id", deviceId);
    refreshKey();
  }

  public void refreshKey() {
    long r;
    do {
      r = (long) (Math.random() * 1e6);
    } while(r < 100000);
    setString("security_key", Long.toString(r));
    saveDB();
  }

  protected static String getTableName() { return "devices"; }

  @Override
  protected Table getDatabase() {
    return getRealDatabase();
  }

  private static ThreadLocal<Table> tDB = new ThreadLocal<Table>() {
    protected synchronized Table initialValue() {
      return openDB(getTableName());
    }
  };

  public static Table getRealDatabase() {
    return tDB.get();
  }

  public static Device findByDevice(String deviceId) {
    return (Device)findFirstBy(Device.class, "device_id", deviceId);
  }
}
