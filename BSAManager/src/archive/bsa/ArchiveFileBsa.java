package archive.bsa;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.io.MappedByteBufferRAF;
import archive.ArchiveEntry;
import archive.ArchiveFile;
import archive.DBException;
import archive.HashCode;

public class ArchiveFileBsa extends ArchiveFile
{
	private int archiveFlags; //in BSA id

	private int fileFlags; //in BSA id

	private boolean isCompressed;

	private boolean defaultCompressed;

	private boolean hasKTXFiles = false;

	private boolean hasASTCFiles = false;

	private Map<Long, String> filenameHashToFileNameMap;

	public ArchiveFileBsa(File file)
	{
		super(SIG.BSA, file);
	}

	public int getFileFlags()
	{
		return fileFlags;
	}

	/**
	 * CAUTION Super HEAVY WEIGHT!!
	 * @return
	 */
	public List<ArchiveEntry> getEntries()
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

	protected void loadFolder(Folder folder) throws IOException
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

				if (entryFileName == null)
					System.out.println("entry of null with hash of " + fileHash);

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

	public void load() throws DBException, IOException
	{
		//TODO: support large files with 2 maps
		if (file.length() < Integer.MAX_VALUE)
		{
			in = new MappedByteBufferRAF(file, "r");
		}
		else
		{
			in = new RandomAccessFile(file, "r");
		}

		// lock just in case anyone else tries an early read
		synchronized (in)
		{
			//load header
			byte header[] = new byte[36];

			int count = in.read(header);
			if (count != 36)
				throw new EOFException("Archive header is incomplete " + file.getAbsolutePath());

			String id = new String(header, 0, 4);

			if (!id.equals("BSA\0"))
				throw new DBException("Archive id is bad " + id + " " + file.getAbsolutePath());

			version = getInteger(header, 4);
			if (version != 104 && version != 103)
				throw new DBException("BSA version " + version + " is not supported " + file.getAbsolutePath());

			long folderOffset = getInteger(header, 8) & 0xffffffffL;
			archiveFlags = getInteger(header, 12);
			folderCount = getInteger(header, 16);
			fileCount = getInteger(header, 20);
			int folderNamesLength = getInteger(header, 24);
			int fileNamesLength = getInteger(header, 28);
			fileFlags = getInteger(header, 32);
			//end of load header

			if ((archiveFlags & 3) != 3)
				throw new DBException("Archive does not use directory/file names " + file.getAbsolutePath());

			isCompressed = (archiveFlags & 4) != 0;//WTF is the difference?
			defaultCompressed = (archiveFlags & 0x100) != 0;

			//load fileNameBlock
			byte[] nameBuffer = new byte[fileNamesLength];
			long nameOffset = folderOffset + (folderCount * 16) + (fileCount * 16) + (folderCount + folderNamesLength);
			in.seek(nameOffset);

			count = in.read(nameBuffer);

			if (count != fileNamesLength)
				throw new EOFException("File names buffer is incomplete " + file.getAbsolutePath());

			String[] fileNames = new String[fileCount];

			filenameHashToFileNameMap = new HashMap<Long, String>(fileCount);

			int bufferIndex = 0;
			for (int nameIndex = 0; nameIndex < fileCount; nameIndex++)
			{
				int startIndex = bufferIndex;
				// search through for the end of the filename
				for (; bufferIndex < fileNamesLength && nameBuffer[bufferIndex] != 0; bufferIndex++)
				{
					;
				}

				if (bufferIndex >= fileNamesLength)
					throw new DBException("File names buffer truncated " + file.getAbsolutePath());

				String filename = new String(nameBuffer, startIndex, bufferIndex - startIndex);

				fileNames[nameIndex] = filename;
				//these must be loaded and hashed now as the folder only has the hash values in it
				filenameHashToFileNameMap.put(new HashCode(filename, false).getHash(), filename);

				hasKTXFiles = hasKTXFiles || filename.endsWith("ktx");

				hasASTCFiles = hasASTCFiles || filename.endsWith("astc");

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
					throw new EOFException("Folder record is incomplete " + file.getAbsolutePath());

				folderOffset += 16L; //set pointer ready for next folderIndex for loop

				// get the folder record data out fo the buffer read above
				long folderHash = getLong(buffer, 0);
				int folderFileCount = getInteger(buffer, 8);
				long fileOffset = (getInteger(buffer, 12) - fileNamesLength) & 0xffffffffL;

				folderHashToFolderMap.put(folderHash, new Folder(folderFileCount, fileOffset));

			}

		}

	}

	public boolean hasNifOrKf()
	{
		return (fileFlags & 1) != 0 || (fileFlags & 0x40) != 0;
	}

	public boolean hasDDS()
	{
		return (fileFlags & 2) != 0;
	}

	public boolean hasKTX()
	{
		return hasKTXFiles;
	}

	public boolean hasASTC()
	{
		return hasASTCFiles;
	}

	public boolean hasSounds()
	{
		return (fileFlags & 8) != 0 || (fileFlags & 0x10) != 0;
	}

}