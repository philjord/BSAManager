package FO3Archive;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

public class MainWindow extends JFrame implements ActionListener
{

	private boolean windowMinimized;

	private JTree tree;

	private DefaultTreeModel treeModel;

	private ArchiveFile archiveFile;

	public MainWindow()
	{
		super("Fallout 3 Archive Utility");
		windowMinimized = false;
		setDefaultCloseOperation(2);
		String propValue = Main.properties.getProperty("window.main.position");
		if (propValue != null)
		{
			int sep = propValue.indexOf(',');
			int frameX = Integer.parseInt(propValue.substring(0, sep));
			int frameY = Integer.parseInt(propValue.substring(sep + 1));
			setLocation(frameX, frameY);
		}
		int frameWidth = 800;
		int frameHeight = 640;
		propValue = Main.properties.getProperty("window.main.size");
		if (propValue != null)
		{
			int sep = propValue.indexOf(',');
			frameWidth = Integer.parseInt(propValue.substring(0, sep));
			frameHeight = Integer.parseInt(propValue.substring(sep + 1));
		}
		setPreferredSize(new Dimension(frameWidth, frameHeight));
		JMenuBar menuBar = new JMenuBar();
		menuBar.setOpaque(true);
		JMenu menu = new JMenu("File");
		menu.setMnemonic(70);
		JMenuItem menuItem = new JMenuItem("New Archive");
		menuItem.setActionCommand("new");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuItem = new JMenuItem("Open Archive");
		menuItem.setActionCommand("open");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuItem = new JMenuItem("Close Archive");
		menuItem.setActionCommand("close");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuItem = new JMenuItem("Exit Program");
		menuItem.setActionCommand("exit");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuBar.add(menu);
		menu = new JMenu("Action");
		menu.setMnemonic(65);
		menuItem = new JMenuItem("Extract Selected Files");
		menuItem.setActionCommand("extract selected");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuItem = new JMenuItem("Extract All Files");
		menuItem.setActionCommand("extract all");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuBar.add(menu);
		menu = new JMenu("Help");
		menu.setMnemonic(72);
		menuItem = new JMenuItem("About");
		menuItem.setActionCommand("about");
		menuItem.addActionListener(this);
		menu.add(menuItem);
		menuBar.add(menu);
		setJMenuBar(menuBar);
		treeModel = new DefaultTreeModel(new ArchiveNode());
		tree = new JTree(treeModel);
		JScrollPane scrollPane = new JScrollPane(tree);
		scrollPane.setPreferredSize(new Dimension(700, 540));
		JPanel contentPane = new JPanel();
		contentPane.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
		contentPane.add(scrollPane);
		setContentPane(contentPane);
		addWindowListener(new ApplicationWindowListener());
	}

	public void actionPerformed(ActionEvent ae)
	{
		try
		{
			String action = ae.getActionCommand();
			if (action.equals("new"))
				newFile();
			else if (action.equals("open"))
				openFile();
			else if (action.equals("close"))
				closeFile();
			else if (action.equals("exit"))
				exitProgram();
			else if (action.equals("about"))
				aboutProgram();
			else if (action.equals("extract selected"))
				extractFiles(false);
			else if (action.equals("extract all"))
				extractFiles(true);
		}
		catch (Throwable exc)
		{
			Main.logException("Exception while processing action event", exc);
		}
	}

