package management;

import java.net.UnknownHostException;
import java.util.HashSet;

import twitter4j.IDs;
import java.util.Map;
import twitter4j.Paging;
import twitter4j.RateLimitStatus;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import com.mongodb.BasicDBObject;

public class TwitterHandler {

	/**
	 * @param info the authorization info from the account in the schwergsyAccounts collection
	 * @return
	 */
	public static Twitter getTwitter(BasicDBObject info) {	
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		.setOAuthConsumerKey(info.getString("customerKey"))
		.setOAuthConsumerSecret(info.getString("customerSecret"))
		.setOAuthAccessToken(info.getString("authorizationKey"))
		.setOAuthAccessTokenSecret(info.getString("authorizationSecret"));
		TwitterFactory tf = new TwitterFactory(cb.build());
		return tf.getInstance();
	}

	/**
	 * @param init
	 * @throws TwitterException
	 * @throws UnknownHostException 
	 */
	public static HashSet<Long> getFollowers(Twitter bird) throws TwitterException, UnknownHostException{
		int ratecount = 0;
		IDs blah;
		blah = bird.getFollowersIDs(-1);
		HashSet<Long> followers = new HashSet<>();
		for(int i = 0; i < blah.getIDs().length; i++){
			followers.add(blah.getIDs()[i]);
		}
		ratecount++;

		while(blah.getNextCursor()!=0 && ratecount<14){
			blah = (bird.getFollowersIDs(blah.getNextCursor()));
			for(int i = 0; i < blah.getIDs().length; i++){
				followers.add(blah.getIDs()[i]);
			}
			ratecount++;
		}
		return followers;
	}

	public static void updateStatus(Twitter twitter, StatusUpdate status) throws TwitterException{
		twitter.updateStatus(status);
	}

	public static void follow(Twitter twitter, long id) throws TwitterException{
		twitter.createFriendship(id);
	}

	public static void unfollow(Twitter twitter, long id) throws TwitterException{
		twitter.destroyFriendship(id);
	}

	public static ResponseList<Status> getUserTimeline(Twitter twitter, long id) throws TwitterException{
		return twitter.getUserTimeline(id);
	}

	public static ResponseList<Status> getUserTimeline(Twitter twitter, long id, Paging query) throws TwitterException{
		return twitter.getUserTimeline(id, query);
	}

	public static long[] getRetweeterIds(Twitter twitter, long id, int number, long sinceStatus) throws TwitterException{
		return twitter.getRetweeterIds(id, number, sinceStatus).getIDs();
	}

	public static void favorite(Twitter twitter, long id) throws TwitterException{
		twitter.createFavorite(id);
	}

	public static boolean isAtRateLimit(Twitter twitter, String endpoint) throws TwitterException{
		Map<String ,RateLimitStatus> rateLimitStatus = twitter.getRateLimitStatus();
		RateLimitStatus status = rateLimitStatus.get(endpoint);
		if (status.getRemaining() == 0){
			return true;
		}
		else {
			return false;
		}
	}
}
