package CHStorage;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *		Responsible for deciphering and executing commands.
 */
public class NodeMaster {
	private JSONArray servers; 
	private JSONObject storage;
	private int port;

	/*
	 * TODO:	Lots of work still left to be done...
	 * TODO:	Alternative way to request key / value
	 * TODO: 	Limit storage space allowed to 64MB
	 * TODO:	Failure handling: backup data on other nodes, redirect to new node on failure, manage backup values
	 */

	/**
	 * 		Constructor - a new NodeMaster object based on the server text file.
	 * 
	 * @param serverlist		The list of servers that will be running the program.
	 * @throws JSONException	if something was wrong with the server list format.
	 */
	public NodeMaster( JSONObject serverlist, int serverport ) throws JSONException {
		servers = serverlist.getJSONArray("servers");
		storage = new JSONObject();
		this.port = serverport;
	}

	private static void broadcast ( String m ) {
		System.out.println( "NodeMaster> " + m );
	}

	/**
	 * 		Synchronized access to this node's key values.
	 * 
	 * @return	This nodes key values stored.
	 */
	public synchronized JSONObject getStorage(){
		return this.storage;
	}

	/**
	 * 		A director to execute a command.
	 * 
	 * @param j		The command to execute.
	 * @return		The result of the execution, including "ErrorCode". ErrorCode 5 is for a command not found,
	 * 				see sub-functions for rest of error codes.
	 */
	public JSONObject keycommand ( JSONObject j ){
		Boolean isinternal = false;
		JSONObject response = null;

		if ( j == null ){
			try {
				response = new JSONObject().put("ErrorCode", 5);
			} catch (JSONException e1) {}
			return response;
		}
		
		try{
			if ( j.has("internal") == true || j.getBoolean("internal") == true )
				isinternal = true;
		} catch (JSONException e) {
			isinternal = false;
		}

		if ( isinternal == false ){
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
		try {
			response = new JSONObject().put("ErrorCode", 5);
		} catch (JSONException e1) {}
		return response;
	}

	/**
	 * 		Puts a key and its value into the system.
	 * 
	 * @param j		The key/value pair to put in.
	 * @return		JSONObject with the status: ErrorCode 4 if the put failed, 0 if successful.
	 */
	public synchronized JSONObject putKV( JSONObject j ) {
		JSONObject response = new JSONObject();

		try {
			this.storage.put( j.getString( "key" ), j.getString( "value" ) );
			response.put("ErrorCode", 0);
			broadcast("Put key: " + j.getString( "key" ) );
		} catch (JSONException e) {
			try {
				broadcast( "invalid put?" );
				response.put("ErrorCode", 4);
			} catch (JSONException e1) {}
		}

		return response;
	}

	/**
	 * 		Retrieves a key from the system.
	 * 
	 * @param j		The key to get the value for.
	 * @return		JSONObject with the status: ErrorCode 1 if key was not found, 0 if successful.
	 * 				Will also contain the value in the object if it was successful.
	 */
	public synchronized JSONObject getKV( JSONObject j ) {
		String value = null;
		JSONObject response = new JSONObject();

		try {
			value = this.storage.getString( j.getString( "key" ) );
			response.put("ErrorCode", 0);
			response.put("value", value);
			broadcast("Retrieved key: " + j.getString( "key" ) );
		} catch (JSONException e) {
			try {
				broadcast( "invalid get: not existant?" );
				response.put("ErrorCode", 1);
			} catch (JSONException e1) {}
		}

		return response;
	}

	/**
	 * 		Will remove a key and its value from the system.
	 * 
	 * @param j		A container with the key of the object to remove.
	 * @return		JSONObject with the status: ErrorCode 1 if the key was not found, 0 if successful.
	 */
	public synchronized JSONObject removeKV( JSONObject j ) {
		JSONObject response = new JSONObject();

		try {
			String s = (String)this.storage.remove( j.getString( "key" ) );
			if ( s == null ) throw new JSONException("null");
			response.put("ErrorCode", 0);
			broadcast("Removed key: " + j.getString( "key" ) );
		} catch (JSONException e) {
			try {
				broadcast( "invalid remove: not existant?" );
				response.put("ErrorCode", 1);
			} catch (JSONException e1) {}
		}

		return response;
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
	public int mapto( String key ){
		byte[] hash = null;

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			hash = digest.digest( key.getBytes("ISO-8859-1") ); // Keep all data with a 1 to 1 string encoding
		} catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
			e.printStackTrace(); // Hardcoded algorithms/encoding, won't get here.
		}
		ByteBuffer bb = ByteBuffer.wrap(hash);
		int loc = bb.getInt();
		loc = Math.abs( loc % this.servers.length() );
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
		SocketHelper sh = new SocketHelper();
		String url = null;
		JSONObject response = new JSONObject();

		try {
			int location = mapto ( j.getString("key") );
			url = this.servers.getString(location);
			j.put("internal", true);
		} catch (JSONException e) {
			try { // Couldn't map to a location, most likely malformed input.
				response = new JSONObject().put("ErrorCode", 5);
			} catch (JSONException e1) {}
			return response;
		}

		sh.CreateConnection( url, port );
		sh.SendMessage( j.toString() );
		String recmessage = sh.ReceiveMessage(10); // Give the server 10 seconds? TODO: Maybe not hardcoded?

		if( recmessage == null ){
			try {
				broadcast( "Response from external server failure (null): " + url );
				response.put("ErrorCode", 3); // overloaded?
				return response;
			} catch (JSONException e1) {}
		}

		try {
			response = new JSONObject(recmessage);
		} catch (JSONException e) {
			try {
				broadcast( "Response from external server, internal failure: " + url );
				response.put("ErrorCode", 4); // BAD or malformed response from server: shouldn't get here.
			} catch (JSONException e1) {}
		}
		
		sh.SendMessage("{\"stop\":\"true\"}"); //derp?
		sh.CloseConnection();

		return response;
	}


}
