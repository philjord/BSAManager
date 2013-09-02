// Decompiled by DJ v3.6.6.79 Copyright 2004 Atanas Neshkov  Date: 5/27/2009 3:52:54 PM
// Home Page : http://members.fortunecity.com/neshkov/dj.html  - Check often for new version!
// Decompiler options: packimports(3) 
// Source File Name:   StatusDialog.java

package FO3Archive;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

public class StatusDialog extends JDialog
{
	private JFrame parent;

	private JLabel messageText;

	private JProgressBar progressBar;

	private int status;

	private String deferredText;

	private int deferredProgress;

	public StatusDialog(JFrame parent, String text)
	{
		super(parent, "Fallout 3 Archive Utility", true);
		status = -1;
		this.parent = parent;
		JPanel progressPane = new JPanel();
		progressPane.setLayout(new BoxLayout(progressPane, 1));
		progressPane.add(Box.createVerticalStrut(15));
		messageText = new JLabel("<html><b>" + text + "</b></html>");
		progressPane.add(messageText);
		progressPane.add(Box.createVerticalStrut(15));
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressPane.add(progressBar);
		progressPane.add(Box.createVerticalStrut(15));
		JPanel contentPane = new JPanel();
		contentPane.add(progressPane);
		setContentPane(contentPane);
	}

	public int showDialog()
	{
		pack();
		setLocationRelativeTo(parent);
		setVisible(true);
		return status;
	}

	public void closeDialog(boolean completed)
	{
		status = completed ? 1 : 0;
		setVisible(false);
		dispose();
	}

	public void updateMessage(String text)
	{
		text = "<html><b>" + text + "</b></html>";
		if (SwingUtilities.isEventDispatchThread())
		{
			messageText.setText(text);
		}
		else
		{
			deferredText = text;
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					messageText.setText(deferredText);
				}
			});
		}
	}

	public void updateProgress(int progress)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			progressBar.setValue(progress);
		}
		else
		{
			deferredProgress = progress;
			SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					progressBar.setValue(deferredProgress);
				}
			});
		}
	}

}