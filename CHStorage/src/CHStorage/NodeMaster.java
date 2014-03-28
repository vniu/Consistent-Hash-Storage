package CHStorage;

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
	private JSONObject storage;

	public GraceList gracelist;
	
	private int storagecount = 0;
	Thread NodeStatus;

	/**
	 * 		Constructor - a new NodeMaster object based on the server text file.
	 * 
	 * @param serverlist		The list of servers that will be running the program.
	 * @throws JSONException	if something was wrong with the server list format.
	 */
	public NodeMaster( JSONObject serverlist ) throws JSONException {
		servers = serverlist.getJSONArray("servers");
		storage = new JSONObject();
		//to_test= new HashSet<String>();
		gracelist = new GraceList();
		NodeStatus = new Thread ( new NodeStatusRunnable( this ) );
		NodeStatus.start();
	}

	private static void broadcast ( String m ) {
		if ( !SysValues.DEBUG ) return;

		System.out.println( "NodeMaster> " + m );
	}

	/**
	 * 		Synchronized access to this node's key values.
	 * 
	 * @return	This nodes key values stored, as a string, or an empty string if a problem occurred.
	 */
	public synchronized String getStorageString(){
		try {
			return this.storage.toString(5);
		} catch (JSONException e) {
			return "";
		}
	}

	/**
	 * 		A director to execute a command.
	 * 
	 * @param j		The command to execute.
	 * @return		The result of the execution, including "ErrorCode". See craftResponse for error codes.
	 */
	public JSONObject keycommand ( JSONObject j ){

		if ( j == null ){
			return craftResponse(5);
		}

		if ( j.has("shutdown") ){
			SysValues.shutdown = true;
			return craftResponse(0);

		}else if( j.has("status") ){
			return craftResponse(200);
			
		}else if ( j.has("internal") == false ){
			// Externalize the command
			return this.sendmessageremote( j );

		}else if ( j.has("put") ){
			return this.putKV( j );

		}else if ( j.has("get") ){
			return this.getKV( j );

		}else if ( j.has("remove") ){
			return this.removeKV( j );

		} 

		broadcast( "No command in the object?");
		return craftResponse(5);
	}

	/**
	 * 		Puts a key and its value into the system.
	 * 
	 * @param j		The key/value pair to put in.
	 * @return		JSONObject with the status: ErrorCode 4 if the put failed, 0 if successful.
	 */
	public synchronized JSONObject putKV( JSONObject j ) {

		try {
			if ( storagecount >= SysValues.MAX_STORAGE )
				return craftResponse(2);

			if ( this.storage.has( j.getString("key") ) ){
				// Already had the key, don't change storage count as we replace it
			}else{
				storagecount++;
			}

			this.storage.put( j.getString( "key" ), j.get( "value" ) );
			broadcast("Put key: " + j.getString( "key" ) );

			return craftResponse(0);

		} catch (JSONException e) {
			broadcast( "Invalid put?" );
			return craftResponse(4); 
		}
	}

	/**
	 * 		Retrieves a key from the system.
	 * 
	 * @param j		The key to get the value for.
	 * @return		JSONObject with the status: ErrorCode 1 if key was not found, 0 if successful.
	 * 				Will also contain the value in the object if it was successful.
	 */
	public synchronized JSONObject getKV( JSONObject j ) {
		//value;
		JSONObject response = craftResponse(0);
		try {
			Object value = this.storage.get( j.getString( "key" ) );
			response.put("value", value);

			return response;

		} catch (JSONException e) {
			broadcast( "Invalid get: not existant?" );
			return craftResponse(1);
		}
	}

	/**
	 * 		Will remove a key and its value from the system.
	 * 
	 * @param j		A container with the key of the object to remove.
	 * @return		JSONObject with the status: ErrorCode 1 if the key was not found, 0 if successful.
	 */
	public synchronized JSONObject removeKV( JSONObject j ) {

		try {
			Object s = this.storage.remove( j.getString( "key" ) );
			if ( s == null ) throw new JSONException("null");
			broadcast("Removed key: " + j.getString( "key" ) );
			storagecount--;
			return craftResponse(0);

		} catch (JSONException e) {
			broadcast( "Invalid remove: not existant?" );
			return craftResponse(1);
		}
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
				return craftResponse(5);
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
					return craftResponse(5);
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
							return craftResponse(5);
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
		if (agreed_response.size() == 0 ) return craftResponse(3);
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
			return craftResponse(23);	// node fail to connect
		}

		sh.SendMessage( j.toString() );
		String recmessage = sh.ReceiveMessage(SysValues.LISTEN_TIMEOUT);

		if( recmessage == null ){
			broadcast( "Response from external server failure (null): " + url );
			sh.CloseConnection();
			return craftResponse(23); // overloaded?	//node fail to connect
		}

		try {
			JSONObject response = new JSONObject(recmessage);
			sh.SendMessage("{\"stop\":true}"); // Tell them not to keep the connection open - it was an internal send, not a client
			sh.CloseConnection();
			return response;

		} catch (JSONException e) {
			broadcast( "Response from external server, internal failure?: " + url );
			sh.CloseConnection();
			return craftResponse(4); // BAD or malformed response from server: shouldn't get here. node fail to connect?
		}
	}

	/**
	 * 		A helper function to quickly make a response with an error code.
	 * 
	 * @param ErrorCode	The (int) error code to be placed under the key "ErrorCode"
	 * @return			The newly formed object
	 */
	static JSONObject craftResponse ( int ErrorCode ){
		JSONObject response = new JSONObject();
		try {
			response.put("ErrorCode", ErrorCode);

			switch(ErrorCode){
			case 0:
				response.put("ErrorInfo", "Success!");
				break;
			case 1:
				response.put("ErrorInfo", "Non-Existant key.");
				break;
			case 2:
				response.put("ErrorInfo", "No space remaining to put.");
				break;
			case 3:
				response.put("ErrorInfo", "System overload.");
				break;
			case 4:
				response.put("ErrorInfo", "Internal Store failure.");
				break;
			case 5:
				response.put("ErrorInfo", "Unrecognized or malformed command.");
				break;
			case 200:
				response.put("ErrorInfo", "OK");
				response.put("status", "OK");
				break;
			default:
				response.put("ErrorInfo", "Unknown Error.");
				break;
			}
		} catch (JSONException e) {
			try {
				broadcast("INTERNAL CODE FAILURE");
				response.put("ErrorCode", 20); // Some sort of internal failure?
			} catch (JSONException e1) {}
		}
		return response;
	}

}
