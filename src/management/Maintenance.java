package management;

import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;

//sets the global maintenance flag for director
public class Maintenance {
	
	//The key is index + runnable type
	public static HashMap<String, Boolean> runStatus; 
	
	//flag that determines whether maintenance is occuring (runnables check this and pause themselves)
	public static boolean flagSet;
	
	public static void cleanBigAccs() throws UnknownHostException, FuckinUpKPException{
		for(int index = 0; index<DataBaseHandler.getCollectionSize("SchwergsyAccounts"); index++){
			Long[] bigAcc = DataBaseHandler.getSchwergsyAccountArray(index, "bigAccounts").toArray(new Long[DataBaseHandler.getSchwergsyAccountArraySize(index, "bigAccounts")]);
			Long[] bigAccWhiteList = DataBaseHandler.getSchwergsyAccountArray(index, "bigAccountsWhiteList").toArray(new Long[DataBaseHandler.getSchwergsyAccountArraySize(index, "bigAccountsWhiteList")]);
			HashSet<Long> toAddToBigAccWhiteList = new HashSet<Long>();
			HashSet<Long> bigAccWhiteListSet = new HashSet<Long>();

			for(Long id : bigAccWhiteList){
				bigAccWhiteListSet.add(id);
			}
			
			for(Long id : bigAcc){
				if(!bigAccWhiteListSet.contains(id)){
					toAddToBigAccWhiteList.add(id);
				}
			}
			
			for(Long id : toAddToBigAccWhiteList){
				DataBaseHandler.addBigAccWhiteList(index, id);
			}
		}
	}
	
	public static void cleanToFollows() throws UnknownHostException, FuckinUpKPException{
		for(int index = 0; index<DataBaseHandler.getCollectionSize("SchwergsyAccounts"); index++){
			Long[] toFollow = DataBaseHandler.getSchwergsyAccountArray(index, "toFollow").toArray(new Long[DataBaseHandler.getToFollowSize(index)]);
			Long[] whiteList = DataBaseHandler.getSchwergsyAccountArray(index, "bigAccountsWhiteList").toArray(new Long[DataBaseHandler.getSchwergsyAccountArraySize(index, "whiteList")]);
			HashSet<Long> toAddToWhiteList = new HashSet<Long>();
			HashSet<Long> whiteListSet = new HashSet<Long>();

			for(Long id : whiteList){
				whiteListSet.add(id);
			}
			
			for(Long id : toFollow){
				if(!whiteListSet.contains(id)){
					toAddToWhiteList.add(id);
				}
			}
			DataBaseHandler.addWhitelist(index, toAddToWhiteList.toArray(new Long[toAddToWhiteList.size()]));	
		}
	}
	
	public static void performMaintenance() throws Exception {
		System.out.println("maintenance started");
		flagSet = true;				
		boolean activityExists = true;
		while (activityExists) {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			//look for a status that's true (indicating that something's still running)
			boolean somethingStillRunning = false;
			for (boolean status : runStatus.values()) {
				if (status) {
					somethingStillRunning = true;
					break;
				}
			}

			if (!somethingStillRunning) {
				activityExists = false;
			}
		}
		
		//TODO start all the threads because the all commit suicide when they see maintenance flag is set

		//TODO save current time, do all maintenance that doesn't do calls to twitter, then if 15 min
		//has passed do maintenance that requires calls to twitter. If < 15 passed, wait until 15 has passed
		
		//update followers for each schwergsy account
		long size = DataBaseHandler.getCollectionSize("SchwergsyAccounts");
		for (int i = 0; i < size; i++) {
			DataBaseHandler.updateFollowers(i);
		}
		
		/*TODO the actual maintenance, Order task such that fastest tasks are done first,
		 * consider having a maintenance cutoff if it runs for like 4 hours
		 * call dbhandler's updateFollowers method for each schwergsy account
		 * old content garbage collection
		 * sweep through links in all pending and regular content checking for validity
		 * check for new schwergsy accounts and start their timertasks
		 * get big accounts (because of high api call amount)
		 */

		//cleans up and syncs bigAccounts with bigAccountsWhiteList
		try {
			cleanBigAccs();
		} catch (UnknownHostException | FuckinUpKPException e) {
			System.err.println("ERROR: failed to clean BigAccs ");
			e.printStackTrace();
		}
		
		//cleans up and syncs toFollow with whiteList
		try {
			cleanToFollows();
		} catch (UnknownHostException | FuckinUpKPException e) {
			System.err.println("ERROR: failed to clean ToFollows ");
			e.printStackTrace();
		}
		
		//get the global variables from the GlobalVariables collection to set the ones in GlobalStuff
		try {
			DataBaseHandler.findAndSetGlobalVars();
		} catch (UnknownHostException e) {
			System.err.println("ERROR: failed to find ");
			e.printStackTrace();
		}

		flagSet = false;
		System.out.println("maintenance complete");
	}
}