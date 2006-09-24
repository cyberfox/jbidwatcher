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
 *  19 Aug 2004 - Konstantin Belous 
 *  Added code for MPEG 2 support 
 *  (used the mpglib (http://ftp.tu-clausthal.de/pub/unix/audio/mpg123) as the source for changes).  
 *---------------------------------------------------------------------------
 */
package javazoom.jlme.decoder;


import java.io.IOException;

/**
 * Description of the Class
 * @author     micah
 * @created    December 8, 2001
 */
public final class Header {

  public final static int[] [] frequencies =
    {{22050, 24000, 16000, 1},
            {44100, 48000, 32000, 1}};

  /** Constant for MPEG-1 version */
  public final static int MPEG1 = 1;

  /** Constant for MPEG-2 version */
  public final static int MPEG2 = 0;

  /** Description of the Field */
  public final static int STEREO = 0;

  /** Description of the Field */
  public final static int JOINT_STEREO = 1;

  /** Description of the Field */
  public final static int DUAL_CHANNEL = 2;

  /** Description of the Field */
  public final static int SINGLE_CHANNEL = 3;

  /** Description of the Field */
  public final static int FOURTYFOUR_POINT_ONE = 0;

  /** Description of the Field */
  public final static int FOURTYEIGHT = 1;

  /** Description of the Field */
  public final static int THIRTYTWO = 2;

  /** Description of the Field */
  public final static int bitrates[] [] [] = {
            {{0
    /*
     *  free format
     */
            , 32000, 48000, 56000, 64000, 80000, 96000,
            112000, 128000, 144000, 160000, 176000, 192000, 224000, 256000, 0},
            {0
    /*
     *  free format
     */
            , 8000, 16000, 24000, 32000, 40000, 48000,
            56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 0},
            {0
    /*
     *  free format
     */
            , 8000, 16000, 24000, 32000, 40000, 48000,
            56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 0}},
            {{0
    /*
     *  free format
     */
            , 32000, 64000, 96000, 128000, 160000, 192000,
            224000, 256000, 288000, 320000, 352000, 384000, 416000, 448000, 0},
            {0
    /*
     *  free format
     */
            , 32000, 48000, 56000, 64000, 80000, 96000,
            112000, 128000, 160000, 192000, 224000, 256000, 320000, 384000, 0},
            {0
    /*
     *  free format
     */
            , 32000, 40000, 48000, 56000, 64000, 80000,
            96000, 112000, 128000, 160000, 192000, 224000, 256000, 320000, 0}}
            };

  /** Description of the Field */
  public final static String bitrate_str[] [] [] = {
            {{"free format", "32 kbit/s", "48 kbit/s", "56 kbit/s", "64 kbit/s",
            "80 kbit/s", "96 kbit/s", "112 kbit/s", "128 kbit/s", "144 kbit/s",
            "160 kbit/s", "176 kbit/s", "192 kbit/s", "224 kbit/s", "256 kbit/s",
            "forbidden"},
            {"free format", "8 kbit/s", "16 kbit/s", "24 kbit/s", "32 kbit/s",
            "40 kbit/s", "48 kbit/s", "56 kbit/s", "64 kbit/s", "80 kbit/s",
            "96 kbit/s", "112 kbit/s", "128 kbit/s", "144 kbit/s", "160 kbit/s",
            "forbidden"},
            {"free format", "8 kbit/s", "16 kbit/s", "24 kbit/s", "32 kbit/s",
            "40 kbit/s", "48 kbit/s", "56 kbit/s", "64 kbit/s", "80 kbit/s",
            "96 kbit/s", "112 kbit/s", "128 kbit/s", "144 kbit/s", "160 kbit/s",
            "forbidden"}},
            {{"free format", "32 kbit/s", "64 kbit/s", "96 kbit/s", "128 kbit/s",
            "160 kbit/s", "192 kbit/s", "224 kbit/s", "256 kbit/s", "288 kbit/s",
            "320 kbit/s", "352 kbit/s", "384 kbit/s", "416 kbit/s", "448 kbit/s",
            "forbidden"},
            {"free format", "32 kbit/s", "48 kbit/s", "56 kbit/s", "64 kbit/s",
            "80 kbit/s", "96 kbit/s", "112 kbit/s", "128 kbit/s", "160 kbit/s",
            "192 kbit/s", "224 kbit/s", "256 kbit/s", "320 kbit/s", "384 kbit/s",
            "forbidden"},
            {"free format", "32 kbit/s", "40 kbit/s", "48 kbit/s", "56 kbit/s",
            "64 kbit/s", "80 kbit/s", "96 kbit/s", "112 kbit/s", "128 kbit/s",
            "160 kbit/s", "192 kbit/s", "224 kbit/s", "256 kbit/s", "320 kbit/s",
            "forbidden"}}
            };


  public static int framesize;
  public static int nSlots;
  private static int h_layer, h_protection_bit, h_bitrate_index, h_padding_bit, h_mode_extension;
  private static int h_version;
  private static int h_mode;
  private static int h_sample_frequency;
  private static int h_number_of_subbands, h_intensity_stereo_bound;
  static byte syncmode = BitStream.INITIAL_SYNC;


  public int version() {
    return h_version;
  }

  public int layer() {
    return h_layer;
  }

  public int bitrate_index() {
    return h_bitrate_index;
  }

  public int sample_frequency() {
    return h_sample_frequency;
  }

  public int frequency() {
    return frequencies[h_version] [h_sample_frequency];
  }

  public int mode() {
    return h_mode;
  }

  public boolean padding() {
    return (h_padding_bit != 0);
  }

  public int slots() {
    return nSlots;
  }

  public int mode_extension() {
    return h_mode_extension;
  }

  public String layer_string() {
    return "III";
  }

  public String bitrate_string() {
    return bitrate_str[h_version] [h_layer - 1] [h_bitrate_index];
  }

  public String sample_frequency_string() {
    switch (frequencies[h_version] [h_sample_frequency]) {
	  case 16000:
		return "16 kHz";
	  case 22050:
		return "22.05 kHz";
	  case 24000:
		return "24 kHz";
      case 32000:
        return "32 kHz";
      case 44100:
        return "44.1 kHz";
      case 48000:
        return "48 kHz";
    }
    return "not set";
  }

  public String mode_string() {
    switch (h_mode) {
      case STEREO:
        return "Stereo";
      case JOINT_STEREO:
        return "Joint stereo";
      case DUAL_CHANNEL:
        return "Dual channel";
      case SINGLE_CHANNEL:
        return "Single channel";
    }

    return "not set";
  }

  public int number_of_subbands() {
    return h_number_of_subbands;
  }

  public int intensity_stereo_bound() {
    return h_intensity_stereo_bound;
  }

  final void read_header(BitStream stream) throws IOException {
    int headerstring;
    int channel_bitrate;
    boolean sync = false;
    do {
      headerstring = stream.syncHeader(syncmode);
      if (syncmode == BitStream.INITIAL_SYNC) {
        h_version = ((headerstring >>> 19) & 1);
        if ((h_sample_frequency = ((headerstring >>> 10) & 3)) == 3) {
          return;
        }
      }
      h_layer = 4 - (headerstring >>> 17) & 3;
      // E.B Fix.
      //h_protection_bit = 0;
      h_protection_bit = (headerstring >>> 16) & 1;
      // End.
      h_bitrate_index = (headerstring >>> 12) & 0xF;
      h_padding_bit = (headerstring >>> 9) & 1;
      h_mode = ((headerstring >>> 6) & 3);
      h_mode_extension = (headerstring >>> 4) & 3;
      if (h_mode == JOINT_STEREO) {
        h_intensity_stereo_bound = (h_mode_extension << 2) + 4;
      } else {
        h_intensity_stereo_bound = 0;
      }
      if (h_layer == 1) {
        h_number_of_subbands = 32;
      } else {
        channel_bitrate = h_bitrate_index;
        // calculate bitrate per channel:
        if (h_mode != SINGLE_CHANNEL) {
          if (channel_bitrate == 4) {
            channel_bitrate = 1;
          } else {
            channel_bitrate -= 4;
          }
        }
        if(h_version == MPEG2) {
			h_number_of_subbands = 30;
      	} else if ((channel_bitrate == 1) || (channel_bitrate == 2)) {
          if (h_sample_frequency == THIRTYTWO) {
            h_number_of_subbands = 12;
          } else {
            h_number_of_subbands = 8;
          }
        } else if ((h_sample_frequency == FOURTYEIGHT) || ((channel_bitrate >= 3) && (channel_bitrate <= 5))) {
          h_number_of_subbands = 27;
        } else {
          h_number_of_subbands = 30;
        }
      }
      if (h_intensity_stereo_bound > h_number_of_subbands) {
        h_intensity_stereo_bound = h_number_of_subbands;
      }
      // calculate framesize and nSlots
      calFrameSize();
      // read framedata:
      stream.read_frame_data(framesize);
      if (stream.isSyncCurrentPosition(syncmode)) {
        if (syncmode == BitStream.INITIAL_SYNC) {
          syncmode = BitStream.STRICT_SYNC;
          stream.set_syncword(headerstring & 0xFFF80CC0);
        }
        sync = true;
      }
      else {
        stream.unreadFrame();
      }
    } while (!sync);
    stream.parse_frame();

	// E.B Fix
	if (h_protection_bit == 0)
	{
	   // frame contains a crc checksum
	   short checksum = (short) stream.readbits(16);
	}
	// End
  }

  private final void calFrameSize() {
	 if(h_version == MPEG1) {
     	framesize = (144 * bitrates[h_version] [h_layer - 1] [h_bitrate_index]) / frequencies[h_version] [h_sample_frequency];
	 } else {
		framesize = (144 * bitrates[h_version] [h_layer - 1] [h_bitrate_index]) / (frequencies[h_version] [h_sample_frequency] << 1);
	 }
     if(h_padding_bit != 0)
       framesize++;

     if(h_version == MPEG1) {
       // E.B Fix
		nSlots = framesize - ((h_mode == SINGLE_CHANNEL) ? 17 : 32) -  ((h_protection_bit!=0) ? 0 : 2) - 4;
     } else {
		nSlots = framesize - ((h_mode == SINGLE_CHANNEL) ? 9 : 17) -  ((h_protection_bit!=0) ? 0 : 2) - 4;
     }

     framesize -= 4;
  }
}
