package bsaio.btdx;

import bsaio.ArchiveEntry;
import bsaio.ArchiveFile;

public class ArchiveEntryDX10 extends ArchiveEntry
{
	public int numChunks; // 

	public int chunkHdrLen; //  - size of one chunk header

	public int height; // 
	
	public int width; // 

	public int numMips; // 

	public int format; //  - DXGI_FORMAT

	public int unk16; //  - 0800

	public DX10Chunk[] chunks;

	public static class DX10Chunk
	{
		public long offset; // 00

		public int packedLen; // 08

		public int unpackedLen; // 0C

		public int startMip; // 10

		public int endMip; // 12

		public int unk14; // 14 - BAADFOOD
	};

	public ArchiveEntryDX10(ArchiveFile archiveFile, String folderName, String fileName)
	{
		super(archiveFile, folderName, fileName);
	}
	//https://github.com/jonwd7/nifskope/commit/2680d9fc33aba8aa300faa850d65b0a3b36eca4a

}