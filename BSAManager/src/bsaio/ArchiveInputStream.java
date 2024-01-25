package bsaio;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jogamp.java3d.compressedtexture.FastByteArrayInputStream;

import tools.io.FileChannelRAF;

public class ArchiveInputStream extends FastByteArrayInputStream {
	public ArchiveInputStream(FileChannelRAF in, ArchiveEntry entry) throws IOException {
		super(new byte[0]);//reset below once data is available

		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());

		byte[] dataBufferOut = new byte[entry.getFileLength()];

		//the inflate doesn't accept a bytebuffer
		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0) {
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];

			//android can't take big files
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE) {
				MappedByteBuffer mappedByteBuffer = null;
				FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
				FileChannel ch = in.getChannel();
				mappedByteBuffer = ch.map(mm, entry.getFileOffset(), compressedLength);
				mappedByteBuffer.get(dataBufferIn, 0, compressedLength);
			} else {
				FileChannel ch = in.getChannel();
				ByteBuffer bb = ByteBuffer.wrap(dataBufferIn);
				ch.read(bb, entry.getFileOffset());
			}

			if (ArchiveFile.USE_NON_NATIVE_ZIP) {
				//JCraft version slower - though I wonder about android? seems real slow too
				com.jcraft.jzlib.Inflater inflater = new com.jcraft.jzlib.Inflater();
				inflater.setInput(dataBufferIn);
				inflater.setOutput(dataBufferOut);
				inflater.inflate(4);//Z_FINISH
				inflater.end();
			} else {

				Inflater inflater = new Inflater();
				inflater.setInput(dataBufferIn);
				try {
					int count = inflater.inflate(dataBufferOut);
					if (count != entry.getFileLength())
						System.err.println("Inflate count issue! " + entry.getFileName());

				} catch (DataFormatException e) {
					System.out.println("Entry infaltion issue " + entry.getFileName());
					e.printStackTrace();
				}
				inflater.end();
			}
		} else {
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE) {
				MappedByteBuffer mappedByteBuffer = null;
				FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
				FileChannel ch = in.getChannel();
				mappedByteBuffer = ch.map(mm, entry.getFileOffset(), entry.getFileLength());

				mappedByteBuffer.get(dataBufferOut, 0, entry.getFileLength());
			} else {
				FileChannel ch = in.getChannel();
				ByteBuffer bb = ByteBuffer.wrap(dataBufferOut);
				ch.read(bb, entry.getFileOffset());
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
	public static ByteBuffer getByteBuffer(FileChannelRAF in, ArchiveEntry entry, boolean allocateDirect)
			throws IOException {

		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());
		
		// biggest file seen nif file at about 12 meg, all great sizes are a bad entry
		if (entry.getFileLength() < 0 || entry.getFileLength() > 16000000) {			
			new Throwable("Bad ArchiveEntry info:" + entry.getFileName()).printStackTrace();
			return null;
		}

		byte[] dataBufferOut = null;

		//the inflate doesn't accept a bytebuffer
		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0) {
			dataBufferOut = new byte[entry.getFileLength()];
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];

			//android can't take big files
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE) {
				FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
				FileChannel ch = in.getChannel();
				MappedByteBuffer mappedByteBuffer = ch.map(mm, entry.getFileOffset(), compressedLength);
				mappedByteBuffer.get(dataBufferIn, 0, compressedLength);
			} else {
				FileChannel ch = in.getChannel();
				ByteBuffer bb = ByteBuffer.wrap(dataBufferIn);
				ch.read(bb, entry.getFileOffset());				
			}

			if (ArchiveFile.USE_NON_NATIVE_ZIP) {
				//JCraft version slower - though I wonder about android? seems real slow too
				com.jcraft.jzlib.Inflater inflater = new com.jcraft.jzlib.Inflater();
				inflater.setInput(dataBufferIn);
				inflater.setOutput(dataBufferOut);
				inflater.inflate(4);//Z_FINISH
				inflater.end();
			} else {
				Inflater inflater = new Inflater();
				inflater.setInput(dataBufferIn);
				try {
						int count = inflater.inflate(dataBufferOut);
						if (count != entry.getFileLength())
							System.err.println("Inflate count issue!  " + entry.getFileName());
				} catch (DataFormatException e) {
					e.printStackTrace();
				}
				inflater.end();
			}

			if (!allocateDirect) {
				return ByteBuffer.wrap(dataBufferOut);
			} else {
				ByteBuffer bb = ByteBuffer.allocateDirect(dataBufferOut.length);
				bb.order(ByteOrder.nativeOrder());
				bb.put(dataBufferOut);
				bb.position(0);
				return bb;
			}
		} else {
			if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE) {
				MappedByteBuffer mappedByteBuffer = null;
				FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
				FileChannel ch = in.getChannel();
				if (entry.getFileOffset() > 0 && entry.getFileLength() > 0)
					mappedByteBuffer = ch.map(mm, entry.getFileOffset(), entry.getFileLength());
				else
					throw new EOFException("Unexpected mapping values entry.getFileOffset() "
											+ entry.getFileOffset() + " entry.getFileLength() "
											+ entry.getFileLength() + " " + entry.getFileName());

				// dear god, protect us
				if (ArchiveFile.RETURN_MAPPED_BYTE_BUFFERS)
					return mappedByteBuffer;
				else {
					ByteBuffer bb = allocateDirect ? ByteBuffer.allocateDirect(entry.getFileLength()) : ByteBuffer.allocate(entry.getFileLength());
					bb.put(mappedByteBuffer);
					bb.position(0);
					return bb;					
				}
			} else {
				FileChannel ch = in.getChannel();
				ByteBuffer bb = allocateDirect ? ByteBuffer.allocateDirect(entry.getFileLength()) : ByteBuffer.allocate(entry.getFileLength());
				ch.read(bb, entry.getFileOffset());
				bb.position(0);
				return bb;
			}
		}
	
	}
}