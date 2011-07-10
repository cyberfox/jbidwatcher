package com.jbidwatcher.util;

public class PauseManager {
  private long mPausedUntil = 0;
  private static PauseManager sInstance = new PauseManager();

  private PauseManager() { }

  public void pause(int seconds) {
    mPausedUntil = System.currentTimeMillis() + Constants.ONE_SECOND * seconds;
  }

  public void pause() {
    //  Pause until 5 minutes from now.
    pause(5 * 60);
  }

  public boolean isPaused() {
    if (mPausedUntil != 0 && mPausedUntil > System.currentTimeMillis()) {
      return true;
    }
    mPausedUntil = 0;
    return false;
  }

  public static PauseManager getInstance() {
    return sInstance;
  }
}
