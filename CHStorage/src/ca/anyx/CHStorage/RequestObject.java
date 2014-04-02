package ca.anyx.CHStorage;

import java.util.Vector;

import org.json.JSONObject;

/**
 * 	Intended as a quick helper object to pass off a bunch of required data over to a finalizing thread.
 */
public class RequestObject {
	Vector<SocketHelper> shs;
	JSONObject message;
	Vector<String> sentLocations;
	long startTime;
	
	/**
	 *		Default construction...
	 * @param _shs			 The sockethelpers in the object
	 * @param _message		 The message intended for these connections
	 * @param _sentLocations Any locations that have already been sent to with this message
	 */
	RequestObject(Vector<SocketHelper> _shs, JSONObject _message, Vector<String> _sentLocations ){
		this.shs = _shs;
		this.message = _message;
		this.sentLocations = _sentLocations;		
		startTime = System.currentTimeMillis();
	}
}
