package bsaio.displayables;

import bsaio.ArchiveFile;

public interface Displayable
{

	String getName();

	String getFolderName();

	String getFileName();
	
	ArchiveFile getArchiveFile();

	void setFileName(String fileName);

}
