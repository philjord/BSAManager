package archive.displayables;

import archive.ArchiveFile;
import archive.btdx.ArchiveEntryDX10;

public class DisplayableArchiveEntryDX10 extends ArchiveEntryDX10 implements Displayable
{
	private ArchiveFile archiveFile;

	private String folderName;

	private String entryName;

	public DisplayableArchiveEntryDX10(ArchiveFile archiveFile, String folderName, String fileName)
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