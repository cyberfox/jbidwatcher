package com.jbidwatcher.auction;

import com.jbidwatcher.util.Record;

/**
 * Created by mrs on 12/23/14.
 */
public interface ItemParser {
  Record parseItemDetails();

  public enum ParseErrors {
    SUCCESS,
    NOT_ADULT,
    BAD_TITLE,
    SELLER_AWAY,
    CAPTCHA,
    DELETED,
    WRONG_SITE
  }
}
