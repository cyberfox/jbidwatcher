package com.jbidwatcher;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.xml.XMLParseException;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.xml.XMLSerializeSimple;
import com.jbidwatcher.ui.OptionUI;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.ErrorManagement;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.awt.*;

public class UpdaterEntry extends XMLSerializeSimple {
  protected String _version = "";
  protected String _severity = "";
  protected String _description = ""; //  Optional
  protected String _url = "";
  protected boolean _valid;
  protected boolean _hasConfigChanges;
  protected ArrayList<XMLElement> configChanges;

  //  For creating, then filling in later, especially when loading from a
  //  file, instead of remotely.
  public UpdaterEntry() { }

  public UpdaterEntry(String packageName, String updateFrom) {
    StringBuffer loadedUpdate;

    try {
      URLConnection uc = Http.getPage(updateFrom);
      loadedUpdate = Http.receivePage(uc);
      _valid = true;
    } catch(IOException e) {
      loadedUpdate = null;
      ErrorManagement.handleException("UpdaterEntry: " + e, e);
      _valid = false;
    }

    if(_valid && (loadedUpdate == null || loadedUpdate.length() == 0)) _valid = false;
    if(_valid) loadFromString(loadedUpdate, packageName);
  }

  public void loadFromString(StringBuffer sb, String packageName) {
    if(sb == null || packageName == null) {
      ErrorManagement.handleException("loadFromString Failed with a null pointer!", new Exception("Updater got incorrect XML file."));
    } else {
      XMLElement xmlUpdate = new XMLElement(true);

      xmlUpdate.parseString(sb.toString());
      if (xmlUpdate.getTagName().equalsIgnoreCase(packageName)) {
        fromXML(xmlUpdate);
      } else {
        throw new XMLParseException(xmlUpdate.getTagName(), "Updater got incorrect XML file.");
      }
    }
  }

  public XMLElement toXML() { throw new RuntimeException("toXML not supported by load-only class."); }

  protected String[] infoTags = { "version", "severity", "description", "url", "config", "knownversion" };
  protected String[] getTags() { return infoTags; }

  protected void handleTag(int i, XMLElement curElement) {
    switch(i) {
      case 0:
        _version = curElement.getContents();
        break;
      case 1:
        _severity = curElement.getContents();
        break;
      case 2:
        _description = curElement.getContents();
        break;
      case 3:
        _url = curElement.getContents();
        break;
      case 4:
        boolean valid = true;
        //  Allow validating individual configuration settings by program version.
        String vNum = curElement.getProperty("VERSION");
        if(vNum != null) {
          valid = vNum.equals(Constants.PROGRAM_VERS);
        }
        //  Allow validating individual configuration settings by configuration settings.
        String checkTrue = curElement.getProperty("CHECKTRUE");
        if(checkTrue != null) {
          if(JConfig.queryConfiguration(checkTrue, "true").equals("false")) valid = false;
        }
        if(valid) {
          String strStamp = curElement.getProperty("STAMP");
          if(strStamp == null) {
            if(configChanges == null) configChanges = new ArrayList<XMLElement>(5);
            configChanges.add(curElement);
          } else {
            long stamp = Long.parseLong(strStamp);
            long last = Long.parseLong(JConfig.queryConfiguration("updates.lastConfig", "0"));
            if(stamp > last) {
              if(configChanges == null) configChanges = new ArrayList<XMLElement>(5);
              configChanges.add(curElement);
            }
          }
        }
        break;
      case 5:
        //  If <knownversion>{this version}</...> exists, then don't alert to a new version.
        String known = curElement.getContents();
        if(known.equals(Constants.PROGRAM_VERS)) _version = known;
        break;
      default:
        //  Do absolutely nothing.  New tags (for later versions) should be
        //  ignored, and not cause errors.
    }
  }

  public boolean isValid() { return _valid; }
  public String getVersion() { return _version; }
  public String getSeverity() { return _severity; }
  public String getDescription() { return _description; }
  public String getURL() { return _url; }

  public static void main(String[] args) {
    UpdaterEntry ue = new UpdaterEntry("update", "http://www.vixen.com/update.xml");
    System.out.println("Available version is: " + ue.getVersion());
    System.out.println("How strongly encouraged: " + ue.getSeverity());
    System.out.println("What is new/necessary: " + ue.getDescription());
    System.out.println("The URL to get that version at is: " + ue.getURL());
  }

  public boolean hasConfigurationUpdates() {
    return configChanges != null;
  }

  public void applyConfigurationUpdates() {
    StringBuffer alert = null;
    boolean cfgChanged = false;
    long lastStamp = Long.parseLong(JConfig.queryConfiguration("updates.lastConfig", "0"));

    for (XMLElement cfg : configChanges) {
      String type = cfg.getProperty("TYPE", "config");
      if (type.equals("message")) {
        if (alert == null) {
          alert = new StringBuffer(cfg.getContents());
        } else {
          alert.append('\n');
          alert.append(cfg.getContents());
        }
      } else if (type.equals("config")) {
        String cfgVar = cfg.getProperty("VARIABLE");
        if (cfgVar != null) {
          //  Save old values, in case we need to restore them later.
          if (JConfig.queryConfiguration(cfgVar) != null) {
            JConfig.setConfiguration("saved." + cfgVar, JConfig.queryConfiguration(cfgVar));
          }
          JConfig.setConfiguration(cfgVar, cfg.getContents());
          cfgChanged = true;
        }
      } else if (type.equals("string")) {
        String cfgVar = cfg.getProperty("STRING");
        if (cfgVar != null) {
          JConfig.setConfiguration("override." + cfgVar, cfg.getContents());
          cfgChanged = true;
        }
      } else if (type.equals("restore")) {
        String cfgVar = cfg.getContents();
        if (cfgVar != null) {
          String oldCfg = JConfig.queryConfiguration("saved." + cfgVar);
          if (oldCfg != null) {
            JConfig.setConfiguration(cfgVar, oldCfg);
            JConfig.kill("saved." + cfgVar);
            cfgChanged = true;
          }
        }
      } else if (type.equals("delete")) {
        String cfgVar = cfg.getContents();
        String oldCfg = JConfig.queryConfiguration(cfgVar);
        if (oldCfg != null) {
          JConfig.kill(cfgVar);
          cfgChanged = true;
        }
      }

      String strStamp = cfg.getProperty("STAMP");
      if (strStamp != null) {
        long stamp = Long.parseLong(strStamp);
        if (stamp > lastStamp) lastStamp = stamp;
      }
    }

    JConfig.setConfiguration("updates.lastConfig", Long.toString(lastStamp));
    if(cfgChanged) JConfig.updateComplete();
    if(alert != null) {
      OptionUI oui = new OptionUI();
      Dimension aboutBoxSize = new Dimension(495, 245);

      oui.showTextDisplay(alert, aboutBoxSize, Constants.PROGRAM_NAME + " News Alert...");
    }
  }
}
