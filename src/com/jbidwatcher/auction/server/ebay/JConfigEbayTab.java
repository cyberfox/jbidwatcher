package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.config.JConfigTab;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.AuctionQObject;
import com.jbidwatcher.ui.JPasteListener;

import javax.swing.*;
import java.awt.BorderLayout;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Feb 25, 2007
* Time: 3:08:00 AM
* To change this template use File | Settings | File Templates.
*/
public class JConfigEbayTab extends JConfigTab {
  JCheckBox adultBox;
  JCheckBox synchBox = null;
  JTextField username;
  JTextField password;
  JComboBox siteSelect;
  private String mDisplayName;

  public String getTabName() { return mDisplayName; }
  public void cancel() { }

  public boolean apply() {
    int selectedSite = siteSelect.getSelectedIndex();

    String old_adult = JConfig.queryConfiguration(getName() + ".adult");
    JConfig.setConfiguration(getName() + ".adult", adultBox.isSelected()?"true":"false");
    String new_adult = JConfig.queryConfiguration(getName() + ".adult");

    String old_user = JConfig.queryConfiguration(getName() + ".user");
    JConfig.setConfiguration(getName() + ".user", username.getText());
    String new_user = JConfig.queryConfiguration(getName() + ".user");
    String old_pass = JConfig.queryConfiguration(getName() + ".password");
    JConfig.setConfiguration(getName() + ".password", password.getText());
    String new_pass = JConfig.queryConfiguration(getName() + ".password");

    if(selectedSite != -1) {
      JConfig.setConfiguration(getName() + ".browse.site", Integer.toString(selectedSite));
    }

    if(old_pass == null || !new_pass.equals(old_pass) ||
       old_user == null || !new_user.equals(old_user) ||
       old_adult == null || !new_adult.equals(old_adult)) {
      MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, "Update login cookie", null));
    }
    return true;
  }

  public void updateValues() {
    String isAdult = JConfig.queryConfiguration(getName() + ".adult", "false");
    adultBox.setSelected(isAdult.equals("true"));

    username.setText(JConfig.queryConfiguration(getName() + ".user", "default"));
    password.setText(JConfig.queryConfiguration(getName() + ".password", "default"));
  }

  private JPanel buildUsernamePanel() {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("eBay User ID"));
    tp.setLayout(new BorderLayout());
    username = new JTextField();
    username.addMouseListener(JPasteListener.getInstance());

    username.setText(JConfig.queryConfiguration(getName() + ".user", "default"));
    username.setEditable(true);
    username.getAccessibleContext().setAccessibleName("User name to log into eBay");
    password = new JPasswordField(JConfig.queryConfiguration(getName() + ".password"));
    password.addMouseListener(JPasteListener.getInstance());
    password.setEditable(true);

    //  Get the password from the configuration entry!  FIX
    password.getAccessibleContext().setAccessibleName("eBay Password");
    password.getAccessibleContext().setAccessibleDescription("This is the user password to log into eBay.");

    Box userBox = Box.createVerticalBox();
    userBox.add(makeLine(new JLabel("Username: "), username));
    userBox.add(makeLine(new JLabel("Password:  "), password));
    tp.add(userBox);

    return(tp);
  }

  private JPanel buildCheckboxPanel() {
    String isAdult = JConfig.queryConfiguration(getName() + ".adult", "false");
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("General eBay Options"));

    tp.setLayout(new BoxLayout(tp, BoxLayout.Y_AXIS));

    adultBox = new JCheckBox("Registered adult");
    adultBox.setSelected(isAdult.equals("true"));
    tp.add(adultBox);

    tp.add(new JLabel("     To have JBidwatcher regularly retrieve auctions listed on your My eBay"));
    tp.add(new JLabel("     page, go to the Search Manager and enable the search also named 'My eBay'."));

    return(tp);
  }

  private JPanel buildBrowseTargetPanel(String[] siteChoices) {
    JPanel tp = new JPanel();

    tp.setBorder(BorderFactory.createTitledBorder("Browse target"));
    tp.setLayout(new BorderLayout());

    String curSite = JConfig.queryConfiguration(getName() + ".browse.site", "0");
    int realCurrentSite;
    try {
      realCurrentSite = Integer.parseInt(curSite);
    } catch(Exception ignore) {
      realCurrentSite = 0;
    }
    siteSelect = new JComboBox(siteChoices);
    siteSelect.setSelectedIndex(realCurrentSite);
    tp.add(makeLine(new JLabel("Browse to site: "), siteSelect), BorderLayout.NORTH);

    return tp;
  }

  public JConfigEbayTab(String displayName, String[] choices) {
    mDisplayName = displayName;
    setLayout(new BorderLayout());
    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildCheckboxPanel()), BorderLayout.NORTH);
    jp.add(panelPack(buildUsernamePanel()), BorderLayout.CENTER);
    add(jp, BorderLayout.NORTH);
    add(panelPack(buildBrowseTargetPanel(choices)), BorderLayout.CENTER);
  }
}
