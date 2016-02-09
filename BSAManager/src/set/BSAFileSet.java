package set;

import gui.ArchiveNode;
import gui.StatusDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import archive.ArchiveEntry;
import archive.ArchiveFile;
import archive.DBException;
import archive.LoadTask;

public class BSAFileSet extends ArrayList<ArchiveFile>
{
	public ArrayList<LoadTask> loadTasks = new ArrayList<LoadTask>();

	public ArrayList<ArchiveNode> nodes = new ArrayList<ArchiveNode>();

	private String name = "";

	/**
	 * If the root file is not a folder, it is assumed to be teh esm file and so it's parent folder is used
	 * 
	 * @param rootFilename
	 * @param loadSiblingBsaFiles
	 * @param loadNodes set true if you want to add this bsa file set to a tree
	 * @param sopErrOnly 
	 */
	public BSAFileSet(String rootFilename, boolean loadSiblingBsaFiles, boolean loadNodes)
	{
		this(new String[]
		{ rootFilename }, loadSiblingBsaFiles, loadNodes);
	}

	public BSAFileSet(String[] rootFilenames, boolean loadSiblingBsaFiles, boolean loadNodes)
	{
		for (String rootFilename : rootFilenames)
		{
			File rootFile = new File(rootFilename);
			if (loadSiblingBsaFiles)
			{
				if (!rootFile.isDirectory())
				{
					rootFile = rootFile.getParentFile();
				}
				name = rootFile.getAbsolutePath();
				for (File file : rootFile.listFiles())
				{
					if (file.getName().toLowerCase().endsWith(".bsa") || file.getName().toLowerCase().endsWith(".ba2"))
					{
						loadFile(file, loadNodes);
					}
				}

			}
			else
			{
				if (!rootFile.isDirectory()
						&& (rootFile.getName().toLowerCase().endsWith(".bsa") || rootFile.getName().toLowerCase().endsWith(".ba2")))
				{
					name = rootFile.getAbsolutePath();
					loadFile(rootFile, loadNodes);
				}
				else
				{
					System.out.println("BSAFileSet bad non sibling load of " + rootFilename);
				}
			}
		}

		for (LoadTask loadTask : loadTasks)
		{
			try
			{
				loadTask.join();
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		loadTasks.clear();

		if (this.size() == 0)
		{
			System.out.println("BSAFileSet loaded no files using root: " + rootFilenames[0]);
		}
	}

	/**
	 * Loading Nodes uses teh progress dialog system
	 * @param file
	 * @param loadNodes
	 */
	public void loadFile(final File file, boolean loadNodes)
	{
		// don't double load ever
		for (ArchiveFile af : this)
		{
			if (af.getName().equals(file.getPath()))
				return;
		}

		System.out.println("BSA File Set loading " + file);

		try
		{
			ArchiveFile archiveFile = ArchiveFile.createArchiveFile(file);

			try
			{
				if (loadNodes)
				{
					StatusDialog statusDialog = new StatusDialog(null, "Loading " + archiveFile.getName());
					ArchiveNode archiveNode = new ArchiveNode(archiveFile);

					LoadTask loadTask = new LoadTask(archiveFile, archiveNode, statusDialog);
					loadTask.start();

					int status = statusDialog.showDialog();

					loadTask.join();
					if (status == 1)
					{
						add(archiveFile);
						nodes.add(archiveNode);
					}
					else
					{
						System.out.println("status != 1 in bsa loader? " + status + " " + file.getAbsolutePath());
					}
				}
				else
				{
					LoadTask loadTask = new LoadTask(archiveFile, null);
					add(archiveFile);
					loadTask.start();
					loadTasks.add(loadTask);
				}
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
				try
				{
					archiveFile.close();
				}
				catch (IOException e2)
				{
					e2.printStackTrace();
				}
			}
		}
		catch (DBException e1)
		{
			e1.printStackTrace();
		}
		catch (IOException e1)
		{
			e1.printStackTrace();
		}

	}

	public void close() throws IOException
	{
		for (ArchiveFile af : this)
		{
			af.close();
		}
		nodes.clear();

	}

	public List<ArchiveEntry> getEntries(StatusDialog statusDialog)
	{
		List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		for (ArchiveFile af : this)
		{
			ret.addAll(af.getEntries(statusDialog));
		}
		return ret;
	}

	public String getName()
	{
		return name;
	}

	public List<ArchiveFile> getMeshArchives()
	{
		List<ArchiveFile> ret = new ArrayList<ArchiveFile>();
		for (ArchiveFile af : this)
		{
			if (af.hasNifOrKf())
				ret.add(af);
		}
		return ret;
	}

	public List<ArchiveFile> getSoundArchives()
	{
		List<ArchiveFile> ret = new ArrayList<ArchiveFile>();
		for (ArchiveFile af : this)
		{
			if (af.hasSounds())
				ret.add(af);
		}
		return ret;
	}

	public List<ArchiveFile> getTextureArchives()
	{
		List<ArchiveFile> ret = new ArrayList<ArchiveFile>();
		for (ArchiveFile af : this)
		{
			if (af.hasDDS())
				ret.add(af);
		}
		return ret;
	}
}
