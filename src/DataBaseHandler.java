import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Date;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class DataBaseHandler {
	
	
	
	public static synchronized void getRandomAssContent() throws UnknownHostException{
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB("Schwergsy");
		DBCollection dbCollection = db.getCollection("AssContent");
		
		//Figure out randomness
	}
	
	public static synchronized void newAssContent(String caption, String imglink) throws UnknownHostException{
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB("Schwergsy");
		DBCollection dbCollection = db.getCollection("AssContent");
		
		BasicDBObject uniqueCheck = new BasicDBObject("imglink", imglink);
		
		if(dbCollection.find(uniqueCheck).limit(1).count()==0){
			int count = 0;
			long id_time = new Date().getTime();

			BasicDBObject newAss = new BasicDBObject("_id", id_time);
			newAss.append("caption", caption);
			newAss.append("imglink", imglink);
			newAss.append("times_accessed", count);
			newAss.append("last_accessed", id_time);

			dbCollection.insert(newAss);
			System.out.println("Successfully added new AssContent "+id_time);
		}
		
		else{
			System.out.println("Image is not unique: "+ imglink);
		}
	}

	public static synchronized void addArrayToSchwergsArray(int index, String[] StringArr, String column) throws UnknownHostException{
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB("Schwergsy");
		DBCollection dbCollection = db.getCollection("SchwergsAccounts");
		BasicDBObject query = new BasicDBObject("_id", index);
		BasicDBObject arr = new BasicDBObject("$addToSet",
				new BasicDBObject(column,
						new BasicDBObject("$each", StringArr)));

		dbCollection.update(query, arr);
	}

	public static synchronized void addElementToSchwergsArray(int index, String element, String column) throws UnknownHostException{
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB("Schwergsy");
		DBCollection dbCollection = db.getCollection("SchwergsAccounts");
		BasicDBObject query = new BasicDBObject("_id", index);
		BasicDBObject ele = new BasicDBObject("$addToSet",
				new BasicDBObject(column, element));

		dbCollection.update(query, ele);
	}
	
	public static synchronized void addFollowers(int index, String[] followersArr) throws UnknownHostException{
		addArrayToSchwergsArray(index,followersArr,"followers");
	}
	
	public static synchronized void addFollowing(int index, String[]followingArr) throws UnknownHostException{
		addArrayToSchwergsArray(index,followingArr,"following");
	}
	
	public static synchronized void addToFollow(int index, String[]toFollowArr) throws UnknownHostException{
		addArrayToSchwergsArray(index,toFollowArr,"to_follow");
	}
	
	public static synchronized void addWhitelist(int index, String[]whitelistArr) throws UnknownHostException{
		addArrayToSchwergsArray(index,whitelistArr,"whitelist");
	}
	
	public static synchronized void addBigAccount(int index, String bigAccountElement) throws UnknownHostException{
		addElementToSchwergsArray(index,bigAccountElement,"bigAccounts");
	}
	

	public static synchronized void insertSchwergsyAccount(
			String dbName,
			String collectionName,
			SchwergsyAccount account) throws UnknownHostException {

		System.out.println("inserting a new Schwergsy Account");
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB(dbName);
		DBCollection dbCollection = db.getCollection(collectionName);

		AuthorizationInfo authInfo = account.getAuthorizationInfo();

		BasicDBList authInfoList = new BasicDBList();
		authInfoList.add(new BasicDBObject("customerSecret", authInfo.getCustomerSecret()));
		authInfoList.add(new BasicDBObject("customerKey", authInfo.getCustomerKey()));
		authInfoList.add(new BasicDBObject("authorizationSecret", authInfo.getAuthorizationSecret()));
		authInfoList.add(new BasicDBObject("authorizationKey", authInfo.getAuthorizationKey()));
		authInfoList.add(new BasicDBObject("isIncubated", authInfo.isIncubated()));

		BasicDBObject basicBitch = new BasicDBObject()
		.append("accountID", account.getAccountID())
		.append("name", account.getName())
		.append("followers", account.getFollowers())
		.append("following", account.getFollowing())
		.append("toFollow", account.getToFollow())
		.append("whiteList", account.getWhiteList())
		.append("bigAccounts", account.getBigAccounts())
		.append("authorizationInfo", authInfoList);

		dbCollection.insert(basicBitch);
	}

	public static synchronized AuthorizationInfo getAuthorizationInfo(String dbName, String collectionName, int index) throws Exception {

		System.out.println("scooping authInfo at index " + index);
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB(dbName);
		DBCollection dbCollection = db.getCollection(collectionName);

		DBCursor dbCursor = dbCollection.find();

		for (int i = 0; i < index; i++) {			
			if (dbCursor.hasNext())
				dbCursor.next();
			else {			
				System.out.println("that ass passed in an invalid index for this authorization info");
				throw new FuckinUpKPException();
			}
		}

		DBObject schwergsAccount = dbCursor.next();	

		BasicDBList authInfoList = (BasicDBList) schwergsAccount.get("authorizationInfo");		

		String customerSecret = (String) ((BasicDBObject) authInfoList.get("0")).get("customerSecret");
		String customerKey = (String) ((BasicDBObject) authInfoList.get("1")).get("customerKey");
		String authorizationSecret = (String) ((BasicDBObject) authInfoList.get("2")).get("authorizationSecret");
		String authorizationKey = (String) ((BasicDBObject) authInfoList.get("3")).get("authorizationKey");
		boolean isIncubated = (boolean) ((BasicDBObject) authInfoList.get("4")).get("isIncubated");

		return new AuthorizationInfo(customerSecret, customerKey, authorizationSecret, authorizationKey, isIncubated);		
	}

	public static synchronized long getCollectionSize(String dbName, String collectionName) throws UnknownHostException {

		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB(dbName);
		DBCollection dbCollection = db.getCollection(collectionName);
		return dbCollection.count();

	}

	public static synchronized long getListSize(String dbName, String collectionName, int index, String listName) throws UnknownHostException, FuckinUpKPException {
		return getList(dbName, collectionName, index, listName).size();
	}

	public static synchronized AssContent getRandomishAssImage(String dbName, String collectionName) throws UnknownHostException {

		System.out.println("scooping ass image");
		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB(dbName);
		DBCollection dbCollection = db.getCollection(collectionName);

		int randIndex = (int) (Math.random() * dbCollection.getCount());

		DBCursor dbCursor = dbCollection.find();

		for (int i = 0; i < randIndex; i++) {
			dbCursor.next();
		}

		DBObject ass = dbCursor.next();

		//TODO check that this shouldn't actaully be a BasicBSONList instead
		BasicDBList contents = (BasicDBList) ass.get("contents");
		BasicDBList accessData = (BasicDBList) ass.get("accessData");

		String link = (String) ((BasicDBObject) contents.get("0")).get("link");
		String caption = (String) ((BasicDBObject) contents.get("1")).get("caption");
		int timesAccessed = (int) ((BasicDBObject) accessData.get("0")).get("timesAccessed");
		Date lastAccessDate = (Date) ((BasicDBObject) accessData.get("1")).get("lastAccessDate");

		return new AssContent(link, caption, timesAccessed, lastAccessDate);		
	}


	public static synchronized BasicDBList getList(String dbName, String collectionName, int index, String listName) throws UnknownHostException, FuckinUpKPException {

		MongoClient mongoClient = new MongoClient();
		DB db = mongoClient.getDB(dbName);
		DBCollection dbCollection = db.getCollection(collectionName);

		DBCursor dbCursor = dbCollection.find();

		for (int i = 0; i < index; i++) {			
			if (dbCursor.hasNext())
				dbCursor.next();
			else {			
				System.out.println("that ass passed in an invalid index");
				throw new FuckinUpKPException();
			}
		}

		DBObject schwergsyAccount = dbCursor.next();	

		BasicDBList list = (BasicDBList) schwergsyAccount.get(listName);

		return list;
	}

}
