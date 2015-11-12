// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveInputStream.java

package archive.btdx;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

import tools.io.FastByteArrayInputStream;
import archive.ArchiveEntry;

import com.jcraft.jzlib.Inflater;

/**
 * @author philip
 *
 */
public class ArchiveInputStreamDX10 extends FastByteArrayInputStream
{
	
	DDSImage ddsImage;// in order to put a decent dds texture into the return filestream
	
	public ArchiveInputStreamDX10(RandomAccessFile in, ArchiveEntry entry) throws IOException
	{
		super(new byte[0]);//reset below once data is availble

		byte[] dataBufferOut = new byte[entry.getFileLength()];

		//the deflate doesn't accept a bytebuffer
		boolean isCompressed = entry.isCompressed();
		if (isCompressed && entry.getFileLength() > 0)
		{
			// entry size for buffer
			int compressedLength = entry.getCompressedLength();
			byte[] dataBufferIn = new byte[compressedLength];

			synchronized (in)
			{
				in.seek(entry.getFileOffset());
				int c = in.read(dataBufferIn, 0, compressedLength);
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
			}

			Inflater inflater = new Inflater();
			inflater.setInput(dataBufferIn);
			inflater.setOutput(dataBufferOut);
			inflater.inflate(4);//Z_FINISH
			inflater.end();
		}
		else
		{
			synchronized (in)
			{
				in.seek(entry.getFileOffset());
				int c = in.read(dataBufferOut, 0, entry.getFileLength());
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
			}
		}

		this.buf = dataBufferOut;
		this.pos = 0;
		this.count = buf.length;
	}

	/*void Archive::Extract_DX10(const char * dstRoot)
{
	for(UInt32 i = 0; i < m_textures.size(); i++)
	{
		Texture * tex = &m_textures[i];

		std::string dstPath = dstRoot;
		dstPath += "\\";
		dstPath += m_names[i];

		IFileStream::MakeAllDirs(dstPath.c_str());

		IFileStream dst;
		if(dst.Create(dstPath.c_str()))
		{
			_DMESSAGE("%08X: %08X %.4s %08X %02X %02X %04X %04X %04X %02X %02X %04X", i,
				tex->hdr.nameHash, tex->hdr.ext, tex->hdr.dirHash,
				tex->hdr.unk0C, tex->hdr.numChunks, tex->hdr.chunkHdrLen,
				tex->hdr.width, tex->hdr.height, tex->hdr.numMips, tex->hdr.format, tex->hdr.unk16);

			DDS_HEADER ddsHeader = { 0 };

			ddsHeader.dwSize = sizeof(ddsHeader);
			ddsHeader.dwHeaderFlags = DDS_HEADER_FLAGS_TEXTURE | DDS_HEADER_FLAGS_LINEARSIZE | DDS_HEADER_FLAGS_MIPMAP;
			ddsHeader.dwHeight = tex->hdr.height;
			ddsHeader.dwWidth = tex->hdr.width;
			ddsHeader.dwMipMapCount = tex->hdr.numMips;
			ddsHeader.ddspf.dwSize = sizeof(DDS_PIXELFORMAT);
			ddsHeader.dwSurfaceFlags = DDS_SURFACE_FLAGS_TEXTURE | DDS_SURFACE_FLAGS_MIPMAP;

			bool ok = true;

			switch(tex->hdr.format)
			{
			case DXGI_FORMAT_BC1_UNORM:
				ddsHeader.ddspf.dwFlags = DDS_FOURCC;
				ddsHeader.ddspf.dwFourCC = MAKEFOURCC('D', 'X', 'T', '1');
				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height / 2;	// 4bpp
				break;

			case DXGI_FORMAT_BC2_UNORM:
				ddsHeader.ddspf.dwFlags = DDS_FOURCC;
				ddsHeader.ddspf.dwFourCC = MAKEFOURCC('D', 'X', 'T', '3');
				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height;	// 8bpp
				break;

			case DXGI_FORMAT_BC3_UNORM:
				ddsHeader.ddspf.dwFlags = DDS_FOURCC;
				ddsHeader.ddspf.dwFourCC = MAKEFOURCC('D', 'X', 'T', '5');
				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height;	// 8bpp
				break;

			case DXGI_FORMAT_BC5_UNORM:
				ddsHeader.ddspf.dwFlags = DDS_FOURCC;
				if(m_useATIFourCC)
					ddsHeader.ddspf.dwFourCC = MAKEFOURCC('A', 'T', 'I', '2');	// this is more correct but the only thing I have found that supports it is the nvidia photoshop plugin
				else
					ddsHeader.ddspf.dwFourCC = MAKEFOURCC('D', 'X', 'T', '5');

				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height;	// 8bpp
				break;

			case DXGI_FORMAT_BC7_UNORM:
				// totally wrong but not worth writing out the DX10 header
				ddsHeader.ddspf.dwFlags = DDS_FOURCC;
				ddsHeader.ddspf.dwFourCC = MAKEFOURCC('B', 'C', '7', '\0');
				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height;	// 8bpp
				break;

			case DXGI_FORMAT_B8G8R8A8_UNORM:
				ddsHeader.ddspf.dwFlags = DDS_RGBA;
				ddsHeader.ddspf.dwRGBBitCount = 32;
				ddsHeader.ddspf.dwRBitMask =	0x00FF0000;
				ddsHeader.ddspf.dwGBitMask =	0x0000FF00;
				ddsHeader.ddspf.dwBBitMask =	0x000000FF;
				ddsHeader.ddspf.dwABitMask =	0xFF000000;
				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height * 4;	// 32bpp
				break;

			case DXGI_FORMAT_R8_UNORM:
				ddsHeader.ddspf.dwFlags = DDS_RGB;
				ddsHeader.ddspf.dwRGBBitCount = 8;
				ddsHeader.ddspf.dwRBitMask =	0xFF;
				ddsHeader.dwPitchOrLinearSize = tex->hdr.width * tex->hdr.height;	// 8bpp
				break;

			default:
				_ERROR("unhandled format %02X (%d) (%s)", tex->hdr.format, tex->hdr.format, dstPath.c_str());
				ok = false;
				break;
			}

			if(ok)
			{
				dst.Write32(DDS_MAGIC);	// 'DDS '
				dst.WriteBuf(&ddsHeader, sizeof(ddsHeader));

				gLog.Indent();

				for(UInt32 j = 0; j < tex->chunks.size(); j++)
				{
					DX10Chunk * chunk = &tex->chunks[j];

					_DMESSAGE("%016I64X %08X %08X %04X %04X %08X",
						chunk->offset, chunk->packedLen, chunk->unpackedLen,
						chunk->startMip, chunk->endMip, chunk->unk14);

					UInt8 * srcBuf = new UInt8[chunk->packedLen];

					m_src->SetOffset(chunk->offset);
					m_src->ReadBuf(srcBuf, chunk->packedLen);

					UInt8 * dstBuf = new UInt8[chunk->unpackedLen];

					UInt32 bytesWritten = chunk->unpackedLen;
					int decErr = uncompress(dstBuf, &bytesWritten, srcBuf, chunk->packedLen);
					ASSERT(decErr == Z_OK);
					ASSERT(bytesWritten == chunk->unpackedLen);

					dst.WriteBuf(dstBuf, chunk->unpackedLen);

					delete [] dstBuf;
					delete [] srcBuf;
				}

				gLog.Outdent();
			}
		}
	}*/

}