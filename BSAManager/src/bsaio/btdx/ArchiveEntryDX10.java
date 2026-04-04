package bsaio.btdx;

import bsaio.ArchiveEntry;
import bsaio.ArchiveFile;

public class ArchiveEntryDX10 extends ArchiveEntry {
	public int			numChunks;		// 

	public int			chunkHdrLen;	//  - size of one chunk header

	public int			height;			// 

	public int			width;			// 

	public int			numMips;		// 

	public int			format;			//  - DXGI_FORMAT

	public int			isCubemap;

	public int			tileMode;

	public DX10Chunk[]	chunks;

	public static class DX10Chunk {
		public long	offset;			// 00

		public int	packedLen;		// 08

		public int	unpackedLen;	// 0C

		public int	startMip;		// 10

		public int	endMip;			// 12

		public int	align;			// 14 

		@Override
		public String toString() {
			return "DX10Chunk"	+ " off:" + offset + " packed:" + packedLen + " unpacked:" + unpackedLen + "startMip:"
					+ startMip + "endMip:" + endMip + "align:" + align;
		}
	};

	public ArchiveEntryDX10(ArchiveFile archiveFile, long folderHash, long fileHash) {
		super(archiveFile, folderHash, fileHash);
	}
	
	/**
	 * For Display only
	 * @param archiveFile
	 * @param folderName
	 * @param fileName
	 */
	public ArchiveEntryDX10(ArchiveFile archiveFile, String folderName, String fileName, HashFormat hf) {
		super(archiveFile, folderName, fileName, hf);
	}
	//https://github.com/jonwd7/nifskope/commit/2680d9fc33aba8aa300faa850d65b0a3b36eca4a
	
	 

}