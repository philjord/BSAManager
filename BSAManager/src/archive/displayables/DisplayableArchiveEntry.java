package archive.displayables;

import archive.ArchiveEntry;
import archive.ArchiveFile;

public class DisplayableArchiveEntry extends ArchiveEntry implements Displayable
{
	private ArchiveFile archiveFile;

	private String folderName;

	private String entryName;

	public DisplayableArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName)
	{
		super(archiveFile, folderName, fileName);
		this.archiveFile = archiveFile;
		this.folderName = folderName;
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
	public void setFileName(String fileName)
	{
		super.setFileName(fileName);		
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();

	}

	public ArchiveFile getArchiveFile()
	{
		return archiveFile;
	}

	public String toString()
	{
		return entryName;
	}

}