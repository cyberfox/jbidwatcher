/***************************************************************************
 *  JLayerME is a JAVA library that decodes/plays/converts MPEG 1/2 Layer 3.
 *  Project Homepage: http://www.javazoom.net/javalayer/javalayerme.html.
 *  Copyright (C) JavaZOOM 1999-2005.
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *---------------------------------------------------------------------------
 *  20 Aug 2004 Konstantin Belous 
 *  Changed the code that loads the d16.dat resource file with purpose 
 *  of compatibility to Netscape Navigator. 
 * 
 *  12 Aug 2004 Konstantin Belous 
 *  Replaced the Arrays.fill() calls in the reset() method 
 *  with the fill in the loop with purposes of compatibility to Java 1.1. 
 *---------------------------------------------------------------------------
 */
package javazoom.jlme.decoder;


import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.util.Arrays;

final class SynthesisFilter {
  private final static double MY_PI = 3.14159265358979323846;
  private final static float cos1_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI / 64.0)));
  private final static float cos3_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 3.0 / 64.0)));
  private final static float cos5_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 5.0 / 64.0)));
  private final static float cos7_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 7.0 / 64.0)));
  private final static float cos9_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 9.0 / 64.0)));
  private final static float cos11_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 11.0 / 64.0)));
  private final static float cos13_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 13.0 / 64.0)));
  private final static float cos15_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 15.0 / 64.0)));
  private final static float cos17_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 17.0 / 64.0)));
  private final static float cos19_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 19.0 / 64.0)));
  private final static float cos21_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 21.0 / 64.0)));
  private final static float cos23_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 23.0 / 64.0)));
  private final static float cos25_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 25.0 / 64.0)));
  private final static float cos27_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 27.0 / 64.0)));
  private final static float cos29_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 29.0 / 64.0)));
  private final static float cos31_64 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 31.0 / 64.0)));
  private final static float cos1_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI / 32.0)));
  private final static float cos3_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 3.0 / 32.0)));
  private final static float cos5_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 5.0 / 32.0)));
  private final static float cos7_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 7.0 / 32.0)));
  private final static float cos9_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 9.0 / 32.0)));
  private final static float cos11_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 11.0 / 32.0)));
  private final static float cos13_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 13.0 / 32.0)));
  private final static float cos15_32 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 15.0 / 32.0)));
  private final static float cos1_16 = (float)(1.0 / (2.0 * Math.cos(MY_PI / 16.0)));
  private final static float cos3_16 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 3.0 / 16.0)));
  private final static float cos5_16 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 5.0 / 16.0)));
  private final static float cos7_16 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 7.0 / 16.0)));
  private final static float cos1_8 = (float)(1.0 / (2.0 * Math.cos(MY_PI / 8.0)));
  private final static float cos3_8 = (float)(1.0 / (2.0 * Math.cos(MY_PI * 3.0 / 8.0)));
  private final static float cos1_4 = (float)(1.0 / (2.0 * Math.cos(MY_PI / 4.0)));


  private static float d16[] [] = null;
  private float[] v1;
  private float[] v2;
  private float[] actual_v;
  private int actual_write_pos;
  private float[] samples;
  private int channel;
  private static float scalefactor;

 public SynthesisFilter(int channelnumber, float factor) {
    if (d16 == null)
      loadD16();
    v1 = new float[512];
    v2 = new float[512];
    channel = channelnumber;
    scalefactor = factor;
    reset();
  }

  /**
   *  Converts a 1D array into a number of smaller arrays. This is used to achieve offset + constant indexing into an array. Each sub-array represents a block of values of the original array.
   *@param  array      The array to split up into blocks.
   *@param  blockSize  The size of the blocks to split the array into. This must be an exact divisor of the length of the array, or some data will be lost from the main array.
   *@return            An array of arrays in which each element in the returned array will be of length <code>blockSize</code>.
   */
  private final void loadD16() {
    float d[] = null;
    ObjectInputStream in = null;

    try {
      in = new ObjectInputStream(getClass().getClassLoader().getResourceAsStream("d16.ser"));
      d = (float[]) in.readObject();
    }
    catch (Exception e) {
      System.out.println("2 couldn't load the array for the SynthesisFilter ");
      System.exit(1);
    }
    finally {
      try {
        in.close();
      }
      catch (Exception e) { }
    }
    int size = d.length / 16;
    d16 = new float[size] [];
    for (int i = 0; i < size; i++) {
      d16[i] = subArray(d, i * 16, 16);
    }
  }

  /**
   *  Returns a subarray of an existing array.
   *@param  array  The array to retrieve a subarra from.
   *@param  offs   The offset in the array that corresponds to the first index of the subarray.
   *@param  len    The number of indeces in the subarray.
   *@return        The subarray, which may be of length 0.
   */
  private final static float[] subArray(float[] array, int offs, int len) {
    if (offs + len > array.length) {
      len = array.length - offs;
    }
    if (len < 0) {
      len = 0;
    }
    float[] subarray = new float[len];
    System.arraycopy(array, offs, subarray, 0, len);
    return subarray;
  }

  /** Reset the synthesis filter. */
  public final void reset() {
	for(int i = 0; i < 512; i++) {
		v1[i] = 0;	
		v2[i] = 0;	
	}
    //Arrays.fill(samples, 0);
    actual_v = v1;
    actual_write_pos = 15;
  }


  public final void calculateSamples(float[] s, SampleBuffer buffer) {
    samples = s;
    compute_new_v();
    compute_pcm_samples(buffer);
    actual_write_pos = (actual_write_pos + 1) & 0xf;
    actual_v = (actual_v == v1) ? v2 : v1;
    //Arrays.fill(samples, 0f);
  }

    /* taken out of compute_new_v */

  static float new_v0, new_v1, new_v2, new_v3, new_v4, new_v5, new_v6, new_v7, new_v8, new_v9, new_v10, new_v11, new_v12, new_v13, new_v14, new_v15, new_v16, new_v17, new_v18, new_v19, new_v20,
      new_v21, new_v22, new_v23, new_v24, new_v25, new_v26, new_v27, new_v28, new_v29, new_v30, new_v31, p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15, pp0, pp1, pp2, pp3, pp4,
      pp5, pp6, pp7, pp8, pp9, pp10, pp11, pp12, pp13, pp14, pp15;
  static int loc1, loc2, loc3, loc4, loc5, loc6, loc7, loc8, loc9, loc10, loc11, loc12, loc13, loc14, loc15, loc16, loc17, loc18, loc19, loc20, loc21, loc22, loc23, loc24, loc25, loc26,
      loc27, loc28, loc29, loc30, loc31, loc32;
  static float smp1, smp2, smp3, smp4, smp5, smp6, smp7, smp8, smp9, smp10, smp11, smp12, smp13, smp14, smp15, smp16, smp17, smp18, smp19, smp20, smp21, smp22, smp23, smp24, smp25, smp26,
      smp27, smp28, smp29, smp30, smp31, smp32;

  /** Compute new values via a fast cosine transform. */
  private final void compute_new_v() {
    smp1 = samples[0];
    smp2 = samples[1];
    smp3 = samples[2];
    smp4 = samples[3];
    smp5 = samples[4];
    smp6 = samples[5];
    smp7 = samples[6];
    smp8 = samples[7];
    smp9 = samples[8];
    smp10 = samples[9];
    smp11 = samples[10];
    smp12 = samples[11];
    smp13 = samples[12];
    smp14 = samples[13];
    smp15 = samples[14];
    smp16 = samples[15];
    smp17 = samples[16];
    smp18 = samples[17];
    smp19 = samples[18];
    smp20 = samples[19];
    smp21 = samples[20];
    smp22 = samples[21];
    smp23 = samples[22];
    smp24 = samples[23];
    smp25 = samples[24];
    smp26 = samples[25];
    smp27 = samples[26];
    smp28 = samples[27];
    smp29 = samples[28];
    smp30 = samples[29];
    smp31 = samples[30];
    smp32 = samples[31];
    p0 = smp1 + smp32;
    p1 = smp2 + smp31;
    p2 = smp3 + smp30;
    p3 = smp4 + smp29;
    p4 = smp5 + smp28;
    p5 = smp6 + smp27;
    p6 = smp7 + smp26;
    p7 = smp8 + smp25;
    p8 = smp9 + smp24;
    p9 = smp10 + smp23;
    p10 = smp11 + smp22;
    p11 = smp12 + smp21;
    p12 = smp13 + smp20;
    p13 = smp14 + smp19;
    p14 = smp15 + smp18;
    p15 = smp16 + smp17;
    pp0 = p0 + p15;
    pp1 = p1 + p14;
    pp2 = p2 + p13;
    pp3 = p3 + p12;
    pp4 = p4 + p11;
    pp5 = p5 + p10;
    pp6 = p6 + p9;
    pp7 = p7 + p8;
    pp8 = (p0 - p15) * cos1_32;
    pp9 = (p1 - p14) * cos3_32;
    pp10 = (p2 - p13) * cos5_32;
    pp11 = (p3 - p12) * cos7_32;
    pp12 = (p4 - p11) * cos9_32;
    pp13 = (p5 - p10) * cos11_32;
    pp14 = (p6 - p9) * cos13_32;
    pp15 = (p7 - p8) * cos15_32;
    p0 = pp0 + pp7;
    p1 = pp1 + pp6;
    p2 = pp2 + pp5;
    p3 = pp3 + pp4;
    p4 = (pp0 - pp7) * cos1_16;
    p5 = (pp1 - pp6) * cos3_16;
    p6 = (pp2 - pp5) * cos5_16;
    p7 = (pp3 - pp4) * cos7_16;
    p8 = pp8 + pp15;
    p9 = pp9 + pp14;
    p10 = pp10 + pp13;
    p11 = pp11 + pp12;
    p12 = (pp8 - pp15) * cos1_16;
    p13 = (pp9 - pp14) * cos3_16;
    p14 = (pp10 - pp13) * cos5_16;
    p15 = (pp11 - pp12) * cos7_16;
    pp0 = p0 + p3;
    pp1 = p1 + p2;
    pp2 = (p0 - p3) * cos1_8;
    pp3 = (p1 - p2) * cos3_8;
    pp4 = p4 + p7;
    pp5 = p5 + p6;
    pp6 = (p4 - p7) * cos1_8;
    pp7 = (p5 - p6) * cos3_8;
    pp8 = p8 + p11;
    pp9 = p9 + p10;
    pp10 = (p8 - p11) * cos1_8;
    pp11 = (p9 - p10) * cos3_8;
    pp12 = p12 + p15;
    pp13 = p13 + p14;
    pp14 = (p12 - p15) * cos1_8;
    pp15 = (p13 - p14) * cos3_8;
    p0 = pp0 + pp1;
    p1 = (pp0 - pp1) * cos1_4;
    p2 = pp2 + pp3;
    p3 = (pp2 - pp3) * cos1_4;
    p4 = pp4 + pp5;
    p5 = (pp4 - pp5) * cos1_4;
    p6 = pp6 + pp7;
    p7 = (pp6 - pp7) * cos1_4;
    p8 = pp8 + pp9;
    p9 = (pp8 - pp9) * cos1_4;
    p10 = pp10 + pp11;
    p11 = (pp10 - pp11) * cos1_4;
    p12 = pp12 + pp13;
    p13 = (pp12 - pp13) * cos1_4;
    p14 = pp14 + pp15;
    p15 = (pp14 - pp15) * cos1_4;
    // this is pretty insane coding
    float tmp1;
    new_v19/*
         *  36-17
         */
    = -(new_v4 = (new_v12 = p7) + p5) - p6;
    new_v27/*
         *  44-17
         */
    = -p6 - p7 - p4;
    new_v6 = (new_v10 = (new_v14 = p15) + p11) + p13;
    new_v17/*
         *  34-17
         */
    = -(new_v2 = p15 + p13 + p9) - p14;
    new_v21/*
         *  38-17
         */
    = (tmp1 = -p14 - p15 - p10 - p11) - p13;
    new_v29/*
         *  46-17
         */
    = -p14 - p15 - p12 - p8;
    new_v25/*
         *  42-17
         */
    = tmp1 - p12;
    new_v31/*
         *  48-17
         */
    = -p0;
    new_v0 = p1;
    new_v23/*
         *  40-17
         */
    = -(new_v8 = p3) - p2;
    p0 = (smp1 - smp32) * cos1_64;
    p1 = (smp2 - smp31) * cos3_64;
    p2 = (smp3 - smp30) * cos5_64;
    p3 = (smp4 - smp29) * cos7_64;
    p4 = (smp5 - smp28) * cos9_64;
    p5 = (smp6 - smp27) * cos11_64;
    p6 = (smp7 - smp26) * cos13_64;
    p7 = (smp8 - smp25) * cos15_64;
    p8 = (smp9 - smp24) * cos17_64;
    p9 = (smp10 - smp23) * cos19_64;
    p10 = (smp11 - smp22) * cos21_64;
    p11 = (smp12 - smp21) * cos23_64;
    p12 = (smp13 - smp20) * cos25_64;
    p13 = (smp14 - smp19) * cos27_64;
    p14 = (smp15 - smp18) * cos29_64;
    p15 = (smp16 - smp17) * cos31_64;
    pp0 = p0 + p15;
    pp1 = p1 + p14;
    pp2 = p2 + p13;
    pp3 = p3 + p12;
    pp4 = p4 + p11;
    pp5 = p5 + p10;
    pp6 = p6 + p9;
    pp7 = p7 + p8;
    pp8 = (p0 - p15) * cos1_32;
    pp9 = (p1 - p14) * cos3_32;
    pp10 = (p2 - p13) * cos5_32;
    pp11 = (p3 - p12) * cos7_32;
    pp12 = (p4 - p11) * cos9_32;
    pp13 = (p5 - p10) * cos11_32;
    pp14 = (p6 - p9) * cos13_32;
    pp15 = (p7 - p8) * cos15_32;
    p0 = pp0 + pp7;
    p1 = pp1 + pp6;
    p2 = pp2 + pp5;
    p3 = pp3 + pp4;
    p4 = (pp0 - pp7) * cos1_16;
    p5 = (pp1 - pp6) * cos3_16;
    p6 = (pp2 - pp5) * cos5_16;
    p7 = (pp3 - pp4) * cos7_16;
    p8 = pp8 + pp15;
    p9 = pp9 + pp14;
    p10 = pp10 + pp13;
    p11 = pp11 + pp12;
    p12 = (pp8 - pp15) * cos1_16;
    p13 = (pp9 - pp14) * cos3_16;
    p14 = (pp10 - pp13) * cos5_16;
    p15 = (pp11 - pp12) * cos7_16;
    pp0 = p0 + p3;
    pp1 = p1 + p2;
    pp2 = (p0 - p3) * cos1_8;
    pp3 = (p1 - p2) * cos3_8;
    pp4 = p4 + p7;
    pp5 = p5 + p6;
    pp6 = (p4 - p7) * cos1_8;
    pp7 = (p5 - p6) * cos3_8;
    pp8 = p8 + p11;
    pp9 = p9 + p10;
    pp10 = (p8 - p11) * cos1_8;
    pp11 = (p9 - p10) * cos3_8;
    pp12 = p12 + p15;
    pp13 = p13 + p14;
    pp14 = (p12 - p15) * cos1_8;
    pp15 = (p13 - p14) * cos3_8;
    p0 = pp0 + pp1;
    p1 = (pp0 - pp1) * cos1_4;
    p2 = pp2 + pp3;
    p3 = (pp2 - pp3) * cos1_4;
    p4 = pp4 + pp5;
    p5 = (pp4 - pp5) * cos1_4;
    p6 = pp6 + pp7;
    p7 = (pp6 - pp7) * cos1_4;
    p8 = pp8 + pp9;
    p9 = (pp8 - pp9) * cos1_4;
    p10 = pp10 + pp11;
    p11 = (pp10 - pp11) * cos1_4;
    p12 = pp12 + pp13;
    p13 = (pp12 - pp13) * cos1_4;
    p14 = pp14 + pp15;
    p15 = (pp14 - pp15) * cos1_4;
    // manually doing something that a compiler should handle sucks
    // coding like this is hard to read
    float tmp2;
    new_v5 = (new_v11 = (new_v13 = (new_v15 = p15) + p7) + p11) + p5 + p13;
    new_v7 = (new_v9 = p15 + p11 + p3) + p13;
    new_v16/*
         *  33-17
         */
    = -(new_v1 = (tmp1 = p13 + p15 + p9) + p1) - p14;
    new_v18/*
         *  35-17
         */
    = -(new_v3 = tmp1 + p5 + p7) - p6 - p14;
    new_v22/*
         *  39-17
         */
    = (tmp1 = -p10 - p11 - p14 - p15) - p13 - p2 - p3;
    new_v20/*
         *  37-17
         */
    = tmp1 - p13 - p5 - p6 - p7;
    new_v24/*
         *  41-17
         */
    = tmp1 - p12 - p2 - p3;
    new_v26/*
         *  43-17
         */
    = tmp1 - p12 - (tmp2 = p4 + p6 + p7);
    new_v30/*
         *  47-17
         */
    = (tmp1 = -p8 - p12 - p14 - p15) - p0;
    new_v28/*
         *  45-17
         */
    = tmp1 - tmp2;
    loc1 = actual_write_pos;
     loc2 = 16 + actual_write_pos;
     loc3 = 32 + actual_write_pos;
     loc4 = 48 + actual_write_pos;
     loc5 = 64 + actual_write_pos;
     loc6 = 80 + actual_write_pos;
     loc7 = 96 + actual_write_pos;
     loc8 = 112 + actual_write_pos;
     loc9 = 128 + actual_write_pos;
     loc10 = 144 + actual_write_pos;
     loc11 = 160 + actual_write_pos;
     loc12 = 176 + actual_write_pos;
     loc13 = 192 + actual_write_pos;
     loc14 = 208 + actual_write_pos;
     loc15 = 224 + actual_write_pos;
     loc16 = 240 + actual_write_pos;
     loc17 = 256 + actual_write_pos;
     loc18 = 272 + actual_write_pos;
     loc19 = 288 + actual_write_pos;
     loc20 = 304 + actual_write_pos;
     loc21 = 320 + actual_write_pos;
     loc22 = 336 + actual_write_pos;
     loc23 = 352 + actual_write_pos;
     loc24 = 368 + actual_write_pos;
     loc25 = 384 + actual_write_pos;
     loc26 = 400 + actual_write_pos;
     loc27 = 416 + actual_write_pos;
     loc28 = 432 + actual_write_pos;
     loc29 = 448 + actual_write_pos;
     loc30 = 464 + actual_write_pos;
     loc31 = 480 + actual_write_pos;
     loc32 = 496 + actual_write_pos;
    float dest[] = actual_v;
    dest[loc1] = new_v0;
    dest[loc2] = new_v1;
    dest[loc3] = new_v2;
    dest[loc4] = new_v3;
    dest[loc5] = new_v4;
    dest[loc6] = new_v5;
    dest[loc7] = new_v6;
    dest[loc8] = new_v7;
    dest[loc9] = new_v8;
    dest[loc10] = new_v9;
    dest[loc11] = new_v10;
    dest[loc12] = new_v11;
    dest[loc13] = new_v12;
    dest[loc14] = new_v13;
    dest[loc15] = new_v14;
    dest[loc16] = new_v15;
    dest[loc17] = 0.0f;
    dest[loc18] = -new_v15;
    dest[loc19] = -new_v14;
    dest[loc20] = -new_v13;
    dest[loc21] = -new_v12;
    dest[loc22] = -new_v11;
    dest[loc23] = -new_v10;
    dest[loc24] = -new_v9;
    dest[loc25] = -new_v8;
    dest[loc26] = -new_v7;
    dest[loc27] = -new_v6;
    dest[loc28] = -new_v5;
    dest[loc29] = -new_v4;
    dest[loc30] = -new_v3;
    dest[loc31] = -new_v2;
    dest[loc32] = -new_v1;
    dest = (dest == v1) ? v2 : v1;
    dest[loc1] = -new_v0;
    dest[loc2] = new_v16;
    dest[loc3] = new_v17;
    dest[loc4] = new_v18;
    dest[loc5] = new_v19;
    dest[loc6] = new_v20;
    dest[loc7] = new_v21;
    dest[loc8] = new_v22;
    dest[loc9] = new_v23;
    dest[loc10] = new_v24;
    dest[loc11] = new_v25;
    dest[loc12] = new_v26;
    dest[loc13] = new_v27;
    dest[loc14] = new_v28;
    dest[loc15] = new_v29;
    dest[loc16] = new_v30;
    dest[loc17] = new_v31;
    dest[loc18] = new_v30;
    dest[loc19] = new_v29;
    dest[loc20] = new_v28;
    dest[loc21] = new_v27;
    dest[loc22] = new_v26;
    dest[loc23] = new_v25;
    dest[loc24] = new_v24;
    dest[loc25] = new_v23;
    dest[loc26] = new_v22;
    dest[loc27] = new_v21;
    dest[loc28] = new_v20;
    dest[loc29] = new_v19;
    dest[loc30] = new_v18;
    dest[loc31] = new_v17;
    dest[loc32] = new_v16;
  }

  static int dvp, pos, channels;
  static float temp;

  private final void compute_pcm_samples(SampleBuffer buff) {
    dvp = 0;
    pos = buff.getBufferIndex(channel);
    channels = buff.getBufferChannelCount();
    final byte[] bytes = buff.getBuffer();

    switch (actual_write_pos) {
      case 0:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[dvp] * dp[0]) + (actual_v[15 + dvp] * dp[1]) + (actual_v[14 + dvp] * dp[2]) + (actual_v[13 + dvp] * dp[3]) + (actual_v[12 + dvp] * dp[4]) +
              (actual_v[11 + dvp] * dp[5]) + (actual_v[10 + dvp] * dp[6]) + (actual_v[9 + dvp] * dp[7]) + (actual_v[8 + dvp] * dp[8]) + (actual_v[7 + dvp] * dp[9]) + (actual_v[6 + dvp] * dp[10]) +
              (actual_v[5 + dvp] * dp[11]) + (actual_v[4 + dvp] * dp[12]) + (actual_v[3 + dvp] * dp[13]) + (actual_v[2 + dvp] * dp[14]) + (actual_v[1 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 1:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[1 + dvp] * dp[0]) + (actual_v[dvp] * dp[1]) + (actual_v[15 + dvp] * dp[2]) + (actual_v[14 + dvp] * dp[3]) + (actual_v[13 + dvp] * dp[4]) +
              (actual_v[12 + dvp] * dp[5]) + (actual_v[11 + dvp] * dp[6]) + (actual_v[10 + dvp] * dp[7]) + (actual_v[9 + dvp] * dp[8]) + (actual_v[8 + dvp] * dp[9]) + (actual_v[7 + dvp] * dp[10]) +
              (actual_v[6 + dvp] * dp[11]) + (actual_v[5 + dvp] * dp[12]) + (actual_v[4 + dvp] * dp[13]) + (actual_v[3 + dvp] * dp[14]) + (actual_v[2 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 2:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[2 + dvp] * dp[0]) + (actual_v[1 + dvp] * dp[1]) + (actual_v[dvp] * dp[2]) + (actual_v[15 + dvp] * dp[3]) + (actual_v[14 + dvp] * dp[4]) +
              (actual_v[13 + dvp] * dp[5]) + (actual_v[12 + dvp] * dp[6]) + (actual_v[11 + dvp] * dp[7]) + (actual_v[10 + dvp] * dp[8]) + (actual_v[9 + dvp] * dp[9]) + (actual_v[8 + dvp] * dp[10]) +
              (actual_v[7 + dvp] * dp[11]) + (actual_v[6 + dvp] * dp[12]) + (actual_v[5 + dvp] * dp[13]) + (actual_v[4 + dvp] * dp[14]) + (actual_v[3 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 3:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[3 + dvp] * dp[0]) + (actual_v[2 + dvp] * dp[1]) + (actual_v[1 + dvp] * dp[2]) + (actual_v[dvp] * dp[3]) + (actual_v[15 + dvp] * dp[4]) +
              (actual_v[14 + dvp] * dp[5]) + (actual_v[13 + dvp] * dp[6]) + (actual_v[12 + dvp] * dp[7]) + (actual_v[11 + dvp] * dp[8]) + (actual_v[10 + dvp] * dp[9]) + (actual_v[9 + dvp] * dp[10]) +
              (actual_v[8 + dvp] * dp[11]) + (actual_v[7 + dvp] * dp[12]) + (actual_v[6 + dvp] * dp[13]) + (actual_v[5 + dvp] * dp[14]) + (actual_v[4 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 4:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[4 + dvp] * dp[0]) + (actual_v[3 + dvp] * dp[1]) + (actual_v[2 + dvp] * dp[2]) + (actual_v[1 + dvp] * dp[3]) + (actual_v[dvp] * dp[4]) +
              (actual_v[15 + dvp] * dp[5]) + (actual_v[14 + dvp] * dp[6]) + (actual_v[13 + dvp] * dp[7]) + (actual_v[12 + dvp] * dp[8]) + (actual_v[11 + dvp] * dp[9]) +
              (actual_v[10 + dvp] * dp[10]) + (actual_v[9 + dvp] * dp[11]) + (actual_v[8 + dvp] * dp[12]) + (actual_v[7 + dvp] * dp[13]) + (actual_v[6 + dvp] * dp[14]) +
              (actual_v[5 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 5:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[5 + dvp] * dp[0]) + (actual_v[4 + dvp] * dp[1]) + (actual_v[3 + dvp] * dp[2]) + (actual_v[2 + dvp] * dp[3]) + (actual_v[1 + dvp] * dp[4]) +
              (actual_v[dvp] * dp[5]) + (actual_v[15 + dvp] * dp[6]) + (actual_v[14 + dvp] * dp[7]) + (actual_v[13 + dvp] * dp[8]) + (actual_v[12 + dvp] * dp[9]) + (actual_v[11 + dvp] * dp[10]) +
              (actual_v[10 + dvp] * dp[11]) + (actual_v[9 + dvp] * dp[12]) + (actual_v[8 + dvp] * dp[13]) + (actual_v[7 + dvp] * dp[14]) + (actual_v[6 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 6:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[6 + dvp] * dp[0]) + (actual_v[5 + dvp] * dp[1]) + (actual_v[4 + dvp] * dp[2]) + (actual_v[3 + dvp] * dp[3]) + (actual_v[2 + dvp] * dp[4]) +
              (actual_v[1 + dvp] * dp[5]) + (actual_v[dvp] * dp[6]) + (actual_v[15 + dvp] * dp[7]) + (actual_v[14 + dvp] * dp[8]) + (actual_v[13 + dvp] * dp[9]) + (actual_v[12 + dvp] * dp[10]) +
              (actual_v[11 + dvp] * dp[11]) + (actual_v[10 + dvp] * dp[12]) + (actual_v[9 + dvp] * dp[13]) + (actual_v[8 + dvp] * dp[14]) + (actual_v[7 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 7:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[7 + dvp] * dp[0]) + (actual_v[6 + dvp] * dp[1]) + (actual_v[5 + dvp] * dp[2]) + (actual_v[4 + dvp] * dp[3]) + (actual_v[3 + dvp] * dp[4]) +
              (actual_v[2 + dvp] * dp[5]) + (actual_v[1 + dvp] * dp[6]) + (actual_v[dvp] * dp[7]) + (actual_v[15 + dvp] * dp[8]) + (actual_v[14 + dvp] * dp[9]) + (actual_v[13 + dvp] * dp[10]) +
              (actual_v[12 + dvp] * dp[11]) + (actual_v[11 + dvp] * dp[12]) + (actual_v[10 + dvp] * dp[13]) + (actual_v[9 + dvp] * dp[14]) + (actual_v[8 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 8:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[8 + dvp] * dp[0]) + (actual_v[7 + dvp] * dp[1]) + (actual_v[6 + dvp] * dp[2]) + (actual_v[5 + dvp] * dp[3]) + (actual_v[4 + dvp] * dp[4]) +
              (actual_v[3 + dvp] * dp[5]) + (actual_v[2 + dvp] * dp[6]) + (actual_v[1 + dvp] * dp[7]) + (actual_v[dvp] * dp[8]) + (actual_v[15 + dvp] * dp[9]) + (actual_v[14 + dvp] * dp[10]) +
              (actual_v[13 + dvp] * dp[11]) + (actual_v[12 + dvp] * dp[12]) + (actual_v[11 + dvp] * dp[13]) + (actual_v[10 + dvp] * dp[14]) + (actual_v[9 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 9:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[9 + dvp] * dp[0]) + (actual_v[8 + dvp] * dp[1]) + (actual_v[7 + dvp] * dp[2]) + (actual_v[6 + dvp] * dp[3]) + (actual_v[5 + dvp] * dp[4]) +
              (actual_v[4 + dvp] * dp[5]) + (actual_v[3 + dvp] * dp[6]) + (actual_v[2 + dvp] * dp[7]) + (actual_v[1 + dvp] * dp[8]) + (actual_v[dvp] * dp[9]) + (actual_v[15 + dvp] * dp[10]) +
              (actual_v[14 + dvp] * dp[11]) + (actual_v[13 + dvp] * dp[12]) + (actual_v[12 + dvp] * dp[13]) + (actual_v[11 + dvp] * dp[14]) + (actual_v[10 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 10:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[10 + dvp] * dp[0]) + (actual_v[9 + dvp] * dp[1]) + (actual_v[8 + dvp] * dp[2]) + (actual_v[7 + dvp] * dp[3]) + (actual_v[6 + dvp] * dp[4]) +
              (actual_v[5 + dvp] * dp[5]) + (actual_v[4 + dvp] * dp[6]) + (actual_v[3 + dvp] * dp[7]) + (actual_v[2 + dvp] * dp[8]) + (actual_v[1 + dvp] * dp[9]) + (actual_v[dvp] * dp[10]) +
              (actual_v[15 + dvp] * dp[11]) + (actual_v[14 + dvp] * dp[12]) + (actual_v[13 + dvp] * dp[13]) + (actual_v[12 + dvp] * dp[14]) + (actual_v[11 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 11:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[11 + dvp] * dp[0]) + (actual_v[10 + dvp] * dp[1]) + (actual_v[9 + dvp] * dp[2]) + (actual_v[8 + dvp] * dp[3]) + (actual_v[7 + dvp] * dp[4]) +
              (actual_v[6 + dvp] * dp[5]) + (actual_v[5 + dvp] * dp[6]) + (actual_v[4 + dvp] * dp[7]) + (actual_v[3 + dvp] * dp[8]) + (actual_v[2 + dvp] * dp[9]) + (actual_v[1 + dvp] * dp[10]) +
              (actual_v[dvp] * dp[11]) + (actual_v[15 + dvp] * dp[12]) + (actual_v[14 + dvp] * dp[13]) + (actual_v[13 + dvp] * dp[14]) + (actual_v[12 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 12:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[12 + dvp] * dp[0]) + (actual_v[11 + dvp] * dp[1]) + (actual_v[10 + dvp] * dp[2]) + (actual_v[9 + dvp] * dp[3]) + (actual_v[8 + dvp] * dp[4]) +
              (actual_v[7 + dvp] * dp[5]) + (actual_v[6 + dvp] * dp[6]) + (actual_v[5 + dvp] * dp[7]) + (actual_v[4 + dvp] * dp[8]) + (actual_v[3 + dvp] * dp[9]) + (actual_v[2 + dvp] * dp[10]) +
              (actual_v[1 + dvp] * dp[11]) + (actual_v[0 + dvp] * dp[12]) + (actual_v[15 + dvp] * dp[13]) + (actual_v[14 + dvp] * dp[14]) + (actual_v[13 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 13:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[13 + dvp] * dp[0]) + (actual_v[12 + dvp] * dp[1]) + (actual_v[11 + dvp] * dp[2]) + (actual_v[10 + dvp] * dp[3]) + (actual_v[9 + dvp] * dp[4]) +
              (actual_v[8 + dvp] * dp[5]) + (actual_v[7 + dvp] * dp[6]) + (actual_v[6 + dvp] * dp[7]) + (actual_v[5 + dvp] * dp[8]) + (actual_v[4 + dvp] * dp[9]) + (actual_v[3 + dvp] * dp[10]) +
              (actual_v[2 + dvp] * dp[11]) + (actual_v[1 + dvp] * dp[12]) + (actual_v[dvp] * dp[13]) + (actual_v[15 + dvp] * dp[14]) + (actual_v[14 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 14:
        for (int i = 0; i < 32; i++) {
          final float[] dp = d16[i];
          temp = (float)(((actual_v[14 + dvp] * dp[0]) + (actual_v[13 + dvp] * dp[1]) + (actual_v[12 + dvp] * dp[2]) + (actual_v[11 + dvp] * dp[3]) + (actual_v[10 + dvp] * dp[4]) +
              (actual_v[9 + dvp] * dp[5]) + (actual_v[8 + dvp] * dp[6]) + (actual_v[7 + dvp] * dp[7]) + (actual_v[6 + dvp] * dp[8]) + (actual_v[5 + dvp] * dp[9]) + (actual_v[4 + dvp] * dp[10]) +
              (actual_v[3 + dvp] * dp[11]) + (actual_v[2 + dvp] * dp[12]) + (actual_v[1 + dvp] * dp[13]) + (actual_v[dvp] * dp[14]) + (actual_v[15 + dvp] * dp[15])) * scalefactor);
          short s = (short) (temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
      case 15:
        for (int i = 0; i < 32; i++) {
          final float dp[] = d16[i];
          temp = (float)(((actual_v[15 + dvp] * dp[0]) + (actual_v[14 + dvp] * dp[1]) + (actual_v[13 + dvp] * dp[2]) + (actual_v[12 + dvp] * dp[3]) + (actual_v[11 + dvp] * dp[4]) +
              (actual_v[10 + dvp] * dp[5]) + (actual_v[9 + dvp] * dp[6]) + (actual_v[8 + dvp] * dp[7]) + (actual_v[7 + dvp] * dp[8]) + (actual_v[6 + dvp] * dp[9]) + (actual_v[5 + dvp] * dp[10]) +
              (actual_v[4 + dvp] * dp[11]) + (actual_v[3 + dvp] * dp[12]) + (actual_v[2 + dvp] * dp[13]) + (actual_v[1 + dvp] * dp[14]) + (actual_v[dvp] * dp[15])) * scalefactor);
          short s = (short)(temp > 32767.0f ? 32767.0f : (temp < -32767.0f ? -32767.0f : temp));
          bytes[pos++] = (byte)s;
          bytes[pos] = (byte)(s >>> 8);
          pos += channels;
          dvp += 16;
        }
        break;
    }

    buff.setBufferIndex(channel, pos);
  }
}
