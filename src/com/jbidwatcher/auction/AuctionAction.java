package com.jbidwatcher.auction;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.Currency;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Aug 18, 2005
 * Time: 12:20:35 AM
 *
 * Abstraction of the actions that you can take on an auction (bidding and buying) so they can be acted on independantly.
 */
public interface AuctionAction {
  public String activate();
  public int getResult();
  public String getAmount();
  public int getQuantity();

  /**
   * @brief Get the bid result in plain english text.
   *
   * @param bidAmount - The amount that was bid (for filling into the text).
   * @param bidResult - The integer result of the bidding operation.
   *
   * @return - A string that represents what happened when the bid was attempted.
   */
  public String getBidResult(Currency bidAmount, int bidResult);
  public boolean isSuccessful();
}
