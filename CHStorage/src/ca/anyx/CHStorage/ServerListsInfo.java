package ca.anyx.CHStorage;

import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 	Intended to hold information about the server list:
 * 		servers in grace, that need to be tested, and ones that are confirmed down.
 * 
 */
public class ServerListsInfo {
	public JSONArray servers;
	
	public JSONObject gracelist;
	public Set<String> to_test;
	//public Set<String> dead_servers;
	public Vector<SocketHelper> unfinished_connections;

	public JSONObject dead_data = new JSONObject();
	
	/**
	 * 		Will create a default set of server information
	 * 
	 * @param serverlist		The servers to use, under the key "servers" as a JSON array within a JSON object
	 * @throws JSONException	If the servers could not be deciphered
	 */
	public ServerListsInfo( JSONObject serverlist ) throws JSONException{
		servers = serverlist.getJSONArray("servers");
		gracelist = new JSONObject();
		to_test = new HashSet<String>();
		//dead_servers = new HashSet<String>();
		unfinished_connections = new Vector<SocketHelper>();
		
		//Start the dead_data with empty objects for each server
		for (int index = 0; index < servers.length(); index++ ){
			dead_data.put(servers.getString(index), new JSONObject());
		}
	}
	
	/**
	 * 		Synchronized add.
	 * @param url	URL to grace.
	 */
	public synchronized void addToGraceList( String url ){
		try {
			gracelist.put(url, System.currentTimeMillis() );
		} catch (JSONException e) {}
	}
	
	/**
	 * 		Synchronized remove.
	 * @param url	URL to de-grace.
	 */
	public synchronized void removeFromGraceList( String url ){
		gracelist.remove(url);
	}
	
	/**
	 * 		Synchronized search.
	 * @param url	URL to look for.
	 * @return		true if found, false if not.
	 */
	public synchronized Boolean hasInGraceList( String url ){
		return gracelist.has(url);
	}
	
	/**
	 * 		Synchronized find -- the Long value associated with a URL in grace.
	 * @param url	URL to look for.
	 * @return		The long with the key, or -1 if not found.
	 */
	public synchronized long getGraceListLong( String url ){
		try {
			return gracelist.getLong(url);
		} catch (JSONException e) {
			return -1;
		}
	}
	
	/**
	 * 		Overloaded for  Vector<SocketHelper>, SocketHelper, or String.
	 * 		Will add the url associated to the testing collection.
	 * @param shs_to_add	Will retrieve url from this.
	 */
	public synchronized void addTestURLs( Vector<SocketHelper> shs_to_add ){
		Iterator<SocketHelper> iter = shs_to_add.iterator();
		while ( iter.hasNext() ){
			this.to_test.add( iter.next().myURL );
		}
	}
	/**
	 * 		Overloaded for  Vector<SocketHelper>, SocketHelper, or String.
	 * 		Will add the url associated to the testing collection.
	 * @param sh_to_add	Will retrieve url from this.
	 */
	public synchronized void addTestURLs( SocketHelper sh_to_add ){
		this.to_test.add(sh_to_add.myURL );
	}
	/**
	 * 		Overloaded for  Vector<SocketHelper>, SocketHelper, or String.
	 * 		Will add the url associated to the testing collection.
	 * @param url	Will retrieve url from this.
	 */
	public synchronized void addTestURLs( String url ){
		this.to_test.add( url );
	}
	
	
	
	/**
	 * 			Compares one data JSON object to another, rebuilding
	 * 			ours to take the newest values.
	 * 
	 * @param j		A different NodeData to compare to
	 * @return		Whether or not ALL our data was newer or the same as the other nodes data.
	 */
	public synchronized Boolean RebuildData(JSONObject j) {
		Boolean wearenewest = true;
		long theirstarttime;
		long ourstarttime;
		int their_failcount;
		int our_failcount;
		
		if ( j.toString().equals(dead_data.toString()) ) {
			//broadcast("Rebuild> data objects were same, this shouldn't happen.");
			return wearenewest; 
		}
		
		// iterate over each server and rebuild with the newest values
		for (int i = 0; i < this.servers.length(); i++){

			try{
				theirstarttime = j.getJSONObject(this.servers.getString(i)).getLong("uptime");
			} catch (JSONException e) { // wasn't initialized? or unknown status
				theirstarttime = -2;
			}

			try{
				ourstarttime = this.dead_data.getJSONObject(this.servers.getString(i)).getLong("uptime");
			} catch (JSONException e) { // wasn't initialized? or unknown status
				ourstarttime = -2;
			}	

			
			try{
				their_failcount = j.getJSONObject( this.servers.getString(i) ).getInt("failures");
			} catch (JSONException e) { // wasn't initialized
				their_failcount = -1;
			}
			
			try{
				our_failcount =  this.dead_data.getJSONObject( this.servers.getString(i) ).getInt("failures");
			} catch (JSONException e){ // wasn't initialized
				our_failcount = -1;
			}
				
			try{
				// The larger failure count will contain the newest data
				if ( their_failcount > our_failcount ){
					this.dead_data.put(servers.getString(i), j.getJSONObject(this.servers.getString(i)));
					wearenewest = false;

				}else if ( their_failcount < our_failcount ){
					this.dead_data.put(servers.getString(i), this.dead_data.getJSONObject(this.servers.getString(i)));
					
				// if the fail count is the same, compare uptime.
				}else if (theirstarttime > ourstarttime){
					this.dead_data.put(servers.getString(i), j.getJSONObject(this.servers.getString(i)));
					wearenewest = false;
				}else{
					//when rebuilding we should care to put everything back in, in the same order.
					this.dead_data.put(servers.getString(i), this.dead_data.getJSONObject(this.servers.getString(i)));
				}
			} catch (JSONException e) {
				// should never get here
				e.printStackTrace();
			}	
		}

		//broadcast("Rebuild> " + this.dead_data.toString());

		return wearenewest;
	}
	
