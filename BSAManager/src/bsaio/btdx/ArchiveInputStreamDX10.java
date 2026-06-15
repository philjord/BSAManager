package bsaio.btdx;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.jogamp.java3d.compressedtexture.FastByteArrayInputStream;

import com.github.pbbl.heap.ByteBufferPool;

import bsaio.ArchiveEntry;
import bsaio.ArchiveInputStream;
import bsaio.btdx.ArchiveEntryDX10.DX10Chunk;
import bsaio.btdx.DDS_HEADER.DDS_HEADER_DXT10;
import bsaio.btdx.DDS_HEADER.DDS_PIXELFORMAT;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import tools.io.FileChannelRAF;

/**
 * @author philip
 *
 */
public class ArchiveInputStreamDX10 extends FastByteArrayInputStream {
	public static boolean				m_useATIFourCC	= false;

	//https://github.com/yawkat/lz4-java
	//	<dependency>
	//    <groupId>at.yawk.lz4</groupId>
	//    <artifactId>lz4-java</artifactId>
	//		<version>1.10.2</version>
	//</dependency>

	public static LZ4Factory			factory;
	public static LZ4FastDecompressor	decompressor;

	private static ByteBufferPool		pool			= new ByteBufferPool();

	/**
	 * Only ArchiveEntryDX10 accepted, note this is only for inputstream access, which is not in any game only utils
	 * bytebuffers are static and don't insantiate this class
	 * @param in
	 * @param entry
	 * @throws IOException
	 */
	public ArchiveInputStreamDX10(FileChannelRAF in, ArchiveEntry entry) throws IOException {
		super(new byte[0]);//reset below once data is available

		//FIXME: the FastByteArrayInputStream getBuf is a bad system and I should swap them all you just 
		//getByteBuffer(in, entry, false); somehow, possibly if entry had in with it?
		// also then the last boolean cna be dropped

		//type of FastByteArrayInputStream is this and ArchiveInputStream

		//it looks like this constructor is now only used by things in 
		//BsaMeshSource for utils in 3DUtilsDesktop so not hard to swap out

		//note not direct as we want the byte[] behind it
		ByteBuffer dst = getByteBuffer(in, entry, false);
		this.buf = dst.array();
		this.pos = 0;
		this.count = buf.length;

	}

	//1,0,-1 1 uses mapped from channels, 0 reads into a bytebuffer, both seem the same for speed and memory use
	public static int USE_MAPPED = 1;

	/**
	 * Be careful see ArchiveFile for warning
	 * @param in
	 * @param entry
	 * @return
	 * @throws IOException
	 */
	public static ByteBuffer getByteBuffer(FileChannelRAF in, ArchiveEntry entry) throws IOException {
		return getByteBuffer(in, entry, true); 
	}
	
