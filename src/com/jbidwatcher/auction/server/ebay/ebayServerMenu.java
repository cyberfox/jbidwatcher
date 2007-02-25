package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.ui.ServerMenu;
import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.AuctionQObject;

import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Feb 25, 2007
* Time: 11:49:29 AM
* To change this template use File | Settings | File Templates.
*/
class ebayServerMenu extends ServerMenu {
  public void initialize() {
    addMenuItem("Search eBay", 'F');
    addMenuItem("Get My eBay Items", 'M');
    addMenuItem("Get Selling Items", 'S');
    addMenuItem("Refresh eBay session", "Update login cookie", 'U');
    if(JConfig.debugging) addMenuItem("[Dump eBay activity queue]", 'Q');
  }

  public void actionPerformed(ActionEvent ae) {
    String actionString = ae.getActionCommand();

    //  Handle stuff which is redirected to the search manager.
    if(actionString.equals("Search eBay")) MQFactory.getConcrete("user").enqueue("SEARCH");
    else MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.MENU_CMD, actionString, null));
  }

  protected ebayServerMenu(String serverName, char ch) {
    super(serverName, ch);
  }
}
