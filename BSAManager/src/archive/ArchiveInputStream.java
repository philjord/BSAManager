// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveInputStream.java

package archive;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import tools.io.FastByteArrayInputStream;
import tools.io.MappedByteBufferRAF;

import com.jcraft.jzlib.Inflater;

/**
 * @author philip
 *
 */
public class ArchiveInputStream extends FastByteArrayInputStream
{
	public ArchiveInputStream(RandomAccessFile in, ArchiveEntry entry) throws IOException
	{
		super(new byte[0]);//reset below once data is availble

		byte[] dataBufferOut = new byte[entry.getFileLength()];

		//the deflate doesn't accept a bytebuffer
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
				in.seek(entry.getFileOffset());
				int c = in.read(dataBufferOut, 0, entry.getFileLength());
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
			}
		}

		this.buf = dataBufferOut;
		this.pos = 0;
		this.count = buf.length;
	}

	/**
	 * Be careful see ArchiveFile for warning
	 * @param in
	 * @param entry
	 * @return
	 * @throws IOException
	 */
	public static ByteBuffer getByteBuffer(RandomAccessFile in, ArchiveEntry entry) throws IOException
	{
		if (entry.isCompressed())
			throw new IOException("Can't request a bytebuffer for a compressed entry");
		else if (!(in instanceof MappedByteBufferRAF))
			throw new IOException("Can't request a bytebuffer for !(in instanceof MappedByteBufferRAF)");

		synchronized (in)
		{
			MappedByteBuffer buf = ((MappedByteBufferRAF) in).getMappedByteBuffer();

			buf.limit((int) (entry.getFileOffset() + entry.getFileLength()));
			buf.position((int) entry.getFileOffset());
			ByteBuffer ret = buf.slice();
			buf.position(0);
			buf.limit(buf.capacity());
			return ret;
		}

	}

}