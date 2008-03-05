package com.jbidwatcher.auction.server.ebay;

import com.jbidwatcher.util.http.CookieJar;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.auction.server.LoginManager;

import java.net.URLConnection;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.FileWriter;
import java.util.List;

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
  private String mBadPassword = null;
  private String mBadUsername = null;
  private String mPassword;
  private String mUserId;
  private String mSiteName;

  public ebayLoginManager(String site, String password, String userId) {
    mPassword = password;
    mUserId = userId;
    mSiteName = site;
  }

  public void resetCookie() {
    mBadPassword = null;
    mBadUsername = null;
    mSignInCookie = null;
  }

  /**
   * @param fname - The filename to output to.
   * @param sb    - The StringBuffer to dump out.
   * @brief Debugging function to dump a string buffer out to a file.
   * <p/>
   * This is used for 'emergency' debugging efforts.
   */
  private static void dump2File(String fname, StringBuffer sb) {
    FileWriter fw = null;
    try {
      fw = new FileWriter(fname);

      fw.write(sb.toString());
    } catch (IOException ioe) {
      com.jbidwatcher.util.config.ErrorManagement.handleException("Threw exception in dump2File!", ioe);
    } finally {
      if (fw != null) try {
        fw.close();
      } catch (IOException ignored) { /* I don't care about exceptions on close. */ }
    }
  }

  private URLConnection checkFollowRedirector(URLConnection current, CookieJar cj, String lookFor) throws IOException, CaptchaException {
    StringBuffer signed_in = Http.receivePage(current);
    if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a1.html", signed_in);

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
        return cj.getAllCookiesFromPage(url, null, false);
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
              com.jbidwatcher.util.config.ErrorManagement.logMessage("Failed to build a URL because of encoding transformation failure.");
            }
          }
        }
        //  Now get the actual page...
        return cj.getAllCookiesFromPage(url, null, false);
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

  private static boolean getAdultConfirmation(URLConnection uc_signin, CookieJar cj) throws IOException {
    StringBuffer confirm = Http.receivePage(uc_signin);
    if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a2.html", confirm);
    JHTML confirmPage = new JHTML(confirm);

    List<JHTML.Form> confirm_forms = confirmPage.getForms();
    for (JHTML.Form finalForm : confirm_forms) {
      if (finalForm.hasInput("MfcISAPICommand")) {
        uc_signin = cj.getAllCookiesFromPage(finalForm.getCGI(), null, false);
        StringBuffer confirmed = Http.receivePage(uc_signin);
        if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-a2.html", confirmed);
        JHTML htdoc = new JHTML(confirmed);
        JHTML.Form curForm = htdoc.getFormWithInput("pass");
        if (curForm != null) {
          return false;
        }
      }
    }
    return true;
  }

  public synchronized CookieJar getSignInCookie(CookieJar old_cj) {
    if (getPassword().equals(mBadPassword) && getUserId().equals(mBadUsername)) {
      return old_cj;
    }

    String msg = "Getting the sign in cookie.";

    ErrorManagement.logDebug(msg);
    MQFactory.getConcrete("Swing").enqueue(msg);

    CookieJar cj = getSignInCookie(old_cj, getUserId(), getPassword());

    String done_msg = "Done getting the sign in cookie.";
    MQFactory.getConcrete("Swing").enqueue(done_msg);
    com.jbidwatcher.util.config.ErrorManagement.logDebug(done_msg);

    return cj;
  }

  public synchronized CookieJar getNecessaryCookie(boolean force) {
    if (mSignInCookie == null || force) {
      mSignInCookie = getSignInCookie(mSignInCookie);
    }

    return (mSignInCookie);
  }

  // @noinspection TailRecursion
  public CookieJar getSignInCookie(CookieJar oldCookie, String username, String password) {
    boolean isAdult = JConfig.queryConfiguration(mSiteName + ".adult", "false").equals("true");
    CookieJar cj = (oldCookie == null) ? new CookieJar() : oldCookie;
    String startURL = com.jbidwatcher.util.config.Externalized.getString("ebayServer.signInPage");
    if (isAdult) {
      startURL = com.jbidwatcher.util.config.Externalized.getString("ebayServer.adultPageLogin");
    }
    URLConnection uc_signin = cj.getAllCookiesFromPage(startURL, null, false);
    try {
      StringBuffer signin = Http.receivePage(uc_signin);
      if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-1.html", signin);
      JHTML htdoc = new JHTML(signin);

      JHTML.Form curForm = htdoc.getFormWithInput("pass");
      if (curForm != null) {
        //  If it has a password field, this is the input form.
        curForm.setText("userid", username);
        curForm.setText("pass", password);
        uc_signin = cj.getAllCookiesFromPage(curForm.getCGI(), null, false);
        if (isAdult) {
          if (getAdultRedirector(uc_signin, cj)) {
            MQFactory.getConcrete("Swing").enqueue("VALID LOGIN");
          } else {
            //  Disable adult mode and try again.
            ErrorManagement.logMessage("Disabling 'adult' mode and retrying.");
            JConfig.setConfiguration(mSiteName + ".adult", "false");
            cj = getSignInCookie(cj, username, password);
            //  Re-enable adult mode if logging in via non-adult mode still failed...
            if(cj == null) {
              JConfig.setConfiguration(mSiteName + ".adult", "true");
            }
            return cj;
          }
        } else {
          StringBuffer confirm = Http.receivePage(uc_signin);
          if (JConfig.queryConfiguration("debug.filedump", "false").equals("true")) dump2File("sign_in-2.html", confirm);
          JHTML doc = new JHTML(confirm);
          if (checkSecurityConfirmation(doc)) {
            cj = null;
          } else {
            MQFactory.getConcrete("Swing").enqueue("VALID LOGIN");
          }
        }
      }
    } catch (IOException e) {
      //  We don't know how far we might have gotten...  The cookies
      //  may be valid, even!  We can't assume it, though.
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN " + e.getMessage());
      ErrorManagement.handleException("Couldn't sign in!", e);
      cj = null;
    } catch(CaptchaException ce) {
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN eBay's increased security monitoring has been triggered, JBidwatcher cannot log in for a while.");
      notifySecurityIssue();
      ErrorManagement.handleException("Couldn't sign in, captcha interference!", ce);
      cj = null;
    }

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

  public class CaptchaException extends Exception {
    private String _associatedString;

    public CaptchaException(String inString) {
      _associatedString = inString;
    }
    public String toString() {
      return _associatedString;
    }
  }

  private boolean checkSecurityConfirmation(JHTML doc) throws IOException, CaptchaException {
    if(doc.grep("Security.Measure") != null ||
       doc.grep("Enter verification code:") != null ||
       doc.grep("Enter a verification code to continue") != null ||
       doc.grep("please enter the verification code") != null) {
      ErrorManagement.logMessage("eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN eBay's security monitoring has been triggered, and temporarily requires human intervention to log in.");
      notifySecurityIssue();
      mBadPassword = getPassword();
      mBadUsername = getUserId();
      throw new CaptchaException("Failed eBay security check/captcha; verification code required.");
    }

    if (doc.grep("Your sign in information is not valid.") != null) {
      ErrorManagement.logMessage("Your sign in information is not valid.");
      MQFactory.getConcrete("Swing").enqueue("INVALID LOGIN Your sign in information is not correct.  Fix it in the eBay tab in the Configuration Manager.");
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
