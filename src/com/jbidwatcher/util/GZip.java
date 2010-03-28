package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.io.*;
import java.util.zip.*;

public class GZip {
  private static byte[] _byteHold = new byte[1];
  private static final Boolean _sync = Boolean.TRUE;
  private static final byte[] gzipHdrData = { 0x1f, -117, 0x08, 0x00,
                                              0x00, 0x00, 0x00, 0x00,
                                              0x02, -1 };
  byte[] _gzTest = new byte[2];
  byte[] _data;
  int _uclength;
  int _uccrc32;
  boolean nowrap = true;
  CRC32 _crc32Calculator = new CRC32();
  static int incrementme;

  public byte[] getCompressedData() { return _data; }
  public StringBuffer getUncompressedData() { return uncompress(_data, nowrap); }
  public StringBuffer getUncompressedData(boolean xt_nowrap) { return uncompress(_data, xt_nowrap); }
  public long getLength() { return _uclength; }
  public long getCRC() { return _uccrc32; }
  public void reset() { _data = null; _uclength = 0; _uccrc32 = 0; _crc32Calculator.reset(); }
  public void setData(byte[] newData, int len, int crc) {
    _data = newData;
    _uclength = len;
    _uccrc32 = crc;
  }
  public void setData(byte[] newData) {
    _crc32Calculator.reset();
    _crc32Calculator.update(newData);
    _uccrc32 = (int) _crc32Calculator.getValue();
    _uclength = newData.length;
    _data = compress(newData);
  }

  public int readInt(FileInputStream fis) throws IOException {
    int a, b, c, d;

    a = fis.read();
    b = fis.read();
    c = fis.read();
    d = fis.read();

    return(a + b << 8 + c << 16 + d << 24);
  }

  /**
   * Load a GZipped file into memory.
   * 
   * @noinspection ResultOfMethodCallIgnored
   *
   * @param fp - The file to load from.
   *
   * @throws IOException - If there are any errors reading from the file.
   */
  public void load(File fp) throws IOException {
    FileInputStream fis = new FileInputStream(fp);

    fis.read(_gzTest);
    //  If it's a 'real' GZipped file...
    if(_gzTest[0] == 0x1f && _gzTest[1] == -117) {
      if(fis.skip(8)==8) {
        _data = new byte[(int)fp.length() - 18 + 16];
        fis.read(_data, 0, (int)fp.length() - 18);

        _uccrc32 = readInt(fis);
        _uclength= readInt(fis);
        nowrap = true;
      }
    } else {
      _data = new byte[(int)fp.length()];
      System.arraycopy(_gzTest, 0, _data, 0, 2);
      nowrap = false;
      fis.read(_data, 2, _data.length-2);
    }
    fis.close();
  }

  private byte[] compress(byte[] inBytes) {
    Deflater df = new Deflater(9);
    byte[] newData;
    int deflatedBytes;

    //  We can't compress null.
    if(inBytes == null) return null;

    synchronized(_sync) {
      if(_byteHold == null || _byteHold.length < inBytes.length) {
        _byteHold = new byte[inBytes.length * 2];
      }

      df.setInput(inBytes);
      df.finish();
      deflatedBytes = df.deflate(_byteHold);

      deflatedBytes -= 4;

      newData = new byte[deflatedBytes];
      System.arraycopy(_byteHold, 0, newData, 0, deflatedBytes);
    }
    return newData;
  }

  public static StringBuffer uncompress(byte[] curPage, boolean nowrap) {
    Inflater infl = new Inflater(nowrap);
    byte[] outdata;
    int accumOutputBytes, inflatedBytes;
    int prevLength;

    //  We can't uncompress null.
    if(curPage == null) return null;

    synchronized(_sync) {
      prevLength = _byteHold.length;

      if(prevLength < curPage.length) {
        _byteHold = new byte[curPage.length];
      }
      infl.setInput(curPage);
      try {
        inflatedBytes = infl.inflate(_byteHold);
        accumOutputBytes = inflatedBytes;
        while(!infl.finished() && inflatedBytes != 0) {
          outdata = _byteHold;
          prevLength = _byteHold.length;
          if(accumOutputBytes >= (prevLength - 2048)) {
            try {
              inflatedBytes = 0;
              _byteHold = new byte[_byteHold.length * 3];
              System.arraycopy(outdata, 0, _byteHold, 0, accumOutputBytes);
              inflatedBytes = infl.inflate(_byteHold, accumOutputBytes, prevLength * 2);
            } catch(OutOfMemoryError oome) {
              JConfig.log().handleException("FAILING to allocate more bytes @ " + _byteHold.length * 3, oome);
            }
          } else {
            inflatedBytes = infl.inflate(_byteHold, accumOutputBytes, prevLength - accumOutputBytes);
          }
          accumOutputBytes += inflatedBytes;
        }
        return new StringBuffer(new String(_byteHold, 0, accumOutputBytes));
      } catch(DataFormatException dfe) {
        JConfig.log().handleException("Failed to uncompress data: " + dfe, dfe);
        return null;
      }
    }
  }

  /** 
   * Write out the given int in LSB->MSB format.
   * 
   * @param fos - Output stream to write to.
   * @param outie - Int value to write out in LSB->MSB format.
   *
   * @throws java.io.IOException - If the writing fails.
   */
  private void writeInt(FileOutputStream fos, int outie) throws IOException {
    byte a = (byte) ((outie & 0xff000000) >> 24);
    byte b = (byte) ((outie & 0x00ff0000) >> 16);
    byte c = (byte) ((outie & 0x0000ff00) >> 8);
    byte d = (byte) (outie & 0x000000ff);

    fos.write(d);
    fos.write(c);
    fos.write(b);
    fos.write(a);
  }

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
      //  Write 0x1f,0x8b,0x8,0x0,0x00000000,2,255,{Data},crc32,{datalen}
      fos.write(gzipHdrData, 0, gzipHdrData.length);
      fos.write(_data, 2, _data.length-2);
      writeInt(fos, _uccrc32);
      writeInt(fos, _uclength);
      fos.close();
    } catch(IOException ioe) {
      //  We dont throw the exception back up the chain, and let
      //  something with UI put up a display box because this can
      //  happen during unattended operation.
      //
      //  In fact, this can occur because the user deleted the
      //  directory, and many other reasons, so we do NOT report the
      //  error anymore, unless debugging.  This caused one user
      //  to have >28 megs of error logs.  Stop that!
      JConfig.log().handleDebugException("Error writing " + fileName, ioe);
    }
  }
}
