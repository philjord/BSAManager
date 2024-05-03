package bsaio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jogamp.java3d.compressedtexture.FastByteArrayInputStream;

import com.github.pbbl.heap.ByteBufferPool;

import tools.io.FileChannelRAF;

public class ArchiveInputStream extends FastByteArrayInputStream {
	
	private static ByteBufferPool pool = new ByteBufferPool();
	
	public ArchiveInputStream(FileChannelRAF in, ArchiveEntry entry) throws IOException {
		super(new byte[0]);//reset below once data is available
		FileChannel ch = in.getChannel();
		
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

			ch.read(ByteBuffer.wrap(dataBufferIn), entry.getFileOffset());

			Inflater inflater = new Inflater();
			inflater.setInput(dataBufferIn);
			try {
				int count = inflater.inflate(dataBufferOut);
				if (count != entry.getFileLength())
					System.err.println("Inflate count issue! " + entry.getFileHashCode());

			} catch (DataFormatException e) {
				System.out.println("Entry infaltion issue " + entry.getFileHashCode());
				e.printStackTrace();
			}
			inflater.end();

		} else {
			ch.read(ByteBuffer.wrap(dataBufferOut), entry.getFileOffset());
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
		FileChannel ch = in.getChannel();
		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());
		
		// biggest file seen nif file at about 12 meg, all great sizes are a bad entry
		if (entry.getFileLength() < 0 || entry.getFileLength() > 16000000) {			
			new Throwable("Bad ArchiveEntry info:" + entry.getFileHashCode()).printStackTrace();
			return null;
		}

		byte[] dataBufferOut = null;

		//the inflate doesn't accept a bytebuffer
		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0) {
			dataBufferOut = new byte[entry.getFileLength()];
			
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			ByteBuffer dataBufferInBB = pool.take(compressedLength);
			//byte[] dataBufferIn = new byte[compressedLength];

			//ch.read(ByteBuffer.wrap(dataBufferIn), entry.getFileOffset());
			ch.read(dataBufferInBB, entry.getFileOffset());					

			Inflater inflater = new Inflater();
			//inflater.setInput(dataBufferIn);
			inflater.setInput(dataBufferInBB.array());
			try {
				int count = inflater.inflate(dataBufferOut);
				if (count != entry.getFileLength())
					System.err.println("Inflate count issue!  " + entry.getFileHashCode());
			} catch (DataFormatException e) {
				e.printStackTrace();
			}
			inflater.end();
			pool.give(dataBufferInBB);
 
			// someone is calling no direct, but I think I can't see the advantage, if it ever touches the GPU API it must be direct
			//if (!allocateDirect) {
			//	return ByteBuffer.wrap(dataBufferOut);
			//} else {
				ByteBuffer bb = ByteBuffer.allocateDirect(dataBufferOut.length);
				bb.order(ByteOrder.nativeOrder());
				bb.put(dataBufferOut);
				bb.position(0);
				return bb;
			//}
		} else {
			//ByteBuffer bb = allocateDirect ? ByteBuffer.allocateDirect(entry.getFileLength()) : ByteBuffer.allocate(entry.getFileLength());
			ByteBuffer bb = ByteBuffer.allocateDirect(entry.getFileLength());
			ch.read(bb, entry.getFileOffset());
			bb.position(0);
			return bb;
		}
	
	}
}