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
 */
package javazoom.jlme.decoder;




public class Decoder {
  private SampleBuffer output;
  private SynthesisFilter filter1;
  private SynthesisFilter filter2;
  private LayerIIIDecoder decoder;
  private int outputFrequency;
  private int outputChannels;
  private boolean initialized;

  /**
   *  Constructor for the Decoder object
   *@param  header  Description of Parameter
   *@param  stream  Description of Parameter
   */
  public Decoder(Header header, BitStream stream) {
	// E.B Fix - Damned unloaded static fields !
    BitReserve.totbit = 0;
    BitReserve.buf_bit_idx=0;
    BitReserve.buf_byte_idx=0;
    BitReserve.offset=0;
    // End of fix.
    if (header.layer() == 3) {
      //float scalefactor = 48000.0f;
      float scalefactor = 32700.0f;
      int mode = header.mode();
      int layer = header.layer();
      int channels = mode == Header.SINGLE_CHANNEL ? 1 : 2;
      output = new SampleBuffer(header.frequency(), channels);
      filter1 = new SynthesisFilter(0, scalefactor);
      if (channels == 2) {
        filter2 = new SynthesisFilter(1, scalefactor);
      }
      outputChannels = channels;
      outputFrequency = header.frequency();
      decoder = new LayerIIIDecoder(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS);
    }
    else {
      System.out.println("only supports mp3 files");
      System.exit(1);
    }
  }

  /**
   *  Sets the outputBuffer attribute of the Decoder object
   *@param  out  The new outputBuffer value
   */
  public final void setOutputBuffer(SampleBuffer out) {
    output = out;
  }

  /**
   *  Gets the outputFrequency attribute of the Decoder object
   *@return    The outputFrequency value
   */
  public final int getOutputFrequency() {
    return outputFrequency;
  }

  /**
   *  Gets the outputChannels attribute of the Decoder object
   *@return    The outputChannels value
   */
  public final int getOutputChannels() {
    return outputChannels;
  }

  /**
   *  Gets the outputBlockSize attribute of the Decoder object
   *@return    The outputBlockSize value
   */
  public final int getOutputBlockSize() {
    return SampleBuffer.OBUFFERSIZE;
  }

  /**
   *  Description of the Method
   *@return    Description of the Returned Value
   */
  public final SampleBuffer decodeFrame() {
    output.clear();
    decoder.decodeFrame();
    return output;
  }
}
