package com.jbidwatcher.ui;

import com.google.inject.assistedinject.Assisted;

/**
 * Created by mrs on 12/21/14.
 */
public interface AuctionListHolderFactory {
  AuctionListHolder create(String name, @Assisted("completed") boolean _completed, @Assisted("deletable") boolean deletable);
}
