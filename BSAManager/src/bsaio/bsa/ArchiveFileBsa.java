package bsaio.bsa;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.frostwire.util.LongSparseArray;

import bsaio.ArchiveEntry;
import bsaio.ArchiveFile;
import bsaio.DBException;
import bsaio.HashCode;
import bsaio.displayables.DisplayableArchiveEntry;
import tools.io.FileChannelRAF;

public class ArchiveFileBsa extends ArchiveFile {

	//Only created when isForDisplay is loaded
	private LongSparseArray<String>	filenameHashToFileNameMap;

	private int						archiveFlags;				//in BSA id

	private int						fileFlags;					//in BSA id

	private boolean					isCompressed;

	private boolean					defaultCompressed;

	public ArchiveFileBsa(boolean isForDisplay, FileChannel file, String fileName) {
		super(isForDisplay, SIG.BSA, file, fileName);
	}

	public int getFileFlags() {
		return fileFlags;
	}

	/**
	 * CAUTION Super HEAVY WEIGHT!!
	 * @return
	 */
	@Override
	public List<ArchiveEntry> getEntries() {
		ArrayList<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		int filesToLoad = fileCount;
		int currentProgress = 0;
		try {
			for (int f = 0; f < folderHashToFolderMap.size(); f++) {
				Folder folder = folderHashToFolderMap.get(folderHashToFolderMap.keyAt(f));
				if (folder.fileToHashMap == null) {
					ensureLoaded(folder);
				}
				for (int i = 0; i < folder.fileToHashMap.size(); i++)
					ret.add(folder.fileToHashMap.get(folder.fileToHashMap.keyAt(i)));

				filesToLoad -= folder.folderFileCount;
				int newProgress = (filesToLoad * 100) / fileCount;
				if (newProgress >= currentProgress + 5) {
					currentProgress = newProgress;

				}
			}

		} catch (IOException e) {
			System.out.println("ArchiveFile Exception for filename:  " + e + " " + e.getStackTrace()[0]);
		}

		return ret;

	}

	@Override
	public ArchiveEntry getEntry(String fullFileName) {
		fullFileName = fullFileName.toLowerCase();
		fullFileName = fullFileName.trim();
		if (fullFileName.indexOf("/") != -1) {
			StringBuilder buildName = new StringBuilder(fullFileName);
			int sep;
			while ((sep = buildName.indexOf("/")) >= 0) {
				buildName.replace(sep, sep + 1, "\\");
			}
			fullFileName = buildName.toString();
		}
		try {

			int pathSep = fullFileName.lastIndexOf("\\");
			String folderName = fullFileName.substring(0, pathSep);
			long folderHash = HashCode.hashCode(folderName, true);
			Folder folder = folderHashToFolderMap.get(folderHash);

			if (folder != null) {
				ensureLoaded(folder);
				String fileName = fullFileName.substring(pathSep + 1);
				long fileHashCode = HashCode.hashCode(fileName, false);
				return folder.fileToHashMap.get(fileHashCode);
			}
		} catch (IOException e) {
			System.out.println(
					"ArchiveFile Exception for filename:  " + fullFileName + " " + e + " " + e.getStackTrace()[0]);
		}

		return null;
	}

	/**
	 * this will deal with sync and single load
	 */
	protected void ensureLoaded(Folder folder) throws IOException {
		// Don't reload! 
		if (folder.fileToHashMap == null) {
			// don't let people get entries until we've finished loading thanks.
			synchronized (folder) {
				FileChannel ch = in.getChannel();
				folder.fileToHashMap = new LongSparseArray<ArchiveEntry>(folder.folderFileCount);

				byte[] len = new byte[1];
				byte[] name = new byte[256];

				// now go and read the folders name and then do it's files
				long pos = folder.offset;

				// read off the folder name. 
				int count = ch.read(ByteBuffer.wrap(len), pos);
				pos += 1;
				int length = len[0] & 0xff;

				count = ch.read(ByteBuffer.wrap(name, 0, length), pos);
				pos += length;
				if (count != length)
					throw new EOFException("Folder name is incomplete");
				String folderName = new String(name, 0, length - 1);// last null isn't sexy

				byte[] buffer = new byte[16];

				for (int fileIndex = 0; fileIndex < folder.folderFileCount; fileIndex++) {
					// pull data in a buffer for reading
					count = ch.read(ByteBuffer.wrap(buffer), pos);
					pos += buffer.length;
					if (count != 16)
						throw new EOFException("File record is incomplete");

					// get the file record data from the buffer read in above
					long fileHash = getLong(buffer, 0);
					int dataLength = getInteger(buffer, 8);
					long dataOffset = getInteger(buffer, 12) & 0xffffffffL;

					ArchiveEntry entry;
					if (this.isForDisplay) {
						String fileName = filenameHashToFileNameMap.get(fileHash);

						if (fileName == null)
							System.err.println("entry of null with hash of " + fileHash);

						entry = new DisplayableArchiveEntry(this, folderName, fileName);
					} else {
						entry = new ArchiveEntry(this, HashCode.hashCode(folderName, true), fileHash);
					}

					entry.setFileOffset(dataOffset);
					entry.setFileLength(dataLength);
					folder.fileToHashMap.put(fileHash, entry);

					// Note non display will need ensureReady called on the entries before use

					if (this.isForDisplay) {
						// display just wants it all ready now
						entry.ensureEntryReady(ch);
					}

				}
			}
		}

	}

