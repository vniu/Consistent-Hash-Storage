package ca.anyx.CHStorage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

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
	public Set<String> dead_servers;
	public Vector<SocketHelper> unfinished_connections;

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
		dead_servers = new HashSet<String>();
		unfinished_connections = new Vector<SocketHelper>();
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
}
