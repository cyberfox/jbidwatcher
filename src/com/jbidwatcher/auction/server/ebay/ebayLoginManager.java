package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Externalized;
import com.jbidwatcher.util.TT;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.auction.LoginManager;

import java.net.URLConnection;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Feb 25, 2007
 * Time: 7:11:25 PM
 *
 * Holds and manages the eBay login cookie.
 */
public class ebayLoginManager implements LoginManager {
  private volatile CookieJar mSignInCookie = null;
  private boolean mNotifySwing = true;
  private String mBadPassword = null;
  private String mBadUsername = null;
  private String mPassword;
  private String mUserId;
  private String mSiteName;
  private TT T;

  public ebayLoginManager(TT countryProperties, String site, String password, String userId) {
    mPassword = password;
    mUserId = userId;
    mSiteName = site;
    T = countryProperties;
  }

  public ebayLoginManager(TT countryProperties, String site, String password, String userId, boolean notifyFailures) {
    this(countryProperties, site, password, userId);
    mNotifySwing = notifyFailures;
  }

  public void resetCookie() {
    mBadPassword = null;
    mBadUsername = null;
    mSignInCookie = null;
  }

  public boolean updateLogin(String serverName) {
    String oldUsername = mUserId;
    String oldPassword = mPassword;

    setUserId(JConfig.queryConfiguration(serverName + ".user", "default"));
    setPassword(JConfig.queryConfiguration(serverName + ".password", "default"));

    return !mUserId.equals(oldUsername) || !mPassword.equals(oldPassword);
  }

  private URLConnection checkFollowRedirector(URLConnection current, CookieJar cj, String lookFor) throws IOException, CaptchaException {
    StringBuffer signed_in = Http.net().receivePage(current);
    JConfig.log().dump2File("sign_in-a1.html", signed_in);

    //  Parse the redirector, and find the URL that points to the adult
    //  confirmation page.
    JHTML redirector = new JHTML(signed_in);
    if (checkSecurityConfirmation(redirector)) return null;
    return checkHTMLFollowRedirect(redirector, lookFor, cj);
  }

  private static URLConnection checkHTMLFollowRedirect(JHTML redirectPage, String lookFor, CookieJar cj) {
    redirectPage.reset();
    JHTML.Form alertSuppressor = redirectPage.getFormWithInput("hidUrl");
    if(alertSuppressor != null) {
      if(alertSuppressor != null) {
        String url = redirectPage.getFormWithInput("hidUrl").getInputValue("hidUrl");
        return cj.connect(url);
      }
    }

    List<String> allURLs = redirectPage.getAllURLsOnPage(false);
    for (String url : allURLs) {
      //  If this URL has the text we're looking for in its body someplace, that's the one we want.
      if (url.indexOf(lookFor) != -1) {
        //  Replace nasty quoted amps with single-amps.
        url = url.replaceAll("&amp;", "&");
        url = url.replaceAll("\n", "");
        if (lookFor.equals("BidBin")) {
          int step = url.indexOf("BidBinInfo=");
          if (step != -1) {
            step += "BidBinInfo=".length();

            try {
              String encodedURL = URLEncoder.encode(url.substring(step), "UTF-8");
              //noinspection StringContatenationInLoop
              url = url.substring(0, step) + encodedURL;
            } catch (UnsupportedEncodingException ignored) {
              JConfig.log().logMessage("Failed to build a URL because of encoding transformation failure.");
            }
          }
        }
        //  Now get the actual page...
        return cj.connect(url);
      }
    }

    return null;
  }

  //  Get THAT page, which is actually (usually) a 'redirector' page with a meta-refresh
  //  and a clickable link in case meta-refresh doesn't work.
  private boolean getAdultRedirector(URLConnection uc_signin, CookieJar cj) throws IOException, CaptchaException {
    uc_signin = checkFollowRedirector(uc_signin, cj, "Adult");
    return uc_signin != null && getAdultConfirmation(uc_signin, cj);

  }

