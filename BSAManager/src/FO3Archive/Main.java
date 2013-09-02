// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   Main.java

package FO3Archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

// Referenced classes of package FO3Archive:
//            MainWindow

public class Main
{
	public static JFrame mainWindow;

	public static String fileSeparator;

	public static String lineSeparator;

	public static boolean useShellFolder = true;

	public static String tmpDir;

	public static File propFile;

	public static Properties properties;

	private static String deferredText;

	private static Throwable deferredException;

	public Main()
	{
	}

	public static void main(String args[])
	{
		try
		{
			fileSeparator = System.getProperty("file.separator");
			lineSeparator = System.getProperty("line.separator");
			tmpDir = System.getProperty("java.io.tmpdir");
			String option = System.getProperty("UseShellFolder");
			if (option != null && option.equals("0"))
				useShellFolder = false;
			String filePath = (new StringBuilder()).append(System.getProperty("user.home")).append(fileSeparator)
					.append("Application Data").append(fileSeparator).append("ScripterRon").toString();
			File dirFile = new File(filePath);
			if (!dirFile.exists())
				dirFile.mkdirs();
			filePath = (new StringBuilder()).append(filePath).append(fileSeparator).append("FO3Archive.properties").toString();
			propFile = new File(filePath);
			properties = new Properties();
			if (propFile.exists())
			{
				FileInputStream in = new FileInputStream(propFile);
				properties.load(in);
				in.close();
			}
			properties.setProperty("java.version", System.getProperty("java.version"));
			properties.setProperty("java.home", System.getProperty("java.home"));
			properties.setProperty("os.name", System.getProperty("os.name"));
			properties.setProperty("sun.os.patch.level", System.getProperty("sun.os.patch.level"));
			properties.setProperty("user.name", System.getProperty("user.name"));
			properties.setProperty("user.home", System.getProperty("user.home"));
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					Main.createAndShowGUI();
				}
			});
		}
		catch (Throwable exc)
		{
			logException("Exception during program initialization", exc);
		}
	}

	public static void createAndShowGUI()
	{
		try
		{
			mainWindow = new MainWindow();
			mainWindow.pack();
			mainWindow.setVisible(true);
		}
		catch (Throwable exc)
		{
			logException("Exception while initializing application window", exc);
		}
	}

	public static void saveProperties()
	{
		try
		{
			FileOutputStream out = new FileOutputStream(propFile);
			properties.store(out, "FO3Archive Properties");
			out.close();
		}
		catch (Throwable exc)
		{
			logException("Exception while saving application properties", exc);
		}
	}

	public static void logException(String text, Throwable exc)
	{
		System.out.println("excepton: " + text);
		exc.printStackTrace();
		
		System.runFinalization();
		System.gc();
		if (SwingUtilities.isEventDispatchThread())
		{
			String string = "<html><b>" + text + "</b><br><br>" + "<b>" + exc.toString() + "</b><br><br>";
			StackTraceElement trace[] = exc.getStackTrace();
			int count = 0;
			StackTraceElement arr$[] = trace;
			int len$ = arr$.length;
			for (int i$ = 0; i$ < len$; i$++)
			{
				StackTraceElement elem = arr$[i$];
				string += elem.toString() + "<br>";
				if (++count == 25)
					break;
			}

			string += "</html>";
			JOptionPane.showMessageDialog(mainWindow, string, "Error", 0);
		}
		else if (deferredException == null)
		{
			deferredText = text;
			deferredException = exc;
			try
			{
				SwingUtilities.invokeAndWait(new Runnable()
				{
					public void run()
					{
						Main.logException(Main.deferredText, Main.deferredException);
						Main.deferredException = null;
						Main.deferredText = null;
					}

				});
			}
			catch (Throwable swingException)
			{
				deferredException = null;
				deferredText = null;
			}
		}
	}

}