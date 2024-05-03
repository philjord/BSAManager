package bsaio.displayables;

import bsaio.ArchiveEntry;
import bsaio.ArchiveFile;
import bsaio.HashCode;

public class DisplayableArchiveEntry extends ArchiveEntry implements Displayable
{
	
	protected String	fileName;
	
	private ArchiveFile archiveFile;

	private String folderName;

	private String entryName;

	public DisplayableArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName)
	{
		super(archiveFile, folderName, fileName);
		this.archiveFile = archiveFile;
		this.folderName = folderName;
		this.fileName = fileName;
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
	}

	@Override
	public String getName()
	{
		return entryName;
	}

	@Override
	public String getFolderName()
	{
		return folderName;
	}

	@Override
	public void setFolderName(String folderName)
	{
		super.setFolderName(folderName);
		this.folderName = folderName;
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
	}

	@Override
	public String getFileName() {
		return fileName;
	}

	@Override
	public void setFileName(String fileName)
	{
		if (fileName.length() > 254) {
			throw new IllegalArgumentException("File name is longer than 254 characters " + fileName);
		}
		fileHashCode = new HashCode(fileName, false);
		this.fileName = fileName;
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();

	}

	@Override
	public ArchiveFile getArchiveFile()
	{
		return archiveFile;
	}

	@Override
	public String toString()
	{
		return entryName;
	}

}