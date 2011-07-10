package com.jbidwatcher.ui;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 20, 2008
 * Time: 11:48:22 AM
 *
 * Parameter object for putting onto the user queue.
 */
public class ActionTriple {
  private Object mSource;
  private Object mAuction;
  private String mCommand;

  public ActionTriple(Object source, String command, Object auction) {
    mSource = source;
    mCommand = command;
    mAuction = auction;
  }

  public Object getSource() {
    return mSource;
  }

  public Object getAuction() {
    return mAuction;
  }

  public String getCommand() {
    return mCommand;
  }
}
