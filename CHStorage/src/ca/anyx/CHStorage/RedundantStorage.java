package ca.anyx.CHStorage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 		Intended to change the data structure without modifying every function.
 * 
 * 		Will now have a JSONArray in place of the value,
 * 		containing the value at index 0, and the intended location at index 1.
 * 		Will also put the replica level at index 2.
 * 
 * 		Should only be used with Redundancy enabled (or else intended location will not exist).
 * 		Will simply put "unknown" if intended location does not exist.
 * 
 */
public class RedundantStorage extends DataStorage {
	
	RedundantStorage(){
		super();
	}
	
	@Override
	public synchronized String getKV_actual( JSONObject getrequest ) throws JSONException {
		//String value = this.storage.getString( getrequest.getString( "key" ) );
		//return value;
		JSONArray ja = this.storage.getJSONArray( getrequest.getString( "key" ) );
		String value = ja.getString(0);
		return value;
	}
	
	@Override
	public synchronized void putKV_actual( JSONObject putrequest ) throws JSONException {
		//this.storage.put( putrequest.getString( "key" ), putrequest.get( "value" ) );
		JSONArray ja = new JSONArray();
		ja.put( 0, putrequest.get( "value" ) );
		
		if (putrequest.has( "intended_location" )){
			ja.put( 1, putrequest.get( "intended_location" ) );
		}else{
			ja.put( 1, "unknown" );
		}
		
		if (putrequest.has( "replica_num" )){
			ja.put( 2, putrequest.getInt( "replica_num" ) );
		}else{
			ja.put( 2, -1 );
		}
			
		this.storage.put( putrequest.getString( "key" ), ja );
	}
}
