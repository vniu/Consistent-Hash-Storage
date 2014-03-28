package ca.anyx.CHStorage;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *		Responsible for deciphering and executing commands.
 */
public class NodeMaster {
	public  JSONArray servers; 
	public GraceList gracelist;
	public DataStorage my_storage;
	
	Thread NodeStatus;

	/**
	 * 		Constructor - a new NodeMaster object based on the server text file.
	 * 
	 * @param serverlist		The list of servers that will be running the program.
	 * @throws JSONException	if something was wrong with the server list format.
	 */
	public NodeMaster( JSONObject serverlist ) throws JSONException {
		servers = serverlist.getJSONArray("servers");
		gracelist = new GraceList();
		my_storage = new DataStorage();
		NodeStatus = new Thread ( new NodeStatusRunnable( this ) );
		NodeStatus.start();
	}

	private static void broadcast ( String m ) {
		if ( !SysValues.DEBUG ) return;

		System.out.println( "NodeMaster> " + m );
	}

	/**
	 * 		A director to execute a command.
	 * 
	 * @param j		The command to execute.
	 * @return		The result of the execution, including "ErrorCode". See craftResponse for error codes.
	 */
	public JSONObject keycommand ( JSONObject j ){

		if ( j == null ){
			return DataStorage.craftResponse(5);
		}

		if ( j.has("shutdown") ){
			SysValues.shutdown = true;
			return DataStorage.craftResponse(0);

		}else if( j.has("status") ){
			return DataStorage.craftResponse(200);
			
		}else if ( j.has("internal") == false ){
			// Externalize the command
			return this.sendmessageremote( j );

		}else if ( j.has("put") ){
			return my_storage.putKV( j );

		}else if ( j.has("get") ){
			return my_storage.getKV( j );

		}else if ( j.has("remove") ){
			return my_storage.removeKV( j );

		} 

		broadcast( "No command in the object?");
		return DataStorage.craftResponse(5);
	}

	

	/**
	 * 		A preliminary test of Consistent Hashing. 
	 * 		This method successfully returns a location between 0 and servers.length() -- ( well, minus one, as its an index )
	 * 		which is psuedorandom (even distribution), and the result is consistent across 
	 * 		any node who calls this function on any environment.
	 * 
	 * @param key	The value to get the resulting location from.
	 * @return		A consistent location between 0 and servers.length() based on the key.
	 */
	static public int mapto( String key, JSONArray _servers ){
		byte[] hash = null;

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			hash = digest.digest( key.getBytes("ISO-8859-1") ); // Keep all data with a 1 to 1 string encoding
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			e.printStackTrace(); // Hardcoded algorithms/encoding, won't get here.
		}
		ByteBuffer bb = ByteBuffer.wrap(hash);
		int loc = bb.getInt();
		loc = Math.abs( loc % _servers.length() );
		return loc;
	}


	/**
	 * 		Externalize a command to the location based off of our consistent hashing
	 * 		function. Will basically send the same command, with an appended key
	 * 		that lets the server know it is an internal system command instead of a client one.
	 * 
	 * @param j		The message to externalize.
	 * @return		The result received by the server.
	 */
	public JSONObject sendmessageremote( JSONObject j ){
		int location = 0;


		// This is the old way - no redundancy:

		if ( SysValues.FAILOVER_ONLY ) {
			String url = null;
			JSONObject response = new JSONObject();
			
			try {
				location = NodeMaster.mapto ( j.getString("key"), this.servers );
				url = this.servers.getString(location);
				j.put("internal", true);
			} catch (JSONException e) {
				broadcast("Couldn't map to a location?");
				return DataStorage.craftResponse(5);
			}
			
			while ( gracelist.dead_servers.contains( url ) ){
				location++;
				if ( location == servers.length() ) // Ensure we loop around the servers properly
					location = 0;
				try {
					url = this.servers.getString(location);
					continue;
				} catch (JSONException e1) {
					broadcast("Couldn't map to a location?");
					return DataStorage.craftResponse(5);
				}
			}
			
			while (true){
				response = __DEPRECIATED__sendMessageTo ( j, url );
			
				try {
					if ( response.getInt("ErrorCode") == 23 ){
						gracelist.addTestURLs ( url );
						throw new JSONException("Node connect fail");
					}else{
						gracelist.addToGraceList(url);
						return response;
					}
				} catch (JSONException e) {
				// Node CONNECTION was a failure, go to next node.
					while ( gracelist.dead_servers.contains( url ) ){
						location++;
						if ( location == servers.length() ) // Ensure we loop around the servers properly
							location = 0;
						try {
							url = this.servers.getString(location);
							continue;
						} catch (JSONException e1) {
							broadcast("Couldn't map to a location?");
							return DataStorage.craftResponse(5);
						}
					}
					
				}
			}
		}// End failover only

		//Thread red = new Thread( new RedundancyRunnable(agreed_response, j, this) );
		//red.start();
		
		//int timer = 0;
		//while ( agreed_response.size() == 0 ){
		//	try {
		//		Thread.sleep(1);
		//	} catch (InterruptedException e) {}
		//	
		//	timer++;
		//	if ( timer > SysValues.listentimeout*1000 )
		//		return craftResponse(3);
		//}
		//return agreed_response.firstElement();
		
		Vector<JSONObject> agreed_response = new Vector<JSONObject>();
		RedundancyRunnable RR = new RedundancyRunnable( agreed_response, j, this );
		RR.execute();
		if (agreed_response.size() == 0 ) return DataStorage.craftResponse(3);
		return agreed_response.firstElement();
		
	}
	
	
	
	
	

	/**
	 * 		Depreciated with new redundancy functionality.
	 * 		
	 * 		A helper function to send a message j to a url, and return their response.
	 * 
	 * @param j		The message to send
	 * @param url	The location to send to
	 * @return		The response received, or an error coded response based off of the result.
	 */
	JSONObject __DEPRECIATED__sendMessageTo ( JSONObject j, String url ){
		SocketHelper sh = new SocketHelper();

		if ( sh.CreateConnection( url, SysValues.CLIENT_PORT ) != 0 ) {
			broadcast( "Response from external server failure (connection creation): " + url );
			return DataStorage.craftResponse(23);	// node fail to connect
		}

		sh.SendMessage( j.toString() );
		String recmessage = sh.ReceiveMessage(SysValues.LISTEN_TIMEOUT);

		if( recmessage == null ){
			broadcast( "Response from external server failure (null): " + url );
			sh.CloseConnection();
			return DataStorage.craftResponse(23); // overloaded?	//node fail to connect
		}

		try {
			JSONObject response = new JSONObject(recmessage);
			sh.SendMessage("{\"stop\":true}"); // Tell them not to keep the connection open - it was an internal send, not a client
			sh.CloseConnection();
			return response;

		} catch (JSONException e) {
			broadcast( "Response from external server, internal failure?: " + url );
			sh.CloseConnection();
			return DataStorage.craftResponse(4); // BAD or malformed response from server: shouldn't get here. node fail to connect?
		}
	}

	

}
