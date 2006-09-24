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
 *   20 Aug 2004 Konstantin Belous 
 *   Changed the code that loads the huffman.dat resource file with purpose 
 *   of compatibility to Netscape Navigator. 
 * 
 *   12 Aug 2004 Konstantin Belous 
 *   Added the loadTables() method and moved into it all the code from the HuffmanTables() constructor  
 *   with purposes of compatibility to Microsoft VM. 
 *---------------------------------------------------------------------------
 */
package javazoom.jlme.decoder;


import java.io.ObjectInputStream;

final class HuffmanTables {
  public static HuffmanTables[] ht;
  private final static int MXOFF = 250;
  private final static int HTN = 34;
  private final static int[] bitbuf = new int[32];
  private char tablename0 = ' ';
  private char tablename1 = ' ';
  private char tablename2 = ' ';
  private int xlen;
  private int ylen;
  private int linbits;
  private int linmax;
  private int ref;
  private int[][] val = null;
  private int treelen;
  private static int dmask = 1 << ((4 * 8) - 1);
  private static int hs = 4 * 8;
  private static int point, error, level;

  public static class Huffman{
    static int x, y, v, w;
  }

  private HuffmanTables(String S, int XLEN, int YLEN, int LINBITS, int LINMAX, int REF, int[] [] VAL, int TREELEN) throws Exception {
	tablename0 = S.charAt(0);
	tablename1 = S.charAt(1);
	tablename2 = S.charAt(2);
	xlen = XLEN;
	ylen = YLEN;
	linbits = LINBITS;
	linmax = LINMAX;
	ref = REF;
	val = VAL;
	treelen = TREELEN;
  }

  public static void decode(final HuffmanTables h, final Huffman huff, final BitReserve br) {
    point = 0;
    level = dmask;

    /*
    if (h.val == null) {
      return;
    }

    if (h.treelen == 0) {
      huff.x = huff.y = 0;
      return;
    }
    */

    do {
      if (h.val[point] [0] == 0) {
        huff.x = h.val[point] [1] >>> 4;
        huff.y = h.val[point] [1] & 0xf;
        break;
      }

      int[][] temp = h.val;
      if (br.hget1bit() != 0) {
        while (temp[point] [1] >= MXOFF) {
          point += temp[point] [1];
        }
        point += temp[point] [1];
      }
      else {
        while (temp[point] [0] >= MXOFF) {
          point += temp[point] [0];
        }
        point += temp[point] [0];
      }
      level >>>= 1;
      // MDM: ht[0] is always 0;
    } while ((level != 0) || (point < 0));


    if (h.tablename0 == '3' && (h.tablename1 == '2' || h.tablename1 == '3')) {
      huff.v = (huff.y >> 3) & 1;
      huff.w = (huff.y >> 2) & 1;
      huff.x = (huff.y >> 1) & 1;
      huff.y = huff.y & 1;

      if (huff.v != 0) {
        if (br.hget1bit() != 0) {
          huff.v *= -1; //-v[0];
        }
      }
      if (huff.w != 0) {
        if (br.hget1bit() != 0) {
          huff.w *= -1; //-w[0];
        }
      }
      if (huff.x != 0) {
        if (br.hget1bit() != 0) {
          huff.x *= -1; //-x[0];
        }
      }
      if (huff.y != 0) {
        if (br.hget1bit() != 0) {
          huff.y *= -1; //-y[0];
        }
      }
    }
    else {
      if (h.linbits != 0) {
        if ((h.xlen - 1) == huff.x) {
          huff.x += br.hgetbits(h.linbits);
        }
      }
      if (huff.x != 0) {
        if (br.hget1bit() != 0) {
          huff.x *= -1; //-x[0];
        }
      }
      if (h.linbits != 0) {
        if ((h.ylen - 1) == huff.y) {
          huff.y += br.hgetbits(h.linbits);
        }
      }
      if (huff.y != 0) {
        if (br.hget1bit() != 0) {
          huff.y *= -1; //-y[0];
        }
      }
    }
  }

