package ca.anyx.CHStorage;

import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import org.json.JSONException;

public class RepairServiceRunnable implements Runnable {
	public Vector<String> potentially_dead;
	private ServerListsInfo link_serverinfo;

	RepairServiceRunnable( ServerListsInfo serverinfo ){
		potentially_dead= new Vector<String>();
		this.link_serverinfo = serverinfo;
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
		
		while (SysValues.shutdown == false ){
			
			// Check every second for now TODO: change!
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
			
			
			// Check the status of other nodes. Will happen randomly once within the given time-frame,
			// as to hope to avoid all nodes attacking each other at the same time.
			if ( readyup ){
				// Check to see if we've past the random number between 0 and n seconds, then we do our test.
				if ( (System.currentTimeMillis() - lastCheck ) >= 1000*nextCheck  ){
					this.checkAll(); // TODO: WIP, check all servers for status
					this.attemptRepair();
					readyup = false;
				}
			}
			// If we're past n seconds, ready up the next multicast
			if ( (System.currentTimeMillis() - lastCheck ) > SysValues.REPAIR_TIMER*1000){
				lastCheck = System.currentTimeMillis();
				nextCheck = rng.nextInt(SysValues.REPAIR_TIMER);
				readyup = true;
			}
			
			
			
			// Try and recieve from the connections that were ignored?
			//attemptToFinalize();
			
			
			potentially_dead.addAll(link_serverinfo.to_test);

			// Check the status of all the servers that are potentially dead
			Iterator<String> iter = potentially_dead.iterator();
			while ( iter.hasNext() ){
				String url = iter.next();
				if (url == null){
					broadcast("Null url? Something went wrong when adding urls.");
					iter.remove();
					continue;
				}else if ( link_serverinfo.dead_servers.contains( url ) ){
					iter.remove(); //already noted as dead
					continue;
				}else{
					checkIfDead ( url );
					link_serverinfo.to_test.remove(url); // remove from list to test after we checked it
					iter.remove();
					continue;
				}
			}
		}

	}

	//TODO: 
	public void checkIfDead ( String url ){
		
		if ( link_serverinfo.dead_servers.contains(url) ) return; // already confirmed dead
		
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
			link_serverinfo.dead_servers.add( url );
			broadcast( url + " is DEAD! Socket fail.");
			check.CloseConnection();
			return;
		}
		check.SendMessage("{\"status\":true}");
		String finalstr = check.ReceiveMessage(SysValues.LISTEN_TIMEOUT );
		if ( finalstr == null ){
			link_serverinfo.dead_servers.add( url );
			broadcast( url + " is DEAD! Null reply.");
			return;
		}
		check.CloseConnection();

		
		// If we get here --  Must have just been slow?
		//link_serverinfo.addToGraceList(url);
		//broadcast("Adding " + url + " to grace list.");
		
	}
	
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
	
	/**
	 * 		A quick multicast to detemerine the status of all the other nodes.
	 */
	private void checkAll ( ){
		
		for ( int i = 0; i < link_serverinfo.servers.length(); i++ ){
			try {
				this.checkIfDead( link_serverinfo.servers.getString(i));
			} catch (JSONException e) {}
		}
	}
	
}
