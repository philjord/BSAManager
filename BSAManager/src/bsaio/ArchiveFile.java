package bsaio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;

import com.frostwire.util.LongSparseArray;

import tools.io.FileChannelRAF;

public abstract class ArchiveFile {

	public enum SIG {
		TES3, BSA, BTDX
	};

	protected SIG						sig;

	protected int						version;					// 104 is FO3 and TES5, 103 is TES4
	public static final int				PARTIAL_FILE	= 14233412;

	protected FileChannel				file;
	protected String					fileName;

	protected FileChannelRAF			in; //FIXME: FileChannelRAF can now be converted to just FileChannel

	protected int						folderCount;

	protected int						fileCount;

	// each folder has it's file pointers and hashes so this is teh data model main dictionary
	protected LongSparseArray<Folder>	folderHashToFolderMap;

	protected boolean					isForDisplay	= false;

	/**
	 * folders have the data for file in the hashmap
	 */
	public static class Folder {
		public int								folderFileCount	= 0;

		public long								offset			= -1;

		public LongSparseArray<ArchiveEntry>	fileToHashMap;

		public Folder(int folderFileCount, long offset) {
			this.folderFileCount = folderFileCount;
			this.offset = offset;
		}

		public int getFolderFileCount() {
			return folderFileCount;
		}

		public long getOffset() {
			return offset;
		}

		public LongSparseArray<ArchiveEntry> getFileToHashMap() {
			return fileToHashMap;
		}

	}

	public ArchiveFile(boolean isForDisplay, SIG sig, FileChannel file, String fileName) {
		this.sig = sig;
		this.file = file;
		this.fileName = fileName;
		this.isForDisplay = isForDisplay;
	}

	public String getName() {
		return fileName;
	}

	public SIG getSig() {
		return sig;
	}

	public int getVersion() {
		return version;
	}

	public int size() {
		return fileCount;
	}

	@Override
	public String toString() {
		return "ArchiveFile:" + fileName;
	}

	public abstract List<ArchiveEntry> getEntries();

	public InputStream getInputStream(ArchiveEntry entry) throws IOException {
		if (in == null) {
			throw new IOException("Archive file is not open");
		}

		return new ArchiveInputStream(in, entry);
	}

	/**
	 * The advice below is not correct, but I'ma leave it there cos it's confusing that way You probably don't want this
	 * method! unless you are doing Nif files Be VERY careful, handing mappedbytebuffers to openGL via the addChild call
	 * will push disk access onto the j3d thread, a very bad idea.
	 * @param entry
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer getByteBuffer(ArchiveEntry entry) throws IOException {
		if (in == null) {
			throw new IOException("Archive file is not open");
		}

		return ArchiveInputStream.getByteBuffer(in, entry);
	}	

	public void close() throws IOException {
		if (in != null) {
			in.close();
			in = null;
		}
	}

	/**
	 * Notice isForDisplay unused here due to trouble with other sorts of ArchiveFile Expensive don't call this casually
	 * @param folderName
	 * @param isForDisplay
	 * @return
	 */
	public Folder getFolder(String folderName, boolean isForDisplay) {
		throw new UnsupportedOperationException(
				"ArchiveFile.getEntries() can only be called aginst displayable version");
	}

	public abstract ArchiveEntry getEntry(String fullFileName);

	public static ArchiveFile createArchiveFile(boolean isForDisplay, FileChannel file, String fileName)
			throws DBException, IOException {

		FileChannelRAF in = new FileChannelRAF(file);
		FileChannel ch = in.getChannel();

		// test for TES3 BSA format flag\
		byte[] tes3test = new byte[4];

		int count = ch.read(ByteBuffer.wrap(tes3test), 0);
		if (count != 4) {
			throw new EOFException("Archive tes3 test failed " + fileName);
		}

		if (getInteger(tes3test, 0) == 256) {
			return new bsaio.tes3.ArchiveFileTes3(isForDisplay, file, fileName);
		} else {
			//TES4+ format, reset to start

			//load header
			byte[] header = new byte[36];
			count = ch.read(ByteBuffer.wrap(header), 0);

			if (count != 36) {
				throw new EOFException("Archive header is incomplete " + fileName);
			}
			String id = new String(header, 0, 4);
			if (id.equals("BSA\0")) {
				return new bsaio.bsa.ArchiveFileBsa(isForDisplay, file, fileName);
			} else if (id.equals("BTDX")) {
				return new bsaio.btdx.ArchiveFileBtdx(isForDisplay, file, fileName);
			} else {
				throw new DBException("File is not a BSA archive " + fileName);
			}
		}

	}

	public abstract void load() throws DBException, IOException;

	public abstract boolean hasNifOrKf();

	public abstract boolean hasTextureFiles();

	public abstract boolean hasDDS();

	public abstract boolean hasKTX();

	public abstract boolean hasSounds();

	public abstract boolean hasMaterials();
	
	public boolean hasMaterialCDB() {
		// only for Btdx for starfield for materials
		return false;
	}

	public static int getShort(byte buffer[], int offset) {
		return buffer[offset + 0] & 0xff | (buffer[offset + 1] & 0xff) << 8;
	}

	public static int getUShort(byte buffer[], int offset) {
		return (buffer[offset + 0] & 0xff | (buffer[offset + 1] & 0xff) << 8) & 0xffffffff;
	}

	public static int getInteger(byte buffer[], int offset) {
		return buffer[offset + 0]	& 0xff	| (buffer[offset + 1] & 0xff) << 8 | (buffer[offset + 2] & 0xff) << 16
				| (buffer[offset + 3] & 0xff) << 24;
	}

	public static long getUInteger(byte buffer[], int offset) {
		return (buffer[offset + 0]	& 0xff	| (buffer[offset + 1] & 0xff) << 8 | (buffer[offset + 2] & 0xff) << 16
				| (buffer[offset + 3] & 0xff) << 24) & 0xffffffffL;
	}

	public static long getLong(byte buffer[], int offset) {
		return (buffer[offset + 0] & 255L) << 0 | (buffer[offset + 1] & 255L) << 8 | (buffer[offset + 2] & 255L) << 16
				| (buffer[offset + 3] & 255L) << 24 | (buffer[offset + 4] & 255L) << 32
				| (buffer[offset + 5] & 255L) << 40 | (buffer[offset + 6] & 255L) << 48
				| (buffer[offset + 7] & 255L) << 56;
	}

	

}