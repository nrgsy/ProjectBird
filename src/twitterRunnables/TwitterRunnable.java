package twitterRunnables;
import java.io.File;
import java.util.Map;

import management.DataBaseHandler;

import com.mongodb.DBObject;

import content.ImageManipulator;
import twitter4j.RateLimitStatus;
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
	public TwitterRunnable (String OAuthConsumerKey, String OAuthConsumerSecret, String OAuthAccessToken, String OAuthAccessTokenSecret, int index){
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		.setOAuthConsumerKey(OAuthConsumerKey)
		.setOAuthConsumerSecret(OAuthConsumerSecret)
		.setOAuthAccessToken(OAuthAccessToken)
		.setOAuthAccessTokenSecret(OAuthAccessTokenSecret);
		TwitterFactory tf = new TwitterFactory(cb.build());
		this.index = index;
		bird = tf.getInstance();
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
	 * @throws Exception
	 */
	public void uploadPicTwitter(File file, String message,Twitter twitter) throws Exception  {
		twitter = bird;
		try{
			StatusUpdate status = new StatusUpdate(message);
			status.setMedia(file);
			twitter.updateStatus(status);}
		catch(TwitterException e){
			System.out.println("Pic Upload error" + e.getErrorMessage());
			throw e;
		}
	}


	/**
	 * 	handles downloading image, updating db, and deleting image after upload
	 */
	public void uploadPic(){
		ImageManipulator imgman = new ImageManipulator();
		Twitter blah = null;
		File loe = null;
		try {
			DBObject assContent = DataBaseHandler.getRandomContent("ass", 0);
			String caption = assContent.get("caption").toString();
			String link = assContent.get("imglink").toString();

			//creates temp image and puts file location in loe
			loe = new File(imgman.getImageFile(link));
			TwitterRunnable lol = new TwitterRunnable();

			//calls uploadPicTwitter to upload to twitter
			lol.uploadPicTwitter(loe, caption, blah);
			loe.delete();
		}
		catch (Exception e) {
			System.out.println("Temp download of pic failed "+loe);
			e.printStackTrace();
		}
	}



	/**
	 * @throws TwitterException 
	 * 
	 */
	public void prettyRateLimit() throws TwitterException{
		Map<String ,RateLimitStatus> rateLimitStatus = bird.getRateLimitStatus();
		for (String endpoint : rateLimitStatus.keySet()) {
		    RateLimitStatus status = rateLimitStatus.get(endpoint);
		    System.out.println("Endpoint: " + endpoint);
		    System.out.println(" Limit: " + status.getLimit());
		    System.out.println(" Remaining: " + status.getRemaining());
		    System.out.println(" ResetTimeInSeconds: " + status.getResetTimeInSeconds());
		    System.out.println(" SecondsUntilReset: " + status.getSecondsUntilReset());
		}
	}


	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run(){
		try {
			prettyRateLimit();
		} catch (TwitterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args){
		new Thread(new TwitterRunnable()).start();
	}
}