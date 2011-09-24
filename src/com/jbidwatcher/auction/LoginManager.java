package com.jbidwatcher.auction;

import com.jbidwatcher.util.http.CookieJar;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 7:39:24 PM
 *
 * Abstraction of the process of logging in, to define the API to be used to log in to a service.
 */
public interface LoginManager {
  void resetCookie();

  /**
   * @brief eBay has a cookie that is needed to do virtually anything
   * interesting on their site; this function retrieves that cookie,
   * and holds on to it.
   * <p/>
   * If you are registered as an adult, it also logs in through that
   * page, getting all necessary cookies.
   *
   * @param old_cj - The previous cookie jar before we tried to update it.
   *
   * @return - A collection of cookies that need to be passed around
   *         (and updated) each time pages are requested, etc., on eBay.
   */
  CookieJar getSignInCookie(CookieJar old_cj);

  /**
   * @param force - Force an update of the cookie, even if it's not
   *              time yet.
   * @return - A cookie jar of all the necessary cookies to do eBay connections.
   * @brief Returns the set of cookies necessary to be posted in order
   * to retrieve auctions.  getNecessaryCookie() can return null when
   * the process of logging in can't be done, for whatever reason.
   * (For instance, eBay's 2-3 hour downtime on Friday mornings @
   * 1-3am.)
   */
  CookieJar getNecessaryCookie(boolean force);
}
