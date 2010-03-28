package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.io.*;
import java.util.zip.*;
import com.jbidwatcher.util.config.JConfig;

public class ByteBuffer {
  private byte[] data;
  private int length;
  private CRC32 crcCalc = new CRC32();
  private int crc;

  public ByteBuffer(byte[] inData, int inLength) {
    data = inData;
    length = inLength;

    crcCalc.reset();
    crcCalc.update(data);
    crc = (int)crcCalc.getValue();
  }

  public int getLength() { return length; }
  public byte[] getData() { return data; }
  public int getCRC() { return crc; }

  public void save(String fileName) {
    File fp = new File(fileName);

    if(fp.exists()) {
      File oldFP = new File(fileName + "~");
      if(oldFP.exists()) {
        oldFP.delete();
      }
      fp.renameTo(oldFP);
    }

    try {
      FileOutputStream fos = new FileOutputStream(fp);
      fos.write(data, 0, length);
      fos.close();
    } catch(IOException ioe) {
      JConfig.log().handleException("Error writing " + fileName, ioe);
    }
  }
}
