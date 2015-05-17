package twitterRunnables;

import java.io.File;
import java.util.Date;

import management.DataBaseHandler;
import management.GlobalStuff;
import management.Maintenance;
import management.TwitterHandler;

import com.mongodb.DBObject;

import content.ImageManipulator;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;


/**
 * @author Bojangles and McChrpchrp
 *
 */
public class TwitterRunnable implements Runnable {
	private Twitter bird = null;
	private int index;

	/**
	 * @param OAuthConsumerKey
	 * @param OAuthConsumerSecret
	 * @param OAuthAccessToken
	 * @param OAuthAccessTokenSecret
	 */
	public TwitterRunnable (Twitter twitter, int index){
		this.index = index;
		bird = twitter;
	}

	/**
	 * temp testing constructor
	 */
	public TwitterRunnable(){
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		.setOAuthConsumerKey("uHQV3x8pHZD7jzteRwUIw")
		.setOAuthConsumerSecret("OxfLKbnhfvPB8cpe5Rthex1yDR5l0I7ztHLaZXnXhmg")
		.setOAuthAccessToken("2175141374-5Gg6WRBpW1NxRMNt5UsEUA95sPVaW3a566naNVI")
		.setOAuthAccessTokenSecret("Jz2nLsKm59bbGwCxtg7sXDyfqIo7AqO6JsvWpGoEEux8t");
		TwitterFactory tf = new TwitterFactory(cb.build());
		bird = tf.getInstance();
		this.index = 0;
	}


	/**
	 * handles actual uploading to twitter
	 * 
	 * @param file
	 * @param message
	 * @param twitter
	 * @throws TwitterException 
	 * @throws Exception
	 */
	public void uploadPicTwitter(File file, String message) throws TwitterException{
			StatusUpdate status = new StatusUpdate(message);
			status.setMedia(file);
			TwitterHandler.updateStatus(bird, status);
	}

	/**
	 * 	handles downloading image, updating db, and deleting image after upload
	 */
	public void uploadPic(){
		ImageManipulator imgman = new ImageManipulator();
		File image = null;
		try {
			//TODO assContent structure may have  been changed since writing this method.
			DBObject assContent = DataBaseHandler.getRandomContent("ass", 0);
			String caption = assContent.get("caption").toString();
			String link = assContent.get("imglink").toString();

			//creates temp image and puts file location in "image"
			image = new File(imgman.getImageFile(link));

			//calls uploadPicTwitter to upload to twitter and deletes locally saved image
			uploadPicTwitter(image, caption);
		}
		catch (Exception e) {
			System.out.println("Temp download of pic failed "+image);
			e.printStackTrace();
		}
		finally{
			image.delete();
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		Maintenance.runStatus.put(index+"twitter", true);
		try {
			long now = new Date().getTime();
			Long lastPostTime = GlobalStuff.lastPostTimeMap.get(index);
			boolean canPost = true;

			if (lastPostTime != null && now - lastPostTime < GlobalStuff.MIN_POST_TIME_INTERVAL) {
				canPost = false;
			}

			//post if the random number is less than the alpha constant and we're allowed to post
			if (canPost == true && Math.random() < GlobalStuff.ALPHA) {
				uploadPic();
				GlobalStuff.lastPostTimeMap.put(index, now);
			}
		}
		finally {
			Maintenance.runStatus.put(index+"twitter", false);
		}
	}

	public static void main(String[] args){
		new Thread(new TwitterRunnable()).start();
	}
}