	//non direct added to support the stream init thing
	private static ByteBuffer getByteBuffer(FileChannelRAF in, ArchiveEntry entry, boolean direct) throws IOException {
		if (decompressor == null) {
			factory = LZ4Factory.fastestInstance();
			decompressor = factory.fastDecompressor();
		}

		FileChannel ch = in.getChannel();
		ArchiveEntryDX10 tex = (ArchiveEntryDX10)entry;
		int requiredBufferSize = 32 * 4;

		// DX10 inserts a bit more data
		if (tex.format == DDS_HEADER.DXGI_FORMAT_BC7_UNORM || tex.format == DDS_HEADER.DXGI_FORMAT_BC7_UNORM_SRGB) {
			requiredBufferSize += 24; //4 extra int and a long
		}

		for (int j = 0; j < tex.chunks.length; j++) {
			requiredBufferSize += tex.chunks[j].unpackedLen;
		}
		// collect up all the chunks
		ByteBuffer dst = direct ? ByteBuffer.allocateDirect(requiredBufferSize) : ByteBuffer.allocate(requiredBufferSize);
		dst.order(ByteOrder.LITTLE_ENDIAN);//NOTE!!!

		insertHeader(tex, dst);

		//FIXME I need to be able to send GNFDX10 entries to something like this, but not
		if (entry.isCompressed()) {
			if (entry.getCompressionType() == ArchiveEntry.CompressionFormat.ZIP) {

				
				//I need to only load mips when needed, not all always!
				//https://stackoverflow.com/questions/34306810/opengl-texture-mipmaps-load-and-unload
				// perhaps start with baeLevel =  half way, then as loaded bring it down
				// prolly need to load these thign the other way around
				// also make the lower mipmap loader thread lower priority
				
					
				if (USE_MAPPED == 0 || USE_MAPPED == 1) {
					// each chunk can have any number of mips in it, so later one could be bigger than earlier!
					Inflater inflater = new Inflater();
					for (int j = 0; j < tex.chunks.length; j++) {
						DX10Chunk chunk = tex.chunks[j];

						ByteBuffer dataBufferInBB = null;
						if (USE_MAPPED == 0) {
							dataBufferInBB = pool.take(chunk.packedLen);
							int c = ch.read(dataBufferInBB, chunk.offset);
							if (c < 0)
								throw new EOFException("Unexpected end of stream while inflating file");
							dataBufferInBB.rewind();
						} else {
							dataBufferInBB = ch.map(MapMode.READ_ONLY, chunk.offset, chunk.packedLen);
						}

						inflater.setInput(dataBufferInBB);

						try {
							int count = inflater.inflate(dst);
							if (count != chunk.unpackedLen)
								System.err.println("Inflate count issue! count = "	+ count + " expected "
													+ chunk.unpackedLen + " hashcode= " + entry.getFileHashCode());
						} catch (DataFormatException e) {
							System.err.println(
									ArchiveInputStream.class.getName()	+ ".getByteBuffer Inflater DataFormatException "
												+ " hashcode= " + entry.getFileHashCode());
						}
						inflater.reset();
						if (USE_MAPPED == 0) {
							pool.give(dataBufferInBB);
						}
					}
				} else {

					// Java straight inflate load near =13sec
					Inflater inflater = new Inflater();

					// each chunk can have any number of mips in it, so later one could be bigger than earlier!
					byte[] srcBuf = new byte[tex.chunks[0].packedLen];
					byte[] dstBuff = new byte[tex.chunks[0].unpackedLen];

					for (int j = 0; j < tex.chunks.length; j++) {
						DX10Chunk chunk = tex.chunks[j];

						if (chunk.packedLen > srcBuf.length)
							srcBuf = new byte[chunk.packedLen];

						if (chunk.unpackedLen > dstBuff.length)
							dstBuff = new byte[chunk.unpackedLen];

						//byte[] srcBuf = new byte[chunk.packedLen];
						int c = ch.read(ByteBuffer.wrap(srcBuf, 0, chunk.packedLen), chunk.offset);
						if (c < 0)
							throw new EOFException("Unexpected end of stream while inflating file");

						inflater.setInput(srcBuf, 0, chunk.packedLen);

						try {

							int count = inflater.inflate(dstBuff);
							if (count != chunk.unpackedLen)
								System.err.println("ZIP Inflate count issue! ArchiveInputStreamDX10 ");

						} catch (DataFormatException e) {
							e.printStackTrace();
						}

						inflater.reset();
						dst.put(dstBuff, 0, chunk.unpackedLen);
					}
				}
			} else if (entry.getCompressionType() == ArchiveEntry.CompressionFormat.LZ4) {

				if (USE_MAPPED == 0 || USE_MAPPED == 1) {

					for (int j = 0; j < tex.chunks.length; j++) {
						DX10Chunk chunk = tex.chunks[j];
						// It turns out that isCompressed is in fact a combo of the zip type and each chunk itself!
						if (chunk.packedLen > 0) {

							ByteBuffer dataBufferInBB = null;
							if (USE_MAPPED == 0) {
								dataBufferInBB = pool.take(chunk.packedLen);
								int c = ch.read(dataBufferInBB, chunk.offset);
								if (c < 0)
									throw new EOFException("Unexpected end of stream while inflating file");
								dataBufferInBB.rewind();
							} else {
								dataBufferInBB = ch.map(MapMode.READ_ONLY, chunk.offset, chunk.packedLen);
							}

							try {
								

								ByteBuffer dstBuff = dst.slice();// notice LITTLE_ENDIAN but dst is big, does it matter?					
								int count = decompressor.decompress(dataBufferInBB, 0, dstBuff, 0,	chunk.unpackedLen);
								dst.position(dst.position() + chunk.unpackedLen);// cos decompress doesn't move this
								
								if (count != chunk.unpackedLen) {
									// super common and using count causings issue, presumably alignment numbers
								}
							} catch (net.jpountz.lz4.LZ4Exception e) {
								System.out.println("net.jpountz.lz4.LZ4Exception " + e.getMessage());
							}

							if (USE_MAPPED == 0) {
								pool.give(dataBufferInBB);
							}

						} else {
							ByteBuffer dataBufferInBB = pool.take(chunk.packedLen);
							int c = ch.read(dataBufferInBB, chunk.offset);
							if (c < 0)
								throw new EOFException("Unexpected end of stream while reading file");

							dst.put(dataBufferInBB);
							pool.give(dataBufferInBB);
						}
					}
				} else {
					byte[] srcBuf = new byte[tex.chunks[0].packedLen];
					byte[] dstBuff = new byte[tex.chunks[0].unpackedLen];

					for (int j = 0; j < tex.chunks.length; j++) {
						DX10Chunk chunk = tex.chunks[j];
						// It turns out that isCompressed is in fact a combo of the zip type and each chunk itself!
						if (chunk.packedLen > 0) {

							if (chunk.packedLen > srcBuf.length)
								srcBuf = new byte[chunk.packedLen];

							if (chunk.unpackedLen > dstBuff.length)
								dstBuff = new byte[chunk.unpackedLen];

							int c = ch.read(ByteBuffer.wrap(srcBuf, 0, chunk.packedLen), chunk.offset);

							if (c < 0)
								throw new EOFException("Unexpected end of stream while inflating file");

							try {
								int count = decompressor.decompress(srcBuf, 0, dstBuff, 0, chunk.unpackedLen);

								if (count != chunk.unpackedLen) {
									//seems super common and doesn't seem to cause trouble
									//System.err.println("LZ4 Inflate count issue! "	+ entry + " chunk.unpackedLen= " + chunk.unpackedLen
									//					+ " count=" + count);
								}
							} catch (net.jpountz.lz4.LZ4Exception e) {
								System.out.println("net.jpountz.lz4.LZ4Exception " + e.getMessage());
							}

							dst.put(dstBuff, 0, chunk.unpackedLen);
						} else {
							if (chunk.packedLen > srcBuf.length)
								srcBuf = new byte[chunk.packedLen];

							int c = ch.read(ByteBuffer.wrap(srcBuf, 0, chunk.packedLen), chunk.offset);
							if (c < 0)
								throw new EOFException("Unexpected end of stream while reading file");

							dst.put(srcBuf, 0, chunk.packedLen);
						}
					}
				}

			} else {
				new Throwable("Unknown ArchiveEntry compressionType! " + entry.getCompressionType()).printStackTrace();
			}
		} else {
			if (USE_MAPPED == 0 || USE_MAPPED == 1) {
				for (int j = 0; j < tex.chunks.length; j++) {
					DX10Chunk chunk = tex.chunks[j];
					ByteBuffer dataBufferInBB = pool.take(chunk.packedLen);
					int c = ch.read(dataBufferInBB, chunk.offset);
					if (c < 0)
						throw new EOFException("Unexpected end of stream while reading file");

					dst.put(dataBufferInBB);
					pool.give(dataBufferInBB);
				}
			} else {
				byte[] srcBuf = new byte[tex.chunks[0].packedLen];
				for (int j = 0; j < tex.chunks.length; j++) {
					DX10Chunk chunk = tex.chunks[j];

					if (chunk.packedLen > srcBuf.length)
						srcBuf = new byte[chunk.packedLen];

					int c = ch.read(ByteBuffer.wrap(srcBuf, 0, chunk.packedLen), chunk.offset);
					if (c < 0)
						throw new EOFException("Unexpected end of stream while reading file");

					dst.put(srcBuf, 0, chunk.packedLen);
				}
			}
		}
		dst.rewind();
		return dst;

	}

