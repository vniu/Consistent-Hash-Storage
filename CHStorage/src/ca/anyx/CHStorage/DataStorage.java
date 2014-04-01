package ca.anyx.CHStorage;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *	The data stored on this node, currently as a JSONObject of key:value pairs.
 */
public class DataStorage {
	protected JSONObject storage;
	private int storagecount = 0;

	/**
	 * 	Default construction, just a new JSONObject.
	 */
	DataStorage(){
		storage = new JSONObject();
	}
	
	private static void broadcast ( String m ) {
		if ( !SysValues.DEBUG ) return;

		System.out.println( "Storage> " + m );
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
	 * 		Puts a key and its value into this system.
	 * 
	 * @param putrequest	Contains the key/value pair to put in.
	 * @return				JSONObject with the status: ErrorCode 4 if the put failed, 0 if successful.
	 */
	public synchronized JSONObject putKV( JSONObject putrequest ) {

		try {
			if ( storagecount >= SysValues.MAX_STORAGE )
				return craftResponse(2);

			if ( this.storage.has( putrequest.getString("key") ) ){
				// Already had the key, don't change storage count as we replace it
			}else{
				storagecount++;
			}

			//this.storage.put( j.getString( "key" ), j.get( "value" ) );
			putKV_actual(putrequest);
			
			broadcast("Put key: " + putrequest.getString( "key" ) );

			return craftResponse(0);

		} catch (JSONException e) {
			broadcast( "Invalid put?" );
			return craftResponse(4); 
		}
	}
	
	/**
	 * 		Intended to be override-able, the actual request to the data structure.
	 * 
	 * @param putrequest		Request containing at least the key and value.
	 * @throws JSONException	If the key/value were somehow formatted wrong.
	 */
	public synchronized void putKV_actual( JSONObject putrequest ) throws JSONException {
		this.storage.put( putrequest.getString( "key" ), putrequest.get( "value" ) );
	}
	

	/**
	 * 		Retrieves a key from the system.
	 * 
	 * @param getrequest	Contains the key to get the value for.
	 * @return				JSONObject with the status: ErrorCode 1 if key was not found, 0 if successful.
	 * 						Will also contain the value in the object if it was successful.
	 */
	public synchronized JSONObject getKV( JSONObject getrequest ) {
		
		try {
			JSONObject response = craftResponse(0);
			//Object value = this.storage.get( getrequest.getString( "key" ) );
			String value = getKV_actual( getrequest );
			response.put("value", value);

			return response;

		} catch (JSONException e) {
			broadcast( "Invalid get: not existant?" );
			return craftResponse(1);
		}
	}
	
	/**
	 * 		Intended to be override-able, the actual request to the data structure.
	 * 
	 * @param getrequest		Contains the key to get
	 * @return					The value associated
	 * @throws JSONException	No value found
	 */
	public synchronized String getKV_actual( JSONObject getrequest ) throws JSONException {
		String value = this.storage.getString( getrequest.getString( "key" ) );
		return value;
	}
	

	/**
	 * 		Will remove a key and its value from the system.
	 * 
	 * @param removerequest		A container with the key of the object to remove.
	 * @return					JSONObject with the status: ErrorCode 1 if the key was not found, 0 if successful.
	 */
	public synchronized JSONObject removeKV( JSONObject removerequest ) {

		try {
			//Object s = this.storage.remove( removerequest.getString( "key" ) );
			Object s = removeKV_actual( removerequest );
			
			if ( s == null ) throw new JSONException("null");
			broadcast("Removed key: " + removerequest.getString( "key" ) );
			storagecount--;
			return craftResponse(0);

		} catch (JSONException e) {
			broadcast( "Invalid remove: not existant?" );
			return craftResponse(1);
		}
	}
	
	/**
	 * 		Intended to be override-able, the actual request to the data structure.
	 * 
	 * @param removerequest		Contains the key to remove
	 * @return					Value associated, or null if not found
	 * @throws JSONException	If the key was not found
	 */
	public synchronized Object removeKV_actual( JSONObject removerequest ) throws JSONException {
		Object s = this.storage.remove( removerequest.getString( "key" ) );
		return s;
	}
	
	
	/**
	 * 		A helper function to quickly make a response with an error code.
	 * 	<br>   0 : Success
	 * 	<br>   1 : Non-Existent key
	 * 	<br>   2 : No space remaining to put
	 * 	<br>   3 : System overload
	 * 	<br>   4 : Internal Store failure
	 * 	<br>   5 : Unrecognized or malformed command
	 * 	<br> 200 : Status OK
	 * 
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
				response.put("ErrorInfo", "Non-Existent key.");
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