  private boolean getAdultConfirmation(URLConnection uc_signin, CookieJar cj) throws IOException {
    StringBuffer confirm = Http.net().receivePage(uc_signin);
    JConfig.log().dump2File("sign_in-a2.html", confirm);
    JHTML confirmPage = new JHTML(confirm);

    List<JHTML.Form> confirm_forms = confirmPage.getForms();
    boolean enqueued = false;
    for (JHTML.Form finalForm : confirm_forms) {
      if (finalForm.hasInput("MfcISAPICommand", "AdultSignIn")) {
        uc_signin = cj.connect(finalForm.getAction(), finalForm.getFormData(), null, true, null);
        StringBuffer confirmed = Http.net().receivePage(uc_signin);
        JConfig.log().dump2File("sign_in-a3.html", confirmed);
        JHTML htdoc = new JHTML(confirmed);
        JHTML.Form curForm = htdoc.getFormWithInput("pass");
        if (curForm != null) {
          MQFactory.getConcrete("login").enqueue("FAILED Couldn't find a password form on the sign in page.");
          return false;
        }
        enqueued = true;
        if(htdoc.grep(T.s("your.user.id.or.password.is.incorrect")) != null) {
          MQFactory.getConcrete("login").enqueue("FAILED Incorrect login information.");
        } else if(htdoc.grep(T.s("your.information.has.been.verified"))!=null) {
          MQFactory.getConcrete("login").enqueue("SUCCESSFUL");
        } else if(htdoc.grep(T.s("mature.audiences.accepted")) != null) {
          MQFactory.getConcrete("login").enqueue("SUCCESSFUL");
        } else if(htdoc.grep(T.s("mature.audiences.disallowed.outside.the.us")) != null) {
          MQFactory.getConcrete("login").enqueue("NEUTRAL Turn off 'Mature Audiences' in JBidwatcher configuration; it's not valid for non-US users.");
          JConfig.setConfiguration("ebay.mature", "false");
          JConfig.setConfiguration("ebay.international", "true");
        } else {
          JConfig.log().logFile("Neutral login result...", confirmed);
          MQFactory.getConcrete("login").enqueue("NEUTRAL");
        }
      }
    }
    if(!enqueued) {
      JConfig.log().logFile("No confirm form found...", confirm);
      MQFactory.getConcrete("login").enqueue("NEUTRAL No confirm form found.");
    }
    return true;
  }

  public synchronized CookieJar getSignInCookie(CookieJar old_cj) {
    if(getUserId().equals("default")) return old_cj;

    if (getPassword().equals(mBadPassword) && getUserId().equals(mBadUsername)) {
      JConfig.log().logDebug("Not getting the sign in cookie; username/password combo hasn't changed.");
      return old_cj;
    }

    String msg = "Getting the sign in cookie for " + T.getCountrySiteName();

    JConfig.log().logDebug(msg);
    MQFactory.getConcrete("Swing").enqueue(msg);

    CookieJar cj = getSignInCookie(getUserId(), getPassword());

    String done_msg = (cj!=null)?"Done getting the sign in cookie for ":"Did not successfully retrieve the sign in cookie for ";
    done_msg += T.getCountrySiteName();
    MQFactory.getConcrete("Swing").enqueue(done_msg);
    JConfig.log().logDebug(done_msg);

    return cj;
  }

  public synchronized CookieJar getNecessaryCookie(boolean force) {
    if (mSignInCookie == null || force) {
      mSignInCookie = getSignInCookie(mSignInCookie);
    }

    return (mSignInCookie);
  }

