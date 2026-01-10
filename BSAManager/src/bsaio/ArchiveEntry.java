package bsaio;

public class ArchiveEntry implements Comparable<ArchiveEntry> {
	//TODO: this should be in the Displayable version
	// only needed in order to   getFilesInFolder(String folderName)  from MeshSource interface
	// however I can't easily reload Ba2 and tes3 archives by folder so it's here for now
	//	protected String	fileName;

	private long	folderHashCode;

	protected long	fileHashCode;

	private long	fileOffset;

	private int		fileLength;

	private int		compressedLength;

	private boolean	isCompressed;

	/**
	 * This is the expensive way to load up entries
	 * @param archiveFile
	 * @param folderName
	 * @param fileName
	 */
	public ArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName) {
		if (folderName == null || fileName == null) {
			throw new IllegalArgumentException("Folder name or file name is null " + folderName + " : " + fileName);
		} else if (folderName.length() > 254) {
			throw new IllegalArgumentException("Folder name is longer than 254 characters " + folderName);
		} else if (fileName.length() > 254) {
			throw new IllegalArgumentException("File name is longer than 254 characters " + fileName);
		}

		folderHashCode = HashCode.hashCode(folderName, true);
		fileHashCode = HashCode.hashCode(fileName, false);
	}

	public ArchiveEntry(ArchiveFile archiveFile, long folderHashCode, long fileHashCode) {
		this.folderHashCode = folderHashCode;
		this.fileHashCode = fileHashCode;
	}

	public void setFolderName(String folderName) {
		if (folderName.length() > 254) {
			throw new IllegalArgumentException("Folder name is longer than 254 characters " + folderName);
		}

		folderHashCode = HashCode.hashCode(folderName, true);
	}

	public long getFolderHashCode() {
		return folderHashCode;
	}

	public long getFileHashCode() {
		return fileHashCode;
	}

	public long getFileOffset() {
		return fileOffset;
	}

	public void setFileOffset(long offset) {
		fileOffset = offset;
	}

	public int getFileLength() {
		return fileLength;
	}

	public void setFileLength(int length) {
		fileLength = length;
	}

	public boolean isCompressed() {
		return isCompressed;
	}

	public void setCompressed(boolean isCompressed) {
		this.isCompressed = isCompressed;
	}

	public int getCompressedLength() {
		return compressedLength;
	}

	public void setCompressedLength(int length) {
		compressedLength = length;
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
		return "ArchiveEntry " + folderHashCode + " " + fileHashCode;
	}

}