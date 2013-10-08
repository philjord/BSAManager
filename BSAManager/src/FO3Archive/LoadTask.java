// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   LoadTask.java

package FO3Archive;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

// Referenced classes of package FO3Archive:
//            ArchiveEntry, FolderNode, FileNode, DBException, 
//            ArchiveFile, Main, ArchiveNode, StatusDialog

public class LoadTask extends Thread
{
	private ArchiveFile archiveFile;

	private ArchiveNode archiveNode = null;

	private StatusDialog statusDialog;

	private boolean completed;

	// Non gui loader
	public LoadTask(ArchiveFile archiveFile, StatusDialog statusDialog)
	{
		completed = false;
		this.archiveFile = archiveFile;
		this.statusDialog = statusDialog;

	}

	public LoadTask(ArchiveFile archiveFile, ArchiveNode archiveNode, StatusDialog statusDialog)
	{
		completed = false;
		this.archiveFile = archiveFile;
		this.archiveNode = archiveNode;
		this.statusDialog = statusDialog;
	}

	public void run()
	{
		try
		{
			if (archiveNode != null)
			{
				HashMap<String, FolderNode> foldersByName = new HashMap<String, FolderNode>();

				archiveFile.load();
				List<ArchiveEntry> entries = archiveFile.getEntries(statusDialog);
				DefaultMutableTreeNode parentNode;

				for (ArchiveEntry entry : entries)
				{
					parentNode = archiveNode;
					String path = entry.getFolderName();
					if (foldersByName.get(path) != null)
					{
						parentNode = foldersByName.get(path);
					}
					else
					{

						int length = path.length();

						int pos = 0;
						int index1;
						while (pos < length)
						{
							String name;
							int sep = path.indexOf('\\', pos);
							if (sep < 0)
							{
								name = path.substring(pos);
								pos = length;
							}
							else
							{
								name = path.substring(pos, sep);
								pos = sep + 1;
							}

							if (foldersByName.get(name) != null)
							{
								parentNode = foldersByName.get(name);
								break;
							}

							int count = parentNode.getChildCount();
							boolean insert = true;
							index1 = 0;
							while (index1 < count)
							{
								TreeNode compare = parentNode.getChildAt(index1);
								if (!(compare instanceof FolderNode))
									break;
								FolderNode folderNode = (FolderNode) compare;
								int diff = name.compareTo(folderNode.getName());
								if (diff <= 0)
								{
									if (diff == 0)
									{
										insert = false;
										parentNode = folderNode;
									}
									break;
								}
								index1++;
							}

							if (insert)
							{
								FolderNode folderNode = new FolderNode(name);
								parentNode.insert(folderNode, index1);
								parentNode = folderNode;
								foldersByName.put(path, folderNode);
							}
						}

					}

					//ignore order, just bang them in in the order loaded (which is in fact in order by hashcode?)
					// this is incredibly slow otherwise
					/*int count = parentNode.getChildCount();
						String name = entry.getFileName();
						int index2;
						for (index2 = 0; index2 < count; index2++)
						{
							TreeNode compare = parentNode.getChildAt(index2);
							if (!(compare instanceof FileNode))
								continue;
							FileNode fileNode2 = (FileNode) compare;
							if (name.compareTo(fileNode2.getEntry().getFileName()) < 0)
								break;
						}*/

					FileNode fileNode = new FileNode(entry);
					//parentNode.insert(fileNode, index2);
					parentNode.add(fileNode);
				}

			}
			else
			{
				archiveFile.load();
			}
			 
			completed = true;
		}
		catch (DBException exc)
		{
			Main.logException("Unable to load archive file", exc);
		}
		catch (IOException exc)
		{
			Main.logException("Unable to read archive file", exc);
		}
		catch (Throwable exc)
		{
			Main.logException("Exception while loading archive", exc);
		}
		
		if (statusDialog != null)
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					statusDialog.closeDialog(completed);
				}
			});
		}
	}
}