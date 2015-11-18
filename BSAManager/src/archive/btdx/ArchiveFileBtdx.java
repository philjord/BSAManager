package archive.btdx;

import gui.StatusDialog;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tools.io.MappedByteBufferRAF;
import archive.ArchiveEntry;
import archive.ArchiveFile;
import archive.ArchiveInputStream;
import archive.DBException;
import archive.HashCode;
import archive.btdx.ArchiveEntryDX10.DX10Chunk;

public class ArchiveFileBtdx extends ArchiveFile
{

	public enum BsaFileType
	{
		GNRL, DX10
	};

	private BsaFileType bsaFileType; // in BTDX id

	private Map<Long, String> filenameHashToFileNameMap;

	public ArchiveFileBtdx(File file)
	{
		super(SIG.BTDX, file);
	}

	/**
	 * CAUTION Super HEAVY WEIGHT!!
	 * 
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
			// do we need to load the files in this folder?
			if (folder.fileToHashMap == null)
			{
				System.out.println("BTDX folderName not indexed " + folderName);
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
		throw new UnsupportedOperationException("BTDX is loaded at intial load time, so this should never be called");
	}

	public InputStream getInputStream(ArchiveEntry entry) throws IOException
	{
		if (in == null)
			throw new IOException("Archive file is not open");
		else if (entry.getIdentifier() != hashCode())
			throw new IllegalArgumentException("Archive entry not valid for this archive");
		else
		{
			if (bsaFileType == BsaFileType.DX10)
			{
				return new ArchiveInputStreamDX10(in, entry);
			}
			else
			{
				return new ArchiveInputStream(in, entry);
			}
		}
	}

	public void load() throws DBException, IOException
	{
		if (file.length() > Integer.MAX_VALUE)
			in = new RandomAccessFile(file, "r");
		else
			in = new MappedByteBufferRAF(file, "r"); // we exceed an int!! new fallout4 gear!

		// lock just in case anyone else tries an early read
		synchronized (in)
		{
			// load header
			byte header[] = new byte[24];

			int count = in.read(header);
			if (count != 24)
				throw new EOFException("Archive header is incomplete");

			String id = new String(header, 0, 4);
			if (!id.equals("BTDX"))
				throw new DBException("Archive file is not BTDX id " + id + " " + file.getAbsolutePath());
			version = getInteger(header, 4);
			if (version != 1)
				throw new DBException("BSA version " + version + " is not supported " + file.getAbsolutePath());

			String type = new String(header, 8, 4); // GRNL or DX10
			if (type.equals("GNRL"))
				bsaFileType = BsaFileType.GNRL;
			else if (type.equals("DX10"))
				bsaFileType = BsaFileType.DX10;
			else
				throw new DBException("BSA bsaFileType " + type + " is not supported " + file.getAbsolutePath());

			fileCount = getInteger(header, 12);
			long nameTableOffset = getLong(header, 16);
			// end of header read 24 bytes long

			// now all the files header records exist in either General format or DX10 format

			// but we are going to jump to the name table (which is after th file records)
			in.seek(nameTableOffset);

			// ready
			String[] fileNames = new String[fileCount];

			// load fileNameBlock
			byte[] nameBuffer = new byte[0x10000];

			for (int i = 0; i < fileCount; i++)
			{
				byte[] b = new byte[2];
				in.read(b);
				int len = getShort(b, 0);

				in.read(nameBuffer, 0, len);
				nameBuffer[len] = 0;

				String filename = new String(nameBuffer, 0, len);
				fileNames[i] = filename;
			}

			// build up a trival folderhash from all the file names
			// and preload the archive entries from the data above
			// reset to below header
			in.seek(24);

			folderHashToFolderMap = new HashMap<Long, Folder>();
			filenameHashToFileNameMap = new HashMap<Long, String>(fileCount);
			for (int i = 0; i < fileCount; i++)
			{
				String fullFileName = fileNames[i].toLowerCase();
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

				if (bsaFileType == BsaFileType.GNRL)
				{
					ArchiveEntry entry = new ArchiveEntry(this, folder.folderName, fileName);

					byte buffer[] = new byte[36];
					in.read(buffer);

					// int nameHash = getInteger(buffer, 0);// 00 - name hash?
					// String ext = new String(buffer, 4, 4); // 04 - extension
					// int dirHash = getInteger(buffer, 8); // 08 - directory hash?
					// int unk0C = getInteger(buffer, 12); // 0C - flags? 00100100
					long offset = getLong(buffer, 16); // 10 - relative to start of file
					int packedLen = getInteger(buffer, 24); // 18 - packed length (zlib)
					int unpackedLen = getInteger(buffer, 28); // 1C - unpacked length
					int unk20 = getInteger(buffer, 32); // 20 - BAADF00D

					entry.setIdentifier(hashCode());
					entry.setFileOffset(offset);
					entry.setFileLength(unpackedLen);
					entry.setCompressed(packedLen != 0 && packedLen != unpackedLen);

					int compLen = packedLen;
					if (compLen == 0)
						compLen = unk20; // what

					entry.setCompressedLength(compLen);
					folder.fileToHashMap.put(fileHashCode, entry);
					folder.folderFileCount++;
				}
				else
				{
					ArchiveEntryDX10 entry = new ArchiveEntryDX10(this, folder.folderName, fileName);

					byte[] buffer = new byte[24];
					in.read(buffer);
					// int nameHash = getInteger(buffer, 0);// 00 - name hash?
					// String ext = new String(buffer, 4, 4); // 04 - extension
					// int dirHash = getInteger(buffer, 8); // 08 - directory hash?
					// int unk0C= buffer[12]& 0xff; //
					entry.numChunks = buffer[13] & 0xff; //
					entry.chunkHdrLen = getShort(buffer, 14); // - size of one chunk header
					entry.width = getShort(buffer, 16); //
					entry.height = getShort(buffer, 18); //
					entry.numMips = buffer[20] & 0xff; //
					entry.format = buffer[21] & 0xff; // - DXGI_FORMAT
					entry.unk16 = getShort(buffer, 22); // - 0800

					if (entry.numChunks != 0)
					{
						entry.chunks = new DX10Chunk[entry.numChunks];

						for (int c = 0; c < entry.numChunks; c++)
						{
							in.read(buffer);
							entry.chunks[c] = new DX10Chunk();
							entry.chunks[c].offset = getLong(buffer, 0); // 00
							entry.chunks[c].packedLen = getInteger(buffer, 8); // 08
							entry.chunks[c].unpackedLen = getInteger(buffer, 12); // 0C
							entry.chunks[c].startMip = getShort(buffer, 16); // 10
							entry.chunks[c].endMip = getShort(buffer, 18); // 12
							entry.chunks[c].unk14 = getInteger(buffer, 20); // 14 - BAADFOOD
						}
					}

					entry.setIdentifier(hashCode());

					folder.fileToHashMap.put(fileHashCode, entry);
					folder.folderFileCount++;

				}
			}

		}

	}

	public boolean hasNifOrKf()
	{
		return bsaFileType == BsaFileType.GNRL;
	}

	public boolean hasDDS()
	{
		return bsaFileType == BsaFileType.DX10;
	}

	public boolean hasSounds()
	{
		return bsaFileType == BsaFileType.GNRL;
	}

}