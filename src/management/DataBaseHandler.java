package management;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.Set;

import org.bson.Document;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.MongoClient;

/**
 * @author Bojangles and McChrpchrp
 *	The data access layer, a class for interfacing with the MongoDB database
 */
public class DataBaseHandler{


	public static MongoClient mongoClient = new MongoClient();

	/**TODO BOJANG TEST
	 * @param type The type of content
	 * @param index The index of the Schwergsy account that wants the content
	 * @return a Document that is the random content, OR NULL IF NONE FOUND
	 */
	public static Document getRandomContent(String type, long index) {

		MongoCollection<Document> collection = getContentCollection(type);
		double collectionSize = collection.count();
		HashSet<Document> contentSample = new HashSet<>();

		if (collectionSize < GlobalStuff.CONTENT_SAMPLE_SIZE) {
			//just add all the valid ones in this case
			FindIterable<Document> findIter = collection.find();
			MongoCursor<Document> cursor = findIter.iterator();
			while (cursor.hasNext()) {
				Document content = cursor.next();
				if (hasNotBeenAccessedRecently(index, content)) {
					contentSample.add(content);
				}
			}
		}
		else {	
			//create a list of numbers 0 to CONTENT_SAMPLE_SIZE - 1
			ArrayList<Integer> numberList = new ArrayList<>();
			for (int i  = 0; i < collectionSize; i++) {
				numberList.add(i);
			}

			for (int i = 0; i < GlobalStuff.CONTENT_SAMPLE_SIZE; i++) {
				int randIndex = (int) (Math.random() * numberList.size());
				//draws a rand number from numberList
				int randNum = numberList.remove(randIndex);
				//using the number from numberList ensures we draw randomly from content with no repeats
				Document content = collection.find().limit(-1).skip(randNum).iterator().next();
				if (hasNotBeenAccessedRecently(index, content)) {
					contentSample.add(content);
				}
			}
		}

		//determine which content in the sample is best to post
		Object[] contentArray = contentSample.toArray();
		Document bestContent = null;
		long minTimesAccessed = Long.MAX_VALUE;
		//number of times timesAccessed <= minTimesAccessed, used to add randomization to posting between
		//different accounts
		int numValidSoFar = 0;
		for (int i = 0; i < contentArray.length; i++) {
			Document candidateContent = (Document)contentArray[i];		
			ArrayList<BasicDBObject> list = (ArrayList<BasicDBObject>) candidateContent.get("accessInfo");
			boolean foundMatch = false;

			for (Object o : list) {
				Document info = (Document) o;
				if ((long) info.get("index") == index) {
					foundMatch = true;
					long timesAccessed = (long) info.get("timesAccessed");
					long lastAccess = (long) info.get("lastAccess");	
					
					//restrict 
					if (lastAccess < (new Date().getTime()) - GlobalStuff.WEEK_IN_MILLISECONDS) {
						if (timesAccessed < minTimesAccessed) {
							minTimesAccessed = timesAccessed;
							bestContent = candidateContent;
							numValidSoFar = 1;
						}
						else if (timesAccessed == minTimesAccessed) {
							numValidSoFar++;
							//with probability 1/numValidSoFar make this the ideal content
							//This adds in randomization in a way that tapers off as more valid content 
							//is found
							if (Math.random() < 1/numValidSoFar) {
								minTimesAccessed = timesAccessed;
								bestContent = candidateContent;	
							}
						}
					}
					break;
				}
			}
			//foundmatch will be false if the candidate content's access info didn't contain the index of
			//the schwergs account corresponding to index
			if (!foundMatch) {
				//because we know this content has never been used before
				bestContent = candidateContent;
				minTimesAccessed = 0;
				break;
			}
		}

		if (bestContent != null) {
			ArrayList<Document> list = (ArrayList<Document>) bestContent.get("accessInfo");
			Document info = null;
			for (Object o : list) {
				info = (Document) o;
				if ((long) info.get("index") == index) {
					long timesAccessed = (long) info.remove("timesAccessed");
					info.remove("lastAccess");
					info.put("timesAccessed", timesAccessed + 1);
					info.put("lastAccess", new Date().getTime());
					break;
				}
				info = null;
			}

			if (info == null) {
				info = new Document()
				.append("index", index)
				.append("timesAccessed", 1L)
				.append("lastAccess", new Date().getTime());
				list.add(info);
			}

			bestContent.remove("accessInfo");
			bestContent.put("accessInfo", list);

			Long id = (Long) bestContent.get("_id");
			Document query = new Document("_id", id);

			collection.findOneAndReplace(query, bestContent);
		}

		return bestContent;
	}

	/**TODO BOJANG TEST
	 * determines if the content has been recently accessed by the schwergsy account corresponding to index
	 * 
	 * @param index the index of the Schwergsy account
	 * @param content the access info from the db
	 * @return
	 */
	public static  boolean hasNotBeenAccessedRecently(long index, Document content) {

		ArrayList<BasicDBObject> list = (ArrayList<BasicDBObject>) content.get("accessInfo");
		boolean valid = true;
		for (Object o : list) {
			Document info = (Document) o;
			if ((long) info.get("index") == index) {
				long msSinceLastAccess = new Date().getTime() - (long) info.get("lastAccess");
				//don't consider content that's been posted sooner that a week ago (on this account)
				if (msSinceLastAccess < GlobalStuff.MIN_TIME_BETWEEN_ACCESSES) {
					valid = false;
				}
				break;
			}
		}
		return valid;
	}

