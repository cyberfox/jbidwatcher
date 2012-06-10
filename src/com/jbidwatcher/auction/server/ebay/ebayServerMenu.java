package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.auction.server.ServerMenu;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;

import java.awt.event.ActionEvent;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Feb 25, 2007
* Time: 11:49:29 AM
* To change this template use File | Settings | File Templates.
*/
class ebayServerMenu extends ServerMenu {
  String mQueueServer = null;
  public void initialize() {
    addMenuItem("Search eBay", 'F');
    addMenuItem("Get My eBay Items", 'M');
    addMenuItem("Get Selling Items", 'S');
    addMenuItem("Refresh eBay Session", "Update login cookie", 'U');
    if(JConfig.debugging) addMenuItem("Dump eBay Activity Queue", 'Q');
  }

  public void actionPerformed(ActionEvent ae) {
    String actionString = ae.getActionCommand();

    //  Handle stuff which is redirected to the search manager.
    if(actionString.equals("Search eBay")) MQFactory.getConcrete("user").enqueue("SEARCH");
    else MQFactory.getConcrete(mQueueServer).enqueueBean(new AuctionQObject(AuctionQObject.MENU_CMD, actionString, null));
  }

  protected ebayServerMenu(String qServer, String serverName, char ch) {
    super(serverName, ch);
    mQueueServer = qServer;
  }
}
