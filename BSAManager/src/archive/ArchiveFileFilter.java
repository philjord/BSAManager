// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   ArchiveFileFilter.java

package archive;

import java.io.File;

import javax.swing.filechooser.FileFilter;

public class ArchiveFileFilter extends FileFilter
{

	public ArchiveFileFilter()
	{
	}

	public String getDescription()
	{
		return "Archive Files (*.bsa, *.ba2)";
	}

	public boolean accept(File file)
	{
		boolean accept = false;
		if (file.isFile())
		{
			String name = file.getName();
			int sep = name.lastIndexOf('.');
			if (sep > 0)
			{
				String extension = name.substring(sep);
				if (extension.equalsIgnoreCase(".bsa") || extension.equalsIgnoreCase(".ba2"))
					accept = true;
			}
		}
		else
		{
			accept = true;
		}
		return accept;
	}
}