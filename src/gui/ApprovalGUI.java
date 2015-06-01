package gui;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import management.DataBaseHandler;
import management.GlobalStuff;

import org.bson.Document;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

//NOTICE: for this to work, you must pass in the content type (ass, workout, weed, etc)
public class ApprovalGUI {

	private static Boolean lastWasApproved;
	private static boolean undoClicked;
	private static String lastApprovedLink;
	private static JFrame frame;
	private static JPanel topPanel;
	private static JPanel labelPanel;
	private static JPanel buttonPanel;
	private static JTextField captionTextField;
	private static JPanel picPanel;
	private static MongoCursor<Document> cursor;
	private static MongoClient mongoClient;
	private static Document currentContent;
	private static int numRemaining;
	//the type of content we're dealing with (ass, workout, etc, but not Pending anything)
	private static String kind;
	//maps a link to its caption, link is key, caption is value
	private static HashMap<String, String> approvedContent;
	private static LinkedList<String> schwagLinks;

	public static void loadNext() throws IOException {

		if (cursor.hasNext()) {
			undoClicked = false;
			currentContent = cursor.next();

			captionTextField.setText(currentContent.get("caption").toString());
			numRemaining--;
			String labelText = "number of pending " + kind +
					" images remaining: " + numRemaining;
			JLabel numRemainingLabel = new JLabel(labelText, SwingConstants.CENTER);
			labelPanel.removeAll();
			labelPanel.add(numRemainingLabel);
			labelPanel.setBackground(Color.GRAY);


			picPanel.removeAll();
			picPanel.add(getPicLabel());
			picPanel.setBackground(Color.GRAY);

			topPanel.removeAll();
			topPanel.add(labelPanel);
			topPanel.add(buttonPanel);
			topPanel.add(captionTextField);
			topPanel.add(picPanel);
			topPanel.setPreferredSize(new Dimension(333, 780));
			topPanel.setBackground(Color.GRAY);

			topPanel.revalidate();
			topPanel.repaint();
			frame.repaint();		
		}
		else {
			System.out.println("No content remaining");
		}
	}

