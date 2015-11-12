// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   FolderNode.java

package gui;

import javax.swing.tree.DefaultMutableTreeNode;

public class FolderNode extends DefaultMutableTreeNode implements Comparable<FolderNode>
{
	private String name;

	public FolderNode(String name)
	{
		super(name);
		this.name = name;
	}

	public String getName()
	{
		return name;
	}

	public boolean equals(Object obj)
	{
		boolean equal = false;
		if (obj != null && (obj instanceof FolderNode) && name.equals(((FolderNode) obj).getName()))
			equal = true;
		return equal;
	}

	public int compareTo(FolderNode compare)
	{
		return name.compareTo(compare.getName());
	}

}