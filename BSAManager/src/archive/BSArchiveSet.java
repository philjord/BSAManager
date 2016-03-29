package archive;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

 

public class BSArchiveSet extends ArrayList<ArchiveFile>
{
	public ArrayList<Thread> loadThreads = new ArrayList<Thread>();

	private String name = "";

	/**
	 * If the root file is not a folder, it is assumed to be teh esm file and so it's parent folder is used
	 * 
	 * @param rootFilename
	 * @param loadSiblingBsaFiles
	 * @param loadNodes set true if you want to add this bsa file set to a tree
	 * @param sopErrOnly 
	 */
	public BSArchiveSet(String rootFilename, boolean loadSiblingBsaFiles, boolean loadNodes)
	{
		this(new String[] { rootFilename }, loadSiblingBsaFiles, loadNodes);
	}

	public BSArchiveSet(String[] rootFilenames, boolean loadSiblingBsaFiles, boolean loadNodes)
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

		for (Thread loadTask : loadThreads)
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
		loadThreads.clear();

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

		Thread t = new Thread() {
			public void run()
			{
				try
				{
					ArchiveFile archiveFile = ArchiveFile.createArchiveFile(file);
					archiveFile.load();
					add(archiveFile);
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
		};
		t.setName("BSArchiveSet ArchiveFile Loader");

		loadThreads.add(t);
		t.start();

	}

	public void close() throws IOException
	{
		for (ArchiveFile af : this)
		{
			af.close();
		}
	}

	public List<ArchiveEntry> getEntries()
	{
		List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		for (ArchiveFile af : this)
		{
			ret.addAll(af.getEntries());
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
