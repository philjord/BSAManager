// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   FileNode.java

package FO3Archive;

import javax.swing.tree.DefaultMutableTreeNode;

// Referenced classes of package FO3Archive:
//            ArchiveEntry

public class FileNode extends DefaultMutableTreeNode implements Comparable<FileNode>
{
	private ArchiveEntry entry;

	public FileNode(ArchiveEntry entry)
	{
		super(entry);
		this.entry = entry;
	}

	public ArchiveEntry getEntry()
	{
		return entry;
	}

	public boolean equals(Object obj)
	{
		boolean equal = false;
		if (obj != null && (obj instanceof FileNode) && entry.getFileName().equals(((FileNode) obj).getEntry().getFileName()))
			equal = true;
		return equal;
	}

	public int compareTo(FileNode compare)
	{
		return entry.getFileName().compareTo(compare.getEntry().getFileName());
	}

	public String toString()
	{
		return entry.getFileName();
	}

}