	/**TODO BOJANG TEST
	 * @param sourceType the type of the content e.g. "ass", "pendingass"
	 * @param sourceLink the link of the content to remove
	 */
	public static  void removeContent(String sourceType, String sourceLink) {		
		MongoCollection<Document> sourceCollection = getContentCollection(sourceType);	
		Document query = new Document("imglink", sourceLink);
		sourceCollection.deleteOne(query);
	}

	/**
	 * Pulls the globals from the GlobalVariables collection and uses them to initialize the globals in
	 * GlobalStuff
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void findAndSetGlobalVars() {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> collection = db.getCollection("GlobalVariables");
		if (collection == null || collection.count() == 0) {
			Maintenance.writeLog("cannot pull global vars. "
					+ "Collection GlobalVariables does not exist", null, -1);
		}
		else if (collection.count() == 1) {
			MongoCursor<Document> globalVarsCursor = collection.find().iterator();
			Document globalVars = globalVarsCursor.next();
			GlobalStuff.setGlobalVars(globalVars);
		}
		else {
			Maintenance.writeLog("GlobalVariables had " + collection.count() + " entries. "
					+ "It should only ever have one entry, or not exist at all", -1);
		}
	}

	/**
	 * Initializes GlobalVars with the hard coded defaults
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void initGlobalVars() {

		//because this may not be initialized if called from gui the 
		if (mongoClient == null) {
			mongoClient = new MongoClient();
		}

		//Setting the global variables in GlobalStuff
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> collection = db.getCollection("GlobalVariables");

		//can't writelog because log depends on initialized globals
		System.out.println("Initializing global vars with default values");

		//These are the default values to set the volatile variables to
		Document globalVars = new Document();

		for(Entry<String,Object> entry : GlobalStuff.getDefaultGlobalVars().entrySet()){
			globalVars.append(entry.getKey(),entry.getValue());
		}

		if (collection.find().first() == null) {
			collection.insertOne(globalVars);		
		}
		else {
			collection.findOneAndReplace(new Document(), globalVars);
		}
	}

	//TODO Bojang Test
	/**
	 * @param caption
	 * @param imglink
	 * @param type
	 * @param isTimeless Where the content is timeless, input null if irrelevant because
	 * the type prefix is pending or schwag
	 * @throws InterruptedException
	 */
	public static void newContent(String caption, String imglink, String type, Boolean isTimeless) {

		//the type without the "pending" or "schwag" prefix, e.g. schwagweed becomes weed
		String baseType;
		//the prefix of the type e.g. "" (empty String), schwag, and pending 
		String prefix;

		//determine whether we're dealing with a pending content, schwag content, or a regular content
		//and set baseType accordingly
		if (type.length() >= 6 && type.substring(0, 6).equals("schwag")) {
			baseType = type.substring(6);
			prefix = "schwag";
		}
		else if (type.length() >= 7 && type.substring(0, 7).equals("pending")) {
			baseType = type.substring(7);
			prefix = "pending";

		}
		else {
			baseType = type;
			prefix = "";
		}

		MongoCollection<Document> normalCollection = getContentCollection(baseType);
		MongoCollection<Document> pendingCollection = getContentCollection("pending" + baseType);
		MongoCollection<Document> schwagCollection = getContentCollection("schwag" + baseType);
		long now = new Date().getTime();
		//to ensure no two contents can have the same _id
		//(occurs when something tries to create two contents in the same millisecond)
		Document creationTimeCheck = new Document("_id", now);

		boolean timeOK = false;
		while (!timeOK) {
			//make sure that the image link is not in the pending, schwag, or regular collections
			//of the base type
			if (!normalCollection.find(creationTimeCheck).iterator().hasNext() &&
					!pendingCollection.find(creationTimeCheck).iterator().hasNext() &&
					!schwagCollection.find(creationTimeCheck).iterator().hasNext()) {
				timeOK = true;
			}
			else {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					Maintenance.writeLog("thread snooze interrupted" + Maintenance.getStackTrace(e),
							"gui", -1);
				}
				now = new Date().getTime();
				creationTimeCheck = new Document("_id", now);
			}
		}

