// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   DBException.java

package bsaio;

public class DBException extends Exception
{
	public DBException()
	{
	}

	public DBException(String exceptionMsg)
	{
		super(exceptionMsg);
	}

	public DBException(String exceptionMsg, Throwable cause)
	{
		super(exceptionMsg, cause);
	}
}