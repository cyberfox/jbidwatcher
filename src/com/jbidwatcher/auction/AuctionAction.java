package com.jbidwatcher.auction;

import com.jbidwatcher.util.Currency;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Aug 18, 2005
 * Time: 12:20:35 AM
 * To change this template use File | Settings | File Templates.
 */
public interface AuctionAction {
  public String activate();
  public int getResult();
  public Currency getAmount();
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
