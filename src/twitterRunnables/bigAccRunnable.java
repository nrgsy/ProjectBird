package twitterRunnables;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

import management.DataBaseHandler;
import management.FuckinUpKPException;
import management.GlobalStuff;
import management.Maintenance;
import management.TwitterHandler;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;



public class bigAccRunnable implements Runnable {
	private Twitter bird;
	private int index;
	private int bigAccountIndex;

	/**
	 * @param OAuthConsumerKey
	 * @param OAuthConsumerSecret
	 * @param OAuthAccessToken
	 * @param OAuthAccessTokenSecret
	 */
	public bigAccRunnable(Twitter twitter, int index, int bigAccountIndex){
		Maintenance.writeLog("New bigAccRunnable created", index);
		this.index = index;
		this.bigAccountIndex = bigAccountIndex;
		bird = twitter;
		Maintenance.runStatus.put(index+"bigAcc", true);
	}

	//This method does not put rejected candidates into the whitelist because they have the potential
	//to become bigAccounts later on.
	@SuppressWarnings("unchecked")
	public synchronized void findBigAccounts() throws IllegalStateException, TwitterException, FuckinUpKPException {
		HashSet<Long> AllCandidates = new HashSet<Long>(); 
		ArrayList<Long> bigAccounts = new ArrayList<Long>();
		int maxCandidates = 300;

		if(DataBaseHandler.getBigAccountsSize(index)!=0 && DataBaseHandler.getFollowersSize(index) > 100){
			ResponseList<Status> OwnTweets = null;

			ArrayList<ResponseList<Status>> ListOwnTweets = TwitterHandler.getUserTimeline(bird,bird.getId(), index);
			if(ListOwnTweets.isEmpty()){
				Maintenance.writeLog("Could not run getUserTimelime in bigAccRunnable.findBigAccounts1",
						index, 1);
				return;
			}
			else{
				OwnTweets = ListOwnTweets.get(0);
			}

			if(OwnTweets.size()>30){
				//sorts by most retweets and cuts out tweets with little retweets
				Collections.sort(OwnTweets, new Comparator<Status>() {
					@Override
					public int compare(Status t1, Status t2) {
						int rts1 = t1.getRetweetCount();
						int rts2 = t2.getRetweetCount();
						if (rts1 == rts2)
							return 0;
						else if (rts1 > rts2)
							return -1;
						else
							return 1;
					}
				});
				while(OwnTweets.size()>1){
					OwnTweets.remove(1);
				}
			}

			ArrayList<Long> AllRTerIDs = new ArrayList<Long>();
			for(Status tweet : OwnTweets){
				//gathers all retweeters' ids from tweets
				if(tweet.getRetweetCount()!=0){
					ArrayList<Long> RTerIDs = TwitterHandler.getRetweeterIds(bird, tweet.getId(), 100, -1, index);
					for(long id : RTerIDs){
						AllRTerIDs.add(id);
					}
				}
			}

			if(AllRTerIDs.size()==0){
				AllRTerIDs = DataBaseHandler.getToFollowList(index);
			}

			while(AllRTerIDs.size()>50){
				//limits to only 50 retweeters
				AllRTerIDs.remove(AllRTerIDs.size() - 1);
			}

			for(long id : AllRTerIDs){
				//gets 50 tweets from each retweeter
				Paging querySettings = new Paging();
				querySettings.setCount(50);
				ResponseList<Status> potentialBigAccs = null;

				ArrayList<ResponseList<Status>> ListPotentialBigAccs = TwitterHandler.getUserTimeline(bird, id, querySettings, index);
				if(ListPotentialBigAccs.isEmpty()){
					Maintenance.writeLog("Could not run getUserTimelime in bigAccRunnable2", index, 1);
					break;
				}
				else{
					potentialBigAccs = ListPotentialBigAccs.get(0);
				}

				for(Status tweet: potentialBigAccs){
					if(AllCandidates.size() == maxCandidates){
						break;
					}
					if(tweet.isRetweet() && tweet.getRetweetedStatus().getUser().getFollowersCount()>5000
							&& tweet.getRetweetedStatus().getUser().getId() != bird.getId()){
						//if the tweet is a retweet, is not from our own account, and the original tweeter has over
						//5000 followers, add that account as a candidate for a bigAccount
						AllCandidates.add(tweet.getRetweetedStatus().getUser().getId());
					}
				}
			}
		}

		//This is usually called when a new SchwergsyAccount has run out of bigAccounts to use.
		//It re-adds the whitelisted bigaccounts into the regular bigaccount pool
		//it resets bigAccountsWhiteList, but the bigAccounts will be readded later in this method
		//to the bigAccountsWhiteList
		else{

			AllCandidates.addAll((ArrayList<Long>)DataBaseHandler.getSchwergsyAccountArray(index, "bigAccountsWhiteList"));
			DataBaseHandler.replaceSchwergsyArray(index, new HashSet<Long>(), "bigAccountsWhiteList");

		}

		for (Iterator<Long> i = AllCandidates.iterator(); i.hasNext();) {
			Long user_id = i.next();
			if(DataBaseHandler.isBigAccWhiteListed(index, user_id)){
				i.remove();
			}
		}
	

		for(long id : AllCandidates){

			if(DataBaseHandler.isBigAccWhiteListed(index, id)){
				continue;
			}

			Paging query = new Paging();
			query.setCount(200);
			ResponseList<Status> timeline = null;
			ArrayList<ResponseList<Status>> ListTimeline = TwitterHandler.getUserTimeline(bird,id, query , index);
			if(ListTimeline.isEmpty()){
				Maintenance.writeLog("Could not run getUserTimelime in bigAccRunnable3", index, 1);
				return;
			}
			else{
				timeline = ListTimeline.get(0);
			}

			ArrayList<Status> noRTTimeline = new ArrayList<Status>();
			int count = 0;
			int totalRTs = 0;
			long firstTime = 0;
			long lastTime = 0;

			//Gets only original tweets
			for(Status tweet: timeline){
				if(!tweet.isRetweet()){
					noRTTimeline.add(tweet);
				}
			}

			//Gets the total amount of retweets
			for(Status tweet: noRTTimeline){
				count++;
				totalRTs+= tweet.getRetweetCount();
				if(count == 1){
					firstTime += tweet.getCreatedAt().getTime();
				}
				if(count == noRTTimeline.size()){
					lastTime += tweet.getCreatedAt().getTime();
				}
			}

			//adds a bigaccount if it averages 30 retweets per tweet and posts daily on average.
			if(count>0) {
				long avgTime = (lastTime-firstTime)/count;
				int avgRTs = totalRTs/count;

				if(avgRTs >= 30 && avgTime <= GlobalStuff.DAY_IN_MILLISECONDS){
					bigAccounts.add(id);
				}
			}
		}
		DataBaseHandler.addBigAccounts(index, bigAccounts);
	}

