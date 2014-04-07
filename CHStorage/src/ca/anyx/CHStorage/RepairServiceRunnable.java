package ca.anyx.CHStorage;

import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RepairServiceRunnable implements Runnable {
	public Vector<String> potentially_dead;
	private ServerListsInfo link_serverinfo;
	private DataStorage link_storage;
	private NodeMaster link_nodemaster;
	Thread tgl;
	GossipListener gl;

	RepairServiceRunnable( ServerListsInfo serverinfo, DataStorage storage, NodeMaster nodemaster ){
		potentially_dead= new Vector<String>();
		this.link_serverinfo = serverinfo;
		this.link_storage = storage;
		this.link_nodemaster = nodemaster;

		gl = new GossipListener(link_serverinfo, SysValues.STATUS_PORT);
		tgl = new Thread( gl );
		tgl.start();
	}

	private void broadcast( String m ){
		if ( !SysValues.DEBUG ) return;

		System.out.println( "RepairService> " + m );
	}

	@Override
	public void run() {
		long lastCheck = 0;
		boolean readyup = false;
		int nextCheck = 0;
		Random rng = new Random();
		Vector<String> unneccessary = null;
		int state = 0;

		while (SysValues.shutdown == false ){

			// Check every 1 second for now TODO: change!
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}

			//boolean newest = 
					this.pollRandom();

			//if ( newest == false ){ // probably a change in the dead server list
				//unneccessary = this.findUneccessaryKeys();
				//removeUneccessaryKeys( unneccessary );
				SysValues.repair_running = true;
				this.fixBrokenKeys();
			//}


			// Check the status of other nodes. Will happen randomly once within the given time-frame,
			// as to hope to avoid all nodes attacking each other at the same time.
			if ( readyup ){
				// Check to see if we've past the random number between 0 and n seconds, then we do our test.
				if ( (System.currentTimeMillis() - lastCheck ) >= 1000*nextCheck  ){
					SysValues.repair_running = true;

					//this.checkAll(); // TODO: WIP, check all servers for status
					//this.attemptRepair(); -- moved

					// Updated the dead list, now remove any unnecessary keys we're holding onto
					// Swap back and forth to avoid clashes (other servers updating their dead lists)
					if (state == 0){
						//unneccessary = this.findUneccessaryKeys();
						state = 1;
					}else{
						// those keys have been stale for 2x the repair timer - remove them
						//removeUneccessaryKeys( unneccessary );
						state = 0;
					}
					//fixBrokenKeys(); -- moved
					readyup = false;
					SysValues.repairs_ran++;
				}
			}
			SysValues.repair_running = false;
			// If we're past n seconds, ready up the next unneccesary key check
			if ( (System.currentTimeMillis() - lastCheck ) > SysValues.REPAIR_TIMER*1000){
				lastCheck = System.currentTimeMillis();
				nextCheck = rng.nextInt(SysValues.REPAIR_TIMER);
				readyup = true;
			}

			//assert (this.tgl.isAlive()) : "Gossip listener DEAD!";




			// Try and recieve from the connections that were ignored?
			//attemptToFinalize();

			/*
			potentially_dead.addAll(link_serverinfo.to_test);

			// Check the status of all the servers that are potentially dead
			Iterator<String> iter = potentially_dead.iterator();
			while ( iter.hasNext() ){
				String url = iter.next();
				if (url == null){
					broadcast("Null url? Something went wrong when adding urls.");
					iter.remove();
					continue;
				}else if ( link_serverinfo.IsServerDead( url ) ){
					iter.remove(); //already noted as dead
					continue;
				}else{
					checkIfDead ( url );
					link_serverinfo.to_test.remove(url); // remove from list to test after we checked it
					iter.remove();
					continue;
				}
			}
			 */
		}

	}

	//TODO: 
	public void checkIfDead ( String url ){

		if ( link_serverinfo.IsServerDead(url) ) return; // already confirmed dead

		/*
		if ( link_serverinfo.hasInGraceList(url) ){
			//Responded OK recently, might still be ok - wait for more confirms of dead
			long grace = link_serverinfo.getGraceListLong(url);
			if ( (System.currentTimeMillis() - grace) > SysValues.MAX_GRACE*1000 ){
				link_serverinfo.removeFromGraceList(url);
				broadcast("Removing " + url + " from grace list.");
			}else{
				//this.gracelist.put( url, System.currentTimeMillis() );
				return;
			}
		}
		 */

		SocketHelper check = new SocketHelper ( );

		if ( check.CreateConnection( url, SysValues.STATUS_PORT ) != 0){
			//link_serverinfo.dead_servers.add( url );
			link_serverinfo.ListServerDead(url);
			broadcast( url + " is DEAD! Socket fail.");
			check.CloseConnection();
			return;
		}
		check.SendMessage("{\"status\":true}");
		String finalstr = check.ReceiveMessage(SysValues.LISTEN_TIMEOUT );
		if ( finalstr == null ){
			//link_serverinfo.dead_servers.add( url );
			link_serverinfo.ListServerDead(url);
			broadcast( url + " is DEAD! Null reply.");
			return;
		}
		check.CloseConnection();


		// If we get here --  Must have just been slow?
		//link_serverinfo.addToGraceList(url);
		//broadcast("Adding " + url + " to grace list.");

	}

	/*
	//TODO:
	private void attemptRepair(){
		Iterator<String> iter = link_serverinfo.dead_servers.iterator();

		while ( iter.hasNext() ){
			String url = iter.next();

			SocketHelper check = new SocketHelper ( );

			if ( check.CreateConnection( url, SysValues.STATUS_PORT ) != 0){
				// Still likely dead
				check.CloseConnection();
				continue;
			}

			check.SendMessage("{\"status\":true}");
			String finalstr = check.ReceiveMessage(SysValues.LISTEN_TIMEOUT );
			if ( finalstr == null ){
				// Still likely dead
				continue;
			}
			check.CloseConnection();


			// If we get here -- they responded to our "ping". They are likely alive again.
			//link_serverinfo.dead_servers.remove(url);
			// TODO: give them any data that we have that they need?
			iter.remove();
			//link_serverinfo.addToGraceList(url);
			//broadcast("Adding " + url + " to grace list as per Repair.");
		}
	}
	 */


	/**
	 * 		A quick multicast to detemerine the status of all the other nodes.
	 */
	/*
	private void checkAll ( ){

		for ( int i = 0; i < link_serverinfo.servers.length(); i++ ){
			try {
				this.checkIfDead( link_serverinfo.servers.getString(i));
			} catch (JSONException e) {}
		}
	}
	 */


	private Vector<String> findUneccessaryKeys( ){
		if (link_storage == null || this.link_storage.storage == null ) return null;

		Iterator<?> iter = this.link_storage.storage.keys();
		Vector<String> to_remove = new Vector<String>();

		while (iter.hasNext() ){
			String key = (String) iter.next();
			try {
				JSONArray working_ja = this.link_storage.storage.getJSONArray(key);

				if ( working_ja.getBoolean(3) == false ){
					// not the intended location
					String intended_url = working_ja.getString(1);

					if ( this.link_serverinfo.IsServerDead(intended_url) == false ){
						// not in the dead list -- the server is back alive.
						// we do not to keep the data, no one will ever ask us for it
						//iter.remove();
						//TODO: Possibly offer this to the newly alive server?
						to_remove.add( key );
					}

				}
			} catch (JSONException e) {
				continue; // shouldn't get here
			}
		}

		return to_remove;
	}

	private void removeUneccessaryKeys( Vector<String> to_remove ){
		if (to_remove == null ) return;
		// Remove the old keys
		Iterator<String> rem_iter = to_remove.iterator();
		while ( rem_iter.hasNext() ){
			String to_rem = rem_iter.next();
			JSONObject request = new JSONObject();
			try {
				request.put("key", to_rem);
				this.link_storage.removeKV_actual(request);
				this.link_storage.storagecount--;
			} catch (JSONException e) {
				// ignore, we don't care if it doesn't exist anymore
			}
		}
	}

	private void fixBrokenKeys(){
		if (link_storage == null || this.link_storage.storage == null ) return;

		Iterator<?> iter = this.link_storage.storage.keys();

		while (iter.hasNext() ){
			String key = (String) iter.next();
			try {
				JSONArray working_ja = this.link_storage.storage.getJSONArray(key);

				// TODO: Not hardcoded to 3
				if (		this.link_serverinfo.IsServerDead(working_ja.getString(4)) 
						||  this.link_serverinfo.IsServerDead(working_ja.getString(5)) 
						||  this.link_serverinfo.IsServerDead(working_ja.getString(6)) 
						){
					JSONObject newReq = new JSONObject();
					newReq.put("put", true);
					newReq.put("key", key);
					newReq.put("value", working_ja.getString(0));

					this.link_nodemaster.keycommand(newReq); // try to update the system
				}

			} catch (JSONException e) {
				continue; // shouldn't get here
			}
		}
	}


	public boolean pollRandom(){
		//pick a friend
		Random rand = new Random();
		int i = rand.nextInt( this.link_serverinfo.servers.length() );
		String url = null;

		try {
			url =  this.link_serverinfo.servers.getString(i);
		} catch (JSONException e) {
			// should never get here
			return false;
		}

		SocketHelper sh = new SocketHelper();

		int status = sh.CreateConnection(url, SysValues.STATUS_PORT);

		if ( status != 0){ // Timed out or couldn't resolve on creation, probably dead.
			this.link_serverinfo.ListServerDead( url );
			sh.CloseConnection();
			return false;
		}
		
		// Get their data
		String theirdata = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT );
		if (theirdata == null){
			sh.SendMessage("FAIL!");
			this.link_serverinfo.ListServerDead( url );
			sh.CloseConnection();
			return false;
		}
		JSONObject their = null;
		try {
			their = new JSONObject(theirdata);
		} catch (JSONException e2) {
			//this.ListServerDead( url );
			sh.SendMessage("FAIL!");
			broadcast("Bad JSON: Unsure of status of " + url);
			sh.CloseConnection();
			return true;
		}

		int num_failures = 0;
		try {
			num_failures = this.link_serverinfo.dead_data.getJSONObject(url).getInt("failures");
		} catch (JSONException e1) {
			num_failures = 0;	// failure count wasn't initialized, start them at zero
		}
		try {
			their.put("failures", num_failures);
		} catch (JSONException e) {
			return true;	// shouldn't get here if past checks worked
		}
		this.link_serverinfo.OverwriteDataAt( url, their );

		/*
		// Send this nodes hash
		String hash = link_serverinfo.getSHA(  );
		sh.SendMessage( hash );
		 */
		/*
		// Receive JSON
		String result = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT );
		if (result == null){
			//this.ListServerDead( url );
			broadcast("NULL response: Unsure of status of " + url);
			sh.CloseConnection();
			return true;
		}
		 */
		/*
		try {
			//JSONObject j = new JSONObject( result );

			if (j.has("same")){
				//we're done
				sh.CloseConnection();
				return true;

			}else{ // was not the same
		 */
		
		// send our data.
		sh.SendMessage( link_serverinfo.dead_data.toString() );

		// Receive JSON
		String result = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT );
		if (result == null){
			//this.ListServerDead( url );
			broadcast("NULL response: Unsure of status of " + url);
			sh.CloseConnection();
			return true;
		}
		
		try{
			JSONObject j = new JSONObject( result );
			
			// rebuild our data.
			boolean newest = link_serverinfo.RebuildData( j );
			
			//broadcast(url +" is OK.");
			sh.CloseConnection();
			return newest;
			
		} catch (JSONException e) {
			// possibly malformed json from partner?
			broadcast("Bad JSON 2: Unsure of status of " + url);
			sh.CloseConnection();
			return true;
		}

		
	}
	/*
		} catch (JSONException e) {
			// possibly malformed json from partner?
			broadcast("Bad JSON 2: Unsure of status of " + url);
			sh.CloseConnection();
			return true;
		}
	 */

	//}
}
