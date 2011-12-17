package com.jbidwatcher.ui.config;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.TT;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.util.JBEditorPane;
import com.jbidwatcher.auction.server.ebay.ebayLoginManager;
import com.jbidwatcher.auction.server.AuctionServerManager;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 3:08:00 AM
 */
public class JConfigEbayTab extends JConfigTab
{
  JTextField username;
  JTextField password;
  JComboBox siteSelect;
  JEditorPane siteWarning;
  MessageQueue.Listener oldLoginListener = null;
  private String mDisplayName;
  //  mSitename is only used to look up configuration values.
  private String mSitename = Constants.EBAY_SERVER_NAME;

  private class LoginTestListener implements ActionListener, MessageQueue.Listener {
    CookieJar cj = null;

    public void actionPerformed(ActionEvent ae) {
      if (ae.getActionCommand().equals("Test Login")) {
        TT T = new TT("ebay.com");
        oldLoginListener = MQFactory.getConcrete("login").registerListener(this);
        ebayLoginManager login = new ebayLoginManager(T, mSitename, password.getText(), username.getText(), false);
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
      MQFactory.getConcrete("login").registerListener(oldLoginListener);
    }
  }

  public String getTabName() { return mDisplayName; }
  public void cancel() { }

  public void apply() {
    int selectedSite = siteSelect.getSelectedIndex();

    String old_user = JConfig.queryConfiguration(mSitename + ".user");
    JConfig.setConfiguration(mSitename + ".user", username.getText());
    String new_user = JConfig.queryConfiguration(mSitename + ".user");

    String old_pass = JConfig.queryConfiguration(mSitename + ".password");
    JConfig.setConfiguration(mSitename + ".password", password.getText());
    String new_pass = JConfig.queryConfiguration(mSitename + ".password");

    if(selectedSite != -1) {
      JConfig.setConfiguration(mSitename + ".browse.site", Integer.toString(selectedSite));
    }

    if(old_pass == null || !new_pass.equals(old_pass) ||
       old_user == null || !new_user.equals(old_user)) {
      MQFactory.getConcrete(AuctionServerManager.getInstance().getServer()).enqueueBean(new AuctionQObject(AuctionQObject.MENU_CMD, "Update login cookie", null));
    }
  }

  public void updateValues() {
    username.setText(JConfig.queryConfiguration(mSitename + ".user", "default"));
    password.setText(JConfig.queryConfiguration(mSitename + ".password", "default"));
  }

  private JPanel buildUsernamePanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("eBay User ID"));
    tp.setLayout(new BorderLayout());
    username = new JTextField();
    username.addMouseListener(JPasteListener.getInstance());

    username.setText(JConfig.queryConfiguration(mSitename + ".user", "default"));
    username.setEditable(true);
    username.getAccessibleContext().setAccessibleName("User name to log into eBay");
    password = new JPasswordField(JConfig.queryConfiguration(mSitename + ".password"));
    password.addMouseListener(JPasteListener.getInstance());
    password.setEditable(true);

    //  Get the password from the configuration entry!  FIX
    password.getAccessibleContext().setAccessibleName("eBay Password");
    password.getAccessibleContext().setAccessibleDescription("This is the user password to log into eBay.");

    Box userBox = Box.createVerticalBox();
    userBox.add(makeLine(new JLabel("Username: "), username));
    userBox.add(makeLine(new JLabel("Password:  "), password));
    JButton testButton = new JButton("Test Login");
    testButton.addActionListener(new LoginTestListener());
    tp.add(testButton, BorderLayout.EAST);
    tp.add(userBox);

    return(tp);
  }

  private JPanel buildCheckboxPanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("General eBay Options"));

    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    String searchNotice = "<html><body><div style=\"margin-left: 10px; font-size: 0.96em;\"><i>To have JBidwatcher regularly retrieve auctions listed on your My eBay<br>" +
                          "page, go to the <a href=\"/SEARCH\">Search Manager</a> and enable the search also named 'My eBay'.</i></div></body></html>";
    JBEditorPane jep = OptionUI.getHTMLLabel(searchNotice);
    tp.add(jep);

    return(tp);
  }

  private JPanel buildBrowseTargetPanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("Browse target"));
    tp.setLayout(new BorderLayout());

    String curSite = JConfig.queryConfiguration(mSitename + ".browse.site", "0");
    int realCurrentSite;
    try {
      realCurrentSite = Integer.parseInt(curSite);
    } catch(Exception ignore) {
      realCurrentSite = 0;
    }
    siteSelect = new JComboBox(Constants.SITE_CHOICES);
    siteSelect.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        int selectedSite = siteSelect.getSelectedIndex();
        if (Constants.SITE_CHOICES[selectedSite].equals("ebay.com")) {
          siteWarning.setVisible(false);
          siteWarning.setText("");
        } else if(Constants.SITE_CHOICES[selectedSite].equals("ebay.co.uk")) {
          String ukSiteWarning = "<html><body><div style=\"font-size: 0.96em;\"><i>Bidding happens on ebay.com preferentially, or ebay.co.uk if the item is not visible on ebay.com.<br>If you have a seller dispute, it may need to be made on ebay.com.</i></div></body></html>";
          siteWarning.setText(ukSiteWarning);
          siteWarning.setVisible(true);
        } else {
          String generalSiteWarning = "<html><body><div style=\"font-size: 0.96em;\"><i>Bidding happens on ebay.com or ebay.co.uk, even if neither is your local site.<br>If you have a seller dispute, it will need to be made on one of those sites.</i></div></body></html>";
          siteWarning.setText(generalSiteWarning);
          siteWarning.setVisible(true);
        }
      }
    });
    tp.add(makeLine(new JLabel("Country site: "), siteSelect), BorderLayout.NORTH);

    siteWarning = OptionUI.getHTMLLabel("");
    siteSelect.setSelectedIndex(realCurrentSite);

    tp.add(siteWarning, BorderLayout.EAST);
    return tp;
  }

  public JConfigEbayTab() {
    mDisplayName = Constants.EBAY_DISPLAY_NAME;
    setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildUsernamePanel()), BorderLayout.NORTH);
    jp.add(panelPack(buildBrowseTargetPanel()), BorderLayout.CENTER);
    add(jp, BorderLayout.NORTH);
    add(panelPack(buildCheckboxPanel()), BorderLayout.CENTER);
  }
}
