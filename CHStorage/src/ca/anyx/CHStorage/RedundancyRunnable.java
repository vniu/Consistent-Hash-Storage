package ca.anyx.CHStorage;

import java.util.Iterator;
import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;

public class RedundancyRunnable { //implements Runnable {
	private Vector<JSONObject> responses;	// A vector to hold redundant responses
	private int[] responsecodes;		// An array of all current ErrorCodes from the responses: Should be populated with -1 if no response yet.
	private Vector<SocketHelper> shs;	// An array of socket connections (using the helper)
	private JSONObject message;
	private NodeMaster nodemaster;

	public Vector<JSONObject> agreed_response;


	/**
	 * 	Setup of the thread: needs message to pass along, and the servers in the system.
	 * 	Will put redundantly, and also update the list of "dead" servers.
	 * 
	 * 		TODO: Assumes nodes never come back online.
	 * 
	 * @param _agreed_response	The response will be appended to this vector.
	 * @param _message	The message to send to the redundant servers
	 * @param nm		The nodemaster who created the thread ( To gather servers, or update dead servers )
	 */
	RedundancyRunnable( Vector<JSONObject> _agreed_response, JSONObject _message, NodeMaster nm ){
		responses = new Vector<JSONObject>();

		responsecodes = new int[SysValues.REDUNDANCY_LEVEL];
		shs = new Vector<SocketHelper>();

		this.agreed_response = _agreed_response;
		this.message = _message;
		this.nodemaster = nm;

	}

	private void broadcast( String m ){
		if ( !SysValues.DEBUG ) return;
		// Add in thread ID so we can see who's who
		String ID = Long.toString( Thread.currentThread().getId() ); 
		System.out.println( "RedundancyThread(" + ID + ")> " + m );
	}
	
	

	//@Override
	//public void run() {
	public void execute(){
		// This is for redundancy in values

		try {
			// Keep track of locations we send to -- if we try to send to the same location, we have lost a level of redundancy.
			Vector<String> sentLocations = new Vector<String>();
			
			this.message.put("internal", true); 	// Notify of internal connection or else we get recursion

			// Initialize the connection across n redundant locations
			for ( int i = 0; i < SysValues.REDUNDANCY_LEVEL; i++){

				responsecodes[i] = -1; // As per convention, initialize response code to -1

				int location = NodeMaster.mapto ( this.message.getString("key"), nodemaster.servers );	// Get the initial location via Consistent Hashing
				
				//increment location based off which redundancy we are
				location = incrementLocBy( location, i );
				String redundanturl = nodemaster.servers.getString(location); // Get the current url
				//Let it be known the intended location of this info
				message.put("intended_location", redundanturl);
				
				// Check against the dead servers for the current url -> if it is dead, skip it.
				int increments = 0;
				while ( nodemaster.gracelist.dead_servers.contains( redundanturl ) ){
					broadcast("Skipping dead URL: " + redundanturl );
					
					location = incrementLocBy( location, SysValues.REDUNDANCY_LEVEL ); // increment to the next subset of size redundancylevel 
					increments += SysValues.REDUNDANCY_LEVEL;
					if ( increments >= nodemaster.servers.length() ){
						// looped ALL the way around the nodes? uh oh
						broadcast("SEVERE: Looped around nodes: losing a level of redundancy!");
						// just put the intended location in
						location = NodeMaster.mapto ( this.message.getString("key"), nodemaster.servers );
						location = incrementLocBy( location, i );
						redundanturl = nodemaster.servers.getString(location);
						break;
					}
					redundanturl = nodemaster.servers.getString(location);
				}
				
				// Already sent to the server on a previous iteration
				if ( sentLocations.contains(redundanturl) ){
					// In theory this should only happen if the server list is small -
					// or, the amount of alive servers is less than the redundancy level,
					// at which case it will try to be redundant multiple times per server.
					broadcast("SEVERE: Attempted to send to the same location more than once: losing a level of redundancy!");
					continue;
				}

				SocketHelper sh = new SocketHelper();			
				int status = sh.CreateConnection(redundanturl, SysValues.INTERNAL_PORT); // Make an internal connection to the url
				if (status != 0){
					// create connection fail!
					broadcast( sh.myURL + "is DEAD! Connection Create fail.");
					nodemaster.gracelist.dead_servers.add( sh.myURL );
					sh.CloseConnection();
					i--;
					continue; // Try this iteration again with new dead server info
				}

				sh.SendMessage( this.message.toString() );	// Send the url the command message
				shs.add( sh );	// Add to the vector of connections for when we check for responses
				sentLocations.add(redundanturl);
			}

		} catch (JSONException e) {		// If we get here then the response is bad
			broadcast("Couldn't map to a location? Did not include key? Redundancy Req.");
			agreed_response.add( DataStorage.craftResponse(5) );
			return;
		}

		// Initial states of null/zero 
		//JSONObject agreed_response = null;
		Boolean have_agreement = false;
		int responsecount = 0;

		double starttime = System.currentTimeMillis();

		while ( !have_agreement ){

			// If we are running out of time, we should just return the best response that we have
			if ( (System.currentTimeMillis() - starttime) > SysValues.REDUNDANCY_TIMEOUT*1000){
				broadcast("TIMEOUT ON REDUNDANCY.");

				if ( mode(responsecodes)[1] == 0 ) { // Not good, max count is zero...
					// Looks like we couldn't store it.. //TODO:
					agreed_response.add( DataStorage.craftResponse(4) );
					finalizeSockets( shs );
					return;
				}
				//just return the current mode.
				agreed_response.add( responses.elementAt(mode(responsecodes)[0]) );
				finalizeSockets( shs );
				return;
			}

			// Iterate over all the socket connections in the vector
			Iterator<SocketHelper> iter = shs.iterator();
			while( iter.hasNext() ){
				SocketHelper working_sh = iter.next();
				String str = working_sh.ReceiveMessage(0); // Check immediately, without waiting, for a message, and if its null just move on
				if ( str == null ) continue;

				try {	
					JSONObject resp = new JSONObject( str );	// Have a response from this connection

					working_sh.SendMessage("{\"stop\":true}");
					working_sh.CloseConnection();
					iter.remove();				// Close and remove the connection from the vector, don't need it anymore
					
					responses.add(resp);
					responsecodes[responsecount] = resp.getInt("ErrorCode");
					responsecount++;		// Add to our information the response and code

					// Add server that responded to the grace list
					nodemaster.gracelist.addToGraceList( working_sh.myURL );
					
					// Check for a valid agreement -- m values (a mode) of the n redundant servers must agree

					if ( mode(responsecodes)[1] >= SysValues.MOFN_REDUNDANT ){   //Math.ceil( ((double)SysValues.redundancylevel)/2) ){  <-- Old way, where m is defined as 'half'

						agreed_response.add( resp ); // If the mode just became acceptable, the latest response must be part of the mode
						have_agreement = true;
						break;
					}

				} catch (JSONException e) { // Error with the response? Just move on.
					continue;
				}
			}
		}

		// We have enough responses
		broadcast("Using " + mode(responsecodes)[1] + " of " + responses.size() + " responses.");
		
		finalizeSockets( shs );
		return;
	}
	
