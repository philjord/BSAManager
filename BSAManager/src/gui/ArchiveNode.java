// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveNode.java

package gui;

import javax.swing.tree.DefaultMutableTreeNode;

import archive.ArchiveFile;

// Referenced classes of package FO3Archive:
//            ArchiveFile

public class ArchiveNode extends DefaultMutableTreeNode
{
	private ArchiveFile archiveFile;

	public ArchiveNode()
	{
		super("(closed)");
	}

	public ArchiveNode(ArchiveFile archiveFile)
	{
		super(archiveFile);
		this.archiveFile = archiveFile;
	}

	public ArchiveFile getArchiveFile()
	{
		return archiveFile;
	}
}