		//to ensure no two contents can have the same link
		Document linkCheck = new Document("imglink", imglink);
		//make sure that the image link is not in the pending, schwag, or regular collections of the base type
		if (!normalCollection.find(linkCheck).iterator().hasNext() &&
				!pendingCollection.find(linkCheck).iterator().hasNext() &&
				!schwagCollection.find(linkCheck).iterator().hasNext()) {

			Document newContent = new Document("_id", now);
			newContent.append("imglink", imglink);

			switch (prefix) {
			case "": //add to normal content collection
				newContent.append("caption", caption);
				newContent.append("isTimeless", isTimeless);
				//accessInfo is a list of BasicBObjects, [{index : ..., timesAccessed : ..., lastAccess : ...},{...}]
				newContent.append("accessInfo", new BasicDBList());
				normalCollection.insertOne(newContent);
				Maintenance.writeLog("Successfully added new content of type " + type, "content");		
				break;
			case "pending": //add to pending content collection
				newContent.append("caption", caption);
				pendingCollection.insertOne(newContent);
				Maintenance.writeLog("Successfully added new content of type " + type, "content");		
				break;
			case "schwag": //add to schwag content collection, needs nothing more than id and link
				schwagCollection.insertOne(newContent);
				Maintenance.writeLog("Successfully added new content of type " + type, "content");
				break;
			default: Maintenance.writeLog("invalid content type", "content", -1);
			}
		}
		else {
			Maintenance.writeLog("Image is not unique: "+ imglink, "content");
		}
	}

	/**TODO BOJANG TEST
	 * @param type the type of content
	 * @return
	 */
	public static MongoCollection<Document> getContentCollection(String type) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = null;
		dbCollection = db.getCollection(type.toLowerCase() + "content");
		return dbCollection;
	}

	/**
	 * Adds the given an array of strings to the given list in the a particular schwergsy account
	 * @param index The index (database id) of the schwergsy account
	 * @param StringArr	The Strings to add to the list (should be twitter id's)
	 * @param column The list to add to (e.g. followers, toFollow, whiteList, etc)
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */

	@SuppressWarnings("rawtypes")
	private static  void addArrayToSchwergsArray(int index, ArrayList StringArr, String column) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");

		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");

		Document query = new Document("_id", index);

		Document arr = new Document("$each", StringArr);

		dbCollection.updateOne(query, new Document("$addToSet", new Document(column,arr)));

		Maintenance.writeLog("successfully added " +
				StringArr.size() + " items to " + column + " for account " + index, index);		
	}

	/**
	 * replaces the schwergs array in column with the given String Array
	 * 
	 * @param index the index of the schwergsy account we're referring to
	 * @param StringArr
	 * @param column The name of the field (whitelist, tofollow, etc)
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void replaceSchwergsyArray(int index, HashSet<Long> Set, String column) {
		BasicDBList freshList = new BasicDBList();
		freshList.addAll(Set);
		replaceSchwergsyField(index, freshList, column);
	}

	/**
	 * replaces the data of the given field with the replacement object
	 * 
	 * @param index The index of the Schwergsy account
	 * @param replacement
	 * @param column
	 */
	public static  void replaceSchwergsyField(int index, Object replacement, String fieldName) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		dbCollection.findOneAndUpdate(
				new Document("_id", index),
				new Document("$set", new Document(fieldName, replacement)));

		Maintenance.writeLog("successfully replaced Schwergsy Account field: " + fieldName, index);
	}

	/**TODO BOJANG TEST
	 * @param index
	 */
	public static void finishedIncubation(int index){
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccount");
		dbCollection.findOneAndUpdate(
				new Document("_id", index),
				new Document("$set", new Document("isIncubated", false)));

		Maintenance.writeLog("congratulations, SchwergsyAccount #" + index + 
				" has graduated from incubation", index);
	}

	public static void incubate (int index){
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccount");
		dbCollection.findOneAndUpdate(
				new Document("_id", index),
				new Document("$set", new Document("isIncubated", true)));
	}

	/**
	 * Adds the given object to the given list in the a particular schwergsy account
	 * @param index The index (databse id) of the schwergsy account
	 * @param element The object that should be a String (a twitter id) or a Document (a statistic) 
	 * that will be added to the list 
	 * @param column The list to add to (e.g. followers, toFollow, whiteList, statistics, etc)
	 * @throws FuckinUpKPException 
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	private static  void addElementToSchwergsArray(int index, Object element, String column) {

		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id", index);

		Document ele = null;

		if (element instanceof String) { 
			ele = new Document("$addToSet", new Document(column, (String) element));
		}
		else if (element instanceof Document) { 
			ele = new Document("$addToSet", new Document(column, (Document) element));
		}
		else if (element instanceof Long) {
			ele = new Document("$addToSet", new Document(column, (long) element));

		}
		else {
			Maintenance.writeLog("method addElementToSchwergsArray is not a trash can.\n"
					+ "You can't just be throwin' whatever you please in.", index, -1);
		}

		dbCollection.findOneAndUpdate(query, ele);
		Maintenance.writeLog("successfully added an element to "+ column, index);
	}

	/**
	 * @param index
	 * @param followingArr
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void addFollowing(int index, ArrayList<Long>followingArr) {
		addArrayToSchwergsArray(index,followingArr,"following");
	}

	/**
	 * @param index
	 * @param toFollowArr
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void addToFollow(int index, ArrayList<Long>toFollowArr) {
		addArrayToSchwergsArray(index,toFollowArr,"toFollow");
	}

	/**
	 * @param index
	 * @param whitelistArr
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void addWhitelist(int index, ArrayList<Long>whitelistArr) {
		addArrayToSchwergsArray(index,whitelistArr,"whiteList");
	}

	/**TODO Bojang Test
	 * @param index The index of the Schwergsy account we want to add the statistic to
	 * @param unFollows number of unFollows since the last Statistic was taken
	 * @param newFollows unFollows since the last Statistic was taken
	 * @param totalFollowers 
	 * @throws FuckinUpKPException 
	 */
	public static  void addNewStatistic(int index, int unfollows, int newFollows, int retainedFollowers, int totalFollowers) {

		Maintenance.writeLog("Adding statistic",index);
		long now = new Date().getTime();
		long oldStatCreationTime = now;

		ArrayList<Document> statList = (ArrayList<Document>) getSchwergsyAccount(index).get("statistics");
		//get time of creation of the last statistic is statList, which should be the most recent stat
		if (statList.size() > 0) {
			oldStatCreationTime = ((Document) statList.get(statList.size() -1)).getLong("creationDate");
		}

		Document stat = new Document()
		.append("creationDate", now)
		.append("timeSinceLastStat", now - oldStatCreationTime)
		.append("unFollows", unfollows)
		.append("newFollows", newFollows)
		.append("retainedFollowers", retainedFollowers)
		.append("totalFollowers", totalFollowers);

		addElementToSchwergsArray(index, stat, "statistics");
		Maintenance.writeLog("Finished adding statistic", index);
	}

	/**TODO Bojang Test
	 * This should be called once a day for each Schwergsy account and on startup
	 * @param index The id of the Schwergsy account
	 * @throws TwitterException 
	 * @throws Exception 
	 */
	public static void updateFollowers(int index) throws TwitterException {

		//get the current set of followers from twitter
		Document authInfo = DataBaseHandler.getAuthorizationInfo(index);	
		Twitter twitter = TwitterHandler.getTwitter(authInfo);		
		HashSet<Long> freshFollowerSet = TwitterHandler.getFollowers(twitter, index);
		
		if (freshFollowerSet == null) {
			Maintenance.writeLog("Failed to update followers, something went wrong trying to "
					+ "get the the current set of followers from twitter", index, 1);
			return;
		}

		if(freshFollowerSet.size()>=2000){
			finishedIncubation(index);
		}

		if(freshFollowerSet.size()<2000){
			incubate(index);
		}

		int OGsize = freshFollowerSet.size();

		HashSet<Long> storedFollowerSet = new HashSet<>();

		@SuppressWarnings("unchecked")
		ArrayList<Long> tmpList = getSchwergsyAccountArray(index, "followers");

		for(Long id : tmpList) {
			storedFollowerSet.add(id);
		}

		//getting the number of cool new followers and unfollowing bastards.

		@SuppressWarnings("unchecked")
		HashSet<Long> retainedFollowerSet = (HashSet<Long>) freshFollowerSet.clone();
		@SuppressWarnings("unchecked")
		HashSet<Long> OGFreshFollowerSet = (HashSet<Long>) freshFollowerSet.clone();

		if (!retainedFollowerSet.retainAll(storedFollowerSet)) {
			Maintenance.writeLog("FYI: freshFollowerSet doesn't have any elements that weren't"
					+ "already in storedFollowerSet", index);
		}
		int retainedFollowers = retainedFollowerSet.size();

		if (!freshFollowerSet.removeAll(retainedFollowerSet)) {
			Maintenance.writeLog("FYI: freshFollowerSet doesn't have any common elements with"
					+ "retainedFollowerSet", index);
		}
		int newFollows = freshFollowerSet.size();

		if (!storedFollowerSet.removeAll(retainedFollowerSet)) {
			Maintenance.writeLog("FYI: storedFollowerSet doesn't have any common elements with"
					+ "retainedFollowerSet", index);
		}
		int unfollows = storedFollowerSet.size();

		addNewStatistic(index, unfollows, newFollows, retainedFollowers, OGsize);
		replaceSchwergsyArray(index, OGFreshFollowerSet, "followers");
	}


	/**
	 * @param index
	 * @param amount
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	@SuppressWarnings("unchecked")
	public static  ArrayList<Long> popMultipleFollowing(int index, int amount) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		ArrayList<Long> toUnfollowArr = new ArrayList<Long>();
		ArrayList<Long> unfollowed = new ArrayList<Long>();
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");

		Document query = new Document("_id", index);
		Document pop = new Document("$pop", new Document("following", -1));

		MongoCursor<Document> cursor = dbCollection.find(query).iterator();

		Document sliced = cursor.next();
		toUnfollowArr =  (ArrayList<Long>)sliced.get("following");
		cursor.close();
		for(int i = 0; i<amount; i++){
			dbCollection.updateOne(query, pop);
			unfollowed.add(toUnfollowArr.get(i));
		}
		return unfollowed;
	}

	public static ArrayList<Long> getToFollowList(int index){
		return (ArrayList<Long>) getSchwergsyAccount(index).get("toFollow");
	}

	public static String getAccountType(int index){
		return getSchwergsyAccount(index).getString("accountType");
	}

	public static double getAssRatio(int index){
		return getSchwergsyAccount(index).getDouble("assRatio");
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @param property
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	private static  Object getBigAccountStuff(int index, int bigAccountIndex, String property) {

		@SuppressWarnings("unchecked")
		ArrayList<Document> answerList = (ArrayList<Document>) getSchwergsyAccount(index).get("bigAccounts");
		Document answer = (Document) answerList.get(bigAccountIndex);
		return answer.get(property);
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  long getBigAccount(int index, int bigAccountIndex) {
		return  (long)getBigAccountStuff(index, bigAccountIndex,"user_id");
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  int getBigAccountStrikes(int index, int bigAccountIndex) {
		return (int)getBigAccountStuff(index, bigAccountIndex,"strikes");
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  long getBigAccountLatestTweet(int index, int bigAccountIndex) {
		return (long)getBigAccountStuff(index, bigAccountIndex,"latestTweet");
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  int getBigAccountOuts(int index, int bigAccountIndex) {
		return (int)getBigAccountStuff(index,bigAccountIndex,"outs");
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @param property
	 * @param change
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	private static  void editBigAccountStuff(int index, int bigAccountIndex, String property, Object change) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id",index);
		Document updater = new Document("$set", new Document("bigAccounts."+bigAccountIndex+"."+property,
				change));
		dbCollection.findOneAndUpdate(query, updater);
	}

	/**TODO BOJANG TEST
	 * @param index
	 * @param bigAccountHarvestIndex
	 */
	public static  void editBigAccountHarvestIndex(int index, int bigAccountHarvestIndex){
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id",index);
		Document updater = new Document("$set", new Document("bigAccountHarvestIndex",
				bigAccountHarvestIndex));
		dbCollection.findOneAndUpdate(query, updater);
	}

	/**TODO BOJANG TEST
	 * @param index
	 * @return
	 */
	public static  int getBigAccountHarvestIndex(int index){
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id", index);
		FindIterable<Document> findIter = dbCollection.find(query);
		MongoCursor<Document> cursor = findIter.iterator();
		int bigAccountHarvestIndex = 0;
		if(cursor.hasNext()){
			bigAccountHarvestIndex = (int)cursor.next().get("bigAccountHarvestIndex");
		}
		else{
			Maintenance.writeLog("bigAccountHarvestIndex could not be found while running "
					+ "getBigAccountHarvestIndex", index, -1);
		}
		cursor.close();
		return bigAccountHarvestIndex;
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @param number
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void editBigAccountStrikes(int index, int bigAccountIndex, int number) {
		editBigAccountStuff(index,bigAccountIndex,"strikes",number);
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @param tweetID
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void editBigAccountLatestTweet(int index, int bigAccountIndex, long tweetID) {
		editBigAccountStuff(index,bigAccountIndex,"latestTweet",tweetID);
	}

	/**
	 * @param index
	 * @param bigAccountIndex
	 * @param number
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void editBigAccountOuts(int index, int bigAccountIndex, int number) {
		editBigAccountStuff(index,bigAccountIndex,"outs", number);
	}


	/**
	 * @param index
	 * @param bigAccIndex
	 * @throws FuckinUpKPException
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void moveBigAccountToEnd(int index, int bigAccIndex) throws FuckinUpKPException {
		long user_id = getBigAccount(index, bigAccIndex);
		long latestTweet = getBigAccountLatestTweet(index,bigAccIndex);
		int strikes = getBigAccountStrikes(index, bigAccIndex);
		int outs = getBigAccountOuts(index,bigAccIndex);

		deleteBigAccount(index, bigAccIndex);
		addBigAccount(index, user_id, strikes, outs, latestTweet);
	}


	/**
	 * @param index the index of the schwergsy account
	 * @param bigAccountElement
	 * @throws FuckinUpKPException 
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */

	public static  void addBigAccount(int index, long bigAccountID, int strikes, int outs, long latestTweet) throws FuckinUpKPException{
		Document bigAccount = new Document("user_id", bigAccountID);
		bigAccount.append("strikes", strikes);
		bigAccount.append("outs", outs);
		bigAccount.append("latestTweet", latestTweet);
		ArrayList<Long> listWithOneElement = new ArrayList<Long>();
		listWithOneElement.add(bigAccountID);	
		addBigAccountsWhiteList(index, listWithOneElement);
		DataBaseHandler.addElementToSchwergsArray(index, bigAccount, "bigAccounts");
		Maintenance.writeLog("successfully added an element to bigAccounts", index);
	}
	
	/**TODO BOJANG TEST
	 * @param index
	 * @param bigAccountsArray
	 * @throws FuckinUpKPException 
	 */
	public static  void addBigAccounts(int index, ArrayList<Long> bigAccountsArray) throws FuckinUpKPException {
		for(long id : bigAccountsArray) {			
			addBigAccount(index, id, 0, 0, -1L);
		}
	}

	/**TODO BOJANG TEST
	 * @param index
	 * @param bigAccountsArray
	 */
	public static  void addBigAccountsWhiteList(int index, ArrayList<Long> bigAccountsArray) {
		addArrayToSchwergsArray(index, bigAccountsArray, "bigAccountsWhiteList");
	}

	/**
	 * @param index
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  void deleteBigAccount(int index, int bigAccIndex) {
		long user_id = getBigAccount(index, bigAccIndex);

		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document match = new Document("_id", index); //to match your direct app document
		Document update = new Document("user_id", user_id);
		dbCollection.findOneAndUpdate(match, new Document("$pull", new Document("bigAccounts", update)));
	}

	/**
	 * @param index
	 * @param bigAccountID
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static boolean isInBigAccounts(int index, long bigAccountID) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		boolean result;

		Document query = new Document("_id", index);
		query.append("bigAccounts", new Document("$elemMatch", new Document("user_id", bigAccountID)));
		MongoCursor<Document> cursor = dbCollection.find(query).iterator();

		if(cursor.hasNext()){
			result = true;
		}
		else{
			result = false;
		}

		cursor.close();
		return result;
	}


	/**
	 * @param index
	 * @param user_id
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  boolean isWhiteListed(int index, long user_id) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");		
		boolean result;

		Document query = new Document("_id", index);
		query.append("whiteList", new Document("$eq", user_id));
		MongoCursor<Document> cursor = dbCollection.find(query).iterator();

		if(cursor.hasNext()){
			result = true;
		}
		else{
			result = false;
		}

		cursor.close();
		return result;
	}

	/**
	 * @param index
	 * @param user_id
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  boolean isBigAccWhiteListed(int index, long bigAcc_id) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		boolean result;

		Document query = new Document("_id", index);
		query.append("bigAccountsWhiteList", new Document("$eq", bigAcc_id));
		MongoCursor<Document> cursor = dbCollection.find(query).iterator();

		if(cursor.hasNext()){
			result = true;
		}
		else{
			result = false;
		}

		cursor.close();
		return result;
	}

	/**
	 * Gets one user_id from ToFollow to follow
	 *
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	@SuppressWarnings("unchecked")
	public static  long getOneToFollow(int index) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id", index);
		Document pop = new Document("$pop", new Document("toFollow", -1));
		Document popResult = dbCollection.findOneAndUpdate(query,pop);
		ArrayList<Long> toFollowList = (ArrayList<Long>) popResult.get("toFollow");
		return toFollowList.get(0);
	}

	/**
	 * @param index the Schwergsy Account to get the list from
	 * @param column the specific list to return
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  ArrayList getSchwergsyAccountArray(int index, String column) {
		ArrayList SchwergsList = (ArrayList) getSchwergsyAccount(index).get(column);
		return SchwergsList;
	}

	/**
	 * returns the document corresponding to the schwergsy account referenced by index 
	 * 
	 * @param index
	 * @return
	 */
	public static  Document getSchwergsyAccount(int index) {

		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id", index);
		FindIterable<Document> findIter = dbCollection.find(query);
		MongoCursor<Document> cursor = findIter.iterator();
		Document doc;
		try {
			doc = cursor.next();
		}
		catch (NoSuchElementException e) {
			Maintenance.writeLog("Schwergsy Account with _id: " + index +
					" not found.\n" + Maintenance.getStackTrace(e), null, -1);
			return null;
		}
		cursor.close();
		return doc;
	}

	/**
	 * returns the document corresponding to the schwergsy account referenced by name 
	 * 
	 * @param name The name of the Schwergsy Account
	 * @return
	 */
	public static Document getSchwergsyAccount(String name) {

		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("name", name);
		FindIterable<Document> findIter = dbCollection.find(query);
		MongoCursor<Document> cursor = findIter.iterator();
		Document doc;
		try {
			doc = cursor.next();
		}
		catch (NoSuchElementException e) {
			Maintenance.writeLog("Schwergsy Account with name: " + name +
					" not found.\n" + Maintenance.getStackTrace(e), null, -1);
			return null;
		}
		cursor.close();
		return doc;
	}

	/**
	 * returns the index of the Schwergsy account with the given name
	 * 
	 * @return
	 */
	public static int getSchwergsyAccountIndex(String name) {
		return (int) DataBaseHandler.getSchwergsyAccount(name).get("_id");
	}

	/**
	 * @param index
	 * @param column
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	private static  int getSchwergsyAccountArraySize(int index, String column) {
		return getSchwergsyAccountArray(index, column).size();
	}

	/**
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static int getFollowersSize(int index) {
		return getSchwergsyAccountArraySize(index, "followers");
	}

	/**
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static int getBigAccountsSize(int index) {
		return getSchwergsyAccountArraySize(index, "bigAccounts");
	}

	/**
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static int getWhiteListSize(int index) {
		return getSchwergsyAccountArraySize(index, "whiteList");
	}

	/**
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static int getBigAccountsWhiteListSize(int index) {
		return getSchwergsyAccountArraySize(index, "bigAccountsWhiteList");
	}

	/**
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static int getFollowingSize(int index) {
		return getSchwergsyAccountArraySize(index, "following");
	}

	/**
	 * @param index
	 * @return
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static int getToFollowSize(int index) {
		return getSchwergsyAccountArraySize(index, "toFollow");
	}

	/**
	 * Convenience method to insert a new account into the SchwergsyAccounts collection
	 * using empty lists for the missing parameters
	 * @param name
	 * @param customerSecret
	 * @param customerKey
	 * @param authorizationSecret
	 * @param authorizationKey
	 * @param isIncubated
	 * @return true or false depending on whether the insertion was successful
	 * @throws TwitterException 
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  boolean insertSchwergsyAccount(
			String name,
			String customerSecret,
			String customerKey,
			String authorizationSecret,
			String authorizationKey,
			String accountType,
			double assRatio,
			boolean isIncubated,
			boolean isSuspended,
			boolean isFlaggedForDeletion) throws TwitterException {

		return insertSchwergsyAccount(
				name,
				customerSecret,
				customerKey,
				authorizationSecret,
				authorizationKey,
				accountType,
				assRatio,
				isIncubated,
				isSuspended,
				isFlaggedForDeletion,
				new BasicDBList(),
				new BasicDBList(),
				new BasicDBList(),
				new BasicDBList(),
				new BasicDBList(),
				new BasicDBList(), 
				0);	
	}


	/**
	 * Insert a new account into the the SchwergsyAccounts collection
	 * @param name
	 * @param customerSecret
	 * @param customerKey
	 * @param authorizationSecret
	 * @param authorizationKey
	 * @param isIncubated
	 * @param followers
	 * @param following
	 * @param toFollow
	 * @param whiteList
	 * @param bigAccounts
	 * @param statistics
	 * @return true or false depending on whether the insertion was successful
	 * @throws TwitterException 
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  boolean insertSchwergsyAccount(
			String name,
			String customerSecret,
			String customerKey,
			String authorizationSecret,
			String authorizationKey,
			String accountType,
			double assRatio,
			boolean isIncubated,
			boolean isSuspended,
			boolean isFlaggedForDeletion,
			BasicDBList followers,
			BasicDBList following,
			BasicDBList toFollow,
			BasicDBList whiteList,
			BasicDBList bigAccounts,
			BasicDBList statistics,
			int bigAccountHarvestIndex) throws TwitterException {

		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");

		//check if this schwergsy account already exists in the database
		Document uniqueCheck = new Document("authorizationKey", authorizationKey);
		if (dbCollection.find(uniqueCheck).limit(1).first() != null) {
			Maintenance.writeLog("Schwergsy account already exists in the database, "
					+ "will not add duplicate", null, 1);
			return false;
		}
		else {
			Maintenance.writeLog("inserting a new Schwergsy Account");

			int _id = (int) getCollectionSize("SchwergsyAccounts");
			
			Document basicBitch = new Document("_id", _id)
			.append("name", name)
			.append("customerSecret", customerSecret)
			.append("customerKey", customerKey)
			.append("authorizationSecret", authorizationSecret)
			.append("authorizationKey", authorizationKey)
			.append("accountType", accountType)
			.append("assRatio", assRatio)
			.append("isIncubated", isIncubated)
			.append("isSuspended", isSuspended)
			.append("isFlaggedForDeletion", isFlaggedForDeletion)
			.append("followers", followers)
			.append("following", following)
			.append("toFollow", toFollow)
			.append("whiteList", whiteList)
			.append("bigAccounts", bigAccounts)
			.append("statistics", statistics)
			.append("bigAccountHarvestIndex", 0);
			
			
			//TODO check if an account by the same name already exists in the db
			dbCollection.insertOne(basicBitch);

			try {

				Twitter bird = (new TwitterFactory(new ConfigurationBuilder()
				.setDebugEnabled(true)
				.setOAuthConsumerKey(customerKey)
				.setOAuthConsumerSecret(customerSecret)
				.setOAuthAccessToken(authorizationKey)
				.setOAuthAccessTokenSecret(authorizationSecret).build()).getInstance());
								
				//Makes sure that the account's following is synced in the database.
				initUpdateFollowing(bird,_id);
				updateFollowers(_id);
			}
			catch (TwitterException e) {
				Maintenance.writeLog("Schwergsy account failed to authenticate with the following,"
						+ " error, removing from db\n" + Maintenance.getStackTrace(e), null, 1);	
				//Can remove without the need to remap id's because we know this schwergsy account was
				//the last to be added, so the ids of the others with still be in order without the
				//need to remap.
				suspendSchwergsyAccount(_id);
				removeSchwergsyAccount(_id);
				return false;
			}
		}
		return true;
	}

	/**
	 * Sets the isSuspended flag to true in the db for this Schwergsy account
	 * 
	 * @param _id The _id of the Schwergsy account
	 */
	public static  void suspendSchwergsyAccount(int _id) {
		Maintenance.writeLog("Suspending account with _id = " + _id, _id);
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		Document query = new Document("_id", _id);
		Document updater = new Document("$set", new Document("isSuspended", true));
		dbCollection.findOneAndUpdate(query, updater);
	}

	/**
	 * Returns a boolean telling whether the given account is suspended
	 * 
	 * @param index The index/_id corresponding to the schwergsy account
	 * @return
	 */
	public static  boolean isSuspended(int index) {
		return (boolean) getSchwergsyAccount(index).get("isSuspended");
	}

	/**
	 * Flag a Schwergsy Account for removal, will be remove when maintenance runs.
	 * Also Suspend it so nothing tries to use/run it in the meantime
	 * 
	 * @param _id The _id of the Schwergsy account to flag
	 */
	public static void flagAccountForRemoval(Integer _id) {
		suspendSchwergsyAccount(_id);
		replaceSchwergsyField(_id, true, "isFlaggedForDeletion");
		Maintenance.writeLog("Flagging account with _id: " + _id + " for removal", _id);
	}

	/**
	 * Deletes the Schwergsy accounts flagged in Maintenance from the database and reassign _id's
	 * of all others so that the remaining accounts are numbered 0 to (size - 1)
	 * 
	 */
	public static  void removeSchwergsyAccountsAndRemapIDs() {
		for (int i = 0; i < getCollectionSize("SchwergsyAccounts"); i++) {
			Document doc = getSchwergsyAccount(i);
			if ((boolean) doc.get("isFlaggedForDeletion")) {
				removeSchwergsyAccount((int) doc.get("_id"));
			}
		}
		remapSchwergsyAccountIDs();
	}

	/**
	 * Remove the given schwergsy account from the db.
	 * WARNING, can cause errors if an account is removed while the system is running
	 */
	private static  void removeSchwergsyAccount(int _id) {
		Maintenance.writeLog("Removing Schwergsy account with _id: " + _id);
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> collection = db.getCollection("SchwergsyAccounts");	
		Document query = new Document("_id", _id);
		collection.deleteOne(query);
	}

	/**
	 * Remaps _id's of Schwerhsy Accounts so that they are numbered 0 to (size - 1)
	 * WARNING: This should only be run during maintenance
	 * 
	 */
	private static  void remapSchwergsyAccountIDs() {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection("SchwergsyAccounts");
		//get all documents indiscriminately 
		FindIterable<Document> findIter = dbCollection.find();
		MongoCursor<Document> cursor = findIter.iterator();

		//iterate through all the schwergsy accounts, giving them unique sequential ids starting at 0;
		int index = 0;
		while (cursor.hasNext()) {
			int _id = cursor.next().getInteger("_id");
			Document query = new Document("_id", _id);
			Document updater = new Document("$set", new Document("_id", index));
			dbCollection.findOneAndUpdate(query, updater);
			index++;
		}

		cursor.close();
	}

	/**
	 * @param index the id of the Schwergsy account
	 * @return a Document containing the customerSecret, customerKey, authorizationSecret, authorizationKey,
	 * and isIncubated info from the Schwergsy account
	 * @throws Exception
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  Document getAuthorizationInfo(int index) {

		Maintenance.writeLog("scooping authInfo at index " + index, index);
		Document schwergsyAccount = getSchwergsyAccount(index);

		String customerSecret = (String) schwergsyAccount.get("customerSecret");
		String customerKey = (String) schwergsyAccount.get("customerKey");
		String authorizationSecret = (String) schwergsyAccount.get("authorizationSecret");
		String authorizationKey = (String) schwergsyAccount.get("authorizationKey");
		boolean isIncubated = (boolean) schwergsyAccount.get("isIncubated");

		Document authInfo = new Document()
		.append("customerSecret", customerSecret)
		.append("customerKey", customerKey)
		.append("authorizationSecret", authorizationSecret)
		.append("authorizationKey", authorizationKey)
		.append("isIncubated", isIncubated);

		return authInfo;	
	}


	/**TODO Bojang Test
	 * 
	 * Not tested  yet but pretty sure it will work fine since the DBH method in it is tested.
	 * @param bird
	 * @param index
	 * @throws TwitterException
	 */
	public static void initUpdateFollowing(Twitter bird, int index) throws TwitterException {

		addFollowing(index, new ArrayList<Long>(TwitterHandler.initUpdateFollowing(bird, index)));

	}

	/**
	 * @param collectionName
	 * @return The size of the given collection
	 * 
	 * Tested and given the Bojangles Seal of Approval
	 */
	public static  long getCollectionSize(String collectionName) {
		MongoDatabase db = mongoClient.getDatabase("Schwergsy");
		MongoCollection<Document> dbCollection = db.getCollection(collectionName);
		long count = dbCollection.count();
		return count;
	}

	/** TODO Bojang Test
	 * print the statistics from the given account real nicely
	 * @param schwergsyAccountName
	 */
	public static  void prettyPrintStatistics(String schwergsyAccountName) {		

		try {
			Document account = getSchwergsyAccount(schwergsyAccountName);
			ArrayList<Document> statList = (ArrayList<Document>) account.get("statistics");

			if (statList.size() > 0) {

				System.out.println("\nStatistics for account " + account.get("name") + ":\n");
				Set<Entry<String, Object>> entrySet = ((Document) statList.get(0)).entrySet();
				int columnWidth = 32;

				//print the stat names
				for (Entry<String, Object> e : entrySet) {

					String key = e.getKey();
					int textWidth = key.length();

					//the number of spaces to insert on each side
					int padding = (columnWidth - textWidth)/2;

					if (padding < 0) {
						System.out.println("String to large for column");
					}

					printNSpaces(padding);
					System.out.print(key);
					//need this if to fix off by one misprints
					if (textWidth % 2 == 0)
						printNSpaces(padding - 1);
					else
						printNSpaces(padding);
					System.out.print("|");

				}
				System.out.println("\n");

				//print the values for each stat
				for (Object obj : statList) {

					Document stat = (Document) obj;
					entrySet = stat.entrySet();
					boolean isDate = true;
					boolean isTime = true;

					for (Entry<String, Object> e : entrySet) {

						String value = null;

						//convert to millisecond date to something more readable
						if(isDate) {
							long millisecondDate = (long) e.getValue();
							Date date = new Date(millisecondDate);
							value = date.toString();
							isDate = false;
						}
						else if (isTime) {
							float millisecondTime = ((Long) e.getValue()).floatValue();
							int hours = (int) (millisecondTime / (60 * 60 * 1000));
							int mins = (int) (millisecondTime / (60 * 1000)) % 60;
							float secs = Math.round(((millisecondTime / 1000) % 60) * 1000)/1000f;
							value = hours + ":" + mins + ":" + secs;
							isTime = false;
						}
						else {
							value = e.getValue().toString();
						}

						int textWidth = value.length();

						//the number of spaces to insert on each side
						int padding = (columnWidth - textWidth)/2;

						if (padding < 0) {
							System.out.println("String to large for column");
						}

						printNSpaces(padding);
						System.out.print(value);

						//need this if to fix off by one misprints
						if (textWidth % 2 == 0)
							printNSpaces(padding - 1);
						else
							printNSpaces(padding);
						System.out.print("|");
					}
					System.out.println();
				}
			}
			else {
				Maintenance.writeLog("Cannot print stats. No stats have been entered yet");
			}
			System.out.println("\n------------------------------------------------------------------"
					+ "-----------------------------------------------------------------------------"
					+ "---------------------------------------------------------------------------\n");
		} 		
		catch (Exception e) {
			Maintenance.writeLog("Error printing\n" + Maintenance.getStackTrace(e), null, -1);
		}

		finally{
		}		
	}

	private static void printNSpaces(int n) {	
		for (int i = 0; i < n; i++) {
			System.out.print(" ");
		}
	}
}