  // @noinspection TailRecursion
  public CookieJar getSignInCookie(String username, String password) {
    boolean isAdult = JConfig.queryConfiguration(mSiteName + ".mature", "false").equals("true");
    String startURL = T.s("ebayServer.signInPage");
    CookieJar cj = new CookieJar();

    if (isAdult) startURL = Externalized.getString("ebayServer.adultPageLogin");

    URLConnection uc_signin = cj.connect(startURL);
    if(JConfig.queryConfiguration("debug.auth", "false").equals("true")) {
      JConfig.log().logDebug("GET " + startURL);
      JConfig.log().logDebug(cj.dump());
    }
    try {
      StringBuffer signin = Http.net().receivePage(uc_signin);
      JConfig.log().dump2File("sign_in-1.html", signin);
      JHTML htdoc = new JHTML(signin);

      cj = signInUsingPage(startURL, username, password, isAdult, cj, htdoc);
    } catch (IOException e) {
      //  We don't know how far we might have gotten...  The cookies
      //  may be valid, even!  We can't assume it, though.
      MQFactory.getConcrete("login").enqueue("FAILED " + e.getMessage());
      if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN " + e.getMessage());
      JConfig.log().handleException("Couldn't sign in!", e);
      cj = null;
    } catch(CaptchaException ce) {
      MQFactory.getConcrete("login").enqueue("CAPTCHA");
      if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN eBay's increased security monitoring has been triggered, JBidwatcher cannot log in for a while.");
      notifySecurityIssue();
      JConfig.log().handleException("Couldn't sign in, captcha interference!", ce);
      cj = null;
    } catch(CookieJar.CookieRedirectException cre) {
      MQFactory.getConcrete("login").enqueue("CAPTCHA");
      if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN The login page is redirecting to itself infinitely; this probably means eBay's increased security monitoring has been triggered and JBidwatcher cannot log in for a while.");
      notifySecurityIssue();
      JConfig.log().handleException("Couldn't sign in, endless redirection; probably captcha interference!", cre);
    }

    return cj;
  }

  private CookieJar signInUsingPage(String previousPage, String username, String password, boolean adult, CookieJar cj, JHTML htdoc) throws IOException, CaptchaException {
    List<String> resultPages = new ArrayList<String>();
    JHTML.Form curForm = htdoc.getFormWithInput("pass");
    if (curForm != null) {
      //  If it has a password field, this is the input form.
      curForm.setText("userid", username);
      curForm.setText("pass", password);
      if(JConfig.queryConfiguration("debug.auth", "false").equals("true")) JConfig.log().logDebug("Cookies before posting form: \n" + cj.dump());
      URLConnection uc_signin = cj.connect(curForm.getAction(), curForm.getFormData(), previousPage, true, resultPages);

      if(JConfig.queryConfiguration("debug.auth", "false").equals("true")) {
        JConfig.log().logDebug("POST " + curForm.getAction());
        JConfig.log().logDebug("Data: " + curForm.getFormData());
        for(String page : resultPages) {
          JConfig.log().logDebug("Went to page: " + page);
        }
        JConfig.log().logDebug("Cookies after posting form: \n" + cj.dump());
      }

      boolean fixYourPassword = false;
      for(String page : resultPages) {
        if(page.indexOf("FYPShow") != -1) {
          fixYourPassword = true;
        }
      }

      if (adult) {
        if (getAdultRedirector(uc_signin, cj)) {
          if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("VALID LOGIN");
        } else {
          cj = retryLoginWithoutAdult(cj, username, password);
        }
      } else {
        StringBuffer confirm = Http.net().receivePage(uc_signin);
        JConfig.log().dump2File("sign_in-2.html", confirm);
        JHTML doc = new JHTML(confirm);
        if(fixYourPassword || doc.getTitle().equals("Reset your password")) {
          JConfig.log().logMessage("eBay is requesting that you change your password.");
          MQFactory.getConcrete("login").enqueue("FAILED You must change your password on eBay.");
        } else if (checkSecurityConfirmation(doc)) { //  Check for CAPTCHA and bad passwords...
          cj = null;
          MQFactory.getConcrete("login").enqueue("FAILED Sign in information is not valid.");
        } else {
          JHTML.Form redirect_form = doc.getFormWithInput("hidUrl");
          String hidUrl = null;
          if(redirect_form != null) {
            hidUrl = redirect_form.getInputValue("hidUrl");
          }
          if(hidUrl != null && (hidUrl.matches("^https?://(signin.ebay.(com|co.uk|ie))?.*my.*ebay.*(com|co.uk|ie).*ws.*eBayISAPI.dll.*My.*eBay.*$") || hidUrl.matches("^https?://www.ebay.(com|co.uk|ie).*$"))) {
            MQFactory.getConcrete("login").enqueue("SUCCESSFUL");
          } else {
            JConfig.log().logFile("Security checks out, but no My eBay form link on final page...", confirm);
            MQFactory.getConcrete("login").enqueue("NEUTRAL");
          }
          if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("VALID LOGIN");
        }
      }
    }
    return cj;
  }

