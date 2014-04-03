package ca.anyx.CHStorage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Vector;

import org.json.JSONException;
import org.json.JSONObject;

public class RedundancyRunnable { //implements Runnable {
	private Vector<JSONObject> responses= new Vector<JSONObject>();	// A vector to hold redundant responses
	private int[] responsecodes;		// An array of all current ErrorCodes from the responses: Should be populated with -1 if no response yet.

	private JSONObject message;
	private ServerListsInfo link_serverinfo;	
	private FinalizeRunnable link_finalizer;
	private NodeMaster NM;

	// Keep track of locations we send to -- if we try to send to the same location with this instance, we have lost a level of redundancy.
	Vector<String> sentLocations = new Vector<String>();

	public Vector<JSONObject> agreed_response;


	/**
	 * 	Setup of the thread: needs message to pass along, and the servers in the system.
	 * 	Will put redundantly, and also update the list of "dead" servers.
	 * 
	 * 		TODO: Assumes nodes never come back online.
	 * 
	 * @param _agreed_response	The response will be appended to this vector.
	 * @param _message			The message to send to the redundant servers
	 * @param serverinfo	Server info ( To gather servers, or update dead servers )
	 * @param finalizer 	The finalizer thread
	 * @param _nodeMaster 	Calling nodemaster
	 */
	RedundancyRunnable( Vector<JSONObject> _agreed_response, JSONObject _message, ServerListsInfo serverinfo, FinalizeRunnable finalizer, NodeMaster _nodeMaster ){

		responsecodes = new int[SysValues.REDUNDANCY_LEVEL];
		for ( int i = 0; i < SysValues.REDUNDANCY_LEVEL; i++){
			responsecodes[i] = -1; // As per convention, initialize response code to -1
		}
		this.agreed_response = _agreed_response;
		this.message = _message;
		this.link_serverinfo = serverinfo;
		this.link_finalizer = finalizer;
		this.NM = _nodeMaster;

		try {
			this.message.put("internal", true);// Notify of internal connection or else we get recursion
		} catch (JSONException e) {} // hardcoded, won't get here 	

	}

	private static void broadcast( String m ){
		if ( !SysValues.DEBUG ) return;
		// Add in thread ID so we can see who's who
		String ID = Long.toString( Thread.currentThread().getId() ); 
		System.out.println( "RedundancyThread(" + ID + ")> " + m );
	}



	//@Override
	//public void run() {
	public void execute(){
		// This is for redundancy in values

		// Initial requests - no offset, replicas = jump factor
		Vector<SocketHelper> open_connections = this.openRequests(0, SysValues.REDUNDANCY_LEVEL, SysValues.REDUNDANCY_LEVEL);

		if (open_connections == null){// If we get here then the response is bad -- this would be an internal failure of the program
			agreed_response.add( DataStorage.craftResponse(4) );
			return;
		}	
		broadcast("Open connections: " + Integer.toString(open_connections.size()));
		// Get the agreed response -- will put it into this objects agreed_response collection
		getAgreed ( open_connections );
		
		broadcast("Remaining open connections: " + Integer.toString(open_connections.size()));

		// Any remaining connections (maybe dead, maybe slow), should be finalized. 
		if (SysValues.EXPERIMENTAL_FINALIZER){
			RequestObject ro = new RequestObject (open_connections, this.message, this.sentLocations );
			link_finalizer.add_to_finalize.add(ro);
		}else{
			if (this.message.has("get")){		// We don't need to ensure replication of get requests
				cleanSockets(open_connections);
			}else
				finalizeSockets(open_connections); //TODO: Maybe make just this part a separate thread?
		}
		
		return;
	}

