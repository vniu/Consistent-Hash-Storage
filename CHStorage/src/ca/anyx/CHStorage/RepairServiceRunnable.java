package ca.anyx.CHStorage;

import java.util.Iterator;
import java.util.Vector;

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
		int tempcount = 0;
		
		while (SysValues.shutdown == false ){
			
			// Check every second for now TODO: change!
			try {
				Thread.sleep(1000);
				tempcount++;
			} catch (InterruptedException e) {}

			if ( tempcount > 10 ){ // TODO: hardcoded 10 seconds
				tempcount = 0;
				this.attemptRepair();
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

	public void checkIfDead ( String url ){
		
		if ( link_serverinfo.dead_servers.contains(url) ) return; // already confirmed dead
		
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
		link_serverinfo.addToGraceList(url);
		broadcast("Adding " + url + " to grace list.");
		
	}
	
	
	public void attemptRepair(){
		Iterator<String> iter = link_serverinfo.dead_servers.iterator();
		
		while ( iter.hasNext() ){
			String url = iter.next();
			
			SocketHelper check = new SocketHelper ( );

			if ( check.CreateConnection( url, SysValues.STATUS_PORT ) != 0){
				// Still likely dead
				check.CloseConnection();
				return;
			}
			
			check.SendMessage("{\"status\":true}");
			String finalstr = check.ReceiveMessage(SysValues.LISTEN_TIMEOUT );
			if ( finalstr == null ){
				// Still likely dead
				return;
			}
			check.CloseConnection();

			
			// If we get here -- they responded to our "ping". They are likely alive again.
			link_serverinfo.dead_servers.remove(url);
			link_serverinfo.addToGraceList(url);
			broadcast("Adding " + url + " to grace list as per Repair.");
		}
		
	}
}
