package bsaio;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jogamp.java3d.compressedtexture.FastByteArrayInputStream;

import com.github.pbbl.heap.ByteBufferPool;

import tools.io.FileChannelRAF;

public class ArchiveInputStream extends FastByteArrayInputStream {

	//1,0,-1 1 uses mapped from channels, 0 reads into a bytebuffer, both seem the same for speed and memory use
	public static int				USE_MAPPED	= 1;

	private static ByteBufferPool	pool		= new ByteBufferPool();

	/**
	 * This constructor is used in Android code to get maps out as bitmaps, and various utils, it is not a wise thing to use, getByteBuffer below is the
	 * bettereer
	 * @param in
	 * @param entry
	 * @throws IOException
	 */
	//FIXME: Android should juse getByteBuffer if possible
	public ArchiveInputStream(FileChannelRAF in, ArchiveEntry entry) throws IOException {
		super(new byte[0]);//reset below once data is available
		FileChannel ch = in.getChannel();
		
		entry.ensureEntryReady(ch);

		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());

		byte[] dataBufferOut = new byte[entry.getFileLength()];

		//use the byte bufer calls below bytebuffer
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

		// replace this.buf with a get it when asked for from getByteBuffer?
		this.buf = dataBufferOut;
		this.pos = 0;
		this.count = buf.length;
	}

	
	
	
	
	public static ByteBuffer getByteBuffer(FileChannelRAF in, ArchiveEntry entry) throws IOException {		
		FileChannel ch = in.getChannel();
	
		
		//experiment, instead of the folder loading phase doing extra reads into the data area (at great expense?)
		// we'll just get the entry loaded up now, code was in ensureLoaded()
		
		// FIXME:
		// LOODNif has a problem in NifToJe3
		//	at bsa.source.BsaTextureSource.getTextureUnitState(BsaTextureSource.java:347)
		//	at bsa.source.BsaTextureSource.getTexture(BsaTextureSource.java:212)
		//if(entry.ready) {			
		//	new Throwable("Bad ArchiveEntry ready twice " +entry.archiveFile.fileName+ " " + entry.getFileLength() + " hash "
		//			+ entry.getFileHashCode()).printStackTrace();
		//}
		entry.ensureEntryReady(ch);
		
		
		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());

		// biggest file seen nif file at about 12 meg, all great sizes are a bad entry
		// except the 95 mb (uncompressed) cdb file inside the materials ba2 for starfield
		if ((entry.getFileLength() < 0 || entry.getFileLength() > 16000000) && entry.getFileHashCode() != 3506367987L) {
			new Throwable("Bad ArchiveEntry entry.getFileLength() " + entry.getFileLength() + " hash "
							+ entry.getFileHashCode()).printStackTrace();
			return null;
		}

		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0) {
			if (USE_MAPPED == 1 || USE_MAPPED == 0) {
				ByteBuffer bb = ByteBuffer.allocateDirect(entry.getFileLength()).order(ByteOrder.nativeOrder());
				int compressedLength = entry.getCompressedLength();
				//CAREFUL mapping only works if the data is fully read off, watch out for Textures that go to the GPU
				ByteBuffer dataBufferInBB = null;
				if (USE_MAPPED == 0) {
					dataBufferInBB = pool.take(compressedLength);
					ch.read(dataBufferInBB, entry.getFileOffset());
					dataBufferInBB.rewind();
				} else {
					dataBufferInBB = ch.map(MapMode.READ_ONLY, entry.getFileOffset(), compressedLength);
				}

				Inflater inflater = new Inflater();
				inflater.setInput(dataBufferInBB);
				try {
					int count = inflater.inflate(bb);
					if (count != entry.getFileLength())
						System.err.println("Inflate count issue! count = "	+ count + " expected "
											+ entry.getFileLength() + " hashcode= " + entry.getFileHashCode());
				} catch (DataFormatException e) {
					System.err
							.println(ArchiveInputStream.class.getName() + ".getByteBuffer Inflater DataFormatException "
										+ " hashcode= " + entry.getFileHashCode());
				}
				inflater.end();
				if (USE_MAPPED == 0) {
					pool.give(dataBufferInBB);
				}
				bb.rewind();
				return bb;
			} else {
				byte[] dataBufferOut = new byte[entry.getFileLength()];
				ByteBuffer bb = ByteBuffer.allocateDirect(entry.getFileLength());
				bb.order(ByteOrder.nativeOrder());
				// entry size for buffer
				int compressedLength = entry.getCompressedLength();
				ByteBuffer dataBufferInBB = pool.take(compressedLength);
				byte[] dataBufferIn = new byte[compressedLength];

				ch.read(ByteBuffer.wrap(dataBufferIn), entry.getFileOffset());

				Inflater inflater = new Inflater();
				inflater.setInput(dataBufferIn);
				try {
					int count = inflater.inflate(dataBufferOut);
					if (count != entry.getFileLength())
						System.err.println("Inflate count issue! coutn = "	+ count + " expected "
											+ entry.getFileLength() + " hashcode= " + entry.getFileHashCode());
				} catch (DataFormatException e) {
					System.err
							.println(ArchiveInputStream.class.getName() + ".getByteBuffer Inflater DataFormatException "
										+ " hashcode= " + entry.getFileHashCode());
				}
				inflater.end();
				pool.give(dataBufferInBB);

				bb.put(dataBufferOut);
				bb.rewind();
				return bb;
			}
		} else {
			//This is where a mappedbytebuffer could be a real problem, it might get all the way to the pipeline
			ByteBuffer bb = ByteBuffer.allocateDirect(entry.getFileLength());
			ch.read(bb, entry.getFileOffset());
			bb.rewind();
			return bb;
		}

	}

	//TODO!!!! this will tell me who is using this as a stream and help to get it unstreamed!
	public synchronized int read() {
		return super.read();
	}

	public synchronized int read(byte b[], int off, int len) {
		return super.read(b, off, len);
	}

	public synchronized byte[] readAllBytes() {
		return super.readAllBytes();
	}

	public int readNBytes(byte[] b, int off, int len) {
		return super.readNBytes(b, off, len);
	}

	public synchronized long transferTo(OutputStream out) throws IOException {
		return super.transferTo(out);
	}

	public synchronized long skip(long n) {
		return super.skip(n);
	}

	public synchronized int available() {
		return super.available();
	}

	public void mark(int readAheadLimit) {
		super.mark(readAheadLimit);
	}

	public synchronized void reset() {
		super.reset();
	}

	    
}