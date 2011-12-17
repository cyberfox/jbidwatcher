package com.jbidwatcher.auction;

import com.jbidwatcher.util.db.ActiveRecord;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.xml.XMLElement;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 12/17/11
 * Time: 12:25 AM
 * To change this template use File | Settings | File Templates.
 */
public class MultiSnipeManager {
  private static MultiSnipeManager ourInstance = new MultiSnipeManager();

  public static MultiSnipeManager getInstance() {
    return ourInstance;
  }

  private MultiSnipeManager() {
    MQFactory.getConcrete("multisnipe_xml").registerListener(new MessageQueue.Listener() {
      public void messageAction(Object deQ) {
        String idXMLPair = (String)deQ;
        String identifier = idXMLPair.substring(0, idXMLPair.indexOf(' '));
        XMLElement element = new XMLElement();
        element.parseString(idXMLPair, identifier.length() + 1);
        MultiSnipe ms = MultiSnipe.loadFromXML(element);
        addAuctionToMultisnipe(identifier, ms);
      }
    });

    MQFactory.getConcrete("won").addListener(new MessageQueue.Listener() {
      /**
       * Message queue 'won', receives auction identifiers on completion when the
       * current user is the high bidder.
       *
       * @param deQ - A string containing the auction identifier that was won.
       */
      public void messageAction(Object deQ) {
        MultiSnipe ms = getForAuctionIdentifier((String) deQ);
        if (ms != null) {
          ms.setWonAuction(/*(String)deQ*/);
        }
      }
    });

    MQFactory.getConcrete("notwon").addListener(new MessageQueue.Listener() {
      /**
       * Receives auction identifiers on completion when the current user is
       * not the high bidder.
       *
       * @param deQ - A string containing the auction identifier that ended.
       */
      public void messageAction(Object deQ) {
        MultiSnipe ms = getForAuctionIdentifier((String) deQ);
        if (ms != null) {
          ms.remove((String)deQ);
        }
      }
    });
  }

  public MultiSnipe addAuctionToMultisnipe(String identifier, MultiSnipe ms) {
    MultiSnipe old = getForAuctionIdentifier(identifier);
    if(old != null) {
      //  Shortcut if they're actually the same.
      if (old.equals(ms)) return null;
      old.remove(identifier);
    }

    ms.add(identifier);

    return old;
  }

  public MultiSnipe getForAuctionIdentifier(String identifier) {
    ActiveRecord hash = EntryCorral.getInstance().takeForRead(identifier);
    Integer multisnipeId = hash.getInteger("multisnipe_id");
    return multisnipeId == null ? null : MultiSnipe.find(multisnipeId);
  }

  public void addXMLToMultisnipe(String identifier, XMLElement xmlElement) {
    MultiSnipe ms = MultiSnipe.loadFromXML(xmlElement);
    ms.saveDB();
    addAuctionToMultisnipe(identifier, ms);
  }
}
