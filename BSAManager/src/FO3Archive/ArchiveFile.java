package FO3Archive;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchiveFile
{
	private int version;

	private File file;

	private RandomAccessFile in;

	private int archiveFlags;

	private int fileFlags;

	private int folderCount;

	private int fileCount;

	private int folderNamesLength;

	private int fileNamesLength;

	private boolean isCompressed;

	private boolean defaultCompressed;

	private Map<Long, Folder> folderHashToFolderMap;

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

	private Map<Long, String> filenameHashToFileNameMap;

	public ArchiveFile(String filename)
	{
		this(new File(filename));
	}

	public ArchiveFile(File file)
	{
		this.file = file;
	}

	public String getName()
	{
		return file.getPath();
	}

	public int getFileFlags()
	{
		return fileFlags;
	}

	public int getVersion()
	{
		return version;
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

	public int size()
	{
		return fileCount;
	}

	public InputStream getInputStream(ArchiveEntry entry) throws IOException
	{
		if (in == null)
			throw new IOException("Archive file is not open");
		else if (entry.getIdentifier() != hashCode())
			throw new IllegalArgumentException("Archive entry not valid for this archive");
		else
			return new ArchiveInputStream(in, entry);

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
		try
		{

			int pathSep = fullFileName.lastIndexOf("\\");
			String folderName = fullFileName.substring(0, pathSep);
			long folderHash = new HashCode(folderName, true).getHash();
			Folder folder = folderHashToFolderMap.get(folderHash);

			if (folder != null)
			{
				//do we need to load the files in this folder?
				if (folder.fileToHashMap == null)
				{
					loadFolder(folder);
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
		}
		catch (IOException e)
		{
			System.out.println("ArchiveFile Exception for filename:  " + fullFileName + " " + e + " " + e.getStackTrace()[0]);
		}

		return null;
	}

	private void loadFolder(Folder folder) throws IOException
	{
		if (version == 256)
		{
			throw new IOException("TES3 is loaded at intial load time, so this should never be called");
		}
		else
		{

			folder.fileToHashMap = new HashMap<Long, ArchiveEntry>(folder.folderFileCount);

			synchronized (in)
			{
				byte name[] = new byte[256];

				// now go and read the folders name and then do it's files
				long fp = folder.offset;

				in.seek(fp);

				// read off the folder name
				int length = in.readByte() & 0xff;
				int count = in.read(name, 0, length);
				if (count != length)
					throw new EOFException("Folder name is incomplete");
				folder.folderName = new String(name, 0, length - 1);

				byte buffer[] = new byte[16];
				fp += length + 1; // move pointer beyond folder name ready for file list (+1 is for a null byte)

				for (int fileIndex = 0; fileIndex < folder.folderFileCount; fileIndex++)
				{
					// pull data in a buffer for reading
					in.seek(fp);
					count = in.read(buffer);
					if (count != 16)
						throw new EOFException("File record is incomplete");
					fp += 16L;//set pointer ready for next fileIndex loop

					// get the file record data from the buffer read in above
					long fileHash = getLong(buffer, 0);
					int dataLength = getInteger(buffer, 8);
					long dataOffset = getInteger(buffer, 12) & 0xffffffffL;

					String entryFileName = filenameHashToFileNameMap.get(fileHash);

					ArchiveEntry entry = new ArchiveEntry(this, folder.folderName, entryFileName);

					if (version == 104)
					{
						//FO3 - Fallout 3
						//TES5 - Skyrim

						// go to data area and read sizes off now
						if (defaultCompressed)
						{
							in.seek(dataOffset);
							length = (in.readByte() & 0xff) + 1;
							dataOffset += length;
							dataLength -= length;
						}

						//now do something a bit different if the other compressed flag is set
						int compressedLength = 0;
						if (isCompressed)
						{
							in.seek(dataOffset);
							count = in.read(buffer, 0, 4);
							if (count != 4)
								throw new EOFException("Compressed data is incomplete");

							dataOffset += 4L;
							compressedLength = dataLength - 4;
							dataLength = getInteger(buffer, 0);
						}

						entry.setIdentifier(hashCode());
						entry.setFileOffset(dataOffset);
						entry.setFileLength(dataLength);
						entry.setCompressed(isCompressed);
						entry.setCompressedLength(compressedLength);

					}
					else if (version == 103)
					{
						//TES4 - Oblivion

						boolean compressed = isCompressed;

						//read off special inverted flag
						if ((dataLength & (1 << 30)) != 0)
						{
							dataLength ^= 1 << 30;
							compressed = !compressed;
						}

						int unCompressedLength = 0;
						if (compressed)
						{
							// data area start with uncompressed size tehn compressed data
							in.seek(dataOffset);
							count = in.read(buffer, 0, 4);
							if (count != 4)
								throw new EOFException("Compressed data is incomplete");
							unCompressedLength = getInteger(buffer, 0);

							dataOffset += 4L; // move past teh uncompressed size into compressed data pointer
							dataLength -= 4; // compressed size has uncompressed size int taken off						
						}

						entry.setIdentifier(hashCode());
						entry.setFileOffset(dataOffset);
						entry.setFileLength(unCompressedLength); //different to 104				
						entry.setCompressed(compressed); //different to 104
						entry.setCompressedLength(dataLength);//different to 104
					}

					folder.fileToHashMap.put(fileHash, entry);
				}
			}
		}
	}

	public void load() throws DBException, IOException
	{

		in = new RandomAccessFile(file, "r");
		// lock jut in case anyone else tries an early read
		synchronized (in)
		{
			// test for TES3 BSA format flag\
			byte[] tes3test = new byte[4];
			int count = in.read(tes3test);
			if (count != 4)
				throw new EOFException("Archive tes3 test failed");

			//TES3 format
			if (getInteger(tes3test, 0) == 256)
			{
				//reset to start
				in.seek(0);

				//load header
				byte header[] = new byte[12];

				count = in.read(header);
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
				folderHashToFolderMap = new HashMap<Long, Folder>(folderCount);
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
			else
			{
				//TES4+ format, reset to start
				in.seek(0);

				//load header
				byte header[] = new byte[36];

				count = in.read(header);
				if (count != 36)
					throw new EOFException("Archive header is incomplete");

				String id = new String(header, 0, 4);
				if (!id.equals("BSA\0"))
					throw new DBException("File is not a BSA archive");

				version = getInteger(header, 4);
				if (version != 104 && version != 103)
					throw new DBException("BSA version " + version + " is not supported");

				long folderOffset = getInteger(header, 8) & 0xffffffffL;
				archiveFlags = getInteger(header, 12);
				folderCount = getInteger(header, 16);
				fileCount = getInteger(header, 20);
				folderNamesLength = getInteger(header, 24);
				fileNamesLength = getInteger(header, 28);
				fileFlags = getInteger(header, 32);
				//end of load header

				if ((archiveFlags & 3) != 3)
					throw new DBException("Archive does not use directory/file names");

				isCompressed = (archiveFlags & 4) != 0;//WTF is the difference?
				defaultCompressed = (archiveFlags & 0x100) != 0;

				//load fileNameBlock
				byte[] nameBuffer = new byte[fileNamesLength];
				long nameOffset = folderOffset + (folderCount * 16) + (fileCount * 16) + (folderCount + folderNamesLength);
				in.seek(nameOffset);

				count = in.read(nameBuffer);

				if (count != fileNamesLength)
					throw new EOFException("File names buffer is incomplete");

				String[] fileNames = new String[fileCount];

				filenameHashToFileNameMap = new HashMap<Long, String>(fileCount);

				int bufferIndex = 0;
				for (int nameIndex = 0; nameIndex < fileCount; nameIndex++)
				{
					int startIndex = bufferIndex;
					for (; bufferIndex < fileNamesLength && nameBuffer[bufferIndex] != 0; bufferIndex++)
					{
						;
					}

					if (bufferIndex >= fileNamesLength)
						throw new DBException("File names buffer truncated");

					String filename = new String(nameBuffer, startIndex, bufferIndex - startIndex);
					fileNames[nameIndex] = filename;
					//TODO: I don't need to load these this early, I could do this just as teh folder.load call is made			
					// save tiem and space
					filenameHashToFileNameMap.put(new HashCode(filename, false).getHash(), filename);

					bufferIndex++;
				}

				folderHashToFolderMap = new HashMap<Long, Folder>(folderCount);

				byte buffer[] = new byte[16];
				for (int folderIndex = 0; folderIndex < folderCount; folderIndex++)
				{
					// pull data in a buffer for reading
					in.seek(folderOffset);
					count = in.read(buffer);
					if (count != 16)
						throw new EOFException("Folder record is incomplete");

					folderOffset += 16L; //set pointer ready for next folderIndex for loop

					// get the folder record data out fo the buffer read above
					long folderHash = getLong(buffer, 0);
					int folderFileCount = getInteger(buffer, 8);
					long fileOffset = (getInteger(buffer, 12) - fileNamesLength) & 0xffffffffL;

					folderHashToFolderMap.put(folderHash, new Folder(folderFileCount, fileOffset));

				}
			}
		}

	}

	private int getInteger(byte buffer[], int offset)
	{
		return buffer[offset + 0] & 0xff | (buffer[offset + 1] & 0xff) << 8 | (buffer[offset + 2] & 0xff) << 16
				| (buffer[offset + 3] & 0xff) << 24;
	}

	private long getLong(byte buffer[], int offset)
	{
		return buffer[offset + 0] & 255L | (buffer[offset + 1] & 255L) << 8 | (buffer[offset + 2] & 255L) << 16
				| (buffer[offset + 3] & 255L) << 24 | (buffer[offset + 4] & 255L) << 32 | (buffer[offset + 5] & 255L) << 40
				| (buffer[offset + 6] & 255L) << 48 | (buffer[offset + 7] & 255L) << 56;
	}

	public String toString()
	{
		return file.getPath();
	}

	protected void finalize()
	{
		try
		{
			close();
			super.finalize();
		}
		catch (IOException exc)
		{
			Main.logException("I/O error while finalizing archive", exc);
		}
		catch (Throwable exc)
		{
			Main.logException("Exception while finalizing archive", exc);
		}

	}

	public boolean hasNifOrKf()
	{
		return (fileFlags & 1) != 0 || (fileFlags & 0x40) != 0 || version == 256;
	}

	public boolean hasDDS()
	{
		return (fileFlags & 2) != 0 || version == 256;
	}

	public boolean hasSounds()
	{
		return (fileFlags & 8) != 0 || (fileFlags & 0x10) != 0 || version == 256;

	}

}