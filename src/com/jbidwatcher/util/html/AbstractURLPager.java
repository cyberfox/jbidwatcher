package com.jbidwatcher.util.html;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.AbstractSequentialList;

public abstract class AbstractURLPager extends AbstractSequentialList {
  protected String urlString;
  private int itemsPerPage;
  private int itemCount;
  protected int lastPageNumber;

  abstract protected void setURL(String url);
  abstract public JHTML getPage(int pageNumber);

  protected void setItemsPerPage(int perPage) {
    itemsPerPage = perPage;
  }

  protected int getItemsPerPage() {
    return itemsPerPage;
  }

  // N.B.: setItemCount or setLastPageNumber must be called in order to calculate the number of pages
  protected void setItemCount(int count) {
     itemCount = count;
    lastPageNumber = getItemsPerPage() == 0 ? 0 : (getItemCount() / getItemsPerPage()) + 1;
  }

  protected int getItemCount() {
    return itemCount;
  }

  protected void setLastPageNumber(int lastPage) {
    lastPageNumber = lastPage;
  }

  public int size() {
    return lastPageNumber;
  }
}
