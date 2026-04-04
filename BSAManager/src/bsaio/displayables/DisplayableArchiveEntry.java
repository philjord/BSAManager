package bsaio.displayables;

import bsaio.ArchiveEntry;
import bsaio.ArchiveFile;

public class DisplayableArchiveEntry extends ArchiveEntry implements Displayable {

	protected String	fileName;

	private ArchiveFile	archiveFile;

	private String		folderName;

	private String		entryName;
	
	public DisplayableArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName) {
		this(archiveFile, folderName, fileName, HashFormat.OLD);		 
	}

	public DisplayableArchiveEntry(ArchiveFile archiveFile, String folderName, String fileName, HashFormat hf) {
		super(archiveFile, folderName, fileName, hf);
		this.archiveFile = archiveFile;
		this.folderName = folderName;
		this.fileName = fileName;
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
	}

	@Override
	public String getName() {
		return entryName;
	}

	@Override
	public String getFolderName() {
		return folderName;
	}

	@Override
	public void setFolderName(String folderName, HashFormat hf) {
		super.setFolderName(folderName, hf);
		this.folderName = folderName;
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();
	}

	@Override
	public String getFileName() {
		return fileName;
	}

	@Override
	public void setFileName(String fileName, HashFormat hf) {
		if (fileName.length() > 254) {
			throw new IllegalArgumentException("File name is longer than 254 characters " + fileName);
		}
		setFileHash(fileName, hf);
		this.fileName = fileName;
		entryName = (new StringBuilder()).append(folderName).append("\\").append(fileName).toString();

	}

	@Override
	public ArchiveFile getArchiveFile() {
		return archiveFile;
	}

	@Override
	public String toString() {
		return entryName;
	}

}