	@Override
	public void load() throws DBException, IOException {
		in = new FileChannelRAF(file);
		FileChannel ch = in.getChannel();

		long pos = 0;
		//load header
		byte[] header = new byte[36];

		int count = ch.read(ByteBuffer.wrap(header), pos);
		pos += header.length;
		if (count != 36)
			throw new EOFException("Archive header is incomplete " + fileName);

		String id = new String(header, 0, 4);

		if (!id.equals("BSA\0"))
			throw new DBException("Archive id is bad " + id + " " + fileName);

		version = getInteger(header, 4);
		if (version != 104 && version != 103) {
			if (version == ArchiveFile.PARTIAL_FILE) {
				System.out.println(
						"BSA is in partial state, it is presumably being converted from DDS to KTX " + fileName);
			}
			throw new DBException("BSA version " + version + " is not supported " + fileName);
		}

		long folderOffset = getInteger(header, 8) & 0xffffffffL;
		archiveFlags = getInteger(header, 12);
		folderCount = getInteger(header, 16);
		fileCount = getInteger(header, 20);
		int folderNamesLength = getInteger(header, 24);
		int fileNamesLength = getInteger(header, 28);
		fileFlags = getInteger(header, 32);
		//end of load header

		if ((archiveFlags & 3) != 3)
			throw new DBException("Archive does not use directory/file names " + fileName);

		isCompressed = (archiveFlags & 4) != 0;
		defaultCompressed = (archiveFlags & 0x100) != 0;// note value=256 (this is hex not binary)

		folderHashToFolderMap = new LongSparseArray<Folder>(folderCount);

		byte buffer[] = new byte[16];
		pos = folderOffset;
		for (int folderIndex = 0; folderIndex < folderCount; folderIndex++) {
			// pull data in a buffer for reading
			count = ch.read(ByteBuffer.wrap(buffer), pos);
			if (count != 16)
				throw new EOFException("Folder record is incomplete " + fileName);

			pos += 16L; //set pointer ready for next folderIndex for loop

			// get the folder record data out off the buffer read above
			long folderHash = getLong(buffer, 0);
			int folderFileCount = getInteger(buffer, 8);
			long fileOffset = (getInteger(buffer, 12) - fileNamesLength) & 0xffffffffL;

			folderHashToFolderMap.put(folderHash, new Folder(folderFileCount, fileOffset));

		}

		if (isForDisplay) {
			//load fileNameBlock
			byte[] nameBuffer = new byte[fileNamesLength];
			long nameOffset = folderOffset + (folderCount * 16) + (fileCount * 16) + (folderCount + folderNamesLength);
			pos = nameOffset;

			count = ch.read(ByteBuffer.wrap(nameBuffer), pos);
			pos += nameBuffer.length;
			if (count != fileNamesLength)
				throw new EOFException("File names buffer is incomplete " + fileName);

			filenameHashToFileNameMap = new LongSparseArray<String>(fileCount);

			int bufferIndex = 0;
			for (int nameIndex = 0; nameIndex < fileCount; nameIndex++) {
				int startIndex = bufferIndex;
				// search through for the end of the filename
				for (; bufferIndex < fileNamesLength && nameBuffer[bufferIndex] != 0; bufferIndex++) {
					;
				}

				if (bufferIndex >= fileNamesLength)
					throw new DBException("File names buffer truncated " + fileName);

				String filename = new String(nameBuffer, startIndex, bufferIndex - startIndex);

				filenameHashToFileNameMap.put(HashCode.hashCode(filename, false), filename);

				bufferIndex++;
			}

			//output some interesting facts
			/*	System.out.println("archiveFlags for " + this.fileName + " " + this.archiveFlags);
				System.out.println("fileFlags    for " + this.fileName + " " + this.fileFlags);
			
				System.out.println("hasNifOrKf() " + hasNifOrKf());
				System.out.println("hasTextureFiles() " + hasTextureFiles());
				System.out.println("hasDDS() " + hasDDS());
				System.out.println("hasKTX( " + hasKTX());
				System.out.println("hasSounds() " + hasSounds());
				System.out.println("hasMaterials() " + hasMaterials());*/
		}

	}

	@Override
	public boolean hasNifOrKf() {
		return (fileFlags & 1) != 0 || (fileFlags & 0x40) != 0;
	}

	@Override
	public boolean hasTextureFiles() {
		return (fileFlags & 2) != 0;
	}

	@Override
	public boolean hasDDS() {
		return hasTextureFiles() && !this.fileName.endsWith("_ktx.bsa");
	}

	@Override
	public boolean hasKTX() {
		return hasTextureFiles() && this.fileName.endsWith("_ktx.bsa");
	}

	@Override
	public boolean hasSounds() {
		return (fileFlags & 8) != 0 || (fileFlags & 0x10) != 0;
	}

	@Override
	public boolean hasMaterials() {
		return true;
	}

	public boolean isCompressed() {
		return isCompressed;
	}

	public boolean isDefaultCompressed() {
		return defaultCompressed;
	}
}