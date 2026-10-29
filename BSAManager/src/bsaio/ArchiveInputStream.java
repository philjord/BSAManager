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
import zipBB.ZlibHeaderTrailerParser;
import zipBB.Inflater.InflationException;
import zipBB.ZlibHeaderTrailerParser.ZlibHeader;

public class ArchiveInputStream extends FastByteArrayInputStream {

	public static boolean			USE_JAVE_INFLATOR	= false;
	//1,0,-1 1 uses mapped from channels, 0 reads into a bytebuffer, both seem the same for speed and memory use
	public static int				USE_MAPPED			= 1;

	private static ByteBufferPool	pool				= new ByteBufferPool();

	/**
	 * This constructor is used in Android code to get maps out as bitmaps, and various utils, it is not a wise thing to use, getByteBuffer below is the
	 * bettereer
	 * @param in
	 * @param entry
	 * @throws IOException
	 */
	//FIXME: Android should use getByteBuffer if possible
	public ArchiveInputStream(FileChannelRAF in, ArchiveEntry entry) throws IOException {
		super(new byte[0]);//reset below once data is available
		FileChannel ch = in.getChannel();

		entry.ensureEntryReady(ch);

		// not sure why this is bad, something weird with defaultcompressed flag the the archive load up
		if (entry.getFileLength() == 0)
			entry.setFileLength(entry.getCompressedLength());

		byte[] dataBufferOut = new byte[entry.getFileLength()];
		ByteBuffer dest = ByteBuffer.wrap(dataBufferOut);

		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0) {
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];
			ByteBuffer src = ByteBuffer.wrap(dataBufferIn);

			ch.read(src, entry.getFileOffset());
			inflate(src, dest, entry.getFileLength(), entry.getFileHashCode());
		} else {
			ch.read(dest, entry.getFileOffset());
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
		// LOODNif has a problem in NifToJd3
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
				ByteBuffer dest = ByteBuffer.allocateDirect(entry.getFileLength()).order(ByteOrder.nativeOrder());
				int compressedLength = entry.getCompressedLength();
				//CAREFUL mapping only works if the data is fully read off, watch out for Textures that go to the GPU
				ByteBuffer src = null;
				if (USE_MAPPED == 0) {
					src = pool.take(compressedLength);
					ch.read(src, entry.getFileOffset());
					src.rewind();
				} else {
					src = ch.map(MapMode.READ_ONLY, entry.getFileOffset(), compressedLength);
				}

				inflate(src, dest, entry.getFileLength(), entry.getFileHashCode());

				if (USE_MAPPED == 0) {
					pool.give(src);
				}
				dest.rewind();
				return dest;
			} else {
				ByteBuffer dest = ByteBuffer.allocateDirect(entry.getFileLength());
				dest.order(ByteOrder.nativeOrder());
				int compressedLength = entry.getCompressedLength();
				ByteBuffer src = pool.take(compressedLength);
				ch.read(src, entry.getFileOffset());
				inflate(src, dest, entry.getFileLength(), entry.getFileHashCode());
				pool.give(src);
				dest.rewind();
				return dest;
			}
		} else {
			//This is where a mappedbytebuffer could be a real problem, it might get all the way to the pipeline
			ByteBuffer dest = ByteBuffer.allocateDirect(entry.getFileLength());
			ch.read(dest, entry.getFileOffset());
			dest.rewind();
			return dest;
		}

	}

	/**
	 * A separate method so we can easily swap between native and Java inflators
	 */
	private static void inflate(ByteBuffer src, ByteBuffer dest, int fileLength, long fileHashCode) {
		if (USE_JAVE_INFLATOR) {
			zipBB.Inflater inflater = new zipBB.Inflater();
			try {
				ZlibHeader header = ZlibHeaderTrailerParser.parseHeader(src);
				if (header == null) {
					throw new AssertionError(
							"Expect to read the header in the first try since the buffer is large enough.");
				}
				if (header.dictChecksum != 0) {
					throw new AssertionError("Expect to have no dictionary.");
				}

				boolean done = false;
				while (!done) {
					switch (inflater.inflate(dest, src)) {
						case NEED_MORE_INPUT:
							//not sure about this but keep trucking anyway
							break;
						case NEED_MORE_OUTPUT:
							//only does 65k per call just keep going
							break;
						case DONE:
							done = true;
							break;
					}
				}

			} catch (InflationException e) {
				System.err.println(ArchiveInputStream.class.getName()	+ ".getByteBuffer Inflater InflationException "
									+ " hashcode= " + fileHashCode);
			}
			inflater.reset();
		} else {
			Inflater inflater = new Inflater();
			inflater.setInput(src);
			try {
				int count = inflater.inflate(dest);
				if (count != fileLength)
					System.err.println("Inflate count issue! coutn = "	+ count + " expected " + fileLength
										+ " hashcode= " + fileHashCode);
			} catch (DataFormatException e) {
				System.err.println(ArchiveInputStream.class.getName()	+ ".getByteBuffer Inflater DataFormatException "
									+ " hashcode= " + fileHashCode);
			}
			inflater.end();
		}
	}

	//TODO!!!! this will tell me who is using this as a stream and help to get it unstreamed!
	@Override
	public synchronized int read() {
		return super.read();
	}

	@Override
	public synchronized int read(byte b[], int off, int len) {
		return super.read(b, off, len);
	}

	@Override
	public synchronized byte[] readAllBytes() {
		return super.readAllBytes();
	}

	@Override
	public int readNBytes(byte[] b, int off, int len) {
		return super.readNBytes(b, off, len);
	}

	@Override
	public synchronized long transferTo(OutputStream out) throws IOException {
		return super.transferTo(out);
	}

	@Override
	public synchronized long skip(long n) {
		return super.skip(n);
	}

	@Override
	public synchronized int available() {
		return super.available();
	}

	@Override
	public void mark(int readAheadLimit) {
		super.mark(readAheadLimit);
	}

	@Override
	public synchronized void reset() {
		super.reset();
	}

}