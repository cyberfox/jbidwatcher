package com.jbidwatcher.auction.server.ebay;

/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.NoSuchElementException;
import java.util.ListIterator;

import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.html.AbstractURLPager;
import com.jbidwatcher.util.html.URLPagerIterator;
import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.auction.LoginManager;

public class EbayAuctionURLPager extends AbstractURLPager {
	// constants
	private static final String URLSTYLE_HTTP_STR = "search.dll"; 
	private static final int URLSTYLE_HTTP = 0;
	private static final int URLSTYLE_EBAY = 1;	
	private static final int ITEMS_PER_PAGE = 100;

  private ebayCleaner mCleaner = new ebayCleaner();
	private LoginManager mLogin;
	private int urlStyle;

	private JHTML lastPage;
	private boolean itemCountSet;

	public EbayAuctionURLPager(String url, LoginManager aucServ) {
		setURL(url);
		setItemsPerPage(ITEMS_PER_PAGE);
		mLogin = aucServ;

		// set the item count
		getPage(1);
	}

  protected void setURL(String url) {
		urlString = url;

    if(url.indexOf("ebay.") != -1) {
      // Determine the type of URL
      // Remove paging information if exists so we can add our own when needed
      if (url.indexOf(URLSTYLE_HTTP_STR) != -1) {
        urlStyle = URLSTYLE_HTTP;
        removePattern("&frpp=[0-9]*");
        removePattern("&skip=[0-9]*");
      } else {
        urlStyle = URLSTYLE_EBAY;
        removePattern("QQfrppZ[0-9]*");
        removePattern("QQfrtsZ[0-9]*");
      }
    }
  }

	private void removePattern(String regex) {
    urlString = urlString.replaceAll("(?i)"+regex, "");
	}

  private String getPageURL(int pageNumber) {
    if (urlString.indexOf("ebay.") == -1) return urlString;

    if (urlStyle == URLSTYLE_HTTP) {
      return urlString + "&frpp=" + getItemsPerPage() + "&skip=" + (pageNumber - 1) * getItemsPerPage();
    } else if (urlStyle == URLSTYLE_EBAY) {
      return urlString + "QQfrppZ" + getItemsPerPage() + "QQfrtsZ" + (pageNumber - 1) * getItemsPerPage();
    } else {
      JConfig.log().logMessage("Unknown URLSTYLE: " + urlStyle);
      return null;
    }
  }

	protected JHTML getPage(String pageURL) {
		if(pageURL == null) return null;

    CookieJar cj = mLogin.getNecessaryCookie(false);
    String cookies = null;
    if(cj != null) cookies = cj.toString();
    JHTML htmlDocument = new JHTML(pageURL, cookies, mCleaner);
    	if(htmlDocument.isLoaded()) {
    		return htmlDocument;
    	} else {
    		return null;
    	}
	}

	public JHTML getPage(int pageNumber) {
		if(pageNumber < 1 || (itemCountSet && pageNumber > size())) {
			throw new NoSuchElementException();
		}

		// We cache the last page since the constructor itself
		// retrieves the first page to determine the count number.
		// This can be reused when an iterator is instantiated.
		if(pageNumber == lastPageNumber)
			return lastPage;

		lastPageNumber = pageNumber;

		lastPage = getPage(getPageURL(pageNumber));

		if(pageNumber == 1 && !itemCountSet) {
			setItemCount();
		}

		return lastPage;
	}

	private void setItemCount() {
    if(lastPage == null) {
      setItemCount(0);
      itemCountSet = true;
    } else {
      String count = lastPage.getContentBeforeContent("items found for");
      if (count == null) {
        count = lastPage.getContentBeforeContent("items found in");
      }

      try {
        setItemCount(Integer.parseInt(count));
      } catch (NumberFormatException e) {
        JConfig.log().logMessage("Unable to find item count on page! URL: " + getPageURL(1));
      }

      // We set the flag regardless an error occurred.
      itemCountSet = true;
    }
  }

  public ListIterator listIterator(int index) {
    return new URLPagerIterator(this, index);
  }
}
