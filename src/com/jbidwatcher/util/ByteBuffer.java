package com.jbidwatcher.util;
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

import com.jbidwatcher.config.JConfig;

import java.io.*;
import java.util.zip.*;

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
    } catch(IOException ioe) {
      if(JConfig.debugging) {
        ErrorManagement.handleException("Error writing " + fileName, ioe);
      }
    }
  }
}
