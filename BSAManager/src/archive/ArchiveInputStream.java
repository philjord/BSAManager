// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveInputStream.java

package archive;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import tools.io.FastByteArrayInputStream;

/**
 * @author philip
 *
 */
public class ArchiveInputStream extends FastByteArrayInputStream
{
	public ArchiveInputStream(RandomAccessFile in, ArchiveEntry entry) throws IOException
	{
		super(new byte[0]);//reset below once data is available

		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());

		byte[] dataBufferOut = new byte[entry.getFileLength()];

		//the inflate doesn't accept a bytebuffer
		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0)
		{
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];

			//android can't take big files
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE)
			{
				MappedByteBuffer mappedByteBuffer = null;
				synchronized (in)
				{
					FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
					FileChannel ch = in.getChannel();
					mappedByteBuffer = ch.map(mm, entry.getFileOffset(), compressedLength);

				}
				mappedByteBuffer.get(dataBufferIn, 0, compressedLength);
			}
			else
			{
				synchronized (in)
				{
					in.seek(entry.getFileOffset());
					int c = in.read(dataBufferIn, 0, compressedLength);
					if (c < 0)
						throw new EOFException("Unexpected end of stream while inflating file");
				}
			}

			if (ArchiveFile.USE_NON_NATIVE_ZIP)
			{
				//JCraft version slower - though I wonder about android? seems real slow too
				com.jcraft.jzlib.Inflater inflater = new com.jcraft.jzlib.Inflater();
				inflater.setInput(dataBufferIn);
				inflater.setOutput(dataBufferOut);
				inflater.inflate(4);//Z_FINISH
				inflater.end();
			}
			else
			{

				Inflater inflater2 = new Inflater();
				inflater2.setInput(dataBufferIn);
				try
				{
					int count = inflater2.inflate(dataBufferOut);
					if (count != entry.getFileLength())
						System.err.println("Inflate count issue! " + this);

				}
				catch (DataFormatException e)
				{
					e.printStackTrace();
				}
			}
		}
		else
		{
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE)
			{
				MappedByteBuffer mappedByteBuffer = null;
				synchronized (in)
				{
					FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
					FileChannel ch = in.getChannel();
					mappedByteBuffer = ch.map(mm, entry.getFileOffset(), entry.getFileLength());
				}
				mappedByteBuffer.get(dataBufferOut, 0, entry.getFileLength());
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
	public static ByteBuffer getByteBuffer(RandomAccessFile in, ArchiveEntry entry, boolean allocateDirect) throws IOException
	{

		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());

		byte[] dataBufferOut =null ;

		//the inflate doesn't accept a bytebuffer
		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0)
		{
			dataBufferOut = new byte[entry.getFileLength()];
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];

			//android can't take big files
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE)
			{
				MappedByteBuffer mappedByteBuffer = null;
				synchronized (in)
				{
					FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
					FileChannel ch = in.getChannel();
					mappedByteBuffer = ch.map(mm, entry.getFileOffset(), compressedLength);
				}
				mappedByteBuffer.get(dataBufferIn, 0, compressedLength);

			}
			else
			{
				synchronized (in)
				{
					in.seek(entry.getFileOffset());
					int c = in.read(dataBufferIn, 0, compressedLength);
					if (c < 0)
						throw new EOFException("Unexpected end of stream while inflating file");
				}

			}

			if (ArchiveFile.USE_NON_NATIVE_ZIP)
			{
				//JCraft version slower - though I wonder about android? seems real slow too
				com.jcraft.jzlib.Inflater inflater = new com.jcraft.jzlib.Inflater();
				inflater.setInput(dataBufferIn);
				inflater.setOutput(dataBufferOut);
				inflater.inflate(4);//Z_FINISH
				inflater.end();
			}
			else
			{

				Inflater inflater = new Inflater();
				inflater.setInput(dataBufferIn);
				try
				{
					{
						int count = inflater.inflate(dataBufferOut);
						if (count != entry.getFileLength())
							System.err.println("Inflate count issue! " + entry);
					}
				}
				catch (DataFormatException e)
				{
					e.printStackTrace();
				}
			}

		}
		else
		{
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE)
			{
				MappedByteBuffer mappedByteBuffer = null;
				synchronized (in)
				{
					FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
					FileChannel ch = in.getChannel();
					mappedByteBuffer = ch.map(mm, entry.getFileOffset(), entry.getFileLength());
				}
				
				// dear god, protect us
				if (ArchiveFile.RETURN_MAPPED_BYTE_BUFFERS)
					return mappedByteBuffer;
				else
				{
					dataBufferOut = new byte[entry.getFileLength()];
					mappedByteBuffer.get(dataBufferOut, 0, entry.getFileLength());
				}

			}
			else
			{
				dataBufferOut = new byte[entry.getFileLength()];
				synchronized (in)
				{
					in.seek(entry.getFileOffset());
					int c = in.read(dataBufferOut, 0, entry.getFileLength());
					if (c < 0)
						throw new EOFException("Unexpected end of stream while inflating file");
				}
			}
		}
		if (!allocateDirect)
		{
			return ByteBuffer.wrap(dataBufferOut);
		}
		else
		{
			ByteBuffer bb = ByteBuffer.allocateDirect(dataBufferOut.length);
			bb.order(ByteOrder.nativeOrder());
			bb.put(dataBufferOut);
			bb.position(0);
			return bb;
		}
	}

}