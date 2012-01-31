package com.jbidwatcher.ui.config;

import com.jbidwatcher.auction.server.ebay.ebayLoginManager;
import com.jbidwatcher.util.TT;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
* Created by IntelliJ IDEA.
* User: mrs
* Date: 1/31/12
* Time: 12:16 AM
* To change this template use File | Settings | File Templates.
*/
class LoginTestListener implements ActionListener, MessageQueue.Listener {
  CookieJar cj = null;
  private String mSitename;
  private JTextField mUsernameField, mPasswordField;
  private MessageQueue.Listener mOldLoginListener;

  public LoginTestListener(String siteName, JTextField usernameField, JTextField passwordField) {
    mSitename = siteName;
    mUsernameField = usernameField;
    mPasswordField = passwordField;
  }

  public void actionPerformed(ActionEvent ae) {
    if (ae.getActionCommand().equals("Test Login")) {
      TT T = new TT("ebay.com");
      mOldLoginListener = MQFactory.getConcrete("login").registerListener(this);
      ebayLoginManager login = new ebayLoginManager(T, mSitename, mPasswordField.getText(), mUsernameField.getText(), false);
      cj = login.getNecessaryCookie(true);
    }
  }

  private final String FAILED="FAILED";
  private final String NEUTRAL="NEUTRAL";

  public void messageAction(Object deQ) {
    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) { }
    String result = (String)deQ;
    String loginMessage = "An unrecognized result occurred ("+result+"); please report this.";
    if(result.startsWith(FAILED)) {
      loginMessage = "Login failed.";
      if(result.length() > FAILED.length()) {
        loginMessage += "\n" + result.substring(FAILED.length() + 1);
      }
    } else if(result.startsWith(NEUTRAL)) {
      if(cj == null) {
        loginMessage = "The login did not cause any errors but found no\ncookies.  It probably failed.";
      } else {
        loginMessage = "The login did not cause any errors and delivered\ncookies, but was not clearly recognized as successful.";
      }
      if (result.length() > NEUTRAL.length()) {
        loginMessage += "\n" + result.substring(NEUTRAL.length() + 1);
      }
    } else if(result.startsWith("CAPTCHA")) {
      loginMessage = "eBay put up a 'captcha', to prevent programs from logging into your account.  Login failed.";
    } else if(result.startsWith("SUCCESSFUL")) {
      loginMessage = "Successfully logged in.";
    }
    JOptionPane.showMessageDialog(null, loginMessage, "Login Test", JOptionPane.INFORMATION_MESSAGE);
    MQFactory.getConcrete("login").removeListener(this);
    MQFactory.getConcrete("login").registerListener(mOldLoginListener);
  }
}
