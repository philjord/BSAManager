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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import archive.ArchiveEntry;
import archive.ArchiveFile;
import archive.btdx.ArchiveEntryDX10.DX10Chunk;
import archive.btdx.DDS_HEADER.DDS_HEADER_DXT10;
import archive.btdx.DDS_HEADER.DDS_PIXELFORMAT;
import tools.io.FastByteArrayInputStream;

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
		DDS_HEADER_DXT10 dx10Header = new DDS_HEADER_DXT10();
		boolean dx10 = false;

		ddsHeader.dwSize = 31;//sizeof(ddsHeader);
		ddsHeader.dwHeaderFlags = ddsHeader.DDS_HEADER_FLAGS_TEXTURE | ddsHeader.DDS_HEADER_FLAGS_LINEARSIZE
				| ddsHeader.DDS_HEADER_FLAGS_MIPMAP;
		ddsHeader.dwHeight = tex.height;
		ddsHeader.dwWidth = tex.width;
		ddsHeader.dwMipMapCount = tex.numMips;
		//ddsHeader.ddspf.dwSize = 8*4;//sizeof(DDS_PIXELFORMAT);
		ddsHeader.dwSurfaceFlags = ddsHeader.DDS_SURFACE_FLAGS_TEXTURE | ddsHeader.DDS_SURFACE_FLAGS_MIPMAP;

		if (tex.unk16 == 2049)
			ddsHeader.dwCubemapFlags = ddsHeader.DDS_CUBEMAP_ALLFACES;

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
			//System.out.println(" BC5 " + entry.getFileName());

			ddsHeader.ddspf = ddsHeader.DDSPF_ATI2;
			ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
			break;
		//GL.GL_ATI_texture_compression_3dc
		//ddsHeader.ddspf = ddsHeader.DDSPF_DXT5;// this works fine and I can't use ATI2			
		//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
		//if (m_useATIFourCC)
		//	ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('A', 'T', 'I', '2'); // this is more correct but the only thing I have found that supports it is the nvidia photoshop plugin
		//else
		//	ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('D', 'X', 'T', '5');

		//ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
		//break;

		case DDS_HEADER.DXGI_FORMAT_BC7_UNORM:
			ddsHeader.ddspf = ddsHeader.DDSPF_DX10;
			ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
			// NOT seen yet
			System.out.println("dx10 fourCC!!!!!!!!!!!!!!!!!!!!!!!!!! " + ddsHeader.ddspf.dwFourCC + " " + entry.getFileName());
			dx10 = true;
			dx10Header.dxgiFormat = DDS_HEADER.DXGI_FORMAT_BC7_UNORM;
			break;
		// totally wrong but not worth writing out the DX10 header
		//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
		//ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('B', 'C', '7', '\0');

		//ddsHeader.ddspf = new DDS_PIXELFORMAT(8 * 4, ddsHeader.DDS_FOURCC, ddsHeader.MAKEFOURCC('B', 'C', '7', '\0'), 0, 0, 0, 0, 0);

		//ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
		case DDS_HEADER.DXGI_FORMAT_B8G8R8A8_UNORM:
			ddsHeader.ddspf = new DDS_PIXELFORMAT();
			ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGBA;// in fact BGRA!
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
			return;
		}

		int requiredBufferSize = 32 * 4;
		for (int j = 0; j < tex.chunks.length; j++)
		{
			requiredBufferSize += tex.chunks[j].unpackedLen;
		}
		// collect up all the chunks
		ByteBuffer dst = ByteBuffer.allocate(requiredBufferSize);
		dst.order(ByteOrder.LITTLE_ENDIAN);

		dst.putInt(ddsHeader.DDS_MAGIC); // 'DDS '		
		dst.putInt(ddsHeader.dwSize);//dst.WriteBuf(ddsHeader, sizeof(ddsHeader));
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

		if (dx10)
		{
			dx10Header.resourceDimension = ddsHeader.DDS_DIMENSION_TEXTURE2D;
			dx10Header.miscFlag = 0;
			dx10Header.arraySize = 1;
			dx10Header.miscFlags2 = 0;

			dst.putInt(dx10Header.dxgiFormat);
			dst.putLong(dx10Header.resourceDimension); //int?
			dst.putInt(dx10Header.miscFlag);
			dst.putInt(dx10Header.arraySize);
			dst.putInt(dx10Header.miscFlags2);
		}

		synchronized (in)
		{
			/* All at once results in bad data
			 int totalPackedLen = 0;
			int totalUnpackedLen = 0;
			for (int j = 0; j < tex.chunks.length; j++)
			{
				totalPackedLen += tex.chunks[j].packedLen;
				totalUnpackedLen += tex.chunks[j].unpackedLen;
			}
			
			byte[] srcBuf = new byte[totalPackedLen];
			int pos = 0;
			for (int j = 0; j < tex.chunks.length; j++)
			{
				DX10Chunk chunk = tex.chunks[j];
			
				in.seek(chunk.offset);
				int c = in.read(srcBuf, pos, chunk.packedLen);
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
				pos += chunk.packedLen;
			}
			
			byte[] dstBuf = new byte[totalUnpackedLen];
			
			com.jcraft.jzlib.Inflater inflater = new com.jcraft.jzlib.Inflater();
			inflater.setInput(srcBuf);
			inflater.setOutput(dstBuf);
			inflater.inflate(4);//Z_FINISH
			inflater.end();
			
			dst.put(dstBuf, 0, totalUnpackedLen);*/

			//JCraft load near = 22sec
			/*	for (int j = 0; j < tex.chunks.length; j++)
			
			{
				DX10Chunk chunk = tex.chunks[j];
			
				byte[] srcBuf = new byte[chunk.packedLen];
			
				in.seek(chunk.offset);
				int c = in.read(srcBuf, 0, chunk.packedLen);
				if (c < 0)
					throw new EOFException("Unexpected end of stream while inflating file");
			
				byte[] dstBuf = new byte[chunk.unpackedLen];
			
				com.jcraft.jzlib.Inflater inflater = new com.jcraft.jzlib.Inflater();
				inflater.setInput(srcBuf);
				inflater.setOutput(dstBuf);
				inflater.inflate(4);//Z_FINISH
				inflater.end();
			
				dst.put(dstBuf, 0, chunk.unpackedLen);
			}*/

			// Java straight inflate load near =13sec
			Inflater inflater = new Inflater();
			// each chunk can have any number of mips in it, so later one could be bigger than earlier!
			byte[] dstBuff = new byte[tex.chunks[0].unpackedLen];
			byte[] srcBuf = new byte[tex.chunks[0].packedLen];
			for (int j = 0; j < tex.chunks.length; j++)
			{
				DX10Chunk chunk = tex.chunks[j];

				if (chunk.packedLen > srcBuf.length)
					srcBuf = new byte[chunk.packedLen];

				if (chunk.unpackedLen > dstBuff.length)
					dstBuff = new byte[chunk.unpackedLen];

				//byte[] srcBuf = new byte[chunk.packedLen];
				if (ArchiveFile.USE_MINI_CHANNEL_MAPS && entry.getFileOffset() < Integer.MAX_VALUE)
				{
					FileChannel.MapMode mm = FileChannel.MapMode.READ_ONLY;
					FileChannel ch = in.getChannel();
					MappedByteBuffer mappedByteBuffer = ch.map(mm, chunk.offset, chunk.packedLen);

					mappedByteBuffer.get(srcBuf, 0, chunk.packedLen);

				}
				else
				{
					in.seek(chunk.offset);
					int c = in.read(srcBuf, 0, chunk.packedLen);
					if (c < 0)
						throw new EOFException("Unexpected end of stream while inflating file");
				}
				//byte[] dstBuff = new byte[chunk.unpackedLen];		
				inflater.reset();
				inflater.setInput(srcBuf, 0, chunk.packedLen);
				//ByteArrayOutputStream outputStream = new ByteArrayOutputStream(chunk.unpackedLen);

				try
				{
					//while (!inflater.finished())
					{
						int count = inflater.inflate(dstBuff);
						if (count != chunk.unpackedLen)
							System.err.println("Inflate count issue! " + this);
						//outputStream.write(b, 0, count);
					}
				}
				catch (DataFormatException e)
				{
					e.printStackTrace();
				}
				//outputStream.close();
				//dst.put(outputStream.toByteArray(), 0, chunk.unpackedLen);
				dst.put(dstBuff, 0, chunk.unpackedLen);

			}
		}

		this.buf = dst.array();
		this.pos = 0;
		this.count = buf.length;

	}
}