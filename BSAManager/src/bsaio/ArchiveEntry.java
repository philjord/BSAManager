package bsaio;

public class ArchiveEntry implements Comparable<ArchiveEntry>
{
	//TODO: this should be in the Displayable version
	// only needed in order to   getFilesInFolder(String folderName)  from MeshSource interface
	// however I can't easily reload Ba2 and tes3 archives by folder so it's here for now
	protected String fileName;

	private HashCode folderHashCode;

	private HashCode fileHashCode;

	private long fileOffset;

	private int fileLength;

	private int compressedLength;

	private boolean isCompressed;

	public ArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName)
	{
		if (folderName == null || fileName == null)
		{
			throw new IllegalArgumentException("Folder name or file name is null " + folderName + " : " + fileName);
		}
		else if (folderName.length() > 254)
		{
			throw new IllegalArgumentException("Folder name is longer than 254 characters " + folderName);
		}
		else if (fileName.length() > 254)
		{
			throw new IllegalArgumentException("File name is longer than 254 characters " + fileName);
		}

		this.fileName = fileName;
		folderHashCode = new HashCode(folderName, true);
		fileHashCode = new HashCode(fileName, false);
	}

	public void setFolderName(String folderName)
	{
		if (folderName.length() > 254)
		{
			throw new IllegalArgumentException("Folder name is longer than 254 characters " + folderName);
		}

		folderHashCode = new HashCode(folderName, true);
	}

	public HashCode getFolderHashCode()
	{
		return folderHashCode;
	}

	public String getFileName()
	{
		return fileName;
	}

	public void setFileName(String fileName)
	{
		if (fileName.length() > 254)
		{
			throw new IllegalArgumentException("File name is longer than 254 characters " + fileName);
		}
		this.fileName = fileName;
		fileHashCode = new HashCode(fileName, false);

	}

	public HashCode getFileHashCode()
	{
		return fileHashCode;
	}

	public long getFileOffset()
	{
		return fileOffset;
	}

	public void setFileOffset(long offset)
	{
		fileOffset = offset;
	}

	public int getFileLength()
	{
		return fileLength;
	}

	public void setFileLength(int length)
	{
		fileLength = length;
	}

	public boolean isCompressed()
	{
		return isCompressed;
	}

	public void setCompressed(boolean isCompressed)
	{
		this.isCompressed = isCompressed;
	}

	public int getCompressedLength()
	{
		return compressedLength;
	}

	public void setCompressedLength(int length)
	{
		compressedLength = length;
	}

	public boolean equals(Object obj)
	{
		boolean equal = false;
		if (obj != null && (obj instanceof ArchiveEntry))
		{
			ArchiveEntry compare = (ArchiveEntry) obj;
			if (folderHashCode.equals(compare.getFolderHashCode()) && fileHashCode.equals(compare.getFileHashCode()))
				equal = true;
		}
		return equal;
	}

	public int compareTo(ArchiveEntry obj)
	{
		int diff = folderHashCode.compareTo(obj.getFolderHashCode());
		if (diff == 0)
			diff = fileHashCode.compareTo(obj.getFileHashCode());
		return diff;
	}

	public String toString()
	{
		return "ArchiveEntry " + folderHashCode + " " + fileHashCode;
	}

}