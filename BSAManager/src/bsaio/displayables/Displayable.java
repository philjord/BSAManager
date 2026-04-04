package bsaio.displayables;

import bsaio.ArchiveFile;
import bsaio.ArchiveEntry.HashFormat;

public interface Displayable
{

	String getName();

	String getFolderName();

	String getFileName();
	
	ArchiveFile getArchiveFile();

	void setFileName(String fileName, HashFormat hf); 

}
