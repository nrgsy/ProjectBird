import java.net.UnknownHostException;
import java.util.Timer;


public class Director {

<<<<<<< HEAD
//		while(true){
////			RedditScraper scoop = new RedditScraper();
////			scoop.contentSnatch();
//
//			for(int id : /*MongoDB authinfo total collections*/){
//				new Thread(new TwitterRunnable(id.OAuthConsumerKey,
//						id.OAuthConsumerSecret,
//						id.OAuthAccessToken,
//						id.OAuthAccessTokenSecret,
//						id.isIncubated)).start();
//			}
//		}
=======
	public static void main(String[]args) throws UnknownHostException, Exception{
		long scrapetime = 86400000;

		for(int id =0;id< DataBaseHandler.getCollectionSize(GlobalStuff.DATABASE_NAME, GlobalStuff.COLLECTION_NAME);id++){
			AuthorizationInfo info = DataBaseHandler.getAuthorizationInfo(GlobalStuff.DATABASE_NAME, GlobalStuff.COLLECTION_NAME, id);
			
			long followtime = 100000;
			long posttime = 1380000;
			
			//If in incubation, follows at a rate of 425 per day
			if(info.isIncubated()){
				followtime = 203250;
			}
			
			new Timer().scheduleAtFixedRate(new java.util.TimerTask() {
				@Override
				public void run() {
			new Thread(new TwitterRunnable(info.getCustomerKey(),
					info.getCustomerSecret(),
					info.getAuthorizationKey(),
					info.getAuthorizationSecret())).start();
				}},0L, posttime);


			new Timer().scheduleAtFixedRate(new java.util.TimerTask() {
				@Override
				public void run() {
					new Thread(new FollowRunnable(info.getCustomerKey(),
							info.getCustomerSecret(),
							info.getAuthorizationKey(),
							info.getAuthorizationSecret())).start();
				}}, 0L, followtime);
		}
		
		new Timer().scheduleAtFixedRate(new java.util.TimerTask() {
			@Override
			public void run() {
				new Thread(new RedditScraper()).start();
			}},0L, scrapetime);
>>>>>>> 5b0292d18bc0fea6f08c7b082145bf8ab00d5ef9
	}
}
