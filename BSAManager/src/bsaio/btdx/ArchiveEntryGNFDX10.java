package bsaio.btdx;

import bsaio.ArchiveEntry;
import bsaio.ArchiveFile;
import bsaio.btdx.ArchiveEntryDX10.DX10Chunk;

public class ArchiveEntryGNFDX10 extends ArchiveEntry {
	public int			numChunks;

	public int			chunkHdrLen;	//  - size of one chunk header

	public int			format;			//  - DXGI_FORMAT	

	public int			numFormat;

	public int			width;

	public int			height;

	public long			offset;

	public int			size;

	public int			realSize;

	public int			unk2;

	public int			align;

	public DX10Chunk[]	chunks;

	public ArchiveEntryGNFDX10(ArchiveFile archiveFile, long folderHash, long fileHash) {
		super(archiveFile, folderHash, fileHash);
	}
	/**
	 * For Display only
	 * @param archiveFile
	 * @param folderName
	 * @param fileName
	 */
	public ArchiveEntryGNFDX10(ArchiveFile archiveFile, String folderName, String fileName) {
		super(archiveFile, folderName, fileName);
	}
	//https://github.com/jonwd7/nifskope/commit/2680d9fc33aba8aa300faa850d65b0a3b36eca4a

}