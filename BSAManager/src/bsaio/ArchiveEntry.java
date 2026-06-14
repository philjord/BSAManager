package bsaio;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import bsaio.bsa.ArchiveFileBsa;

public class ArchiveEntry implements Comparable<ArchiveEntry> {

	protected ArchiveFile	archiveFile;				
	
	protected long	folderHashCode;

	protected long	fileHashCode;

	private long	dataOffset;

	private int		dataLength;

	private int		compressedLength;

	private boolean	isCompressed = true;
	
	public enum CompressionFormat {
		ZIP, LZ4
	};
	
	public enum HashFormat {
		OLD, CRC32
	};
 

	private CompressionFormat compressionType = CompressionFormat.ZIP;

	/**
	 * This is the expensive way to load up entries
	 * @param archiveFile
	 * @param folderName
	 * @param fileName
	 */
	public ArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName, HashFormat hf) {
		if (folderName == null || fileName == null) {
			throw new IllegalArgumentException("Folder name or file name is null " + folderName + " : " + fileName);
		} else if (folderName.length() > 254) {
			throw new IllegalArgumentException("Folder name is longer than 254 characters " + folderName);
		} else if (fileName.length() > 254) {
			throw new IllegalArgumentException("File name is longer than 254 characters " + fileName);
		}
		
		setFolderHash(folderName, hf);
		setFileHash(fileName, hf);
		this.archiveFile = archiveFile;
	}

	
	public ArchiveEntry(ArchiveFile archiveFile, long folderHashCode, long fileHashCode) {
		this.folderHashCode = folderHashCode;
		this.fileHashCode = fileHashCode;
		this.archiveFile = archiveFile;
	}
	
	/**
	 * This allows Starfield archive to use hasCodeRCR32 instead of the older hash code
	 * @param hf 
	 */
	protected void setFolderHash(String folderName, HashFormat hf) {
		if (hf == HashFormat.OLD)
			folderHashCode = HashCode.hashCode(folderName, true);
		else
			folderHashCode = HashCode.hashCodeCRC32(folderName, true);
	}
	/**
	 * This allows Starfield archive to use hasCodeRCR32 instead of the older hash code
	 * @param hf 
	 */
	protected void setFileHash(String fileName, HashFormat hf) {
		if (hf == HashFormat.OLD)
			fileHashCode = HashCode.hashCode(fileName, false);
		else
			fileHashCode = HashCode.hashCodeCRC32(fileName, false);
	}
	
	/**
	 * For use with texture conversion mainly, I can't see any other use for it
	 */
	public void setFolderName(String folderName, HashFormat hf) {
		if (folderName.length() > 254) {
			throw new IllegalArgumentException("Folder name is longer than 254 characters " + folderName);
		}

		setFolderHash(folderName, hf);
	}

	public long getFolderHashCode() {
		return folderHashCode;
	}

	public long getFileHashCode() {
		return fileHashCode;
	}

	public long getFileOffset() {
		return dataOffset;
	}

	public void setFileOffset(long offset) {
		dataOffset = offset;
	}

	public int getFileLength() {
		return dataLength;
	}

	public void setFileLength(int length) {
		dataLength = length;		
	}

	public boolean isCompressed() {
		return isCompressed;
	}

	public void setCompressed(boolean isCompressed) {
		this.isCompressed = isCompressed;
		this.ready = true;
	}

	public int getCompressedLength() {
		return compressedLength;
	}

	public void setCompressedLength(int length) {
		compressedLength = length;
		this.ready = true;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && (obj instanceof ArchiveEntry)) {
			ArchiveEntry compare = (ArchiveEntry)obj;
			if (folderHashCode == compare.getFolderHashCode() && fileHashCode == compare.getFileHashCode())
				return true;
		}
		return false;
	}

	@Override
	public int compareTo(ArchiveEntry obj) {
		if (folderHashCode < obj.folderHashCode)
			return -1;
		else if (folderHashCode > obj.folderHashCode)
			return 1;
		else if (fileHashCode < obj.fileHashCode)
			return -1;
		else if (fileHashCode > obj.fileHashCode)
			return 1;
		else
			return 0;

	}

	@Override
	public String toString() {
		return "ArchiveEntry folderHash:" + folderHashCode + " fileHash:" + fileHashCode;
	}

	public CompressionFormat getCompressionType() {
		return compressionType;
	}

	public void setCompressionType(CompressionFormat compressionType) {
		this.compressionType = compressionType;
	}
	
	
	public boolean ready = false;
	
	public void ensureEntryReady(FileChannel ch) throws IOException {
		int version = archiveFile.getVersion();
		if(ready) {
			//FIXME: I'm gettign some double calls see ArchiveInputStream
			//System.out.println("aww hell nay double ready call!");
			return;
		}
		ready = true;
		
		

		byte[] buffer = new byte[16];
		int count;

			
		if (version == 103) {
			//TES4 - Oblivion

			boolean compressed = ((ArchiveFileBsa)archiveFile).isCompressed();
			
			//read off special inverted flag
			if ((dataLength & (1 << 30)) != 0) {
				dataLength ^= 1 << 30;
				compressed = !compressed;
			}

			int unCompressedLength = 0;
			if (compressed) {
				// data area start with uncompressed size then compressed data
				count = ch.read(ByteBuffer.wrap(buffer, 0, 4), dataOffset);
				if (count != 4)
					throw new EOFException("Compressed data is incomplete");
				unCompressedLength = ArchiveFile.getInteger(buffer, 0);

				dataOffset += 4L; // move past the uncompressed size into compressed data pointer
				dataLength -= 4; // compressed size has uncompressed size int taken off						
			}

			setFileOffset(dataOffset);
			setFileLength(unCompressedLength); //different to 104				
			setCompressed(compressed); //different to 104
			setCompressedLength(dataLength);//different to 104
		} else if (version == 104) {
			//FO3 - Fallout 3
			//TES5 - Skyrim
			
			byte[] len = new byte[1];
			int length;

			// go to data area and read sizes off now
			if (((ArchiveFileBsa)archiveFile).isDefaultCompressed()) {
				count = ch.read(ByteBuffer.wrap(len), dataOffset);
				length = (len[0] & 0xff) + 1;
				dataOffset += length;
				dataLength -= length;
			}

			//now do something a bit different if the other compressed flag is set
			int compressedLength = 0;
			boolean compressed = ((ArchiveFileBsa)archiveFile).isCompressed();
			if (compressed) {

				count = ch.read(ByteBuffer.wrap(buffer, 0, 4), dataOffset);
				if (count != 4)
					throw new EOFException("Compressed data is incomplete");

				dataOffset += 4L;// move past the uncompressed size into compressed data pointer
				compressedLength = dataLength - 4;// compressed size has uncompressed size int taken off		
				dataLength = ArchiveFile.getInteger(buffer, 0);
			}

			setFileOffset(dataOffset);
			setFileLength(dataLength);
			setCompressed(compressed);
			setCompressedLength(compressedLength);

		} else {
			throw new UnsupportedOperationException("BSA version " + version + " is not supported " + archiveFile.getName());
		}
	}

}