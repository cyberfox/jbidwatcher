package com.jbidwatcher.search;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public interface SearchManagerInterface {
  public Searcher getSearchByIndex(int i);
  public Searcher getSearchById(long i);

  public int findSearch(Searcher s);
  public void deleteSearch(Searcher s);
  public int getSearchCount();

  public Searcher addSearch(String type, String name, String search, String server, int period, long identifier);
  public Searcher getSearchByName(String searchName);
}
