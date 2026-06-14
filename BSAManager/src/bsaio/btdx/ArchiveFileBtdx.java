package bsaio.btdx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.frostwire.util.LongSparseArray;

import bsaio.ArchiveEntry;
import bsaio.ArchiveEntry.CompressionFormat;
import bsaio.ArchiveEntry.HashFormat;
import bsaio.ArchiveFile;
import bsaio.ArchiveInputStream;
import bsaio.DBException;
import bsaio.HashCode;
import bsaio.btdx.ArchiveEntryDX10.DX10Chunk;
import bsaio.displayables.DisplayableArchiveEntry;
import bsaio.displayables.DisplayableArchiveEntryDX10;
import bsaio.displayables.DisplayableArchiveEntryGNFDX10;
import tools.io.FileChannelRAF;

/**
 * 
 * good code here https://github.com/AlexxEG/BSA_Browser/
 * https://github.com/AlexxEG/BSA_Browser/tree/master/Sharp.BSA.BA2/BA2Util
 * 
 * 
 * https://github.com/Ryan-rsm-McKenzie/bsa/tree/master/src/bsa
 * 
 * https://github.com/miere43/ba2tools
 * 
 * this has a good bsa parsing code in it
 * https://github.com/fo76utils/fo76utils/blob/main/libfo76utils/src/ba2file.cpp#L16
 * //https://github.com/Ryan-rsm-McKenzie/bsa/blob/master/src/bsa/fo4.cpp version 3 can be zip or
 * https://github.com/yawkat/lz4-java
 */
public class ArchiveFileBtdx extends ArchiveFile {

	public enum BsaFileType {
		GNRL, // general files
		DX10, // DX10 file with 24 bytes of header and many 24 byte chunks
		GNMF // also a DX10 format with 72 bytes of header and 24 byte chunks
	};

	private BsaFileType			bsaFileType;			// in BTDX id

	private boolean				hasDDSFiles		= false;

	private boolean				hasKTXFiles		= false;

	private boolean				hasMaterials	= false;

	private boolean				hasMaterialCDB	= false;

	private int					Unknown1;

	private int					Unknown2;

	private int					Unknown3;

	private CompressionFormat	compressionType;

	public ArchiveFileBtdx(boolean isForDisplay, FileChannel file, String fileName) {
		super(isForDisplay, SIG.BTDX, file, fileName);
	}

