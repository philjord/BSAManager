// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ExtractTask.java

package FO3Archive;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.swing.SwingUtilities;

// Referenced classes of package FO3Archive:
//            ArchiveEntry, ArchiveFile, StatusDialog, Main

public class ExtractTask extends Thread
{
	private File dirFile;

	private ArchiveFile archiveFile;

	private List<ArchiveEntry> entries;

	private StatusDialog statusDialog;

	private boolean completed;

	public ExtractTask(File dirFile, ArchiveFile archiveFile, List<ArchiveEntry> entries, StatusDialog statusDialog)
	{
		completed = false;
		this.dirFile = dirFile;
		this.archiveFile = archiveFile;
		this.entries = entries;
		this.statusDialog = statusDialog;
	}

	public void run()
	{
		try
		{
			String basePath = dirFile.getPath();
			byte buffer[] = new byte[32000];
			int fileCount = entries.size();
			int fileIndex = 0;
			int currentProgress = 0;
			for (ArchiveEntry entry : entries)
			{
				String folderPath = (new StringBuilder()).append(basePath).append("\\").append(entry.getFolderName()).toString();
				File folderFile = new File(folderPath);
				if (!folderFile.exists())
				{
					folderFile.mkdirs();
				}
				if (!folderFile.isDirectory())
				{
					folderFile.delete();
					folderFile.mkdir();
				}
				String filePath = (new StringBuilder()).append(basePath).append("\\").append(entry.getName()).toString();
				File file = new File(filePath);
				if (file.exists())
				{
					file.delete();
				}
				InputStream in = archiveFile.getInputStream(entry);
				FileOutputStream out = new FileOutputStream(file);

				int count = 0;
				while ((count = in.read(buffer)) >= 0)
				{
					out.write(buffer, 0, count);
				}

				in.close();
				out.close();
				int newProgress = (++fileIndex * 100) / fileCount;
				if (newProgress >= currentProgress + 5)
				{
					currentProgress = newProgress;
					statusDialog.updateProgress(currentProgress);
				}
			}

			completed = true;
		}
		catch (IOException exc)
		{
			Main.logException("I/O error while extracting files", exc);
		}
		catch (Throwable exc)
		{
			Main.logException("Exception while extracting files", exc);
		}
		SwingUtilities.invokeLater(new Runnable()
		{
			public void run()
			{
				statusDialog.closeDialog(completed);
			}
		});
	}

}