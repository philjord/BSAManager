// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveEntry.java

package archive;

// Referenced classes of package FO3Archive:
//            HashCode

public class ArchiveEntry implements Comparable<ArchiveEntry>
{
	private ArchiveFile archiveFile;

	private int archiveIdentifier;

	private String folderName;//do I need this? only wanted by the display gear

	private HashCode folderHashCode; 

	private String fileName;//do I need this? only wanted by the display gear

	private HashCode fileHashCode;

	private String entryName;

	private long fileOffset;

	private int fileLength;

	private int compressedLength;

	private boolean isCompressed;

	public ArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName)
	{
		this.archiveFile = archiveFile;
		if (folderName == null || fileName == null)
		{
			throw new IllegalArgumentException("Folder name or file name is null " + folderName + " : " + fileName);
		}
		else if (folderName.length() > 254)
		{
			throw new IllegalArgumentException("Folder name is longer than 254 characters");
		}
		else if (fileName.length() > 254)
		{
			throw new IllegalArgumentException("File name is longer than 254 characters");
		}
		else
		{
			this.folderName = folderName;
			this.fileName = fileName;
			entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
			folderHashCode = new HashCode(folderName, true);
			fileHashCode = new HashCode(fileName, false);
		}
	}

	public String getName()
	{
		return entryName;
	}

	public String getFolderName()
	{
		return folderName;
	}

	public void setFolderName(String name)
	{
		if (name.length() > 254)
		{
			throw new IllegalArgumentException("Folder name is longer than 254 characters");
		}
		else
		{
			folderName = name;
			folderHashCode = new HashCode(folderName, true);
			entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
			return;
		}
	}

	public HashCode getFolderHashCode()
	{
		return folderHashCode;
	}

	public String getFileName()
	{
		return fileName;
	}

	public void setFileName(String name)
	{
		if (name.length() > 254)
		{
			throw new IllegalArgumentException("File name is longer than 254 characters");
		}
		else
		{
			fileName = name;
			fileHashCode = new HashCode(fileName, false);
			entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
			return;
		}
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

	public int getIdentifier()
	{
		return archiveIdentifier;// this is the hash of the archive file, not really important, just a double check
	}

	public void setIdentifier(int identifier)
	{
		archiveIdentifier = identifier;
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
		return entryName;
	}

	public ArchiveFile getArchiveFile()
	{
		return archiveFile;
	}

}