	/**
	 * 		Will consume out of the open connections until an agreed response is achieved.
	 * 		The vector may still have elements in it! - They should be finalized.
	 * 
	 * @param open_connections	The connections to read from.
	 */
	void getAgreed ( Vector<SocketHelper> open_connections ){
		
		String my_url = null;
		try {
			InetAddress iAddress = InetAddress.getLocalHost();
			my_url = iAddress.getHostName();
		} catch (UnknownHostException e) {}
		
		// Initial states
		Boolean have_agreement = false;
		int responsecount = 0;
		double starttime = System.currentTimeMillis();

		while ( !have_agreement ){

			// If we are running out of time, we should just return the best response that we have
			if ( (System.currentTimeMillis() - starttime) > SysValues.REDUNDANCY_TIMEOUT*1000){
				broadcast("TIMEOUT ON REDUNDANCY.");

				if ( mode(responsecodes)[1] == 0 ) { // Not good, max count is zero...
					// Looks like we couldn't store it.. //TODO: ??????
					agreed_response.add( DataStorage.craftResponse(4) );
					//finalizeSockets( shs ); // Now returning with non-finished sockets still in vector
					return;
				}
				//just return the current mode.
				agreed_response.add( responses.elementAt(mode(responsecodes)[0]) );
				//finalizeSockets( shs ); // Now returning with non-finished sockets still in vector
				return;
			}

			// Iterate over all the socket connections in the vector
			Iterator<SocketHelper> iter = open_connections.iterator();
			while( iter.hasNext() ){
				SocketHelper working_sh = iter.next();
				
				if (my_url != null && my_url == working_sh.myURL){
					iter.remove();
					JSONObject resp = NM.keycommand(message);
					responses.add(resp);
					try {
						responsecodes[responsecount] = resp.getInt("ErrorCode");
					} catch (JSONException e) {} // Internal system -- won't get here
					responsecount++;		// Add to our information the response and code
					
					if (SysValues.EXPERIMENTAL_ANY_SUCCESS){
						try {
							if ( resp.getInt("ErrorCode") == 0 ){
								agreed_response.add( resp ); // If the mode just became acceptable, the latest response must be part of the mode
								have_agreement = true;
								break;
							}
						} catch (JSONException e) {}
					}
					
					if ( mode(responsecodes)[1] >= SysValues.MOFN_REDUNDANT ){   //Math.ceil( ((double)SysValues.redundancylevel)/2) ){  <-- Old way, where m is defined as 'half'

						agreed_response.add( resp ); // If the mode just became acceptable, the latest response must be part of the mode
						have_agreement = true;
						break;
					}
					continue;
				}
				
				
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
					link_serverinfo.addToGraceList( working_sh.myURL );

					// Check for a valid agreement -- m values (a mode) of the n redundant servers must agree
					
					if (SysValues.EXPERIMENTAL_ANY_SUCCESS){
						if ( resp.getInt("ErrorCode") == 0 ){
							agreed_response.add( resp ); // If the mode just became acceptable, the latest response must be part of the mode
							have_agreement = true;
							break;
						}
					}
					
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

		//finalizeSockets( shs ); // Now returning with non-finished sockets still in vector
		return;
	}

	
	Vector<SocketHelper> openRequests ( int offsetFirst, int replicas, int JumpFactor){
		return openRequests(offsetFirst, replicas, JumpFactor, this.message, this.link_serverinfo, this.sentLocations);
	}
	/**
	 * 		Creates connections to the first replicas, and sends the message.
	 * 		If a server is realized to be dead in this function, it is incremented by the JumpFactor,
	 * 		then attempted again.
	 * 
	 * 		In most cases, the JumpFactor should be precisely the same as the replicas.
	 * 
	 * @param offsetFirst	Where to start the first replica creation. Should only be used for one specific target destination.
	 * @param replicas		Amount of replicas to create in this instance
	 * @param JumpFactor	How much to skip over on failure (should be = replicas)
	 * @param message		The message being sent.
	 * @param link_serverinfo	The link to the server information
	 * @param sentLocations		Where we have already sent with this message.
	 * @return				The created open connections, one for each replica being created
	 */
	static Vector<SocketHelper> openRequests ( int offsetFirst, int replicas, int JumpFactor, JSONObject message, ServerListsInfo link_serverinfo, Vector<String> sentLocations ){
		Vector<SocketHelper> shs = new Vector<SocketHelper>();	// An array of socket connections (using the helper)
		
		String my_url = null;
		try {
			InetAddress iAddress = InetAddress.getLocalHost();
			my_url = iAddress.getHostName();
		} catch (UnknownHostException e) {} // Ignore, just misses a bit of optimization

		try {
			Vector<ReplicaObject> to_launch = new Vector<ReplicaObject>();
			// Initialize the connection across all redundant locations
			for ( int i = (0+offsetFirst); i < (replicas+offsetFirst); i++){

				int location = NodeMaster.mapto ( message.getString("key"), link_serverinfo.servers );	// Get the initial location via Consistent Hashing

				//increment location based off which redundancy we are
				location = incrementLocBy( location, i, link_serverinfo.servers.length() );

				// Get the current url
				String redundanturl = link_serverinfo.servers.getString(location); 
				
				//Let it be known the intended location of this info
				ReplicaObject this_launch = new ReplicaObject(redundanturl, i, true);
				//message.put("intended_location", redundanturl);
				//message.put("replica_num", i);
				//message.put("is_intended", true);

				// Check against the dead servers for the current url -> if it is dead, skip it.
				int increments = 0;
				while ( link_serverinfo.dead_servers.contains( redundanturl ) ){
					broadcast("Skipping dead URL: " + redundanturl );
					this_launch.is_intended = false;
					//message.put("is_intended", false);
					
					location = incrementLocBy( location, JumpFactor, link_serverinfo.servers.length() ); // increment to the next subset of size redundancylevel 
					increments += JumpFactor;
					if ( increments >= link_serverinfo.servers.length() ){
						// looped ALL the way around the nodes? uh oh
						broadcast("WARN: Looped around nodes: losing a level of redundancy!");
						// just put the intended location in
						location = NodeMaster.mapto ( message.getString("key"), link_serverinfo.servers );
						location = incrementLocBy( location, i, link_serverinfo.servers.length() );
						redundanturl = link_serverinfo.servers.getString(location);
						break;
					}
					redundanturl = link_serverinfo.servers.getString(location);
				}

				// Already sent to the server on a previous iteration
				if ( sentLocations.contains(redundanturl) ){
					// In theory this should only happen if the server list is small -
					// or, the amount of alive servers is less than the redundancy level,
					// at which case it will try to be redundant multiple times per server.
					broadcast("WARN: Attempted to send to the same location more than once: losing a level of redundancy!");
					continue;
				}
				
				message.put( ("rep_loc_"+Integer.toString(i)), redundanturl);
				this_launch.actual_location = redundanturl;
				
				SocketHelper sh = new SocketHelper();	
				sh.replica_number = i;// ro.replica_num;
				
				if (my_url != null && my_url == redundanturl){
					// Don't bother opening the socket
					sh.myURL = my_url;
					shs.add( sh );
					sentLocations.add(redundanturl);
					continue; 
				}
				
				int status = sh.CreateConnection(redundanturl, SysValues.INTERNAL_PORT); // Make an internal connection to the url
				if (status != 0){
					// create connection fail!
					broadcast( sh.myURL + "is DEAD! Connection Create fail.");
					link_serverinfo.dead_servers.add( sh.myURL );
					sh.CloseConnection();
					i--;
					continue; // Try this iteration again with new dead server info
				}
				sentLocations.add(redundanturl);
				
				//Connection okay, add to list of targets to send to
				this_launch.sh = sh;
				to_launch.add(this_launch);
				
			}
			
			//send to all targets
			Iterator<ReplicaObject> temp = to_launch.iterator();
			while (temp.hasNext() ){
				ReplicaObject ro = temp.next();
				
				message.put("intended_location", ro.intended_location);
				message.put("replica_num", ro.replica_num);
				message.put("is_intended", ro.is_intended);
				
				ro.sh.SendMessage( message.toString() );	// Send the url the command message
				shs.add( ro.sh );	// Add to the vector of connections for when we check for responses
			}
					

		} catch (JSONException e) {		// If we get here then the response is bad -- this would be an internal failure of the program
			broadcast("Couldn't map to a location? Did not include key? Redundancy Req.");
			return null;
		}
		return shs;
	}
	
	/**
	 *  Simple 'struct' to store some data
	 */
	static class ReplicaObject{
		String intended_location;
		int replica_num;
		boolean is_intended;
		SocketHelper sh;
		
		String actual_location;
		
		ReplicaObject( String _intended_location, int _replica_num, boolean _is_intended){
			this.intended_location = _intended_location;
			this.replica_num = _replica_num;
			this.is_intended = _is_intended;
		}
	}


	/**
	 * 		A quick helper to increment a location on the server list by an amount,
	 * 		wrapping around to the beginning if necessary.
	 * 		
	 * 		Authors note: Could probably just do location + amount then modulus by the server length...
	 * 
	 * @param loc		The location to update
	 * @param amount	How many times to increment the location by
	 * @param len		The server list length
	 * @return			The updated location
	 */
	static public int incrementLocBy( int loc, int amount, int len ){
		for (int i = 0; i < amount; i++){
			loc++;
			if ( loc == len ) // Ensure we loop around the servers properly
				loc = 0;
		}
		return loc;
	}

	
	
	void finalizeSockets(Vector<SocketHelper> shs){
		finalizeSockets( this.link_serverinfo, shs, this.message, this.sentLocations);
	}
	/**
	 * 		Attempts to finalize connects that are open ( useful after an agreed response was reached )
	 * 		Is responsible for propagating if there was a failed message.
	 * 
	 * 		Will discard any message that gets recieved, or adds the url to the dead list if it fails.
	 * 
	 * @param link_serverinfo	The server info, for the dead server list.
	 * @param shs				The open socket helpers.
	 * @param message			The message being sent.
	 * @param sentLocations		Where we have already sent with this message.
	 * 
	 */
	static void finalizeSockets( ServerListsInfo link_serverinfo, Vector<SocketHelper> shs, JSONObject message, Vector<String> sentLocations ){
		
		if (shs.size() == 0 ) return; // Nothing to do!
		
		Vector<Integer> failed_replicas = new Vector<Integer>();
		
		long startTime = System.currentTimeMillis();
		
		// Give all the sockets the full timeout value to respond
		while ( (System.currentTimeMillis() - startTime) < SysValues.LISTEN_TIMEOUT*1000){
			
			if (shs.size() == 0 ) return; // All responded -- Nothing to do!
			
			
			Iterator<SocketHelper> iter = shs.iterator();
			while ( iter.hasNext() ){
				SocketHelper working_sh = iter.next();

				if ( link_serverinfo.dead_servers.contains( working_sh.myURL ) ) { 
					// Already marked as dead --
					// This could happen if another thread found the url to be dead.
					failed_replicas.add( working_sh.replica_number );
					
					working_sh.SendMessage("{\"stop\":true}");
					working_sh.CloseConnection();
					iter.remove();	// Close and remove the SocketHelper

				}else{ 
					// if ( nodemaster.gracelist.to_test.contains( working_sh.myURL ) ){ 
					// Already marked for testing (could be dead)
					// --Actually, we will test here

					// Do a read check on the connection
					String response = working_sh.ReceiveMessage(0);
					if ( response == null ){
						continue; // No response -- continue until we time out
					}

					// if response wasn't null, its still alive
					working_sh.SendMessage("{\"stop\":true}");
					working_sh.CloseConnection();
					iter.remove();	// Close and remove the SocketHelper
				}
			} // End of iteration
			
		} // End of while, time ran out
		
		// Out of time -- add all null responses to the dead list.
		Iterator<SocketHelper> iter = shs.iterator();
		while ( iter.hasNext() ){
			SocketHelper working_sh = iter.next();

			String response = working_sh.ReceiveMessage(0);
			if ( response == null ){
				link_serverinfo.dead_servers.add( working_sh.myURL );

				failed_replicas.add( working_sh.replica_number );
			}
			working_sh.SendMessage("{\"stop\":true}");
			working_sh.CloseConnection();
			iter.remove();

		} // End of iteration
		
		
		if ( failed_replicas.size() == 0 ){
			// We're done, no more failed replicas!
			return;
		}
		
		// At this point, all the connections were finalized. 
		// We now need to replicate anything that was discovered dead.
		// Since the dead list has been updated, we can re-call the original function - it will skip dead servers.
		Vector<SocketHelper> combined_new = new Vector<SocketHelper>();
		
		Iterator<Integer> make_new = failed_replicas.iterator();
		while ( make_new.hasNext() ){
			int replica_number = make_new.next();
			
			// Since we have a replica amount of 1, the add all call will actually only add one open SocketHelper to the combined.
			// Note the jump factor must be the original jump factor -- in this case, the redundancy level.
			combined_new.addAll( openRequests(replica_number, 1, SysValues.REDUNDANCY_LEVEL, message, link_serverinfo, sentLocations) );
		}
		
		finalizeSockets( link_serverinfo, combined_new, message, sentLocations ); // Recursion time, I hope this works.
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
	
	/**
	 * 		Tell anyone left (has not been removed from the vector) 
	 * 		that we no longer need their input, and close the connections.
	 * 
	 * @param shs	Sockets to clean.
	 */
	void cleanSockets( Vector<SocketHelper> shs ){
		//nodemaster.gracelist.addTestURLs(shs);

		Iterator<SocketHelper> cleanup_iter = shs.iterator();
		while( cleanup_iter.hasNext() ){
			SocketHelper working_sh = cleanup_iter.next();
			working_sh.SendMessage("{\"stop\":true}");
			working_sh.CloseConnection();
			cleanup_iter.remove();
		}
	}

}
