package CHStorage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 	Intended to hold information about the server list:
 * 		servers in grace, that need to be tested, and ones that are confirmed down.
 * 
 */
public class GraceList {
	public JSONObject gracelist;
	public Set<String> to_test;
	public Set<String> dead_servers;
	public Vector<SocketHelper> unfinished_connections;

	public GraceList(){
		gracelist = new JSONObject();
		to_test = new HashSet<String>();
		dead_servers = new HashSet<String>();
		unfinished_connections = new Vector<SocketHelper>();
	}
	
	public synchronized void addToGraceList( String url ){
		try {
			gracelist.put(url, System.currentTimeMillis() );
		} catch (JSONException e) {}
	}
	
	public synchronized void removeFromGraceList( String url ){
		gracelist.remove(url);
	}
	
	public synchronized Boolean hasInGraceList( String url ){
		return gracelist.has(url);
	}
	
	public synchronized long getGraceListLong( String url ){
		try {
			return gracelist.getLong(url);
		} catch (JSONException e) {
			return -1;
		}
	}
	
	public synchronized void addTestURLs( Vector<SocketHelper> shs_to_add ){
		Iterator<SocketHelper> iter = shs_to_add.iterator();
		while ( iter.hasNext() ){
			this.to_test.add( iter.next().myURL );
		}
	}
	public synchronized void addTestURLs( SocketHelper sh_to_add ){
		this.to_test.add(sh_to_add.myURL );
	}
	public synchronized void addTestURLs( String url ){
		this.to_test.add( url );
	}
}