	/**
	 * 				Overwrites the data package at a given server name
	 * 				TODO: Reduce overhead of building a new object then copying over?
	 * 			
	 * @param url		The url to put the new data in.
	 * @param newdata	The new data to put to the url's slot.
	 * @return			Whether or not the overwrite was necessary. (True = necessary)
	 */
	public synchronized Boolean OverwriteDataAt ( String url, JSONObject newdata ){
		JSONObject j = new JSONObject();

		try {
			if ( this.dead_data.getJSONObject(url).toString().equals(newdata.toString()) ){
				//broadcast("Overwrite> nothing to do.");
				return false;	//nothing to do
			}
			//build new data into j
			for (int i = 0; i < this.servers.length(); i++){
				if (servers.getString(i).equals(url)){
					j.put( servers.getString(i), newdata );
				} else{
					j.put(servers.getString(i), this.dead_data.getJSONObject(this.servers.getString(i)) );
				}
			}

			//replace data with j to keep ordering
			for (int i = 0; i < this.servers.length(); i++){
				dead_data.put( servers.getString(i), j.get( servers.getString(i) ) );
			}

		} catch (JSONException e) {
			// should never get here
			e.printStackTrace();
		}
		
		//broadcast( "Overwrite> " + this.dead_data.toString() );
		
		return true;
	}
	
	/**
	 * 		Responsible for gathering and packaging our nodes' data.
	 * 
	 * @return	A JSONObject that contains at the minimum, our 'start time' of
	 * 			the program, as well as any other relevant data we wish to tell
	 * 			the other nodes.
	 */
	public synchronized JSONObject BuildMyData(){
		JSONObject MyData = new JSONObject();
		try {

			RuntimeMXBean mxBean = ManagementFactory.getRuntimeMXBean();

			MyData.put("uptime", mxBean.getStartTime());
			
			/*
			if ( counter  ==  0 ){
				counter = 5;
				// No need to spam the network with constantly changing things like RAM usage
				mydatapacket.put( "up", 	BashCommander.GetUptime() );
				mydatapacket.put( "usage", 	BashCommander.GetDiskUsage() );
				mydatapacket.put( "space", 	BashCommander.GetDiskSpace() );
				broadcast("Updating data packet.");
			}else{
				counter--;
			}
			*/
			
			
			//MyData.put("datapackage", mydatapacket);

		} catch (JSONException e) {
			// should never get here
			e.printStackTrace();
		}

		return MyData;
	}
	
	/**
	 * 	Lists a server as dead.
	 * 
	 * @param url	The server to apply the dead info to.
	 */
	public synchronized void ListServerDead( String url ){
		JSONObject deadurl = new JSONObject();

		try {
			deadurl.put("uptime", -1);
		} catch (JSONException e) {}// shouldn't get here

		int num_failures;
		try {
			num_failures = this.dead_data.getJSONObject(url).getInt("failures");
			if (this.dead_data.getJSONObject(url).getLong("uptime") > 0 ){
				num_failures++; // Used to be up, it's a new failure
			}
		} catch (JSONException e1) {
			num_failures = 0; // Failures not initialized, start them at 0
		}

		try {
			deadurl.put("failures", num_failures);
		} catch (JSONException e) {}// shouldn't get here

		OverwriteDataAt( url, deadurl );

		//broadcast( url + " is dead.");

		//this.polltime = MIN_POLLTIME;
	}
	
	/**
	 * 		Checks if a server is online or offline via our data.
	 * 
	 * @param url	The server to look for
	 * @return		True if online, false if not.
	 */
	public boolean IsServerDead ( String url ){
		try {
			
			int uptime = this.dead_data.getJSONObject(url).getInt("uptime");
			
			if ( uptime > 0 ) {
				return true;
			}else{
				return false;
			}
			
		} catch (JSONException e) {
			return true; // Server not found? Uptime not initialized? Report as dead
		}
		
	}
	
	/**
	 * @return		Returns a string representation of HEX values associated with
	 * 				the SHA256 of the data package.
	 */
	public synchronized String getSHA(){
		byte[] hash = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			hash = digest.digest( this.dead_data.toString().getBytes("ISO-8859-1"));
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		String s = DatatypeConverter.printHexBinary(hash);
		return s;
	}

}
