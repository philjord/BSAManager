// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveInputStream.java

package FO3Archive;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

import com.jcraft.jzlib.Inflater;

/**
 * @author philip
 *
 */
public class ArchiveInputStream extends ByteArrayInputStream
{

	@SuppressWarnings("deprecation")
	public ArchiveInputStream(RandomAccessFile in, ArchiveEntry entry) throws IOException
	{
		super(new byte[0]);//reset below once data is availble

		byte[] dataBufferOut = new byte[entry.getFileLength()];

		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0)
		{
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];

			synchronized (in)
			{
				in.seek(entry.getFileOffset());
				int c = in.read(dataBufferIn, 0, compressedLength);
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
			}

			Inflater inflater = new Inflater();
			inflater.setInput(dataBufferIn);
			inflater.setOutput(dataBufferOut);
			inflater.inflate(4);//Z_FINISH
			inflater.end();
		}
		else
		{
			synchronized (in)
			{
				int c = in.read(dataBufferOut, 0, entry.getFileLength());
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
			}
		}

		this.buf = dataBufferOut;
		this.pos = 0;
		this.count = buf.length;
	}

}