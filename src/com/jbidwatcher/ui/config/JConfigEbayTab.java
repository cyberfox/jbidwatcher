package com.jbidwatcher.ui.config;

import com.cyberfox.util.platform.Platform;
import com.jbidwatcher.ui.commands.UserActions;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.util.JBEditorPane;
import com.jbidwatcher.util.queue.SuperQueue;

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
  boolean quickConfig = false;
  JTextField username;
  JTextField password;
  JComboBox siteSelect;
  JCheckBox homeSite;
  JEditorPane siteWarning;
  private String mDisplayName;
  //  mSitename is only used to look up configuration values.
  private String mSitename = Constants.EBAY_SERVER_NAME;
  private String friendlyName;

  public String getTabName() { return mDisplayName; }
  public void cancel() { }

  public void apply() {
    if (JConfig.queryConfiguration("config.level", "quick").equals("quick") != quickConfig) {
      //  Do not run advanced, if the mode is quick, or quick if the mode is advanced.
      return;
    }

    String old_user = JConfig.queryConfiguration(mSitename + ".user");
    JConfig.setConfiguration(mSitename + ".user", username.getText());
    String new_user = JConfig.queryConfiguration(mSitename + ".user");

    String old_pass = JConfig.queryConfiguration(mSitename + ".password");
    JConfig.setConfiguration(mSitename + ".password", password.getText());
    String new_pass = JConfig.queryConfiguration(mSitename + ".password");

    if(old_pass == null || !new_pass.equals(old_pass) ||
       old_user == null || !new_user.equals(old_user)) {
      MQFactory.getConcrete(friendlyName).enqueueBean(new AuctionQObject(AuctionQObject.MENU_CMD, "Update login cookie", null));
    }

    if(homeSite != null) {
      boolean usOnly = homeSite.isSelected();
      JConfig.setConfiguration(mSitename + ".non_us", Boolean.toString(usOnly));
    }

    if(siteSelect != null) {
      int selectedSite = siteSelect.getSelectedIndex();
      if (selectedSite != -1) {
        JConfig.setConfiguration(mSitename + ".browse.site", Integer.toString(selectedSite));
      }
    }

    //  If it's the first time running the program, try to load My eBay for them in about 12 seconds.
    if(JConfig.queryConfiguration("first.run", "false").equals("true")) {
      SuperQueue.getInstance().preQueue(UserActions.MY_EBAY, "user", System.currentTimeMillis() + Constants.THREE_SECONDS * 5, 0);
    }
  }

  public void updateValues() {
    username.setText(JConfig.queryConfiguration(mSitename + ".user", "default"));
    password.setText(JConfig.queryConfiguration(mSitename + ".password", "default"));
    if (homeSite != null) {
      homeSite.setSelected(JConfig.queryConfiguration(mSitename + ".non_us", Boolean.toString(!Platform.isUSBased())).equals("true"));
    }
  }

  private JPanel buildUsernamePanel(JPasteListener pasteListener) {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("eBay User ID"));
    tp.setLayout(new BorderLayout());
    username = new JTextField();
    username.addMouseListener(pasteListener);

    username.setText(JConfig.queryConfiguration(mSitename + ".user", "default"));
    username.setEditable(true);
    username.getAccessibleContext().setAccessibleName("User name to log into eBay");
    password = new JPasswordField(JConfig.queryConfiguration(mSitename + ".password"));
    password.addMouseListener(pasteListener);
    password.setEditable(true);

    //  Get the password from the configuration entry!  FIX
    password.getAccessibleContext().setAccessibleName("eBay Password");
    password.getAccessibleContext().setAccessibleDescription("This is the user password to log into eBay.");

    Box userBox = Box.createVerticalBox();
    userBox.add(makeLine(new JLabel("Username: "), username));
    userBox.add(makeLine(new JLabel("Password:  "), password));
    JButton testButton = new JButton("Test Login");
    testButton.addActionListener(new LoginTestListener(mSitename, username, password));
    tp.add(testButton, BorderLayout.EAST);
    tp.add(userBox);

    return(tp);
  }

  private JPanel buildCheckboxPanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("General eBay Options"));

    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));
    homeSite = new JCheckBox("Prefer non-US auction server?");
    Box siteBox = Box.createHorizontalBox();
    siteBox.add(homeSite);
    siteBox.add(Box.createHorizontalGlue());
    tp.add(siteBox);
    String nonUSNotice = "<html><body><div style=\"margin-left: 7px; font-size: 0.96em;\"<i>If this is checked, JBidwatcher will " +
        "use <b>ebay.co.uk</b> as the source of auctions<br>and destination for placing bids. Otherwise, <b>ebay.com</b> " +
        "will be used.";
    JBEditorPane jep = OptionUI.getHTMLLabel(nonUSNotice);
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

  public JConfigEbayTab(boolean isQuickConfig, JPasteListener pasteListener, String friendlyName) {
    this.friendlyName = friendlyName;
    quickConfig = isQuickConfig;
    mDisplayName = Constants.EBAY_DISPLAY_NAME;
    setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildUsernamePanel(pasteListener)), BorderLayout.NORTH);
    if(!quickConfig) {
      jp.add(panelPack(buildBrowseTargetPanel()), BorderLayout.CENTER);
      add(jp, BorderLayout.NORTH);
      add(panelPack(buildCheckboxPanel()), BorderLayout.CENTER);
      String searchNotice = "<html><body><div style=\"margin-left: 10px; font-size: 0.96em;\"><i>To have JBidwatcher regularly retrieve auctions listed on your My eBay " +
          "page,<br>go to the <a href=\"/SEARCH\">Search Manager</a> and enable the search also named 'My eBay'.</i></div></body></html>";
      JBEditorPane jep = OptionUI.getHTMLLabel(searchNotice);
      add(jep, BorderLayout.SOUTH);
    } else {
      mDisplayName += " (quick)";
      add(jp, BorderLayout.NORTH);
      JPanel welcomeMessage = new JPanel();
      welcomeMessage.setLayout(new BoxLayout(welcomeMessage, BoxLayout.Y_AXIS));
      String prefix = "<html><body><center><em>";
      String suffix = "</em></center></body></html>";
      welcomeMessage.add(OptionUI.getHTMLLabel(prefix +
          "Enter your username and password and click the <code>Save</code> button to get started!" +
          suffix));
      add(welcomeMessage, BorderLayout.CENTER);
    }
  }
}