  private void loadTables() {
	ObjectInputStream in = null;
	int array[][]=null;

	try{
	  in = new ObjectInputStream(getClass().getClassLoader().getResourceAsStream("huffman.ser"));
	  ht = new HuffmanTables[HTN];
	  array = (int[][])in.readObject();
	  ht[0] = new HuffmanTables("0  ", 0, 0, 0, 0, -1,  array, 0);
	  array = (int[][])in.readObject();
	  ht[1] = new HuffmanTables("1  ", 2, 2, 0, 0, -1,   array, 7);
	  array = (int[][])in.readObject();
	  ht[2] = new HuffmanTables("2  ", 3, 3, 0, 0, -1,   array, 17);
	  array = (int[][])in.readObject();
	  ht[3] = new HuffmanTables("3  ", 3, 3, 0, 0, -1,   array, 17);
	  array = (int[][])in.readObject();
	  ht[4] = new HuffmanTables("4  ", 0, 0, 0, 0, -1,   array, 0);
	  array = (int[][])in.readObject();
	  ht[5] = new HuffmanTables("5  ", 4, 4, 0, 0, -1,   array, 31);
	  array = (int[][])in.readObject();
	  ht[6] = new HuffmanTables("6  ", 4, 4, 0, 0, -1,   array, 31);
	  array = (int[][])in.readObject();
	  ht[7] = new HuffmanTables("7  ", 6, 6, 0, 0, -1,   array, 71);
	  array = (int[][])in.readObject();
	  ht[8] = new HuffmanTables("8  ", 6, 6, 0, 0, -1,   array, 71);
	  array = (int[][])in.readObject();
	  ht[9] = new HuffmanTables("9  ", 6, 6, 0, 0, -1,   array, 71);
	  array = (int[][])in.readObject();
	  ht[10] = new HuffmanTables("10 ", 8, 8, 0, 0, -1,   array, 127);
	  array = (int[][])in.readObject();
	  ht[11] = new HuffmanTables("11 ", 8, 8, 0, 0, -1,   array, 127);
	  array = (int[][])in.readObject();
	  ht[12] = new HuffmanTables("12 ", 8, 8, 0, 0, -1,   array, 127);
	  array = (int[][])in.readObject();
	  ht[13] = new HuffmanTables("13 ", 16, 16, 0, 0, -1,   array, 511);
	  array = (int[][])in.readObject();
	  ht[14] = new HuffmanTables("14 ", 0, 0, 0, 0, -1,  array, 0);
	  array = (int[][])in.readObject();
	  ht[15] = new HuffmanTables("15 ", 16, 16, 0, 0, -1,   array, 511);
	  array = (int[][])in.readObject();
	  ht[16] = new HuffmanTables("16 ", 16, 16, 1, 1, -1,   array, 511);
	  ht[17] = new HuffmanTables("17 ", 16, 16, 2, 3, 16,   (int[][])array.clone(), 511);
	  ht[18] = new HuffmanTables("18 ", 16, 16, 3, 7, 16,   (int[][])array.clone(), 511);
	  ht[19] = new HuffmanTables("19 ", 16, 16, 4, 15, 16,   (int[][])array.clone(), 511);
	  ht[20] = new HuffmanTables("20 ", 16, 16, 6, 63, 16,   (int[][])array.clone(), 511);
	  ht[21] = new HuffmanTables("21 ", 16, 16, 8, 255, 16,   (int[][])array.clone(), 511);
	  ht[22] = new HuffmanTables("22 ", 16, 16, 10, 1023, 16,   (int[][])array.clone(), 511);
	  ht[23] = new HuffmanTables("23 ", 16, 16, 13, 8191, 16,   (int[][])array.clone(), 511);
	  array = (int[][])in.readObject();
	  ht[24] = new HuffmanTables("24 ", 16, 16, 4, 15, -1,   array, 512);
	  ht[25] = new HuffmanTables("25 ", 16, 16, 5, 31, 24,   (int[][])array.clone(), 512);
	  ht[26] = new HuffmanTables("26 ", 16, 16, 6, 63, 24,   (int[][])array.clone(), 512);
	  ht[27] = new HuffmanTables("27 ", 16, 16, 7, 127, 24,   (int[][])array.clone(), 512);
	  ht[28] = new HuffmanTables("28 ", 16, 16, 8, 255, 24,   (int[][])array.clone(), 512);
	  ht[29] = new HuffmanTables("29 ", 16, 16, 9, 511, 24,   (int[][])array.clone(), 512);
	  ht[30] = new HuffmanTables("30 ", 16, 16, 11, 2047, 24,   (int[][])array.clone(), 512);
	  ht[31] = new HuffmanTables("31 ", 16, 16, 13, 8191, 24,   (int[][])array.clone(), 512);
	  array = (int[][])in.readObject();
	  ht[32] = new HuffmanTables("32 ", 1, 16, 0, 0, -1,   array, 31);
	  array = (int[][])in.readObject();
	  ht[33] = new HuffmanTables("33 ", 1, 16, 0, 0, -1,   array, 31);
	}
	catch(Exception e){
	  System.out.println("couldn't load the Huffman Tables");
	  System.exit(1);
	}
	finally{
	  try{
		in.close();
	  }
	  catch(Exception e){}
	}
  }

  public HuffmanTables() {
	loadTables();
  }

}