	private static class AddListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			String link = currentContent.get("imglink").toString();
			approvedContent.put(link, captionTextField.getText());
			lastWasApproved = true;
			lastApprovedLink = link;
			try {
				ApprovalGUI.loadNext();
			} catch (IOException e1) {
				e1.printStackTrace();
			}			
		}		
	}

	private static class TrashListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {		
			schwagLinks.add(currentContent.get("imglink").toString());
			lastWasApproved = false;
			try {
				ApprovalGUI.loadNext();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}		
	}

	private static class UndoListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {

			if (lastWasApproved != null && !undoClicked) {
				if (lastWasApproved) {
					approvedContent.remove(lastApprovedLink);
				}
				else {
					schwagLinks.removeLast();
				}
				undoClicked = true;
				System.out.println("Undo completed");
			}
		}
	}

	private static class DoneListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			for (Entry<String, String> entry : approvedContent.entrySet()) {
				try {
					DataBaseHandler.newContent(entry.getValue(), entry.getKey(), kind);
					DataBaseHandler.removeContent("pending" + kind, entry.getKey());
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
			for (String link : schwagLinks) {
				try {
					DataBaseHandler.newContent(null, link, "schwagass");
					DataBaseHandler.removeContent("pending" + kind, link);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		}		
	}

	private static class SchwergsListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			System.out.println("implement SchwergsListener");
		}		
	}

	private static class ContentListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			
			frame.setVisible(false);
			frame.dispose();
			
			//TODO figure out how to use JComboBoxes (use it for selecting a type a content; ass, workout, etc)
			JComboBox<JButton> contentTypeList = new JComboBox<JButton>();
			//contentTypeList.setSelectedIndex(0);
			contentTypeList.add(new JButton("ass"));
			contentTypeList.add(new JButton("workout"));
			contentTypeList.addActionListener(new SelectionListener());
			
			JPanel panel = new JPanel();
			panel.add(contentTypeList);
			panel.setBackground(Color.GRAY);

			JFrame frame = new JFrame("Select Content Type");
			frame.add(panel);
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			frame.setSize(300, 100);
			frame.setLocationRelativeTo(null);	
			frame.setVisible(true);
		
			//TODO implement the below code (it creates the image GUI) in an action listener for the 
			//user selected content
			
			//			if (args.length == 0) {
			//				System.err.println("must pass in the type of images as an argument");
			//			}
			//
			//			kind = args[0];
			//
			//			if (!kind.equals("ass") &&
			//					!kind.equals("workout") &&
			//					!kind.equals("weed") &&
			//					!kind.equals("college") &&
			//					!kind.equals("canimals") &&
			//					!kind.equals("space")) {
			//				System.err.println("invalid argument, must be ass, workout, etc");
			//			}
			//			else {
			//				lastWasApproved = null;
			//				undoClicked = false;
			//				approvedContent = new HashMap<>();
			//				schwagLinks = new LinkedList<>();
			//
			//				mongoClient = new MongoClient();
			//				MongoDatabase db = mongoClient.getDatabase("Schwergsy");
			//
			//				MongoCollection<Document> collection = DataBaseHandler.getCollection("pending" + kind, db);
			//				numRemaining = (int) DataBaseHandler.getCollectionSize(
			//						collection.getNamespace().getCollectionName()) - 1;
			//
			//				FindIterable<Document> findIter = collection.find();
			//				cursor = findIter.iterator();
			//
			//				if (cursor.hasNext()) {
			//
			//					currentContent = cursor.next();
			//
			//					picPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			//					picPanel.add(getPicLabel());
			//					picPanel.setBackground(Color.GRAY);
			//
			//					String labelText = "number of pending " + kind +
			//							" images remaining: " + numRemaining;			
			//					JLabel numRemainingLabel = new JLabel(labelText, SwingConstants.CENTER);
			//					labelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			//					labelPanel.add(numRemainingLabel);
			//					labelPanel.setBackground(Color.GRAY);
			//
			//					Font font = new Font("SansSerif", Font.BOLD, 25);
			//					captionTextField = new JTextField();
			//					captionTextField.setFont(font);
			//					captionTextField.setText(currentContent.get("caption").toString());
			//					captionTextField.setPreferredSize(new Dimension(333,30));
			//
			//					JButton addButton = new JButton("Add");
			//					addButton.addActionListener(new AddListener());	
			//					JButton trashButton = new JButton("Trash");
			//					trashButton.addActionListener(new TrashListener());	
			//					JButton undoButton = new JButton("Undo");
			//					undoButton.addActionListener(new UndoListener());	
			//					JButton doneButton = new JButton("Done");
			//					doneButton.addActionListener(new DoneListener());		
			//
			//					buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			//					buttonPanel.add(addButton);
			//					buttonPanel.add(trashButton);
			//					buttonPanel.add(undoButton);
			//					buttonPanel.add(doneButton);
			//					buttonPanel.setBackground(Color.GRAY);
			//
			//					topPanel = new JPanel();
			//					topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
			//					topPanel.setAlignmentX(Container.LEFT_ALIGNMENT);
			//					topPanel.add(labelPanel);
			//					topPanel.add(buttonPanel);
			//					topPanel.add(captionTextField);
			//					topPanel.add(picPanel);
			//					topPanel.setPreferredSize(new Dimension(333, 780));
			//					topPanel.setBackground(Color.GRAY);
			//
			//					JPanel bottomPanel = new JPanel();
			//					bottomPanel.setBackground(Color.GRAY);
			//
			//					JPanel containerPanel = new JPanel();
			//					containerPanel.setLayout(new BorderLayout());	
			//					containerPanel.add(topPanel, BorderLayout.NORTH);
			//					containerPanel.add(bottomPanel, BorderLayout.CENTER);
			//
			//					JScrollPane scrPane = new JScrollPane(containerPanel);	
			//
			//					frame = new JFrame("Content Reviewer");			
			//					//So that these things close when we end the program
			//					frame.addWindowListener(new WindowAdapter()
			//					{
			//						public void windowClosing(WindowEvent e)
			//						{
			//							cursor.close();
			//							mongoClient.close();			        
			//						}
			//					});			
			//					frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			//					frame.getContentPane().add(scrPane);
			//					frame.pack();
			//					frame.setMinimumSize(new Dimension(300, 300));
			//					frame.setSize(800, 900);
			//					frame.setLocationRelativeTo(null);	
			//					frame.setVisible(true);	
			//				}
			//				else {
			//					System.out.println("No content found in pending" + kind);
			//				}
			//			}
		}		
	}
	
	private static class SelectionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			System.out.println("implement SchwergsListener");
		}		
	}

	/**
	 * Get the JLabel contain the image in currentContent
	 * 
	 * @return the JLabel
	 * @throws IOException
	 */
	public static JLabel getPicLabel() throws IOException {

		URL url = new URL(currentContent.get("imglink").toString());			
		BufferedImage bufferedImage = ImageIO.read(url);
		double newHeight = GlobalStuff.MAX_IMAGE_DIMENSION;
		double ratio = newHeight/bufferedImage.getHeight();
		double newWidth = bufferedImage.getWidth() * ratio;

		//to guarantee really wide images will be fully contained by the window
		if (newWidth > GlobalStuff.MAX_IMAGE_DIMENSION) {
			newWidth = GlobalStuff.MAX_IMAGE_DIMENSION;
			ratio = newWidth/bufferedImage.getWidth();
			newHeight = bufferedImage.getWidth() * ratio;
		}

		Image scaledImage =
				bufferedImage.getScaledInstance(
						(int) newWidth, (int) newHeight, Image.SCALE_SMOOTH);
		ImageIcon image = new ImageIcon(scaledImage);		
		return new JLabel(image, SwingConstants.RIGHT);
	}

	public static void main(String[] args) throws IOException {

		//for opening the gui that adds or removes schwergsy accounts from the database
		JButton schwergsButton = new JButton("Add or Remove Schwergsy Accounts");
		schwergsButton.addActionListener(new SchwergsListener());
		//for opening the gui that edits images
		JButton contentButton = new JButton("Review Content");
		contentButton.addActionListener(new ContentListener());	

		JPanel panel = new JPanel(new GridLayout(1, 2));
		panel.add(schwergsButton);
		panel.add(contentButton);
		panel.setBackground(Color.GRAY);

		frame = new JFrame("Main Menu");
		frame.add(panel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(600, 100);
		frame.setLocationRelativeTo(null);	
		frame.setVisible(true);
	}
}
