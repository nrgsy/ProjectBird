package management;

import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;
import twitterRunnables.FollowRunnable;
import twitterRunnables.TwitterRunnable;
import twitterRunnables.bigAccRunnable;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

import content.RedditScraper;



/**
 * @author Bojangles and McChrpchrp
 *
 */
public class Director {

	/**
	 * @param base
	 * @param hourOfDay
	 * @return
	 */
	public static Date getNextTime(Date base, int hourOfDay) {
		Calendar then = Calendar.getInstance();
		then.setTime(base);
		then.set(Calendar.HOUR_OF_DAY, hourOfDay);
		then.set(Calendar.MINUTE, 0);
		then.set(Calendar.SECOND, 0);
		then.set(Calendar.MILLISECOND, 0);
		if (then.getTime().before(base)) {
			then.add(Calendar.DAY_OF_YEAR, 1);
		}
		return then.getTime();
	}

	//TODO runstatus is not scheduled correctly. it would run immediately after runnable is instantiated, not finished.
	/**
	 * @param Twitter created by Director
	 * @param int of SchwergsAccount index
	 * @return
	 */
	private static TimerTask createTwitterRunnableTimerTask(final Twitter bird, final int index){
		return new TimerTask() {
			@Override
			public void run() {
				if (!Maintenance.flagSet) {
					new TwitterRunnable(bird,index);
				}
			}
		};
	}

	/**
	 * @param Twitter created by Director
	 * @param int of SchwergsAccount index
	 * @return
	 */
	private static TimerTask createFollowRunnableTimerTask(final Twitter bird, final int index){
		return new TimerTask() {
			@Override
			public void run() {
				if (!Maintenance.flagSet) {
					new FollowRunnable(bird,index);
				}
			}
		};
	}

	/**
	 * @param Twitter created by Director
	 * @param int of SchwergsAccount index
	 * @return
	 */
	private static TimerTask createBigAccRunnableTimerTask(final Twitter bird, final int index){
		return new TimerTask() {
			@Override
			public void run() {
				if (!Maintenance.flagSet) {
					new bigAccRunnable(bird,index);
				}
			}
		};
	}


	public static TimerTask createMaintenanceTimerTask() {

		return new TimerTask() {
			@Override
			public void run() {
				Maintenance.performMaintenance();
			}};
	}

	/**
	 * Runs all the threads and initializes the volatile variables in GlobalStuff
	 * 
	 * @param args
	 * @throws UnknownHostException
	 * @throws Exception
	 */
	public static void main(String[]args) throws UnknownHostException, Exception {
		
		GlobalStuff.lastPostTimeMap = new HashMap<Integer, Long>();
		
		DataBaseHandler.initGlobalVars();
		DataBaseHandler.findAndSetGlobalVars();

		Date nextOccurrenceOf3am = getNextTime(new Date(), 3);
		//The timer who's task fires once a day to do the maintenance tasks
		new Timer().scheduleAtFixedRate(
				createMaintenanceTimerTask(),
				nextOccurrenceOf3am,
				GlobalStuff.DAY_IN_MILLISECONDS);

		long scrapetime = GlobalStuff.DAY_IN_MILLISECONDS;
		
		for(int id = 0; id < DataBaseHandler.getCollectionSize("SchwergsyAccounts"); id++) {
			final BasicDBObject info = DataBaseHandler.getAuthorizationInfo(id);			

			long followtime_min = GlobalStuff.FOLLOW_TIME_MIN;
			long followtime_max = GlobalStuff.FOLLOW_TIME_MAX;
			long incubated_followtime_min = GlobalStuff.FOLLOW_TIME_INCUBATED_MIN;
			long incubated_followtime_max = GlobalStuff.FOLLOW_TIME_INCUBATED_MAX;

			Random r = new Random();
			long followtime = followtime_min+((long)(r.nextDouble()*(followtime_max-followtime_min)));
			
			//TODO implement new posting strategy, this probably could be deleted
			long incubated_followtime = incubated_followtime_min +
					((long)r.nextDouble()*(incubated_followtime_max - incubated_followtime_min));
			long bigacctime =  0L; //TODO figure out rate for bigAcc scraping and harvesting

			//If in incubation, follows at a rate of 425 per day
			if(info.getBoolean("isIncubated")){
				followtime = incubated_followtime;
			}

			ConfigurationBuilder cb = new ConfigurationBuilder();
			cb.setDebugEnabled(true)
				.setOAuthConsumerKey(info.getString("customerKey"))
				.setOAuthConsumerSecret(info.getString("customerSecret"))
				.setOAuthAccessToken(info.getString("authorizationKey"))
				.setOAuthAccessTokenSecret(info.getString("authorizationSecret"));
			TwitterFactory tf = new TwitterFactory(cb.build());
			Twitter twitter = tf.getInstance();


			//TODO add in DateTime variable to check against to know when to run probability to post.
			new Timer().scheduleAtFixedRate(createTwitterRunnableTimerTask(twitter, id), 0L, 60000L);
			new Timer().scheduleAtFixedRate(createFollowRunnableTimerTask(twitter, id), 0L, followtime);
			new Timer().scheduleAtFixedRate(createBigAccRunnableTimerTask(twitter, id), 0L, bigacctime);

			new Timer().scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					new Thread(new RedditScraper()).start();
				}},0L, scrapetime);
		}
	}
}
