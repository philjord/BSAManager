package archive.tes3;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import com.frostwire.util.LongSparseArray;

import archive.ArchiveEntry;
import archive.ArchiveFile;
import archive.DBException;
import archive.HashCode;
import archive.displayables.DisplayableArchiveEntry;
import tools.io.MappedByteBufferRAF;

public class ArchiveFileTes3 extends ArchiveFile
{
	private LongSparseArray<String> filenameHashToFileNameMap;

	public ArchiveFileTes3(File file)
	{
		super(SIG.TES3, file);
	}

	/**
	 * CAUTION Super HEAVY WEIGHT!!
	 * @return
	 */
	@Override
	public List<ArchiveEntry> getEntries()
	{
		ArrayList<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		int filesToLoad = fileCount;
		int currentProgress = 0;
		try
		{
			for (int f = 0; f < folderHashToFolderMap.size(); f++)
			{
				Folder folder = folderHashToFolderMap.get(folderHashToFolderMap.keyAt(f));
				if (folder.fileToHashMap == null)
				{
					loadFolder(folder);
				}
				for (int i = 0; i < folder.fileToHashMap.size(); i++)
					ret.add(folder.fileToHashMap.get(folder.fileToHashMap.keyAt(i)));

				filesToLoad -= folder.folderFileCount;
				int newProgress = (filesToLoad * 100) / fileCount;
				if (newProgress >= currentProgress + 5)
				{
					currentProgress = newProgress;

				}
			}

		}
		catch (IOException e)
		{
			System.out.println("ArchiveFile Exception for filename:  " + e + " " + e.getStackTrace()[0]);
		}

		return ret;

	}

	@Override
	public ArchiveEntry getEntry(String fullFileName)
	{
		fullFileName = fullFileName.toLowerCase();
		fullFileName = fullFileName.trim();
		if (fullFileName.indexOf("/") != -1)
		{
			StringBuilder buildName = new StringBuilder(fullFileName);
			int sep;
			while ((sep = buildName.indexOf("/")) >= 0)
			{
				buildName.replace(sep, sep + 1, "\\");
			}
			fullFileName = buildName.toString();
		}

		int pathSep = fullFileName.lastIndexOf("\\");
		String folderName = fullFileName.substring(0, pathSep);
		long folderHash = new HashCode(folderName, true).getHash();
		Folder folder = folderHashToFolderMap.get(folderHash);

		if (folder != null)
		{
			//do we need to load the files in this folder?
			if (folder.fileToHashMap == null)
			{
				System.out.println("TES3 folderName not indexed " + folderName);
				return null;
			}

			String fileName = fullFileName.substring(pathSep + 1);
			long fileHashCode = new HashCode(fileName, false).getHash();
			String bsaFileName = filenameHashToFileNameMap.get(fileHashCode);
			if (bsaFileName != null)
			{
				if (bsaFileName.equals(fileName))
				{
					return folder.fileToHashMap.get(fileHashCode);
				}
				else
				{
					System.out.println("BSA File name mismatch: " + bsaFileName + " " + fileName);
				}
			}
		}

		return null;
	}

	@Override
	protected void loadFolder(Folder folder) throws IOException
	{
		throw new UnsupportedOperationException("TES3 is loaded at intial load time, so this should never be called");
	}

	@Override
	public void load(boolean isForDisplay) throws DBException, IOException
	{
		if (file.length() > Integer.MAX_VALUE || !USE_FILE_MAPS)
			in = new RandomAccessFile(file, "r");
		else
			in = new MappedByteBufferRAF(file, "r");

		// lock just in case anyone else tries an early read
		synchronized (in)
		{
			//reset to start
			in.seek(0);

			//load header
			byte header[] = new byte[12];

			int count = in.read(header);
			if (count != 12)
				throw new EOFException("Archive header is incomplete");

			version = getInteger(header, 0);
			int hashtableOffset = getInteger(header, 4);
			fileCount = getInteger(header, 8);

			int[] fileSizes = new int[fileCount];
			long[] fileOffsets = new long[fileCount];
			byte[] buffer = new byte[8];

			for (int i = 0; i < fileCount; i++)
			{
				count = in.read(buffer);

				if (count != buffer.length)
					throw new EOFException("buffer is incomplete");
				fileSizes[i] = getInteger(buffer, 0);
				fileOffsets[i] = getInteger(buffer, 4);
			}

			long[] fileNameOffsets = new long[fileCount];
			buffer = new byte[4];
			for (int i = 0; i < fileCount; i++)
			{
				count = in.read(buffer);

				if (count != buffer.length)
					throw new EOFException("buffer is incomplete");
				fileNameOffsets[i] = getInteger(buffer, 0);
			}
			//restate the offsets as lengths for use below
			int[] fileNameLengths = new int[fileCount];
			for (int i = 1; i < fileCount; i++)
			{
				fileNameLengths[i - 1] = (int) (fileNameOffsets[i] - fileNameOffsets[i - 1]);
			}
			//last filename length calculated				
			fileNameLengths[fileCount - 1] = (int) ((hashtableOffset - (12 * fileCount)) - fileNameOffsets[fileCount - 1]);

			String[] fileNames = new String[fileCount];
			for (int i = 0; i < fileCount; i++)
			{
				buffer = new byte[fileNameLengths[i]];
				count = in.read(buffer);

				if (count != buffer.length)
					throw new EOFException("buffer is incomplete");
				fileNames[i] = new String(buffer, 0, buffer.length - 1);
			}

			//hash section ignored (just use the tes4+ hash system)					

			long fileDataStartOffset = 12 + hashtableOffset + (8 * fileCount);

			//build up a trival folderhash from all the file names
			// and preload the archive entries from the data above
			folderHashToFolderMap = new LongSparseArray<Folder>();
			filenameHashToFileNameMap = new LongSparseArray<String>(fileCount);

			for (int i = 0; i < fileCount; i++)
			{
				String fullFileName = fileNames[i];

				int pathSep = fullFileName.lastIndexOf("\\");
				String folderName = fullFileName.substring(0, pathSep);
				long folderHash = new HashCode(folderName, true).getHash();
				Folder folder = folderHashToFolderMap.get(folderHash);

				if (folder == null)
				{
					folder = new Folder(0, -1, isForDisplay);
					folder.folderName = folderName;
					folder.fileToHashMap = new LongSparseArray<ArchiveEntry>();
					folderHashToFolderMap.put(folderHash, folder);
				}

				String fileName = fullFileName.substring(pathSep + 1).trim();
				long fileHashCode = new HashCode(fileName, false).getHash();
				filenameHashToFileNameMap.put(fileHashCode, fileName);

				ArchiveEntry entry;
				if (isForDisplay)
					entry = new DisplayableArchiveEntry(this, folder.folderName, fileName);
				else
					entry = new ArchiveEntry(this, folder.folderName, fileName);

				entry.setFileOffset(fileDataStartOffset + fileOffsets[i]);
				entry.setFileLength(fileSizes[i]);
				entry.setCompressed(false);//never compressed
				entry.setCompressedLength(-1);
				folder.fileToHashMap.put(fileHashCode, entry);
				folder.folderFileCount++;
			}

		}

	}

	@Override
	public boolean hasNifOrKf()
	{
		return true;
	}

	@Override
	public boolean hasDDS()
	{
		return true;
	}

	@Override
	public boolean hasKTX()
	{
		return false;
	}

	@Override
	public boolean hasASTC()
	{
		return false;
	}

	public boolean hasSounds()
	{
		return true;
	}

}