	/**
	 * CAUTION Super HEAVY WEIGHT!!
	 * 
	 * @return
	 */
	@Override
	public List<ArchiveEntry> getEntries() {
		ArrayList<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		int filesToLoad = fileCount;
		int currentProgress = 0;

		for (int f = 0; f < folderHashToFolderMap.size(); f++) {
			Folder folder = folderHashToFolderMap.get(folderHashToFolderMap.keyAt(f));
			for (int i = 0; i < folder.fileToHashMap.size(); i++)
				ret.add(folder.fileToHashMap.get(folder.fileToHashMap.keyAt(i)));

			filesToLoad -= folder.folderFileCount;
			int newProgress = (filesToLoad * 100) / fileCount;
			if (newProgress >= currentProgress + 5) {
				currentProgress = newProgress;

			}
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

		int pathSep = fullFileName.lastIndexOf("\\");
		String folderName = fullFileName.substring(0, pathSep);
		long folderHash = HashCode.hashCodeCRC32(folderName, true);
		Folder folder = folderHashToFolderMap.get(folderHash);

		if (folder != null) {
			// do we need to load the files in this folder?
			if (folder.fileToHashMap == null) {
				System.out.println("BTDX folderName not indexed " + folderName);
				return null;
			}

			String fileName = fullFileName.substring(pathSep + 1);
			long fileHashCode = HashCode.hashCodeCRC32(fileName, false);
			return folder.fileToHashMap.get(fileHashCode);
		}

		return null;
	}

	@Override
	public InputStream getInputStream(ArchiveEntry entry) throws IOException {
		if (in == null) {
			throw new IOException("Archive file is not open");
		}

		if (bsaFileType == BsaFileType.DX10) {
			return new ArchiveInputStreamDX10(in, entry);
		} else if (bsaFileType == BsaFileType.GNMF) {
			throw new UnsupportedOperationException(
					"GNMF getInputStream not yet quite finished mip count is a problem");
		} else {
			return new ArchiveInputStream(in, entry);
		}
	}

	@Override
	public ByteBuffer getByteBuffer(ArchiveEntry entry) throws IOException {
		if (in == null) {
			throw new IOException("Archive file is not open");
		}

		if (bsaFileType == BsaFileType.DX10) {
			return ArchiveInputStreamDX10.getByteBuffer(in, entry);
		} else if (bsaFileType == BsaFileType.GNMF) {
			throw new UnsupportedOperationException("GNMF getByteBuffer not yet quite finished mip count is a problem");
		} else {
			return ArchiveInputStream.getByteBuffer(in, entry);
		}
	}

	 

	@Override
	public void load() throws DBException, IOException {
		in = new FileChannelRAF(file);// needed elsewhere
		FileChannel ch = file;

		long pos = 0;
		// load header id and version so we can get a header length sorted out
		byte[] header = new byte[8];
		ch.read(ByteBuffer.wrap(header), pos);

		String id = new String(header, 0, 4);
		if (!id.equals("BTDX"))
			throw new DBException("Archive file is not BTDX id " + id + " " + fileName);
		version = getInteger(header, 4);

		int headerLen = 24;
		if (version == 2)
			headerLen = 32;
		if (version == 3)
			headerLen = 36;

		//7 and 8 seem to go back to 24 bytes

		if (version > 1) {
			//examples
			//Newer version of btdx file! 2 Starfield - Terrain03.ba2
			// looks like only textures are version 3
			//Newer version of btdx file! 3 Starfield - Textures08.ba2
			//System.err.println("Newer version of btdx file! " + version + " " +this.fileName);
		}

		//reread the header again with the length in play
		header = new byte[headerLen];
		pos = 0;
		ch.read(ByteBuffer.wrap(header), pos);

		String type = new String(header, 8, 4); // GRNL, DX10, GNMF
		if (type.equals("GNRL")) {
			bsaFileType = BsaFileType.GNRL;
		} else if (type.equals("DX10")) {
			bsaFileType = BsaFileType.DX10;
		} else if (type.equals("GNMF")) {
			bsaFileType = BsaFileType.GNMF;
			System.err.println("BSA bsaFileType " + type + " is not (yet) supported " + fileName);
		} else
			throw new DBException("BSA bsaFileType " + type + " is not supported " + fileName);

		fileCount = getInteger(header, 12);
		long nameTableOffset = getLong(header, 16);
		// end of  24 bytes header read

		compressionType = CompressionFormat.ZIP;

		// 2 and 3 versions have a bit more header data
		if (version == 2) {
			Unknown1 = getInteger(header, 24);
			Unknown2 = getInteger(header, 28);
		} else if (version == 3) {
			Unknown1 = getInteger(header, 24);
			Unknown2 = getInteger(header, 28);
			Unknown3 = getInteger(header, 32);

			// If version is 3, then Unknown1 means which compression format is used. TODO: Consider renaming Unknown1
			compressionType = Unknown1 == 1 ? CompressionFormat.LZ4 : CompressionFormat.ZIP;
			//if(compressionType == CompressionFormat.LZ4)
			//	System.out.println("Archive has LZ4 compression!! " + this.fileName);
		}

		String[] fileNames = null;
		if (this.isForDisplay) {
			// we jump to the name table (which is after the file records)
			pos = nameTableOffset;

			// ready
			fileNames = new String[fileCount];

			// load fileNameBlock
			byte[] nameBuffer = new byte[0x10000];
			byte[] b = new byte[2];

			for (int i = 0; i < fileCount; i++) {
				ch.read(ByteBuffer.wrap(b), pos);
				pos += b.length;
				int len = getShort(b, 0);

				ch.read(ByteBuffer.wrap(nameBuffer, 0, len), pos);
				pos += len;
				nameBuffer[len] = 0;// null terminate (FIXME: why?)

				String filename = new String(nameBuffer, 0, len);
				fileNames[i] = filename;
			}

		}

		// reset pos to below header, ready to read the files (an by extension find the folders they are in)
		pos = headerLen;

		folderHashToFolderMap = new LongSparseArray<Folder>();

		// reuse same buffer declare here
		byte[] buffer = null;
		if (bsaFileType == BsaFileType.GNRL) {
			// we can read it all up front in this case
			buffer = new byte[fileCount * 36];
			ch.read(ByteBuffer.wrap(buffer), pos);
			pos += buffer.length;
		} else if (bsaFileType == BsaFileType.DX10) {
			buffer = new byte[24];
		} else if (bsaFileType == BsaFileType.GNMF) {
			buffer = new byte[72];
		}

		for (int i = 0; i < fileCount; i++) {

			Folder folder = null;

			// these 2 and folder above will  be filled now when if (this.isForDisplay || this.hasUndecipheredHash) {
			// is true so only use them in that case, folder gets set during file indexing below otherwise
			String fileName = null;
			String folderName = null;
			if (this.isForDisplay) {
				String fullFileName = fileNames[i].toLowerCase();
				fullFileName = fullFileName.trim();
				if (fullFileName.indexOf("/") != -1) {
					StringBuilder buildName = new StringBuilder(fullFileName);
					int sep;
					while ((sep = buildName.indexOf("/")) >= 0) {
						buildName.replace(sep, sep + 1, "\\");
					}
					fullFileName = buildName.toString();
				}

				int pathSep = fullFileName.lastIndexOf("\\");
				pathSep = pathSep == -1 ? 0 : pathSep;
				folderName = fullFileName.substring(0, pathSep);
				long folderHash = HashCode.hashCodeCRC32(folderName, true);
				folder = folderHashToFolderMap.get(folderHash);

				if (folder == null) {
					folder = new Folder(0, -1);
					folder.fileToHashMap = new LongSparseArray<ArchiveEntry>();
					folderHashToFolderMap.put(folderHash, folder);
				}

				fileName = fullFileName.substring(pathSep + 1); //NOTE no trim! sometimes leading space in name
			}

			long nameHash = -1;
			String extension = null;
			long dirHash = -1;
			if (bsaFileType == BsaFileType.GNRL) {

				nameHash = getUInteger(buffer, i * 36 + 0);
				extension = new String(buffer, i * 36 + 4, 4);
				dirHash = getUInteger(buffer, i * 36 + 8);

				ArchiveEntry entry;
				if (this.isForDisplay) {
					entry = new DisplayableArchiveEntry(this, folderName, fileName, HashFormat.CRC32);
					// this hashing is new so keep an eye on it
					if (dirHash != HashCode.hashCodeCRC32(folderName, true)) {
						System.out.println("hash incorrect dir " + folderName + " hash in archive " + dirHash + ", but crc32  "
								+ HashCode.hashCodeCRC32(folderName, true));
					}
					if( nameHash != HashCode.hashCodeCRC32(fileName, false)) {						
						System.out.println("hash incorrect fileName " + fileName + " hash in archive " + nameHash + ", but crc32  "
											+ HashCode.hashCodeCRC32(fileName, false) + " fullfilename is ["+fileNames[i]+"]");
					}
				} else {

					folder = folderHashToFolderMap.get(dirHash);

					if (folder == null) {
						folder = new Folder(0, -1);
						folder.fileToHashMap = new LongSparseArray<ArchiveEntry>();
						folderHashToFolderMap.put(dirHash, folder);
					}
					entry = new ArchiveEntry(this, dirHash, nameHash);
				}
				entry.setCompressionType(compressionType);

				//Archive Entry doesn't have flags and it's the header of the DX10 entries so need a general sub class
				int flags = getInteger(buffer, i * 36 + 12); // 0C - flags 00100100

				long offset = getLong(buffer, i * 36 + 16); // 10 - relative to start of file
				int packedLen = getInteger(buffer, i * 36 + 24); // 18 - packed length (zlib)
				int unpackedLen = getInteger(buffer, i * 36 + 28); // 1C - unpacked length
				int unk20 = getInteger(buffer, i * 36 + 32); // 20 - BAADF00D

				entry.setFileOffset(offset);
				entry.setFileLength(unpackedLen);
				entry.setCompressed(packedLen != 0 && packedLen != unpackedLen);

				int compLen = packedLen;
				if (compLen == 0)
					compLen = unk20; // what

				entry.setCompressedLength(compLen);
				folder.fileToHashMap.put(nameHash, entry);
				folder.folderFileCount++;
			} else if (bsaFileType == BsaFileType.DX10) {

				ch.read(ByteBuffer.wrap(buffer), pos);
				pos += buffer.length;

				nameHash = getUInteger(buffer, 0);
				extension = new String(buffer, 4, 4);
				dirHash = getUInteger(buffer, 8);

				ArchiveEntryDX10 entry;
				if (this.isForDisplay) {
					entry = new DisplayableArchiveEntryDX10(this, folderName, fileName, HashFormat.CRC32);
					// this hashing is new so keep an eye on it
					if (dirHash != HashCode.hashCodeCRC32(folderName, true)) {
						System.out.println("hash incorrect dir " + folderName + " hash in archive " + dirHash + ", but crc32  "
								+ HashCode.hashCodeCRC32(folderName, true));
					}
					if( nameHash != HashCode.hashCodeCRC32(fileName, false)) {						
						System.out.println("hash incorrect fileName " + fileName + " hash in archive " + nameHash + ", but crc32  "
											+ HashCode.hashCodeCRC32(fileName, false) + " fullfilename is ["+fileNames[i]+"]");
					}
				} else {
					folder = folderHashToFolderMap.get(dirHash);

					if (folder == null) {
						folder = new Folder(0, -1);
						folder.fileToHashMap = new LongSparseArray<ArchiveEntry>();
						folderHashToFolderMap.put(dirHash, folder);
					}
					entry = new ArchiveEntryDX10(this, dirHash, nameHash);
				}
				entry.setCompressionType(compressionType);

				//byte unk1 = buffer[12];

				entry.numChunks = buffer[13] & 0xff;
				entry.chunkHdrLen = getShort(buffer, 14); // - size of one chunk header
				entry.height = getShort(buffer, 16);
				entry.width = getShort(buffer, 18);
				entry.numMips = buffer[20] & 0xff;
				entry.format = buffer[21] & 0xff; // - DXGI_FORMAT
				entry.isCubemap = buffer[22] & 0xff;
				entry.tileMode = buffer[23] & 0xff;

				if (entry.numChunks != 0) {
					entry.chunks = new DX10Chunk[entry.numChunks];
					//read them all off at once
					byte[] chunkBuffer = new byte[entry.numChunks * 24];
					ch.read(ByteBuffer.wrap(chunkBuffer), pos);
					pos += chunkBuffer.length;
					for (int c = 0; c < entry.numChunks; c++) {
						entry.chunks[c] = new DX10Chunk();
						entry.chunks[c].offset = getLong(chunkBuffer, (c * 24) + 0); // 00
						entry.chunks[c].packedLen = getInteger(chunkBuffer, (c * 24) + 8); // 08
						entry.chunks[c].unpackedLen = getInteger(chunkBuffer, (c * 24) + 12); // 0C
						entry.chunks[c].startMip = getShort(chunkBuffer, (c * 24) + 16); // 10
						entry.chunks[c].endMip = getShort(chunkBuffer, (c * 24) + 18); // 12
						entry.chunks[c].align = getInteger(chunkBuffer, (c * 24) + 20); // 14 
					}
				}

				folder.fileToHashMap.put(nameHash, entry);
				folder.folderFileCount++;
			} else if (bsaFileType == BsaFileType.GNMF) {

				System.err.println("decoding GNMF now, is it any godd at all?");

				ch.read(ByteBuffer.wrap(buffer), pos);
				pos += buffer.length;

				nameHash = getInteger(buffer, 0);
				extension = new String(buffer, 4, 4);
				dirHash = getInteger(buffer, 8);

				//https://github.com/AlexxEG/BSA_Browser/blob/master/Sharp.BSA.BA2/BA2Util/BA2GNFEntry.cs

				ArchiveEntryGNFDX10 entry;
				if (this.isForDisplay) {
					entry = new DisplayableArchiveEntryGNFDX10(this, folderName, fileName, HashFormat.CRC32);
					// this hashing is new so keep an eye on it
					if (dirHash != HashCode.hashCodeCRC32(folderName, true)) {
						System.out.println("hash incorrect dir " + folderName + " hash in archive " + dirHash + ", but crc32  "
								+ HashCode.hashCodeCRC32(folderName, true));
					}
					if( nameHash != HashCode.hashCodeCRC32(fileName, false)) {						
						System.out.println("hash incorrect fileName " + fileName + " hash in archive " + nameHash + ", but crc32  "
											+ HashCode.hashCodeCRC32(fileName, false) + " fullfilename is ["+fileNames[i]+"]");
					}
				} else {
					folder = folderHashToFolderMap.get(dirHash);

					if (folder == null) {
						folder = new Folder(0, -1);
						folder.fileToHashMap = new LongSparseArray<ArchiveEntry>();
						folderHashToFolderMap.put(dirHash, folder);
					}
					entry = new ArchiveEntryGNFDX10(this, dirHash, nameHash);
				}
				entry.setCompressionType(compressionType);

				//byte unk1 = buffer[12];				
				entry.numChunks = buffer[13] & 0xff; //
				entry.chunkHdrLen = getShort(buffer, 14); // - size of one chunk header

				byte[] GNFHeader = new byte[32];
				System.arraycopy(buffer, 16, GNFHeader, 0, 32);

				int formatInfo = getInteger(GNFHeader, 4);
				entry.format = formatInfo >> 20 & ((1 << 6) - 1); // Skip first 20 bits then take 6 next bits
				entry.numFormat = formatInfo >> 26 & ((1 << 4) - 1); // Skip first 26 bits then take 4 next bits

				int IntFirst14BitMask = (1 << 14) - 1;
				int wh = getInteger(GNFHeader, 8);
				entry.width = (wh & IntFirst14BitMask) + 1; // Get first 14 bits
				entry.height = (wh >> 14 & IntFirst14BitMask) + 1; // Shifts past first 14 bits then get first 14 bits again

				entry.offset = getLong(buffer, 48);
				entry.size = getInteger(buffer, 56);
				entry.realSize = getInteger(buffer, 60);
				entry.unk2 = getInteger(buffer, 64);
				entry.align = getInteger(buffer, 68);

				if (entry.numChunks != 0) {
					entry.chunks = new DX10Chunk[entry.numChunks];
					//read them all off at once
					byte[] chunkBuffer = new byte[entry.numChunks * 24];
					ch.read(ByteBuffer.wrap(chunkBuffer), pos);
					pos += chunkBuffer.length;
					for (int c = 0; c < entry.numChunks; c++) {
						entry.chunks[c] = new DX10Chunk();
						entry.chunks[c].offset = getLong(chunkBuffer, (c * 24) + 0); // 00
						entry.chunks[c].packedLen = getInteger(chunkBuffer, (c * 24) + 8); // 08
						entry.chunks[c].unpackedLen = getInteger(chunkBuffer, (c * 24) + 12); // 0C
						entry.chunks[c].startMip = getShort(chunkBuffer, (c * 24) + 16); // 10
						entry.chunks[c].endMip = getShort(chunkBuffer, (c * 24) + 18); // 12
						entry.chunks[c].align = getInteger(chunkBuffer, (c * 24) + 20); // 14
					}
				}

				folder.fileToHashMap.put(nameHash, entry);
				folder.folderFileCount++;
			}

			// notice extension is 4 chars, 4th one is a 0 which is bad news for java strings
			hasDDSFiles = hasDDSFiles || extension.startsWith("dds");
			hasKTXFiles = hasKTXFiles || extension.startsWith("ktx");
			hasMaterials = hasMaterials || extension.startsWith("bgsm") || extension.startsWith("bgem");
			hasMaterialCDB = hasMaterialCDB || extension.startsWith("cdb");
		}
	}

	@Override
	public boolean hasNifOrKf() {
		return bsaFileType == BsaFileType.GNRL;
	}

	@Override
	public boolean hasTextureFiles() {
		return hasDDSFiles || hasKTXFiles;
	}

	@Override
	public boolean hasSounds() {
		return bsaFileType == BsaFileType.GNRL;
	}

	@Override
	public boolean hasDDS() {
		return hasDDSFiles;
	}

	@Override
	public boolean hasKTX() {
		return hasKTXFiles;
	}

	@Override
	public boolean hasMaterials() {
		return hasMaterials;
	}
		
	@Override
	public boolean hasMaterialCDB() {
		return hasMaterialCDB;
	}

}