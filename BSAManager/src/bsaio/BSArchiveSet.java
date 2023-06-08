package bsaio;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

public abstract class BSArchiveSet extends ArrayList<ArchiveFile> {
	public ArrayList<Thread>	loadThreads	= new ArrayList<Thread>();

	private String				name		= "";

	/**
	 * No display archive loader
	 * @param file
	 */
	public void loadFile(final FileChannel file, String fileName) {
		// don't double load ever, use for i to avoid concurrent mod exceptions
		for (int i = 0; i < this.size(); i++) {
			if (this.get(i).getName().equals(fileName))
				return;
		}

		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					long start = System.currentTimeMillis();
					System.out.println("BSA File Set loading " + fileName);
					ArchiveFile archiveFile = ArchiveFile.createArchiveFile(file, fileName);
					archiveFile.load(false);
					synchronized (BSArchiveSet.this) {
						add(archiveFile);
					}
					System.out.println("BSA File Set loaded " + fileName + " in " + (System.currentTimeMillis() - start));
				} catch (DBException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		};
		t.setName("BSArchiveSet ArchiveFile Loader");

		loadThreads.add(t);
		t.start();

	}

	public void close() throws IOException {
		for (ArchiveFile af : this) {
			af.close();
		}
	}

	public String getName() {
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
