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
	
	/*
	 * TODO:	Lots of work still left to be done...
	 * TODO:	Need to make get/put/remove all external requests instead of local.
	 */

	/**
	 * 		Constructor - a new NodeMaster object based on the server text file.
	 * 
	 * @param serverlist		The list of servers that will be running the program.
	 * @throws JSONException	if something was wrong with the server list format.
	 */
	public NodeMaster( JSONObject serverlist ) throws JSONException {
		servers = serverlist.getJSONArray("servers");
		storage = new JSONObject();
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
		
		if ( j.has("put") ){
			return this.putKV( j );
			
		}else if ( j.has("get") ){
			return this.getKV( j );
			
		}else if ( j.has("remove") ){
			return this.removeKV( j );
		}
		
		broadcast( "No command in the object?");
		JSONObject response = null;
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
			this.storage.remove( j.getString( "key" ) );
			response.put("ErrorCode", 0);
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
	 * 		This method successfully returns a location between 0 and servers.length()
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
	

}
