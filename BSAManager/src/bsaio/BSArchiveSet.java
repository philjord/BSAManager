package bsaio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class BSArchiveSet extends ArrayList<ArchiveFile>
{	
	public ArrayList<Thread> loadThreads = new ArrayList<Thread>();

	private String name = "";

	/**
	 * If the root file is not a folder, it is assumed to be the esm file and so it's parent folder is used
	 * A folder of resources will load all bsa files and check for resource sub folders
	 * 
	 * @param rootFilename
	 * @param folderOfResources
	 * @param sopErrOnly 
	 */
	public BSArchiveSet(String rootFilename, boolean folderOfResources)
	{
		this(new String[] { rootFilename }, folderOfResources);
	}

	public BSArchiveSet(String[] rootFilenames, boolean folderOfResources)
	{
		long start = System.currentTimeMillis();
		for (String rootFilename : rootFilenames)
		{
			File rootFile = new File(rootFilename);
			if (rootFile.exists())
			{
				if (folderOfResources)
				{
					if (!rootFile.isDirectory())
					{
						rootFile = rootFile.getParentFile();
					}
					
					
					name = rootFile.getAbsolutePath();
					for (File file : rootFile.listFiles())
					{
						if (file.getName().toLowerCase().endsWith(".bsa") //
								|| file.getName().toLowerCase().endsWith(".ba2")//
								|| file.getName().toLowerCase().endsWith(".obb")) //android expansion file name
						{
							loadFile(file);
						}
					}
				}
				else
				{
					if (!rootFile.isDirectory() && (rootFile.getName().toLowerCase().endsWith(".bsa") //
							|| rootFile.getName().toLowerCase().endsWith(".ba2")//
							|| rootFile.getName().toLowerCase().endsWith(".obb")//
					))
					{
						name = rootFile.getAbsolutePath();
						loadFile(rootFile);
					}
					else
					{
						System.out.println("BSAFileSet bad non sibling load of " + rootFilename);
					}
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
		System.out.println("BSAFileSet (" + loadThreads.size() + ") completely loaded in " + (System.currentTimeMillis() - start));

		loadThreads.clear();

		if (this.size() == 0)
		{
			System.out.println("BSAFileSet loaded no files using root: " + rootFilenames[0]);
		}
	}

	/**
	 * No display archive loader
	 * @param file
	 */
	public void loadFile(final File file)
	{
		// don't double load ever, use for i to avoid concurrent mod exceptions
		for (int i = 0; i < this.size(); i++)
		{
			if (this.get(i).getName().equals(file.getPath()))
				return;
		}

		Thread t = new Thread() {
			@Override
			public void run()
			{
				try
				{
					long start = System.currentTimeMillis();
					System.out.println("BSA File Set loading " + file);
					ArchiveFile archiveFile = ArchiveFile.createArchiveFile(file);
					archiveFile.load(false);
					synchronized (BSArchiveSet.this)
					{
						add(archiveFile);
					}
					System.out.println("BSA File Set loaded " + file + " in " + (System.currentTimeMillis() - start));
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
	
	public String getName()
	{
		return name;
	}

/*	public List<ArchiveEntry> getEntries()
	{
		List<ArchiveEntry> ret = new ArrayList<ArchiveEntry>();
		for (ArchiveFile af : this)
		{
			ret.addAll(af.getEntries());
		}
		return ret;
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
	}*/
}
