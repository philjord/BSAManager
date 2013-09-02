// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   CreateTask.java

package FO3Archive;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;

import javax.swing.SwingUtilities;

// Referenced classes of package FO3Archive:
//            DBException, ArchiveEntry, Main, HashCode, 
//            StatusDialog

public class CreateTask extends Thread
{

	private File archiveFile;

	private File dirFile;

	private StatusDialog statusDialog;

	private boolean completed;

	private int archiveFlags;

	private int fileFlags;

	private int folderCount;

	private int fileCount;

	private int folderNamesLength;

	private int fileNamesLength;

	private ArrayList<ArchiveEntry> entries;

	private List<Folder> folders;

	public CreateTask(File archiveFile, File dirFile, StatusDialog statusDialog)
	{
		completed = false;
		this.archiveFile = archiveFile;
		this.dirFile = dirFile;
		this.statusDialog = statusDialog;
	}

	public void run()
	{
		RandomAccessFile out = null;
		try
		{
			entries = new ArrayList<ArchiveEntry>(256);
			folders = new ArrayList<Folder>(256);
			archiveFlags = 7;
			fileFlags = 0;

			File files[] = dirFile.listFiles();
			if (files == null)
				throw new IOException("Unable to access directory '" + dirFile.getPath() + "'");

			for (int i = 0; i < files.length; i++)
			{
				File file = files[i];
				if (file.isDirectory())
					addFolderFiles(file);
			}

			if (fileCount != 0)
			{
				if (archiveFile.exists() && !archiveFile.delete())
					throw new IOException("Unable to delete '" + archiveFile.getPath() + "'");
				out = new RandomAccessFile(archiveFile, "rw");
				writeArchive(out);
				out.close();
				out = null;
			}
			completed = true;
		}
		catch (DBException exc)
		{
			Main.logException("Format error while creating archive", exc);
		}
		catch (IOException exc)
		{
			Main.logException("I/O error while creating archive", exc);
		}
		catch (Throwable exc)
		{
			Main.logException("Exception while creating archive", exc);
		}

		if (!completed && out != null)
		{
			try
			{
				out.close();
				out = null;
				if (archiveFile.exists())
					archiveFile.delete();
			}
			catch (IOException exc)
			{
				Main.logException("I/O error while cleaning up", exc);
			}
		}
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				statusDialog.closeDialog(completed);
			}
		});
	}

	private void addFolderFiles(File dirFile2) throws DBException, IOException
	{
		File files[] = dirFile2.listFiles();
		if (files == null)
			throw new IOException("Unable to access directory '" + dirFile2.getPath() + "'");
		if (files.length == 0)
			return;
		entries.ensureCapacity(files.length);
		for (int i = 0; i < files.length; i++)
		{
			File file = files[i];
			if (file.isDirectory())
				addFolderFiles(file);
			else
				insertFile(file);
		}

	}

	private void insertFile(File file) throws DBException
	{
		String folderName = file.getParent().toLowerCase();
		String baseName = dirFile.getPath();

		folderName = folderName.substring(baseName.length() + 1);
		if (folderName.length() > 254)
		{
			throw new DBException("Maximum folder path length is 254 characters");
		}

		String fileName = file.getName().toLowerCase();
		if (fileName.length() > 254)
		{
			throw new DBException("Maximum file name length is 254 characters");
		}

		ArchiveEntry entry = new ArchiveEntry(null, folderName, fileName);
		boolean insert = true;

		int count = entries.size();
		int i = 0;
		while (i < count)
		{
			ArchiveEntry listEntry = entries.get(i);
			int diff = entry.compareTo(listEntry);
			if (diff == 0)
			{
				throw new DBException("Hash collision: '" + entry.getName() + "' and '" + listEntry.getName() + "'");
			}
			if (diff < 0)
			{
				insert = false;
				entries.add(i, entry);
				break;
			}
			i++;
		}

		if (insert)
		{
			entries.add(entry);
		}

		int sep = fileName.lastIndexOf('.');
		if (sep >= 0)
		{
			String ext = fileName.substring(sep);
			if (ext.equals(".nif"))
			{
				fileFlags |= 1;
				archiveFlags |= 0x80;
			}
			else if (ext.equals(".dds"))
			{
				fileFlags |= 2;
				archiveFlags |= 0x100;
			}
			else if (ext.equals(".kf"))
			{
				fileFlags |= 0x40;
			}
			else if (ext.equals(".wav"))
			{
				fileFlags |= 8;
				archiveFlags |= 0x10;
			}
			else if (ext.equals(".lip"))
			{
				fileFlags |= 8;
			}
			else if (ext.equals(".mp3"))
			{
				fileFlags |= 0x10;
				archiveFlags |= 0x10;
				archiveFlags &= -5;
			}
			else if (ext.equals(".ogg"))
			{
				fileFlags |= 0x10;
				archiveFlags &= -5;
			}
			else if (ext.equals(".xml"))
			{
				fileFlags |= 0x100;
			}
		}

		if ((fileFlags & 2) != 0 && (fileFlags & -3) != 0)
		{
			throw new DBException("Texture files must be packaged by themselves");
		}
		insert = true;
		Iterator<Folder> i$ = folders.iterator();
		while (i$.hasNext())
		{
			Folder folder = i$.next();
			if (!folder.getName().equals(folderName))
				continue;

			folder.incrementFileCount();
			insert = false;
			break;
		}

		if (insert)
		{
			Folder folder = new Folder(folderName);
			folder.incrementFileCount();

			for (int i2 = 0; i2 < folders.size(); i2++)
			{
				Folder listFolder = folders.get(i2);
				if (folder.getHashCode().compareTo(listFolder.getHashCode()) < 0)
				{
					insert = false;
					folders.add(i2, folder);
					break;
				}
			}

			if (insert)
			{
				folders.add(folder);
			}
			folderNamesLength += folderName.length() + 1;
			folderCount++;
		}
		fileNamesLength += fileName.length() + 1;
		fileCount++;
	}

	private void writeArchive(RandomAccessFile out) throws DBException, IOException
	{
		byte[] buffer = new byte[256];
		byte[] dataBuffer = new byte[32000];
		byte[] compressedBuffer = new byte[8000];
		byte[] header = new byte[36];

		header[0] = 66;
		header[1] = 83;
		header[2] = 65;
		setInteger(104, header, 4);
		setInteger(36, header, 8);
		setInteger(archiveFlags, header, 12);
		setInteger(folderCount, header, 16);
		setInteger(fileCount, header, 20);
		setInteger(folderNamesLength, header, 24);
		setInteger(fileNamesLength, header, 28);
		setInteger(fileFlags, header, 32);

		out.write(header);

		long fileOffset = header.length + folderCount * 16 + fileNamesLength;
		if (fileOffset > 0x7fffffffL)
		{
			throw new DBException("File offset exceeds 2GB");
		}

		for (Folder folder : folders)
		{
			setLong(folder.getHashCode().getHash(), buffer, 0);
			setInteger(folder.getFileCount(), buffer, 8);
			setInteger((int) fileOffset, buffer, 12);
			out.write(buffer, 0, 16);
			fileOffset += folder.getName().length() + 2 + folder.getFileCount() * 16;
			if (fileOffset > 0x7fffffffL)
			{
				throw new DBException("File offset exceeds 2GB");
			}
		}

		int fileIndex = 0;
		for (Folder folder : folders)
		{
			String folderName = folder.getName();
			byte[] nameBuffer = folderName.getBytes();
			if (nameBuffer.length != folderName.length())
			{
				throw new DBException("Encoded folder name is longer than character name");
			}
			buffer[0] = (byte) (nameBuffer.length + 1);
			System.arraycopy(nameBuffer, 0, buffer, 1, nameBuffer.length);
			buffer[nameBuffer.length + 1] = 0;
			out.write(buffer, 0, nameBuffer.length + 2);

			for (int i = 0; i < folder.getFileCount(); i++)
			{
				ArchiveEntry entry = entries.get(fileIndex++);
				setLong(entry.getFileHashCode().getHash(), buffer, 0);
				setInteger(0, buffer, 8);
				setInteger(0, buffer, 12);
				out.write(buffer, 0, 16);
			}
		}

		for (ArchiveEntry entry : entries)
		{
			String fileName = entry.getFileName();
			byte[] nameBuffer = fileName.getBytes();
			if (nameBuffer.length != fileName.length())
			{
				throw new DBException("Encoded file name is longer than character name");
			}
			System.arraycopy(nameBuffer, 0, buffer, 0, nameBuffer.length);
			buffer[nameBuffer.length] = 0;
			out.write(buffer, 0, nameBuffer.length + 1);
		}

		int currentProgress = 0;
		fileIndex = 0;

		for (ArchiveEntry entry : entries)
		{
			FileInputStream in = null;
			Deflater deflater = null;

			try
			{
				File file = new File(dirFile.getPath() + "\\" + entry.getName());
				int residualLength = (int) file.length();
				entry.setFileOffset(out.getFilePointer());
				entry.setFileLength(residualLength);
				in = new FileInputStream(file);

				if ((archiveFlags & 0x100) != 0)
				{
					byte nameBuffer2[] = entry.getName().getBytes();
					buffer[0] = (byte) nameBuffer2.length;
					out.write(buffer, 0, 1);
					out.write(nameBuffer2);
				}

				if ((archiveFlags & 4) != 0)
				{
					setInteger(residualLength, buffer, 0);
					out.write(buffer, 0, 4);
					int compressedLength = 4;
					if (residualLength > 0)
					{
						deflater = new Deflater(6);
						while (!deflater.finished())
						{
							int count;
							if (deflater.needsInput())
							{
								int length = Math.min(dataBuffer.length, residualLength);
								count = in.read(dataBuffer, 0, length);
								if (count == -1)
								{
									throw new EOFException("Unexpected end of stream while deflating data");
								}
								residualLength -= count;
								deflater.setInput(dataBuffer, 0, count);
								if (residualLength == 0)
									deflater.finish();
							}
							count = deflater.deflate(compressedBuffer, 0, compressedBuffer.length);
							if (count > 0)
							{
								out.write(compressedBuffer, 0, count);
								compressedLength += count;
							}
						}
					}
					entry.setCompressed(true);
					entry.setCompressedLength(compressedLength);
				}
				else
				{
					int count;
					for (; residualLength > 0; residualLength -= count)
					{
						count = in.read(dataBuffer);
						if (count == -1)
						{
							throw new EOFException("Unexpected end of stream while copying data");
						}
						out.write(dataBuffer, 0, count);
					}

					entry.setCompressed(false);
				}

				if (in != null)
					in.close();
				if (deflater != null)
					deflater.end();
			}
			catch (IOException e)
			{
				if (in != null)
					in.close();
				if (deflater != null)
					deflater.end();
				throw e;
			}

			int newProgress = (++fileIndex * 100) / fileCount;
			if (newProgress >= currentProgress + 5)
			{
				currentProgress = newProgress;
				statusDialog.updateProgress(currentProgress);
			}

		}

		long fileOffset2 = header.length + folderCount * 16;
		out.seek(fileOffset2);
		int entryIndex = 0;
		for (Folder folder : folders)
		{
			int length = out.readByte() & 0xff;
			out.skipBytes(length);

			for (int i = 0; i < folder.getFileCount(); i++)
			{
				ArchiveEntry entry = entries.get(entryIndex++);
				int count;
				if ((archiveFlags & 0x100) != 0)
				{
					count = entry.getName().getBytes().length + 1;
				}
				else
				{
					count = 0;
				}

				if (entry.isCompressed())
				{
					count += entry.getCompressedLength();
				}
				else
				{
					count += entry.getFileLength();
				}

				setInteger(count, buffer, 0);
				fileOffset2 = entry.getFileOffset();
				if (fileOffset2 > 0x7fffffffL)
				{
					throw new DBException("File offset exceeds 2GB");
				}
				setInteger((int) fileOffset2, buffer, 4);
				out.skipBytes(8);
				out.write(buffer, 0, 8);
			}
		}
	}

	private static void setInteger(int number, byte buffer[], int offset)
	{
		buffer[offset] = (byte) number;
		buffer[offset + 1] = (byte) (number >>> 8);
		buffer[offset + 2] = (byte) (number >>> 16);
		buffer[offset + 3] = (byte) (number >>> 24);
	}

	private static void setLong(long number, byte buffer[], int offset)
	{
		buffer[offset] = (byte) (int) number;
		buffer[offset + 1] = (byte) (int) (number >>> 8);
		buffer[offset + 2] = (byte) (int) (number >>> 16);
		buffer[offset + 3] = (byte) (int) (number >>> 24);
		buffer[offset + 4] = (byte) (int) (number >>> 32);
		buffer[offset + 5] = (byte) (int) (number >>> 40);
		buffer[offset + 6] = (byte) (int) (number >>> 48);
		buffer[offset + 7] = (byte) (int) (number >>> 56);
	}

	private static class Folder
	{
		private String name;

		private HashCode hashCode;

		private int fileCount2;

		public Folder(String name)
		{
			this.name = name;
			hashCode = new HashCode(name, true);
		}

		public String getName()
		{
			return name;
		}

		public HashCode getHashCode()
		{
			return hashCode;
		}

		public void incrementFileCount()
		{
			fileCount2++;
		}

		public int getFileCount()
		{
			return fileCount2;
		}

		public boolean equals(Object obj)
		{
			return (obj != null && (obj instanceof Folder) && hashCode.equals(((Folder) obj).getHashCode()));
		}

	}

}