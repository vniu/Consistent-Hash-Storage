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
	private ServerListsInfo	link_serverinfo;
	
	public DataStorage my_storage;

	Thread NodeStatus;
	
	FinalizeRunnable finalizer;

	/**
	 * 		Constructor - a new NodeMaster object based on the servers.
	 * 
	 * @param serverinfo	The server lists object.
	 */
	public NodeMaster( ServerListsInfo serverinfo ) {
		link_serverinfo = serverinfo;
		
		if (SysValues.STORAGE_STRATEGY == StorageStrategy.REDUNDANCY){
			my_storage = new RedundantStorage();
			NodeStatus = new Thread ( new RepairServiceRunnable( link_serverinfo, this.my_storage, this ) );
			NodeStatus.start();
		}else{
			my_storage = new DataStorage();
		}
		
		
		finalizer = new FinalizeRunnable( link_serverinfo );
		
		if (SysValues.EXPERIMENTAL_FINALIZER) {
			Thread fr = new Thread ( finalizer );
			fr.start();
		}
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
			SysValues.shutdown = true;				// End the program
			return DataStorage.craftResponse(0);

		}else if( j.has("status") ){
			return DataStorage.craftResponse(200);	// Recieved the status check - reply OK

		}else if( j.has("dump") ){
			this.my_storage.storage = new JSONObject();
			this.my_storage.storagecount = 0;
			return DataStorage.craftResponse(200);	// Dump the internal storage - reply OK

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
	 * @param key		The value to get the resulting location from.
	 * @param _servers The JSON array containing the list of servers.
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
	 * 		function. Method changes depending on strategy.
	 * 
	 * 		If it's internal only, it will change the external command to an internal one.		
	 * 
	 * 		For Failover or Redundancy:
	 * 		Will basically send the same command, with an appended key
	 * 		that lets the server know it is an internal system command instead of a client one.
	 * 
	 * @param message		The message to externalize.
	 * @return					The result received by the server.
	 */
	public JSONObject sendmessageremote( JSONObject message ){
		
		switch (SysValues.STORAGE_STRATEGY){
			case INTERNAL_ONLY:
				return strategy_InternalOnly ( message );
			
			case FAILOVER_ONLY:
				return strategy_FailoverOnly( message );
				
			case REDUNDANCY:
				return strategy_Redundancy( message );
				
			default:
				return strategy_Redundancy( message );
		}
	}

	/**
	 * 		Will execute the message send using a redundancy strategy.
	 * 
	 * @param message	The request to send.
	 * @return			The agreed upon response over replicas.
	 */
	private JSONObject strategy_Redundancy( JSONObject message ){
		//TODO: Threadify?
		
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
		
		long start = System.currentTimeMillis();
		Vector<JSONObject> agreed_response = new Vector<JSONObject>();
		RedundancyRunnable RR = new RedundancyRunnable( agreed_response, message, link_serverinfo, finalizer, this );
		RR.execute();
		if (agreed_response.size() == 0 ) return DataStorage.craftResponse(3);
		
		broadcast ("SERVED IN: " + Long.toString(System.currentTimeMillis() - start) );
		return agreed_response.firstElement();
	}


	/**
	 * 		This is the old way - no redundancy. Will send to the intended location,
	 * 		or failover to the next location.
	 * 
	 * @param message	The request to send.
	 * @return			The agreed upon response over replicas.
	 */
	private JSONObject strategy_FailoverOnly ( JSONObject message ){
		String url = null;
		JSONObject response = new JSONObject();
		int location = 0;


		try {
			location = NodeMaster.mapto ( message.getString("key"), link_serverinfo.servers );
			url = link_serverinfo.servers.getString(location);
			message.put("internal", true);
		} catch (JSONException e) {
			broadcast("Couldn't map to a location?");
			return DataStorage.craftResponse(5);
		}

		while ( link_serverinfo.IsServerDead( url ) ){
			location++;
			if ( location == link_serverinfo.servers.length() ) // Ensure we loop around the servers properly
				location = 0;
			try {
				url = link_serverinfo.servers.getString(location);
				continue;
			} catch (JSONException e1) {
				broadcast("Couldn't map to a location?");
				return DataStorage.craftResponse(5);
			}
		}

		while (true){
			response = __DEPRECIATED__sendMessageTo ( message, url );

			try {
				if ( response.getInt("ErrorCode") == 23 ){
					link_serverinfo.addTestURLs ( url );
					throw new JSONException("Node connect fail");
				}else{
					link_serverinfo.addToGraceList(url);
					return response;
				}
			} catch (JSONException e) {
				// Node CONNECTION was a failure, go to next node.
				while ( link_serverinfo.IsServerDead( url ) ){
					location++;
					if ( location == link_serverinfo.servers.length() ) // Ensure we loop around the servers properly
						location = 0;
					try {
						url = link_serverinfo.servers.getString(location);
						continue;
					} catch (JSONException e1) {
						broadcast("Couldn't map to a location?");
						return DataStorage.craftResponse(5);
					}
				}

			}
		}
	}// End failover only
	
	/**
	 * 	The simplest of strategies - ignore other nodes, only store data locally.
	 * 
	 * @param message	The request to send.
	 * @return			The agreed upon response over replicas.
	 */
	private JSONObject strategy_InternalOnly ( JSONObject message ){

		try {
			message.put("internal", true);
		} catch (JSONException e) {} // Hardcoded non null key, can't get here
		
		return keycommand(message);
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
