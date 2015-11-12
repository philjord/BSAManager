package archive.tes3;

import gui.StatusDialog;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.io.MappedByteBufferRAF;
import archive.ArchiveEntry;
import archive.ArchiveFile;
import archive.DBException;
import archive.HashCode;

public class ArchiveFileTes3 extends ArchiveFile
{
	private Map<Long, String> filenameHashToFileNameMap;

	public ArchiveFileTes3(File file)
	{
		super(SIG.TES3, file);
	}

	/**
	 * CAUTION Super HEAVY WEIGHT!!
	 * @return
	 */
	public List<ArchiveEntry> getEntries(StatusDialog statusDialog)
	{
		ArrayList<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		int filesToLoad = fileCount;
		int currentProgress = 0;
		try
		{
			for (Folder folder : folderHashToFolderMap.values())
			{
				if (folder.fileToHashMap == null)
				{
					loadFolder(folder);
				}
				ret.addAll(folder.fileToHashMap.values());

				filesToLoad -= folder.folderFileCount;
				int newProgress = (filesToLoad * 100) / fileCount;
				if (newProgress >= currentProgress + 5)
				{
					currentProgress = newProgress;
					if (statusDialog != null)
						statusDialog.updateProgress(currentProgress);
				}
			}

		}
		catch (IOException e)
		{
			System.out.println("ArchiveFile Exception for filename:  " + e + " " + e.getStackTrace()[0]);
		}

		return ret;

	}

	public ArchiveEntry getEntry(String fullFileName)
	{
		fullFileName = fullFileName.toLowerCase();
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

	protected void loadFolder(Folder folder) throws IOException
	{
		throw new UnsupportedOperationException("TES3 is loaded at intial load time, so this should never be called");
	}

	public void load() throws DBException, IOException
	{
		//in = new RandomAccessFile(file, "r");
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
			folderHashToFolderMap = new HashMap<Long, Folder>();
			filenameHashToFileNameMap = new HashMap<Long, String>(fileCount);

			for (int i = 0; i < fileCount; i++)
			{
				String fullFileName = fileNames[i];

				int pathSep = fullFileName.lastIndexOf("\\");
				String folderName = fullFileName.substring(0, pathSep);
				long folderHash = new HashCode(folderName, true).getHash();
				Folder folder = folderHashToFolderMap.get(folderHash);

				if (folder == null)
				{
					folder = new Folder(0, -1);
					folder.folderName = folderName;
					folder.fileToHashMap = new HashMap<Long, ArchiveEntry>();
					folderHashToFolderMap.put(folderHash, folder);
				}

				String fileName = fullFileName.substring(pathSep + 1);
				long fileHashCode = new HashCode(fileName, false).getHash();
				filenameHashToFileNameMap.put(fileHashCode, fileName);

				ArchiveEntry entry = new ArchiveEntry(this, folder.folderName, fileName);
				entry.setIdentifier(hashCode());
				entry.setFileOffset(fileDataStartOffset + fileOffsets[i]);
				entry.setFileLength(fileSizes[i]);
				entry.setCompressed(false);//never compressed
				entry.setCompressedLength(-1);
				folder.fileToHashMap.put(fileHashCode, entry);
				folder.folderFileCount++;
			}

		}

	}

	public boolean hasNifOrKf()
	{
		return true;
	}

	public boolean hasDDS()
	{
		return true;
	}

	public boolean hasSounds()
	{
		return true;
	}

}