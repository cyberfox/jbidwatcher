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


import java.io.IOException;
import java.io.InputStream;

public class PushBackStream{
    private byte[] buf;
    private int pos, temp, temp2, avail;
    private long pskip;
    private InputStream in;

    public PushBackStream(InputStream in, int size) {
	this.in = in;
        this.buf = new byte[size];
	this.pos = size;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        avail = buf.length - pos;
	if (avail > 0) {
	    if (len < avail)
		avail = len;

            if(avail==3){
              temp = pos;
              temp2 = off;
              b[temp2++] = buf[temp++];
              b[temp2++] = buf[temp++];
              b[temp2] = buf[temp];
            }
            else{ //equals 1
              b[off]= buf[pos];
            }

            pos += avail;
	    off += avail;
	    len -= avail;
	}

        if (len > 0) {
          if ((len = in.read(b, off, len)) == -1)
            return avail == 0 ? -1 : avail;

          return avail + len;
	}

        return avail;
    }


    public void unread(byte[] b, int off, int len){
	temp = pos -= 4;

        buf[temp++] = b[off++];
        buf[temp++] = b[off++];
        buf[temp++] = b[off++];
        buf[temp] = b[off++];
    }

    public synchronized void close() throws IOException {
        if (in == null)
            return;
        in.close();
        in = null;
        buf = null;
    }

}
