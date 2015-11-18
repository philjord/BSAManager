// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveInputStream.java

package archive.btdx;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import tools.io.FastByteArrayInputStream;
import archive.ArchiveEntry;
import archive.btdx.ArchiveEntryDX10.DX10Chunk;
import archive.btdx.DDS_HEADER.DDS_PIXELFORMAT;

import com.jcraft.jzlib.Inflater;

/**
 * @author philip
 *
 */
public class ArchiveInputStreamDX10 extends FastByteArrayInputStream
{
	public static boolean m_useATIFourCC = false;

	/**
	 * Only ArchiveEntryDX10 accepted
	 * @param in
	 * @param entry
	 * @throws IOException
	 */
	public ArchiveInputStreamDX10(RandomAccessFile in, ArchiveEntry entry) throws IOException
	{
		super(new byte[0]);//reset below once data is availble

		ArchiveEntryDX10 tex = (ArchiveEntryDX10) entry;

		DDS_HEADER ddsHeader = new DDS_HEADER();

		ddsHeader.dwSize = 31;//sizeof(ddsHeader);
		ddsHeader.dwHeaderFlags = ddsHeader.DDS_HEADER_FLAGS_TEXTURE | ddsHeader.DDS_HEADER_FLAGS_LINEARSIZE
				| ddsHeader.DDS_HEADER_FLAGS_MIPMAP;
		ddsHeader.dwHeight = tex.height;
		ddsHeader.dwWidth = tex.width;
		ddsHeader.dwMipMapCount = tex.numMips;
		//ddsHeader.ddspf.dwSize = 8*4;//sizeof(DDS_PIXELFORMAT);
		ddsHeader.dwSurfaceFlags = ddsHeader.DDS_SURFACE_FLAGS_TEXTURE | ddsHeader.DDS_SURFACE_FLAGS_MIPMAP;

		boolean ok = true;

		switch (tex.format)
		{
			case DDS_HEADER.DXGI_FORMAT_BC1_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT1;
				//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
				//ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('D', 'X', 'T', '1');
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height / 2; // 4bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_BC2_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT3;
				//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
				//ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('D', 'X', 'T', '3');
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_BC3_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT5;
				//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
				//ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('D', 'X', 'T', '5');
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_BC5_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT5;

				//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
				//if (m_useATIFourCC)
				//	ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('A', 'T', 'I', '2'); // this is more correct but the only thing I have found that supports it is the nvidia photoshop plugin
				//else
				//	ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('D', 'X', 'T', '5');

				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_BC7_UNORM:
				// totally wrong but not worth writing out the DX10 header
				//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
				//ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('B', 'C', '7', '\0');

				ddsHeader.ddspf = new DDS_PIXELFORMAT(8 * 4, ddsHeader.DDS_FOURCC, ddsHeader.MAKEFOURCC('B', 'C', '7', '\0'), 0, 0, 0, 0, 0);

				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_B8G8R8A8_UNORM:
				ddsHeader.ddspf = new DDS_PIXELFORMAT();
				ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGBA;
				ddsHeader.ddspf.dwRGBBitCount = 32;
				ddsHeader.ddspf.dwRBitMask = 0x00FF0000;
				ddsHeader.ddspf.dwGBitMask = 0x0000FF00;
				ddsHeader.ddspf.dwBBitMask = 0x000000FF;
				ddsHeader.ddspf.dwABitMask = 0xFF000000;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 4; // 32bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_R8_UNORM:
				ddsHeader.ddspf = new DDS_PIXELFORMAT();
				ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGB;
				ddsHeader.ddspf.dwRGBBitCount = 8;
				ddsHeader.ddspf.dwRBitMask = 0xFF;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
				break;

			default:
				System.err.println("unhandled format %02X (%d) (%s) " + tex.format + " " + tex.getFileName());
				ok = false;
				break;
		}

		if (ok)
		{
			int requiredBufferSize = 32*4;
			for (int j = 0; j < tex.chunks.length; j++)
			{
				requiredBufferSize += tex.chunks[j].unpackedLen;
			}
			// collect up all the chunks
			ByteBuffer dst = ByteBuffer.allocate(requiredBufferSize);
			dst.order(ByteOrder.LITTLE_ENDIAN);

			dst.putInt(ddsHeader.DDS_MAGIC); // 'DDS '
			//dst.WriteBuf(ddsHeader, sizeof(ddsHeader));
			dst.putInt(ddsHeader.dwSize);
			dst.putInt(ddsHeader.dwHeaderFlags);
			dst.putInt(ddsHeader.dwHeight);
			dst.putInt(ddsHeader.dwWidth);
			dst.putInt(ddsHeader.dwPitchOrLinearSize);
			dst.putInt(ddsHeader.dwDepth);
			dst.putInt(ddsHeader.dwMipMapCount);
			dst.putInt(0);//alphaBitDepth);
			dst.putInt(0);//reserved1);
			dst.putInt(0);//surface);
			dst.putInt(0);//colorSpaceLowValue);
			dst.putInt(0);//colorSpaceHighValue);
			dst.putInt(0);//destBltColorSpaceLowValue);
			dst.putInt(0);//destBltColorSpaceHighValue);
			dst.putInt(0);//srcOverlayColorSpaceLowValue);
			dst.putInt(0);//srcOverlayColorSpaceHighValue);
			dst.putInt(0);//srcBltColorSpaceLowValue);
			dst.putInt(0);//srcBltColorSpaceHighValue);
			dst.putInt(ddsHeader.ddspf.dwSize);
			dst.putInt(ddsHeader.ddspf.dwFlags);
			dst.putInt(ddsHeader.ddspf.dwFourCC);
			dst.putInt(ddsHeader.ddspf.dwRGBBitCount);
			dst.putInt(ddsHeader.ddspf.dwRBitMask);
			dst.putInt(ddsHeader.ddspf.dwGBitMask);
			dst.putInt(ddsHeader.ddspf.dwBBitMask);
			dst.putInt(ddsHeader.ddspf.dwABitMask);
			dst.putInt(ddsHeader.dwSurfaceFlags);
			dst.putInt(ddsHeader.dwCubemapFlags);
			dst.putInt(0);//ddsCapsReserved1);
			dst.putInt(0);//ddsCapsReserved2);
			dst.putInt(0);//textureStage);

			synchronized (in)
			{
				for (int j = 0; j < tex.chunks.length; j++)
				{
					DX10Chunk chunk = tex.chunks[j];

					byte[] srcBuf = new byte[chunk.packedLen];

					in.seek(chunk.offset);
					int c = in.read(srcBuf, 0, chunk.packedLen);
					if (c < 0)
						throw new EOFException("Unexpected end of stream while inflating file");

					byte[] dstBuf = new byte[chunk.unpackedLen];

					Inflater inflater = new Inflater();
					inflater.setInput(srcBuf);
					inflater.setOutput(dstBuf);
					inflater.inflate(4);//Z_FINISH
					inflater.end();

					dst.put(dstBuf, 0, chunk.unpackedLen);
				}
			}

			this.buf = dst.array();
			this.pos = 0;
			this.count = buf.length;

		}

	}
}