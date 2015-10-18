package com.jbidwatcher.util.config;

import com.cyberfox.util.config.ErrorManagement;

/**
 * Created by mrs on 10/17/15.
 */
public class JBErrorManagement extends ErrorManagement {
  @Override
  public void handleDebugException(String sError, Throwable e) {
    if (JConfig.debugging) super.handleDebugException(sError, e);
    else JConfig.getMetrics().trackCustomData("debug_exception", sError + "(" + e.getMessage() + ")");
  }

  @Override
  public void handleException(String sError, Throwable e) {
    JConfig.getMetrics().trackCustomData("exception", sError + "(" + e.getMessage() + ")");
    super.handleException(sError, e);
  }
}
