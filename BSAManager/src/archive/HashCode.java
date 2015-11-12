// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   HashCode.java

package archive;

import tools.io.PrimitiveBytes;

public class HashCode implements Comparable<HashCode>
{
	private long hash = 0L;

	public HashCode(String hashName, boolean isFolder)
	{
		String name = null;
		String ext = null;
		if (isFolder)
		{
			name = hashName;
		}
		else
		{
			int sep = hashName.lastIndexOf('.');
			if (sep < 0)
			{
				name = hashName;
			}
			else if (sep == 0)
			{
				ext = hashName;
			}
			else
			{
				name = hashName.substring(0, sep);
				ext = hashName.substring(sep);
			}
		}

		if (name != null && name.length() > 0)
		{
			byte buffer[] = PrimitiveBytes.getBytesFast(name);//name.getBytes();
			int length = buffer.length;
			hash = (buffer[length - 1] & 255L) + ((long) length << 16) + ((buffer[0] & 255L) << 24);
			if (length > 2)
			{
				hash += (buffer[length - 2] & 255L) << 8;
			}

			if (length > 3)
			{
				long subHash = 0L;
				for (int i = 1; i < length - 2; i++)
				{
					subHash = subHash * 0x1003fL + (buffer[i] & 255L) & 0xffffffffL;
				}

				hash += subHash << 32;
			}
		}

		if (ext != null && ext.length() > 0)
		{
			byte buffer[] = PrimitiveBytes.getBytesFast(ext);//ext.getBytes();
			int length = buffer.length;
			long subHash = 0L;
			for (int i = 0; i < length; i++)
			{
				subHash = subHash * 0x1003fL + (buffer[i] & 255L) & 0xffffffffL;
			}

			hash += subHash << 32;
			if (ext.equals(".nif"))
				hash |= 32768L;
			else if (ext.equals(".kf"))
				hash |= 128L;
			else if (ext.equals(".dds"))
				hash |= 32896L;
			else if (ext.equals(".wav"))
				hash |= 0x80000000L;
		}
	}

	public long getHash()
	{
		return hash;
	}

	public boolean equals(Object obj)
	{
		return (obj != null && (obj instanceof HashCode) && ((HashCode) obj).hash == hash);
	}

	public int compareTo(HashCode compare)
	{
		return hash == compare.hash ? 0 : (hash < compare.hash) ? -1 : 1;
	}

	public String toString()
	{
		return String.format("%08X-%08X", new Object[]
		{ Integer.valueOf((int) (hash >>> 32)), Integer.valueOf((int) (hash & -1L)) });
	}

}