  private CookieJar retryLoginWithoutAdult(CookieJar cj, String username, String password) {//  Disable adult mode and try again.
    JConfig.log().logMessage("Disabling 'mature audiences' mode and retrying.");
    JConfig.setConfiguration(mSiteName + ".mature", "false");
    cj = getSignInCookie(username, password);
    //  Re-enable adult mode if logging in via non-adult mode still failed...
    JConfig.setConfiguration(mSiteName + ".mature", "true");
    return cj;
  }

  private void notifySecurityIssue() {
    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + "eBay's security monitoring has been triggered, and temporarily requires\n" +
        "human intervention to log in.  JBidwatcher will not be able to log in\n" +
        "(including bids, snipes, and retrieving My eBay items) until this is fixed.");
  }

  private void notifyBadSignin() {
    MQFactory.getConcrete("Swing").enqueue("NOTIFY " + "Your sign in information appears to be incorrect, according to\n" +
        "eBay.  Please fix it in the eBay tab in the Configuration Manager.");
  }

  public static class CaptchaException extends Exception {
    private String _associatedString;

    public CaptchaException(String inString) {
      _associatedString = inString;
    }
    public String toString() {
      return _associatedString;
    }
  }

  private boolean checkSecurityConfirmation(JHTML doc) throws CaptchaException {
    if(doc.grep(T.s("security.measure")) != null ||
       doc.grep(T.s("enter.verification.code")) != null ||
       doc.grep(T.s("enter.a.verification.code.to.continue")) != null ||
       doc.grep(T.s("please.enter.the.verification.code")) != null) {
      JConfig.log().logMessage("eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      notifySecurityIssue();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      throw new CaptchaException("Failed eBay security check/captcha; verification code required.");
    }

    if (doc.grep(T.s("your.sign.in.information.is.not.valid")) != null ||
        doc.grep(T.s("your.user.id.or.password.is.incorrect")) != null) {
      JConfig.log().logMessage("Your sign in information is not correct.");
      if (mNotifySwing) MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN Your sign in information is not correct.  Fix it in the eBay tab in the Configuration Manager.");
      notifyBadSignin();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      return true;
    }

    return false;
  }

  public String getPassword() {
    return mPassword;
  }

  public String getUserId() {
    return mUserId;
  }

  public boolean isDefault() {
    return mUserId == null || mUserId.equals("default");
  }

  public boolean validate(String username, String password) {
    return !isDefault() && getUserId().equals(username) && getPassword().equals(password);
  }

  public boolean equals(ebayLoginManager that) {
    String user1 = getUserId();
    String pass1 = getPassword();
    String user2 = that.getUserId();
    String pass2 = that.getPassword();

    return !(user1 != null ? !user1.equals(user2) : user2 != null) &&
        !(pass1 != null ? !pass1.equals(pass2) : pass2 != null) &&
        (user1 == null || pass1 == null || user1.equals(user2) && pass1.equals(pass2));
  }

  public void setPassword(String password) {
    if(!mPassword.equals(password)) {
      mPassword = password;
      mSignInCookie = null;
    }
  }

  public void setUserId(String userId) {
    if(!mUserId.equals(userId)) {
      mUserId = userId;
      mSignInCookie = null;
    }
  }
}
