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


public class SampleBuffer {
  public final static int OBUFFERSIZE = 4 * 1152;
  public final static int MAXCHANNELS = 2;
  private final byte[] buffer = new byte[OBUFFERSIZE];
  private final int[] bufferp = new int[MAXCHANNELS];
  private int channels;
  private int frequency;

  public SampleBuffer(int sample_frequency, int number_of_channels) {
    channels = (number_of_channels == 1) ? 1 : 3;
    frequency = sample_frequency;
    bufferp[0] = 0;
    bufferp[1] = 2;
  }

  public final int getBufferIndex(int channel) {
    return bufferp[channel];
  }

  public final void setBufferIndex(int channel, int index) {
    bufferp[channel] = index;
  }

  public final int getBufferChannelCount() {
    return channels;
  }

  public int getChannelCount() {
    return (channels == 1) ? 1 : 2;
  }

  public int getSampleFrequency() {
    return this.frequency;
  }

  public byte[] getBuffer() {
    return this.buffer;
  }

  public int size() {
    return this.bufferp[0];
  }

  public void clear() {
    bufferp[0] = 0;
    bufferp[1] = 2;
  }
}