	private void newFile() throws InterruptedException, IOException
	{
		closeFile();
		String currentDirectory = Main.properties.getProperty("current.directory");
		JFileChooser chooser;
		if (currentDirectory != null)
		{
			File dirFile = new File(currentDirectory);
			if (dirFile.exists() && dirFile.isDirectory())
				chooser = new JFileChooser(dirFile);
			else
				chooser = new JFileChooser();
		}
		else
		{
			chooser = new JFileChooser();
		}
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.valueOf(Main.useShellFolder));
		chooser.setDialogTitle("New Archive File");
		chooser.setApproveButtonText("Create");
		chooser.setFileFilter(new ArchiveFileFilter());
		if (chooser.showOpenDialog(this) != 0)
			return;
		File file = chooser.getSelectedFile();
		Main.properties.setProperty("current.directory", file.getParent());
		if (file.exists())
		{
			int option = JOptionPane.showConfirmDialog(this, file.getPath() + " already exists.  Do you want to overwrite it?",
					"File already exists", 0);
			if (option != 0)
				return;
			if (!file.delete())
			{
				JOptionPane.showMessageDialog(this, "Unable to delete " + file.getPath(), "Delete failed", 0);
				return;
			}
		}
		String extractDirectory = Main.properties.getProperty("extract.directory");
		File dirFile;
		if (extractDirectory != null)
		{
			dirFile = new File(extractDirectory);
			if (dirFile.exists() && dirFile.isDirectory())
				chooser = new JFileChooser(dirFile);
			else
				chooser = new JFileChooser();
		}
		else
		{
			chooser = new JFileChooser();
		}
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.valueOf(Main.useShellFolder));
		chooser.setDialogTitle("Select Source Directory");
		chooser.setApproveButtonText("Select");
		chooser.setFileSelectionMode(1);
		if (chooser.showOpenDialog(this) != 0)
			return;
		dirFile = chooser.getSelectedFile();
		Main.properties.setProperty("extract.directory", dirFile.getPath());
		StatusDialog statusDialog = new StatusDialog(this, "Creating " + file.getPath());
		CreateTask createTask = new CreateTask(file, dirFile, statusDialog);
		createTask.start();
		int status = statusDialog.showDialog();
		createTask.join();
		if (status != 1)
			return;
		if (!file.exists())
		{
			JOptionPane.showMessageDialog(this, "No files were included in the archive", "Archive empty", 1);
			return;
		}
		ArchiveFile archiveFile2 = new ArchiveFile(file);
		ArchiveNode archiveNode = new ArchiveNode(archiveFile2);
		statusDialog = new StatusDialog(this, "Loading " + archiveFile2.getName());
		LoadTask loadTask = new LoadTask(archiveFile2, archiveNode, statusDialog);
		loadTask.start();
		status = statusDialog.showDialog();
		loadTask.join();
		if (status == 1)
		{
			this.archiveFile = archiveFile2;
			treeModel = new DefaultTreeModel(archiveNode);
			tree.setModel(treeModel);
		}
		else
		{
			archiveFile2.close();
		}
	}

	private void openFile() throws InterruptedException, IOException
	{
		closeFile();
		String currentDirectory = Main.properties.getProperty("current.directory");
		JFileChooser chooser;
		if (currentDirectory != null)
		{
			File dirFile = new File(currentDirectory);
			if (dirFile.exists() && dirFile.isDirectory())
				chooser = new JFileChooser(dirFile);
			else
				chooser = new JFileChooser();
		}
		else
		{
			chooser = new JFileChooser();
		}
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.valueOf(Main.useShellFolder));
		chooser.setDialogTitle("Select Archive File");
		chooser.setFileFilter(new ArchiveFileFilter());
		if (chooser.showOpenDialog(this) == 0)
		{
			File file = chooser.getSelectedFile();
			Main.properties.setProperty("current.directory", file.getParent());
			ArchiveFile archiveFile2 = new ArchiveFile(file);
			ArchiveNode archiveNode = new ArchiveNode(archiveFile2);
			StatusDialog statusDialog = new StatusDialog(this, "Loading " + archiveFile2.getName());
			LoadTask loadTask = new LoadTask(archiveFile2, archiveNode, statusDialog);
			loadTask.start();
			int status = statusDialog.showDialog();
			loadTask.join();
			if (status == 1)
			{
				this.archiveFile = archiveFile2;
				treeModel = new DefaultTreeModel(archiveNode);
				tree.setModel(treeModel);
			}
			else
			{
				archiveFile2.close();
			}
		}
	}

	private void closeFile() throws IOException
	{
		if (archiveFile != null)
		{
			archiveFile.close();
			archiveFile = null;
		}
		treeModel = new DefaultTreeModel(new ArchiveNode());
		tree.setModel(treeModel);
	}

	private void extractFiles(boolean extractAllFiles) throws InterruptedException
	{
		if (archiveFile == null)
		{
			JOptionPane.showMessageDialog(this, "You must open an archive file", "No archive file", 0);
			return;
		}
		List<ArchiveEntry> entries = null;
		if (extractAllFiles)
		{
			StatusDialog statusDialog = new StatusDialog(this, "Extracting files from " + archiveFile.getName());
			entries = archiveFile.getEntries(statusDialog);
		}
		else
		{
			TreePath treePaths[] = tree.getSelectionPaths();
			if (treePaths == null)
			{
				JOptionPane.showMessageDialog(this, "You must select one or more files to extract", "No files selected", 0);
				return;
			}
			entries = new ArrayList<ArchiveEntry>(100);
			for (int i = 0; i < treePaths.length; i++)
			{
				TreePath treePath = treePaths[i];
				Object obj = treePath.getLastPathComponent();
				if (obj instanceof FolderNode)
				{
					addFolderChildren((FolderNode) obj, entries);
					continue;
				}
				if (!(obj instanceof FileNode))
					continue;
				ArchiveEntry entry = ((FileNode) obj).getEntry();
				if (!entries.contains(entry))
					entries.add(entry);
			}

		}
		String extractDirectory = Main.properties.getProperty("extract.directory");
		JFileChooser chooser;
		if (extractDirectory != null)
		{
			File dirFile = new File(extractDirectory);
			if (dirFile.exists() && dirFile.isDirectory())
				chooser = new JFileChooser(dirFile);
			else
				chooser = new JFileChooser();
		}
		else
		{
			chooser = new JFileChooser();
		}
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.valueOf(Main.useShellFolder));
		chooser.setDialogTitle("Select Destination Directory");
		chooser.setApproveButtonText("Select");
		chooser.setFileSelectionMode(1);
		if (chooser.showOpenDialog(this) == 0)
		{
			File dirFile = chooser.getSelectedFile();
			Main.properties.setProperty("extract.directory", dirFile.getPath());
			StatusDialog statusDialog = new StatusDialog(this, "Extracting files from " + archiveFile.getName());
			ExtractTask extractTask = new ExtractTask(dirFile, archiveFile, entries, statusDialog);
			extractTask.start();
			statusDialog.showDialog();
			extractTask.join();
		}
	}

	private void addFolderChildren(FolderNode folderNode, List<ArchiveEntry> entries)
	{
		int count = folderNode.getChildCount();
		for (int i = 0; i < count; i++)
		{
			TreeNode node = folderNode.getChildAt(i);
			if (node instanceof FolderNode)
			{
				addFolderChildren((FolderNode) node, entries);
				continue;
			}
			if (!(node instanceof FileNode))
				continue;
			ArchiveEntry entry = ((FileNode) node).getEntry();
			if (!entries.contains(entry))
				entries.add(entry);
		}

	}

	private void exitProgram()
	{
		if (!windowMinimized)
		{
			Point p = Main.mainWindow.getLocation();
			Dimension d = Main.mainWindow.getSize();
			Main.properties.setProperty("window.main.position", "" + p.x + "," + p.y);
			Main.properties.setProperty("window.main.size", "" + d.width + "," + d.height);
		}
		Main.saveProperties();
		System.exit(0);
	}

	private void aboutProgram()
	{
		String info = "<html>Fallout 3 Archive Utility Version 1.0<br>";
		info += "<br>User name: ";
		info += System.getProperty("user.name");
		info += "<br>Home directory: ";
		info += System.getProperty("user.home");
		info += "<br><br>OS: ";
		info += System.getProperty("os.name");
		info += "<br>OS version: ";
		info += System.getProperty("os.version");
		info += "<br>OS patch level: ";
		info += System.getProperty("sun.os.patch.level");
		info += "<br><br>Java vendor: ";
		info += System.getProperty("java.vendor");
		info += "<br>Java version: ";
		info += System.getProperty("java.version");
		info += "<br>Java home directory: ";
		info += System.getProperty("java.home");
		info += "<br>Java class path: ";
		info += System.getProperty("java.class.path");
		info += "</html>";
		JOptionPane.showMessageDialog(this, info.toString(), "About Fallout 3 Archive Utility", 1);
	}

	private class ApplicationWindowListener extends WindowAdapter
	{

		public ApplicationWindowListener()
		{

		}

		public void windowIconified(WindowEvent we)
		{
			windowMinimized = true;
		}

		public void windowDeiconified(WindowEvent we)
		{
			windowMinimized = false;
		}

		public void windowClosing(WindowEvent we)
		{
			try
			{
				exitProgram();
			}
			catch (Exception exc)
			{
				Main.logException("Exception while closing application window", exc);
			}
		}

	}

}