	public void harvestBigAccounts(int numRecursions) throws TwitterException, FuckinUpKPException {
		HashSet<Long> toFollowSet = new HashSet<Long>();
		Long lastTweet = DataBaseHandler.getBigAccountLatestTweet(index,bigAccountIndex);
		int bigAccountHarvestIndex; 
		int maxNoRTTweets = 30;

		//TODO see if we can take more tweets
		//Only gets the 5 latest tweets of the bigAccount candidate. If the bigaccount was harvested 
		//before, it only takes the tweetsafter the latest tweet used.
		Paging querySettings = new Paging();
		querySettings.setCount(100);
		if(lastTweet != -1){
			querySettings.setSinceId(lastTweet);
		}

		ResponseList<Status> tweets = null;
		ArrayList<ResponseList<Status>> ListTweets = TwitterHandler.getUserTimeline(bird,DataBaseHandler.getBigAccount(index, bigAccountIndex), querySettings, index);
		if(ListTweets.isEmpty()){
			Maintenance.writeLog("Could not run getUserTimelime in bigAccRunnable4", index, 1);
			return;
		}
		else{
			tweets = ListTweets.get(0);
		}

		ArrayList<Status> NoRTTweets = new ArrayList<Status>();

		//Makes sure the tweet is original to the bigAccount candidate
		for(Status tweet: tweets){
			if(!tweet.isRetweet()){
				NoRTTweets.add(tweet);
			}
			if(NoRTTweets.size()==maxNoRTTweets){
				break;
			}
		}

		//Reverse the order so that the latestTweet will be the last tweet used in the upcoming loop.
		Collections.reverse(NoRTTweets);

		//Gets ids of retweeters and puts it into toFollowSet and updates latestTweet for bigAccount
		//By using a HashSet, you get only unique retweeter ids.
		for(Status tweet :NoRTTweets){
			ArrayList<Long> toFollows = TwitterHandler.getRetweeterIds(bird,tweet.getId(), 100, -1, index);
			if(toFollows.size() != 0){
				for(long id : toFollows){
					toFollowSet.add(id);
				}
			}
			DataBaseHandler.editBigAccountLatestTweet(index, bigAccountIndex, tweet.getId());
		}

		//If the retweeter is already in the whitelist, then remove that bitch
		for (Iterator<Long> i = toFollowSet.iterator(); i.hasNext();) {
			Long user_id = i.next();
			if(DataBaseHandler.isWhiteListed(index, user_id)){
				i.remove();
			}
		}
		
		boolean failedToGetToFollows = false;

		if(toFollowSet.size()==0){
						
			if(DataBaseHandler.getBigAccountStrikes(index, bigAccountIndex)+1 >= GlobalStuff.BIG_ACCOUNT_STRIKES_FOR_OUT){
				if(DataBaseHandler.getBigAccountOuts(index, bigAccountIndex)+1 >= GlobalStuff.BIG_ACCOUNT_OUTS_FOR_REMOVAL){

					if (DataBaseHandler.getBigAccountsSize(index) > GlobalStuff.MIN_NUMBER_OF_BIG_ACCOUNTS) {
						//if it gets however many outs, it's removed from bigAccounts
						DataBaseHandler.deleteBigAccount(index, bigAccountIndex);
						bigAccountIndex--;
					}
					else {
						Maintenance.writeLog("Big account struck out, but not removed "
								+ "because there are too few remaining for account " + index, index, 1);
					}
				}
				else{
					//if it gets however many strikes, move it to the end of bigAccounts and reset strikes
					//and adds an out
					DataBaseHandler.editBigAccountStrikes(index, bigAccountIndex, 0);
					DataBaseHandler.editBigAccountOuts(index, bigAccountIndex, 
							DataBaseHandler.getBigAccountOuts(index, bigAccountIndex)+1);
					DataBaseHandler.moveBigAccountToEnd(index, bigAccountIndex);
				}
			}
			else{
				//if it gets a strike, add it to what it has now.
				DataBaseHandler.editBigAccountStrikes(index, bigAccountIndex, 
						DataBaseHandler.getBigAccountStrikes(index, bigAccountIndex) + 1);
			}
			failedToGetToFollows = true;
		}
		else{
			Maintenance.writeLog("Account " + index + " got " + toFollowSet.size() +
					" new tofollows on this harvest :D", index);
			DataBaseHandler.addToFollow(index, new ArrayList<Long>(toFollowSet));
			DataBaseHandler.addWhitelist(index, new ArrayList<Long>(toFollowSet));
		}
		bigAccountHarvestIndex = DataBaseHandler.getBigAccountsSize(index)-1 <= bigAccountIndex ? 0 : bigAccountIndex + 1;
		DataBaseHandler.editBigAccountHarvestIndex(index, bigAccountHarvestIndex);
		
		if(numRecursions >= GlobalStuff.MAX_NUMBER_HARVEST_ATTEMPTS) {
			Maintenance.writeLog("done harvesting, account " + index + " failed to get tofollows on this harvest :(", index);
		}
		else if (failedToGetToFollows) {
			harvestBigAccounts(numRecursions+1);
		}
		else {
			Maintenance.writeLog("done harvesting", index);			
		}
	}

	@Override
	public void run() {
		Maintenance.writeLog("run method called for bigAccRunnable", index);
		if(((DataBaseHandler.getToFollowSize(index) > 3000) && 
				(DataBaseHandler.getBigAccountsSize(index) < 30||DataBaseHandler.getFollowersSize(index) > 100))
				||DataBaseHandler.getBigAccountsSize(index) == 0){
			try {
				findBigAccounts();
			} catch (Exception e) {
				Maintenance.writeLog("Something fucked up in bigAccRunnable's findBigAccounts"
						+ "\n" + Maintenance.getStackTrace(e), index, -1);
				e.printStackTrace();
			}

		}
		else{
			try {
				harvestBigAccounts(0);
			} catch (Exception e) {
				Maintenance.writeLog("Something fucked up in bigAccRunnable's harvestBigAccounts"
						+ "\n" + Maintenance.getStackTrace(e), index, -1);
				e.printStackTrace();
			}
		}
		Maintenance.runStatus.put(index+"bigAcc", false);
	}

}
