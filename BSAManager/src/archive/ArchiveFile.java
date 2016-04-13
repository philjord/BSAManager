package archive;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;

import com.frostwire.util.LongSparseArray;

public abstract class ArchiveFile
{

	public static boolean USE_FILE_MAPS = true;
	public static boolean USE_MINI_CHANNEL_MAPS = false;//true requires ArchiveFile.USE_FILE_MAPS = false;
	public static boolean USE_NON_NATIVE_ZIP = false;// true=slower but no native calls
	public static boolean RETURN_MAPPED_BYTE_BUFFERS = true;// seems waay faster for uncompressed things
	
	public enum SIG
	{
		TES3, BSA, BTDX
	};

	protected SIG sig;

	protected int version;

	protected File file;

	protected RandomAccessFile in;

	protected int folderCount;

	protected int fileCount;

	protected LongSparseArray<Folder> folderHashToFolderMap;

	public class Folder
	{
		public String folderName = "";// need for get folder files

		public int folderFileCount = 0;

		public long offset = -1;

		private boolean isForDisplay = false;

		public LongSparseArray<ArchiveEntry> fileToHashMap;

		public Folder(int folderFileCount, long offset, boolean isForDisplay)
		{
			this.folderFileCount = folderFileCount;
			this.offset = offset;
			this.isForDisplay = isForDisplay;
		}

		public String getFolderName()
		{
			return folderName;
		}

		public int getFolderFileCount()
		{
			return folderFileCount;
		}

		public long getOffset()
		{
			return offset;
		}

		public boolean isForDisplay()
		{
			return isForDisplay;
		}

		public LongSparseArray<ArchiveEntry> getFileToHashMap()
		{
			return fileToHashMap;
		}

		public String toString()
		{
			return "Folder:" + folderName;
		}
	}

	public ArchiveFile(SIG sig, File file)
	{
		this.sig = sig;
		this.file = file;
	}

	public String getName()
	{
		return file.getPath();
	}

	public SIG getSig()
	{
		return sig;
	}

	public int getVersion()
	{
		return version;
	}

	public int size()
	{
		return fileCount;
	}

	@Override
	public String toString()
	{
		return "ArchiveFile:" + file.getPath();
	}

	public abstract List<ArchiveEntry> getEntries();

	public InputStream getInputStream(ArchiveEntry entry) throws IOException
	{
		if (in == null)
		{
			throw new IOException("Archive file is not open");
		}

		return new ArchiveInputStream(in, entry);
	}

	/**
	 * The advice below is not correct, but I'ma leave it there cos it's confusing that way
	 * You probably don't want this method! unless you are doing Nif files
	 * Be VERY careful, handing mappedbytebuffers to openGL via the addChild call will push disk access
	 * onto the j3d thread, a very bad idea.
	 * @param entry
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer getByteBuffer(ArchiveEntry entry) throws IOException
	{
		if (in == null)
		{
			throw new IOException("Archive file is not open");
		}

		return ArchiveInputStream.getByteBuffer(in, entry, false);
	}

	public ByteBuffer getByteBuffer(ArchiveEntry entry, boolean allocateDirect) throws IOException
	{
		if (in == null)
		{
			throw new IOException("Archive file is not open");
		}

		return ArchiveInputStream.getByteBuffer(in, entry, allocateDirect);
	}

	public void close() throws IOException
	{
		if (in != null)
		{
			in.close();
			in = null;
		}
	}

	//Notice isFroDisplay unused here due to trouble with other sorts of ArchiveFile
	public Folder getFolder(String folderName, boolean isForDisplay)
	{

		StringBuilder buildName = new StringBuilder(folderName.toLowerCase());
		int sep;
		while ((sep = buildName.indexOf("/")) >= 0)
		{
			buildName.replace(sep, sep + 1, "\\");
		}
		folderName = buildName.toString();
		long folderHash = new HashCode(folderName, true).getHash();

		Folder folder = folderHashToFolderMap.get(folderHash);

		// fine as likely one of many bsas searched
		/*if (folder == null)
		{
			System.out.println("requested folder does not exist " + folderName + " in " + file);
		}*/

		try
		{
			if (folder != null && folder.fileToHashMap == null)
			{
				loadFolder(folder);
			}

			return folder;
		}
		catch (IOException e)
		{
			System.out.println("ArchiveFile Exception for folderName: " + folderName + " " + e + " " + e.getStackTrace()[0]);
		}

		return null;
	}

	public abstract ArchiveEntry getEntry(String fullFileName);

	protected abstract void loadFolder(Folder folder) throws IOException;

	public static ArchiveFile createArchiveFile(File file) throws DBException, IOException
	{
		//in = new RandomAccessFile(file, "r");
		RandomAccessFile testIn = new RandomAccessFile(file, "r");
		// lock just in case anyone else tries an early read
		synchronized (testIn)
		{
			// test for TES3 BSA format flag\
			byte[] tes3test = new byte[4];
			int count = testIn.read(tes3test);
			if (count != 4)
			{
				testIn.close();
				throw new EOFException("Archive tes3 test failed " + file.getAbsolutePath());
			}

			if (getInteger(tes3test, 0) == 256)
			{
				testIn.close();
				return new archive.tes3.ArchiveFileTes3(file);
			}
			else
			{
				//TES4+ format, reset to start
				testIn.seek(0);

				//load header
				byte header[] = new byte[36];

				count = testIn.read(header);
				if (count != 36)
				{
					testIn.close();
					throw new EOFException("Archive header is incomplete " + file.getAbsolutePath());
				}

				String id = new String(header, 0, 4);
				if (id.equals("BSA\0"))
				{
					testIn.close();
					return new archive.bsa.ArchiveFileBsa(file);
				}
				else if (id.equals("BTDX"))
				{
					testIn.close();
					return new archive.btdx.ArchiveFileBtdx(file);
				}
				else
				{
					testIn.close();
					throw new DBException("File is not a BSA archive " + file.getAbsolutePath());
				}
			}
		}
	}

	public abstract void load(boolean isForDisplay) throws DBException, IOException;

	public abstract boolean hasNifOrKf();

	public abstract boolean hasDDS();

	public abstract boolean hasKTX();

	public abstract boolean hasASTC();

	public abstract boolean hasSounds();

	protected static int getShort(byte buffer[], int offset)
	{
		return buffer[offset + 0] & 0xff | (buffer[offset + 1] & 0xff) << 8;
	}

	protected static int getInteger(byte buffer[], int offset)
	{
		return buffer[offset + 0] & 0xff | (buffer[offset + 1] & 0xff) << 8 | (buffer[offset + 2] & 0xff) << 16
				| (buffer[offset + 3] & 0xff) << 24;
	}

	protected static long getLong(byte buffer[], int offset)
	{
		return buffer[offset + 0] & 255L | (buffer[offset + 1] & 255L) << 8 | (buffer[offset + 2] & 255L) << 16
				| (buffer[offset + 3] & 255L) << 24 | (buffer[offset + 4] & 255L) << 32 | (buffer[offset + 5] & 255L) << 40
				| (buffer[offset + 6] & 255L) << 48 | (buffer[offset + 7] & 255L) << 56;
	}

}