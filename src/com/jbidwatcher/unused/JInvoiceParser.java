package com.jbidwatcher.unused;
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

import com.jbidwatcher.auction.AuctionInfo;
import com.jbidwatcher.util.Currency;

import java.util.*;

public class JInvoiceParser implements JParser {
  private final static int TOK_BUYER = 1;
  private final static int TOK_BUYER_EMAIL = 2;
  private final static int TOK_AUCTIONID = 3;
  private final static int TOK_TITLE = 4;
  private final static int TOK_PRICE = 5;
  private final static int TOK_PLUS = 6;
  private final static int TOK_CURRENCY_PLUS = 7;
  private final static int TOK_REPEAT = 8;
  private final static int TOK_SUM = 9;
  private final static int TOK_INVALID = -1;

  AuctionInfo _auction;
  List _auctions;

  JInvoiceParser(AuctionInfo ai) {
    _auction = ai;

    _auctions = new Vector(1);
    _auctions.add(ai);
  }

  JInvoiceParser(List infoList) {
    _auctions = infoList;
    _auction = (AuctionInfo) infoList.get(0);
  }

  protected String sum(String field) {
    Currency accum = null;

    for(Iterator it=_auctions.iterator(); it.hasNext(); ) {
      if(field.equals("price")) {
        if(accum == null) {
          accum = ((AuctionInfo)it.next()).getCurBid();
        } else {
          try {
            accum = accum.add(((AuctionInfo)it.next()).getCurBid());
          } catch(Exception e) {
            return "$x.xx";
          }
        }
      }
    }

    if(accum == null) return "$x.xx";

    return accum.toString();
  }

  protected String repeat(String duplicate) {
    StringBuffer sb = new StringBuffer();
    String fixedDuplicate;
    JTemplate jtp;

    fixedDuplicate = duplicate.replace('[', '<');
    fixedDuplicate = fixedDuplicate.replace(']', '>');

    for(Iterator it=_auctions.iterator(); it.hasNext(); ) {
      JInvoiceParser jp = new JInvoiceParser((AuctionInfo)it.next());
      jtp = new JTemplate(jp);

      sb.append(jtp.expand(fixedDuplicate));
      System.err.println("-> " + fixedDuplicate + " <-");
    }

    return sb.toString();
  }

  Object[][] cmdList = {
    { "buyer", new Integer(TOK_BUYER) },
    { "buyer_email", new Integer(TOK_BUYER_EMAIL) },
    { "auctionid", new Integer(TOK_AUCTIONID) },
    { "title", new Integer(TOK_TITLE) },
    { "price", new Integer(TOK_PRICE) },
    { "+", new Integer(TOK_PLUS) },
    { "$+", new Integer(TOK_CURRENCY_PLUS) },
    { "repeat", new Integer(TOK_REPEAT) },
    { "$sum", new Integer(TOK_SUM) }
  };

  private int cmdMatch(String cmd) {
    int i;

    for(i=0; i<cmdList.length; i++) {
      if(cmd.equalsIgnoreCase((String)cmdList[i][0])) {
        return ((Integer)cmdList[i][1]).intValue();
      }
    }

    return TOK_INVALID;
  }

  public String evaluate(String cmd) {
    int firstSpace = cmd.indexOf(' ');
    String verb;
    int curTok;

    if(firstSpace == -1) 
      verb = cmd;
    else
      verb = cmd.substring(0,firstSpace);

    curTok = cmdMatch(verb);

    switch(curTok) {
      case TOK_BUYER:
        return _auction.getHighBidder();
      case TOK_BUYER_EMAIL:
        return _auction.getHighBidderEmail();
      case TOK_AUCTIONID:
        return _auction.getIdentifier();
      case TOK_TITLE:
        return _auction.getTitle();
      case TOK_PRICE:
        return _auction.getCurBid().toString();
      case TOK_PLUS:
        return plus(cmd.substring(firstSpace+1));
      case TOK_CURRENCY_PLUS:
        return currency_plus(cmd.substring(firstSpace+1));
      case TOK_REPEAT:
        return repeat(cmd.substring(firstSpace+1));
      case TOK_SUM:
        return sum(cmd.substring(firstSpace+1));
      case TOK_INVALID:
      default:
        return cmd;
    }
  }

  protected String plus(String ops) {
    int nextSpace;
    String noun1, noun2;

    nextSpace = ops.indexOf(' ');

    if(nextSpace == -1) return ops;

    noun1 = ops.substring(0,nextSpace);
    noun2 = ops.substring(nextSpace+1);

    try {
      return Double.toString(Double.parseDouble(noun1) + Double.parseDouble(noun2));
    } catch(Exception e) {
      return "0.0";
    }
  }

  protected String currency_plus(String ops) {
    int nextSpace;
    String noun1, noun2;

    nextSpace = ops.indexOf(' ');

    if(nextSpace == -1) return ops;

    noun1 = ops.substring(0,nextSpace);
    noun2 = ops.substring(nextSpace+1);

    try {
      com.jbidwatcher.util.Currency result;

      result = Currency.getCurrency(noun1).add(Currency.getCurrency(noun2));

      return result.toString();
    } catch(Exception e) {
      return "$x.xx";
    }
  }
}
