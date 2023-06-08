package bsaio;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BSArchiveSetFile extends BSArchiveSet {
	/**
	 * If the root file is not a folder, it is assumed to be the esm file and so it's parent folder is used as a folder
	 * of resources will load all bsa files and check for resource sub folders
	 * 
	 * @param rootFilename
	 * @param folderOfResources
	 * @param sopErrOnly
	 */
	public BSArchiveSetFile(String rootFilename, boolean folderOfResources) {
		this(new String[] {rootFilename}, folderOfResources);
	}

	public BSArchiveSetFile(String[] rootFilenames, boolean folderOfResources) {
		long start = System.currentTimeMillis();
		for (String rootFilename : rootFilenames) {
			try {
				File rootFile = new File(rootFilename);
				if (rootFile.exists()) {
					if (folderOfResources) {
						if (!rootFile.isDirectory()) {
							rootFile = rootFile.getParentFile();
						}

						for (File file : rootFile.listFiles()) {
							if (file.getName().toLowerCase().endsWith(".bsa") //
								|| file.getName().toLowerCase().endsWith(".ba2")//
								|| file.getName().toLowerCase().endsWith(".obb")) //android expansion file name
							{
								FileInputStream fis = new FileInputStream(file);
								loadFile(fis.getChannel(), file.getName());
							}
						}
					} else {
						if (!rootFile.isDirectory() && (rootFile.getName().toLowerCase().endsWith(".bsa") //
														|| rootFile.getName().toLowerCase().endsWith(".ba2")//
														|| rootFile.getName().toLowerCase().endsWith(".obb")//
						)) {
							FileInputStream fis = new FileInputStream(rootFile);
							loadFile(fis.getChannel(), rootFile.getName());
						} else {
							System.out.println("BSAFileSet bad non sibling load of " + rootFilename);
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		for (Thread loadTask : loadThreads) {
			try {
				loadTask.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.println(
				"BSAFileSet (" + loadThreads.size() + ") completely loaded in " + (System.currentTimeMillis() - start));

		loadThreads.clear();

		if (this.size() == 0) {
			System.out.println("BSAFileSet loaded no files using root: " + rootFilenames [0]);
		}
	}
}
