package com.jbidwatcher;

import com.jbidwatcher.util.ErrorManagement;
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

/**
 * @file   TimerHandler.java
 * @author Morgan Schweers <cyberfox@users.sourceforge.net>
 * @date   Sat Oct 12 18:42:10 2002
 * 
 * @brief  Several operations need 'regular wakeups', which is what this class provides.
 * 
 * 
 */

/*!@class TimerHandler
 *
 * @brief Provides once-a-second callbacks to classes that extend it.
 *
 */

public class TimerHandler extends Thread {
  private static final int ALMOST_A_SECOND = 990;
  private WakeupProcess _toWake = null;
  private volatile boolean _remainAsleep = false;
  private long _sleep_ms = ALMOST_A_SECOND;

  public interface WakeupProcess {
    boolean check();
  }

  public TimerHandler(WakeupProcess inWake, long sleeptime) {
    _toWake = inWake;
    _sleep_ms = sleeptime;
  }

  public TimerHandler(WakeupProcess inWake) {
    _toWake = inWake;
  }

  public void pause() { _remainAsleep = true; }
  public void unpause() { _remainAsleep = false; }
  public boolean isPaused() { return _remainAsleep; }

  public void run() {
    boolean interrupted = false;
    while(!interrupted) {
      try {
        sleep(_sleep_ms);
        if(!_remainAsleep) {
            _toWake.check();
        }
      } catch(InterruptedException ignored) {
        interrupted = true;
      } catch(Exception e) {
          ErrorManagement.handleException("Exception during the check() operation of " + _toWake.getClass().toString(), e);
      } catch(Error e) {
        //  This is more sketchy...
        ErrorManagement.handleException("Serious error, consider dying.", e);
      }
    }
  }
}
