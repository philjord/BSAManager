package bsaio.displayables;

import bsaio.ArchiveFile;
import bsaio.ArchiveEntry.HashFormat;

public interface Displayable
{

	String getName();

	String getFolderName();

	String getFileName();
	
	ArchiveFile getArchiveFile();

	
	/**
	 * For use with texture conversion mainly, I can't see any other use for it
	 */
	void setFileName(String fileName, HashFormat hf); 
	
}
