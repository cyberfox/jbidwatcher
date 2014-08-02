package com.jbidwatcher.auction;

import com.jbidwatcher.ui.AuctionsManager;
import com.jbidwatcher.util.Observer;
import com.jbidwatcher.util.StringTools;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 7/11/11
 * Time: 1:14 AM
 * To change this template use File | Settings | File Templates.
 */
public class EntryFactory extends Observer<AuctionEntry> {
  private static Resolver sResolver = null;
  private static EntryFactory instance;

  public static EntryFactory getInstance() {
    if(instance == null) instance = new EntryFactory();
    return instance;
  }

  public AuctionEntry constructEntry() {
    AuctionServerInterface server = sResolver.getServer();
    AuctionEntry ae = AuctionEntry.construct(server);
    ae.setPresenter(new AuctionEntryHTMLPresenter(ae));

    return ae;
  }

  /**
   * Construct a new AuctionEntry having been given an auction id for it.
   * Will not construct an auction if the id was deleted once before, or if it is already in the database.
   *
   * @param auctionId - The auction site identifier for the auction to create.
   * @return The newly (successfully!) loaded auction, or null if the id had previously been deleted, or is already in the database.
   */
  public AuctionEntry constructEntry(String auctionId) {
    AuctionServerInterface server = sResolver.getServer();
    String strippedId = server.stripId(auctionId);

    AuctionEntry ae = AuctionEntry.construct(strippedId, server);
    if(ae != null) ae.setPresenter(new AuctionEntryHTMLPresenter(ae));

    return ae;
  }

  /**
   * Construct and add a new AuctionEntry, optionally setting its label, adding it to the Corral, and the AuctionsManager, as well
   * as notifying the UI that a new auction has been added.
   *
   * @param aucId - The auction identifier to be added
   * @param label - A label, or null if no label, to be assigned to the new object (category).
   *
   * @return The newly created/loaded entry, or null if it had been deleted once before, is already in the database, or some other
   * error prevented it from being loaded.
   */
  public AuctionEntry conditionallyAddEntry(boolean interactive, String aucId, String label) {
    if (interactive) DeletedEntry.remove(aucId);
    AuctionEntry aeNew = EntryFactory.getInstance().constructEntry(aucId);
    if (aeNew != null) {
      if (label != null) aeNew.setCategory(label);
      EntryCorral.getInstance().put(aeNew);
      MQFactory.getConcrete("manager").enqueue(aeNew.getUnique());
      MQFactory.getConcrete("Swing").enqueue("Added [ " + aeNew.getIdentifier() + ", " + aeNew.getTitle() + " ]");
    } else {
      if(interactive) MQFactory.getConcrete("Swing").enqueue("Cannot add auction " + aucId + ", either invalid or\ncommunication error talking to server.");
    }
    return aeNew;
  }

  public static boolean isInvalid(boolean interactive, String aucId) {
    boolean invalid = !StringTools.isNumberOnly(aucId);
    if (invalid) {
      JConfig.log().logDebug("Rejecting bad identifier: " + aucId);
    } else {
      if (interactive) {
        invalid = AuctionEntry.findByIdentifier(aucId) != null;
        if (invalid) {
          MQFactory.getConcrete("Swing").enqueue("Cannot add auction " + aucId + ", it is already in your auction list.");
        }
      }
    }
    return invalid;
  }

  public static void setResolver(Resolver resolver) {
    sResolver = resolver;
  }

  public void afterCreate(AuctionEntry auctionEntry) {
    if(auctionEntry.getServer() == null) {
      auctionEntry.setServer(sResolver.getServer());
    }
    if(auctionEntry.getPresenter() == null) {
      auctionEntry.setPresenter(new AuctionEntryHTMLPresenter(auctionEntry));
    }
  }

  public void afterSave(AuctionEntry auctionEntry) {
    MQFactory.getConcrete("redraw").enqueue(auctionEntry.getIdentifier());
  }
}
