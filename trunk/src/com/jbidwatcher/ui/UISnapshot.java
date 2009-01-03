package com.jbidwatcher.ui;

import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Mar 3, 2008
 * Time: 7:29:55 PM
 *
 * Utility class to manage location and size of the screen between sessions.
 */
public class UISnapshot
{
  public static void recordLocation(JFrame mainFrame)
  {
    Point tempPoint = mainFrame.getLocationOnScreen();
    JConfig.setConfiguration("temp.last.screenx", Integer.toString(tempPoint.x));
    JConfig.setConfiguration("temp.last.screeny", Integer.toString(tempPoint.y));
    JConfig.setConfiguration("temp.last.height", Integer.toString(mainFrame.getHeight()));
    JConfig.setConfiguration("temp.last.width", Integer.toString(mainFrame.getWidth()));
  }

  public static Properties snapshotLocation(JFrame inFrame)
  {
    Properties displayProps = new Properties();
    if(inFrame.isVisible()) {
      Point tempPoint = inFrame.getLocationOnScreen();

      displayProps.setProperty("screenx", Integer.toString(tempPoint.x));
      displayProps.setProperty("screeny", Integer.toString(tempPoint.y));

      displayProps.setProperty("height", Integer.toString(inFrame.getHeight()));
      displayProps.setProperty("width", Integer.toString(inFrame.getWidth()));
    } else {
      displayProps.setProperty("screenx", JConfig.queryConfiguration("temp.last.screenx", "-1"));
      displayProps.setProperty("screeny", JConfig.queryConfiguration("temp.last.screeny", "-1"));
      displayProps.setProperty("width", JConfig.queryConfiguration("temp.last.width", "-1"));
      displayProps.setProperty("height", JConfig.queryConfiguration("temp.last.height", "-1"));
    }
    return displayProps;
  }
}
