package CHStorage;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *		Responsible for deciphering and executing commands.
 */
public class NodeMaster {
	private JSONArray servers; 
	private JSONObject storage;
	private int storagecount = 0;

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
		if ( !SysValues.debug ) return;

		System.out.println( "NodeMaster> " + m );
	}

	/**
	 * 		Synchronized access to this node's key values.
	 * 
	 * @return	This nodes key values stored, as a string, or an empty string if a problem occurred.
	 */
	public synchronized String getStorageString(){
		try {
			return this.storage.toString(2);
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
			if ( storagecount >= SysValues.maxstorage )
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
		int location = 0;
/*		String url = null;
		JSONObject response = new JSONObject();
		

		try {
			location = mapto ( j.getString("key") );
			url = this.servers.getString(location);
			j.put("internal", true);
		} catch (JSONException e) {
			broadcast("Couldn't map to a location?");
			return craftResponse(5);
		}

		response = sendMessageTo ( j, url );
*/

		Vector<JSONObject> responses = new Vector<JSONObject>();
		int[] responsecodes = new int[SysValues.redundancylevel];
		Vector<SocketHelper> shs = new Vector<SocketHelper>();
		try {

			j.put("internal", true);
			location = mapto ( j.getString("key") );

			for ( int i = 0; i < SysValues.redundancylevel; i++){
				responsecodes[i] = -1; //need this later
				if ( location == servers.length() ) 
					location = 0;

				String redundanturl;
				redundanturl = this.servers.getString(location);

				SocketHelper sh = new SocketHelper();
				sh.CreateConnection(redundanturl, SysValues.internalport);
				sh.SendMessage( j.toString() );
				shs.add( sh );
				location++;
			}
		} catch (JSONException e) {
			broadcast("Couldn't map to a location? Did not include key?");
			return craftResponse(5);
		}
		
		JSONObject agreed_response = null;
		
		Boolean have_agreement = false;
		int responsecount = 0;
		double starttime = System.currentTimeMillis();
		
		while ( !have_agreement ){
			
			if ( (System.currentTimeMillis() - starttime) > SysValues.listentimeout*1000/2){ // TODO: Possibly change this
				broadcast("TIMEOUT ON REDUNDANCY.");
				
				if ( mode(responsecodes)[1] == 0 ) { // Not good, max count is zero...
					// Looks like we couldn't store it..
					return craftResponse(4);
				}
				
				// half out of time, just return the current mode.
				return responses.elementAt(mode(responsecodes)[0]);
			}
			
			Iterator<SocketHelper> iter = shs.iterator();
			while( iter.hasNext() ){
				SocketHelper working_sh = iter.next();
				String str = working_sh.ReceiveMessage(0);
				if ( str == null ) continue;
				
				try {	
					JSONObject resp = new JSONObject( str );
				
					working_sh.SendMessage("{\"stop\":true}");
					working_sh.CloseConnection();
					iter.remove();
					responses.add(resp);
					responsecodes[responsecount] = resp.getInt("ErrorCode");
					responsecount++;
					
					if ( mode(responsecodes)[1] >= SysValues.mofnredundant ){   //Math.ceil( ((double)SysValues.redundancylevel)/2) ){
						agreed_response = resp; // If the mode just became acceptable, the latest response must be part of the mode
						have_agreement = true;
						break;
						
					}
					
				} catch (JSONException e) {
					continue;
				}
			}
		}
		// We have enough responses
		System.out.println("Using " + mode(responsecodes)[1] + " of " + responses.size() + " responses.");

		// Tell anyone left that we no longer need their input, and close the connections
		Iterator<SocketHelper> cleanup_iter = shs.iterator();
		while( cleanup_iter.hasNext() ){
			SocketHelper working_sh = cleanup_iter.next();
			working_sh.SendMessage("{\"stop\":true}");
			working_sh.CloseConnection();
			cleanup_iter.remove();
		}

		return agreed_response;
	}
	
	/**
	 * 		Calculate the mode and a location of a mode, Will ignore values of -1.
	 * 
	 * @param inarray	The input array to check.
	 * @return			Returns two ints, [0] being the Location of a mode, and [1] being the count of the mode.
	 */
	public static int[] mode(int inarray[]) {
	    //int maxValue = -1;
	    int maxCount = 0;
	    int maxLocation = -1;

	    for (int i = 0; i < inarray.length; i++) {
	        int count = 0;
	        for (int j = 0; j < inarray.length; j++) {
	            if (inarray[i] == -1){
	            // ignore -1
	            }else if (inarray[i] == inarray[j]) count++;
	        }
	        if (count > maxCount) {
	            maxCount = count;
	           // maxValue = a[i];
	            maxLocation = i;
	        }
	    }

	    return new int[]{maxLocation, maxCount};
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

		if ( sh.CreateConnection( url, SysValues.port ) != 0 ) {
			broadcast( "Response from external server failure (connection creation): " + url );
			return craftResponse(3);	// node fail to connect
		}

		sh.SendMessage( j.toString() );
		String recmessage = sh.ReceiveMessage(10); // Give the server 10 seconds? 

		if( recmessage == null ){
			broadcast( "Response from external server failure (null): " + url );
			sh.CloseConnection();
			return craftResponse(3); // overloaded?	//node fail to connect
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
	JSONObject craftResponse ( int ErrorCode ){
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