	/**
	 * 		A quick helper to increment a location on the server list by an amount,
	 * 		wrapping around to the beginning if necessary.
	 * 		
	 * 		Authors note: Could probably just do location + amount then modulus by the server length...
	 * 
	 * @param loc		The location to update
	 * @param amount	How many times to increment the location by
	 * @return			The updated location
	 */
	public int incrementLocBy( int loc, int amount ){
		for (int i = 0; i < amount; i++){
			loc++;
			if ( loc == nodemaster.servers.length() ) // Ensure we loop around the servers properly
				loc = 0;
		}
		return loc;
	}

	/** DEPRECIATED
	 * 		Tell anyone left (has not been removed from the vector) 
	 * 		that we no longer need their input, and close the connections.
	 * 
	 * @param shs	Sockets to clean.
	 */
	void DEPRECIATED_cleanSockets( Vector<SocketHelper> shs ){
		//nodemaster.gracelist.addTestURLs(shs);

		Iterator<SocketHelper> cleanup_iter = shs.iterator();
		while( cleanup_iter.hasNext() ){
			SocketHelper working_sh = cleanup_iter.next();
			working_sh.SendMessage("{\"stop\":true}");
			working_sh.CloseConnection();
			cleanup_iter.remove();
		}
	}
	
	/**
	 * 	WORK IN PROGRESS
	 * 		Attempts to finalize connects that are open after an agreed response was reached.
	 * 
	 * @param shs	The open socket helpers.
	 */
	void finalizeSockets( Vector<SocketHelper> shs ){
		Iterator<SocketHelper> iter = shs.iterator();
		
		while ( iter.hasNext() ){
			SocketHelper working_sh = iter.next();
			
			if ( nodemaster.gracelist.dead_servers.contains( working_sh.myURL ) ) { // Already marked as dead
				
				// TODO: Need to replicate the data...
				
			}else if ( nodemaster.gracelist.to_test.contains( working_sh.myURL ) ){ // Already marked for testing (could be dead)
				
				// TODO: Need to replicate the data...
				
			}else{
				// Do a full wait on the connection
				String response = working_sh.ReceiveMessage(SysValues.LISTEN_TIMEOUT);
				if ( response == null ){
					//nodemaster.gracelist.addTestURLs ( working_sh.myURL );
					nodemaster.gracelist.dead_servers.add( working_sh.myURL );
					// TODO: Need to replicate the data...
				}
				
				// if response wasn't null, its still alive
			}
			
			working_sh.SendMessage("{\"stop\":true}");
			working_sh.CloseConnection();
			iter.remove();
			
		} // End while
	}

	/**
	 * 		Calculate the mode and a location of a mode. Will ignore values of -1 as per convention.
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

}
