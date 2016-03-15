package archive;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public abstract class ArchiveFile
{

	public static boolean USE_FILE_MAPS = true;
	//TODO: with this idea and read only I can multithread access to bsa files now
	public static boolean USE_MINI_CHANNEL_MAPS = false;//requires ArchiveFile.USE_FILE_MAPS = false;
	public static boolean USE_NON_NATIVE_ZIP = false;

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

	protected Map<Long, Folder> folderHashToFolderMap;

	public class Folder
	{
		public String folderName = "";

		public int folderFileCount = 0;

		public long offset = -1;

		public Map<Long, ArchiveEntry> fileToHashMap;

		public Folder(int folderFileCount, long offset)
		{
			this.folderFileCount = folderFileCount;
			this.offset = offset;
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
			throw new IOException("Archive file is not open");
		else if (entry.getIdentifier() != hashCode())
			throw new IllegalArgumentException("Archive entry not valid for this archive");
		else
			return new ArchiveInputStream(in, entry);
	}

	/**
	 * You probably don't want this method!
	 * Be VERY careful, handing mappedbytebuffers to openGL via the addChild call will push disk access
	 * onto the j3d thread, a very bad idea.
	 * @param entry
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer getByteBuffer(ArchiveEntry entry) throws IOException
	{
		if (in == null)
			throw new IOException("Archive file is not open");
		else if (entry.getIdentifier() != hashCode())
			throw new IllegalArgumentException("Archive entry not valid for this archive");
		else if (entry.isCompressed())
			throw new IOException("Archive entry isCompressed, can't return a bytebuffer " + entry.getFileName());
		else
			return ArchiveInputStream.getByteBuffer(in, entry);

	}

	public void close() throws IOException
	{
		if (in != null)
		{
			in.close();
			in = null;
		}
	}

	public Folder getFolder(String folderName)
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

	public abstract void load() throws DBException, IOException;

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