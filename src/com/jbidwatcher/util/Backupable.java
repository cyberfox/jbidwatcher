package com.jbidwatcher.util;

import javax.annotation.Nonnull;

import java.io.File;

/**
 * Created by mrs on 2/11/17.
 */
public class Backupable {
  @Nonnull
  protected File fileWithBackup(String fileName) {
    File fp = new File(fileName);

    if (fp.exists()) {
      File oldFP = new File(fileName + "~");
      if (oldFP.exists()) {
        oldFP.delete();
      }
      fp.renameTo(oldFP);
    }
    return fp;
  }
}