	private static void insertHeader(ArchiveEntryDX10 tex, ByteBuffer dst) {
		//FIXME: textures not loading yet
		//STF
		//ArchiveFile:Starfield - Textures05.ba2/textures/effects/weather/cloudcards/clouds_directions.dds
		//Pixel format: D3DFMT_DX10		Bad DXT format (for now) 808540228 GL=-1 

		//Unsupported DDS format 0 for file textures\effects\gradients\cloaksurface_ropacity.dds

		//unhandled format 11 ArchiveFile:Starfield - Textures05.ba2/textures/effects/gradients/gasgiantcolor01_grad.dds
		//class org.jogamp.java3d.compressedtexture.CompressedTextureLoader$DDS had a  IO problem with textures\effects\gradients\gasgiantcolor01_grad.dds : java.io.IOException: Incorrect magic number 0x0 (expected 0x20534444) or 0x5a485aa8) compressedtexture.DDSImage$Header.read(DDSImage.java:763)

		//unhandled format 95 ArchiveFile:Starfield - Textures05.ba2/textures/effects/luts/lgt_lut_hdr_int_crimsonoutpost_v01.dds
		//class org.jogamp.java3d.compressedtexture.CompressedTextureLoader$DDS had a  IO problem with textures\effects\luts\lgt_lut_hdr_int_crimsonoutpost_v01.dds : java.io.IOException: Incorrect magic number 0x29000a1e (expected 0x20534444) or 0x5a485aa8) compressedtexture.DDSImage$Header.read(DDSImage.java:763)

		//FO76
		//ArchiveFile:SeventySix - Textures02.ba2/textures/interface/loadingmenubackgrounds/ls_abandonedtruck.dds
		//Pixel format: D3DFMT_DX10		DXGI_FORMAT_BC7_UNORM_SRGB=808540228
		//ArchiveFile:SeventySix - Textures01.ba2/textures/interface/lockpicking/lockinterface01_d.dds
		//Number of mip maps: 1		Pixel format: D3DFMT_DXT1
		//ArchiveFile:SeventySix - Textures01.ba2/textures/dlc03/effects/gradients/wispysmokealphagrad.dds
		//Number of mip maps: 6		Pixel format: D3DFMT_A8B8G8R8

		//FO4		 
		//ArchiveFile:Fallout4 - Textures6.ba2/textures/interface/loadingmenubg.dds
		//DXGI_FORMAT_BC7_UNORM  = 808540228		dx10 fourCC!!!
		//ArchiveFile:Fallout4 - Textures6.ba2/textures/interface/newspaper/dn101note_n.dds
		//I also see the same problem for 1 mipmap as the 0 below perhaps

		//image examplers that not working yet
		//ArchiveFile:Oblivion - Textures - Compressed.bsa/textures/menus/breathmeter/breath_meter_fill.dds
		//Compression format: 0x35545844 (DXT5)		Width: 512 Height: 64 Number of mip maps: 0
		//ArchiveFile:Oblivion - Textures - Compressed.bsa/textures/menus/class/background.dds
		//Number of mip maps: 0	Pixel format: D3DFMT_DXT5
		//ArchiveFile:Oblivion - Textures - Compressed.bsa/textures/menus/container/cont_box_background_2.dds
		//Number of mip maps: 0 		Pixel format: D3DFMT_DXT3
		//ArchiveFile:Oblivion - Textures - Compressed.bsa/textures/menus/container/xbox_cont_select_frame.dds
		//Number of mip maps: 0 		Pixel format: D3DFMT_DXT5
		//ArchiveFile:Oblivion - Textures - Compressed.bsa/textures/magic/shockring.dds
		//Number of mip maps: 0		Pixel format: D3DFMT_A8R8G8B8
		// is it 0 mips?

		//FO3
		//ArchiveFile:Fallout - Textures.bsa/textures/fonts/baked-in_monofonto_large_0_lod_a.dds
		//Number of mip maps: 0		Pixel format: (unknown pixel format 26)  -total crash
		//ArchiveFile:Fallout - Textures.bsa/textures/effects/eyereflection.dds
		//Number of mip maps: 0		Pixel format: D3DFMT_DXT1

		//FO3NV
		//ArchiveFile:Fallout - Textures2.bsa/textures/effects/eyereflection.dds
		//Number of mip maps: 0		Pixel format: D3DFMT_DXT1

		//Skyrim
		//ArchiveFile:Skyrim - Textures.bsa/textures/blood/bloodedge01add.dds
		//Number of mip maps: 9		Pixel format: D3DFMT_DXT5

		DDS_HEADER ddsHeader = new DDS_HEADER();
		DDS_HEADER_DXT10 dx10Header = new DDS_HEADER_DXT10();
		boolean dx10 = false;

		ddsHeader.dwSize = 31;//sizeof(ddsHeader);
		ddsHeader.dwHeaderFlags = ddsHeader.DDS_HEADER_FLAGS_TEXTURE	| ddsHeader.DDS_HEADER_FLAGS_LINEARSIZE
									| ddsHeader.DDS_HEADER_FLAGS_MIPMAP;
		ddsHeader.dwHeight = tex.height;
		ddsHeader.dwWidth = tex.width;
		ddsHeader.dwMipMapCount = tex.numMips;
		//ddsHeader.ddspf.dwSize = 8*4;//sizeof(DDS_PIXELFORMAT);
		ddsHeader.dwSurfaceFlags = ddsHeader.DDS_SURFACE_FLAGS_TEXTURE | ddsHeader.DDS_SURFACE_FLAGS_MIPMAP;

		if (tex.isCubemap != 0)
			ddsHeader.dwCubemapFlags = ddsHeader.DDS_CUBEMAP_ALLFACES;

		switch (tex.format) {
			case DDS_HEADER.DXGI_FORMAT_B8G8R8A8_UNORM:
				ddsHeader.ddspf = new DDS_PIXELFORMAT();
				ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGBA;// in fact BGRA!
				ddsHeader.ddspf.dwRGBBitCount = 32;
				ddsHeader.ddspf.dwRBitMask = 0x000000FF;
				ddsHeader.ddspf.dwGBitMask = 0x0000FF00;
				ddsHeader.ddspf.dwBBitMask = 0x00FF0000;
				ddsHeader.ddspf.dwABitMask = 0xFF000000;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 4; // 32bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_R8G8B8A8_UNORM:
				ddsHeader.ddspf = new DDS_PIXELFORMAT();
				ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGBA;
				ddsHeader.ddspf.dwRGBBitCount = 32;
				ddsHeader.ddspf.dwRBitMask = 0x00FF0000;
				ddsHeader.ddspf.dwGBitMask = 0x0000FF00;
				ddsHeader.ddspf.dwBBitMask = 0x000000FF;
				ddsHeader.ddspf.dwABitMask = 0xFF000000;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 4; // 32bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_R8G8B8A8_UNORM_SRGB:
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
				ddsHeader.ddspf.dwRGBBitCount = 8;
				ddsHeader.ddspf.dwRBitMask = 0xFF;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;
			//Cube maps in STF
			//https://wikis.khronos.org/opengl/Cubemap_Texture
			//GL_RGBA16F pipeline doesn't display properly, so decoded/recoded to ETC now
			case DDS_HEADER.DXGI_FORMAT_R16G16B16A16_FLOAT:
				ddsHeader.ddspf = new DDS_PIXELFORMAT();
				ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGBA;
				ddsHeader.ddspf.dwRGBBitCount = 64;
				ddsHeader.ddspf.dwRBitMask = 0x000000FF;
				ddsHeader.ddspf.dwGBitMask = 0x0000FF00;
				ddsHeader.ddspf.dwBBitMask = 0x00FF0000;
				ddsHeader.ddspf.dwABitMask = 0xFF000000;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 8; // 64bpp
				break;
			//GL_RGBA16 pipeline doesn't display properly, so decoded/recoded to ETC now
			case DDS_HEADER.DXGI_FORMAT_R16G16B16A16_UNORM://not working DDSImage can't decode
				ddsHeader.ddspf = new DDS_PIXELFORMAT();
				ddsHeader.ddspf.dwFlags = ddsHeader.DDS_RGBA;
				ddsHeader.ddspf.dwRGBBitCount = 64;
				ddsHeader.ddspf.dwRBitMask = 0xFF000000;
				ddsHeader.ddspf.dwGBitMask = 0x00FF0000;
				ddsHeader.ddspf.dwBBitMask = 0x0000FF00;
				ddsHeader.ddspf.dwABitMask = 0x000000FF;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 8; // 64bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_BC1_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT1;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height / 2; // 4bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC1_UNORM_SRGB://FIXME:opengl tex format handles srgb
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT1;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height / 2; // 4bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC2_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT3;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC2_UNORM_SRGB://FIXME:opengl tex format handles srgb
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT3;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC3_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT5;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC3_UNORM_SRGB://FIXME:opengl tex format handles srgb
				ddsHeader.ddspf = ddsHeader.DDSPF_DXT5;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC4_UNORM:
				ddsHeader.dwHeaderFlags = 0xA1007;
				ddsHeader.ddspf = ddsHeader.DDSPF_ATI1;
				ddsHeader.dwPitchOrLinearSize = ((tex.width / 4) * (tex.height / 4) * 8);// 4bpp
				break;
			case DDS_HEADER.DXGI_FORMAT_BC5_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_BC5U;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;
			// https://www.gamedev.net/forums/topic/578936-directx-10-unorm-vs-snorm/
			// https://learn.microsoft.com/en-us/windows/win32/api/dxgiformat/ne-dxgiformat-dxgi_format
			case DDS_HEADER.DXGI_FORMAT_BC5_SNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_BC5S;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				break;

			case DDS_HEADER.DXGI_FORMAT_BC7_UNORM:
				ddsHeader.ddspf = ddsHeader.DDSPF_DX10;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height * 1; // 8bpp
				// NOT seen yet
				System.out.println("dx10 fourCC!!!!!!! DXGI_FORMAT_BC7_UNORM=" + ddsHeader.ddspf.dwFourCC + " " + tex);
				dx10 = true;
				dx10Header.dxgiFormat = DDS_HEADER.DXGI_FORMAT_BC7_UNORM;
				break;
			// totally wrong but not worth writing out the DX10 header
			//ddsHeader.ddspf.dwFlags = ddsHeader.DDS_FOURCC;
			//ddsHeader.ddspf.dwFourCC = ddsHeader.MAKEFOURCC('B', 'C', '7', '\0');
			//ddsHeader.ddspf = new DDS_PIXELFORMAT(8 * 4, ddsHeader.DDS_FOURCC, ddsHeader.MAKEFOURCC('B', 'C', '7', '\0'), 0, 0, 0, 0, 0);
			//ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp	

			// see https://learn.microsoft.com/en-us/windows/win32/direct3d11/texture-block-compression-in-direct3d-11

			case DDS_HEADER.DXGI_FORMAT_BC7_UNORM_SRGB:
				ddsHeader.ddspf = ddsHeader.DDSPF_DX10;
				ddsHeader.dwPitchOrLinearSize = tex.width * tex.height; // 8bpp
				// NOT seen yet
				System.out.println(
						"dx10 fourCC!!!!!!! DXGI_FORMAT_BC7_UNORM_SRGB=" + ddsHeader.ddspf.dwFourCC + " " + tex);
				dx10 = true;
				dx10Header.dxgiFormat = DDS_HEADER.DXGI_FORMAT_BC7_UNORM_SRGB;
				break;

			default:
				System.err.println("unhandled format " + tex.format + " " + tex);
				return;
		}

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

		if (dx10) {
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
	}
}