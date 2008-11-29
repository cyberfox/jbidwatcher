package com.jbidwatcher.ui.config;

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.T;
import com.jbidwatcher.ui.util.JPasteListener;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.ui.util.JBEditorPane;
import com.jbidwatcher.auction.server.ebay.ebayLoginManager;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 3:08:00 AM
 */
public class JConfigEbayTab extends JConfigTab
{
  JCheckBox adultBox;
  JTextField username;
  JTextField password;
  JComboBox siteSelect;
  MessageQueue.Listener oldLoginListener = null;
  private String mDisplayName;
  private String mSitename = "ebay";

  private class LoginTestListener implements ActionListener, MessageQueue.Listener {
    CookieJar cj = null;

    public void actionPerformed(ActionEvent ae) {
      if (ae.getActionCommand().equals("Test Login")) {
        oldLoginListener = MQFactory.getConcrete("login").registerListener(this);
        ebayLoginManager login = new ebayLoginManager("ebay", password.getText(), username.getText(), false);
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
      MQFactory.getConcrete("login").deRegisterListener(this);
      MQFactory.getConcrete("login").registerListener(oldLoginListener);
    }
  }

  public String getTabName() { return mDisplayName; }
  public void cancel() { }

  public boolean apply() {
    int selectedSite = siteSelect.getSelectedIndex();

    String old_adult = JConfig.queryConfiguration(mSitename + ".adult");
    JConfig.setConfiguration(mSitename + ".adult", adultBox.isSelected()?"true":"false");
    String new_adult = JConfig.queryConfiguration(mSitename + ".adult");

    String old_user = JConfig.queryConfiguration(mSitename + ".user");
    JConfig.setConfiguration(mSitename + ".user", username.getText());
    String new_user = JConfig.queryConfiguration(mSitename + ".user");

    String old_pass = JConfig.queryConfiguration(mSitename + ".password");
    JConfig.setConfiguration(mSitename + ".password", password.getText());
    String new_pass = JConfig.queryConfiguration(mSitename + ".password");

    if(selectedSite != -1) {
      JConfig.setConfiguration(mSitename + ".browse.site", Integer.toString(selectedSite));
      String selected = (String)siteSelect.getSelectedItem();
      if(!T.setCountrySite(selected)) {
        JOptionPane.showMessageDialog(null, "No site bundle exists; JBidwatcher will operate against ebay.com.", "Country Site Bundle", JOptionPane.INFORMATION_MESSAGE);
      }
    }

    if(old_pass == null || !new_pass.equals(old_pass) ||
       old_user == null || !new_user.equals(old_user) ||
       old_adult == null || !new_adult.equals(old_adult)) {
      MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, "Update login cookie", null));
    }
    return true;
  }

  public void updateValues() {
    String isAdult = JConfig.queryConfiguration(mSitename + ".adult", "false");
    adultBox.setSelected(isAdult.equals("true"));

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
    String isAdult = JConfig.queryConfiguration(mSitename + ".adult", "false");
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("General eBay Options"));

    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    JPanel adultPane = new JPanel();
    adultPane.setLayout(new BorderLayout(0, 0));
    adultBox = new JCheckBox("Registered adult");
    adultBox.setSelected(isAdult.equals("true"));
    adultPane.add(adultBox, BorderLayout.WEST);
    tp.add(adultPane);

    String searchNotice = "<html><body><div style=\"margin-left: 20px; font-size: 0.96em;\"><i>To have JBidwatcher regularly retrieve auctions listed on your My eBay<br>" +
                          "page, go to the <a href=\"/SEARCH\">Search Manager</a> and enable the search also named 'My eBay'.</i></div></body></html>";
    JBEditorPane jep = OptionUI.getHTMLLabel(searchNotice);
    tp.add(jep);

    return(tp);
  }

  private JPanel buildBrowseTargetPanel(String[] siteChoices) {
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
    siteSelect = new JComboBox(siteChoices);
    siteSelect.setSelectedIndex(realCurrentSite);
    tp.add(makeLine(new JLabel("Country site: "), siteSelect), BorderLayout.NORTH);

    JLabel bidWarning = new JLabel("Note: Bidding is not entirely supported yet on non-ebay.com sites");
    bidWarning.setFont(bidWarning.getFont().deriveFont(Font.ITALIC));
    tp.add(bidWarning, BorderLayout.EAST);
    return tp;
  }

  public JConfigEbayTab(String displayName, String[] choices) {
    mDisplayName = displayName;
    setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildUsernamePanel()), BorderLayout.NORTH);
    jp.add(panelPack(buildBrowseTargetPanel(choices)), BorderLayout.CENTER);
    add(jp, BorderLayout.NORTH);
    add(panelPack(buildCheckboxPanel()), BorderLayout.CENTER);
  }
}
