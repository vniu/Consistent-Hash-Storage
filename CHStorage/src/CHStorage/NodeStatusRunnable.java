package CHStorage;

import java.util.Iterator;
import java.util.Vector;

public class NodeStatusRunnable implements Runnable {
	public Vector<String> dead_servers;
	public Vector<String> potentially_dead;
	public Vector<String> to_test;
	private NodeMaster nodemaster;

	NodeStatusRunnable( Vector<String> _dead_servers, Vector<String> _to_test, NodeMaster nm ){
		this.dead_servers = _dead_servers;
		potentially_dead= new Vector<String>();
		this.nodemaster = nm;
		this.to_test = _to_test;
	}

	private void broadcast( String m ){
		if ( !SysValues.debug ) return;

		System.out.println( "NodeStatus> " + m );
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
			
			potentially_dead.addAll(to_test);
			to_test.clear();

			Iterator<String> iter = potentially_dead.iterator();
			while ( iter.hasNext() ){
				String url = iter.next();
				if (url == null){
					broadcast("Null url? Something went wrong when adding urls.");
					iter.remove();
					continue;
				}else if ( dead_servers.contains( url ) ){
					iter.remove(); //already noted as dead
					continue;
				}else{
					checkStatus ( url );
					iter.remove();
					continue;
				}
			}
		}

	}

	public void checkStatus ( String url ){
		
		if ( this.nodemaster.hasGracelist(url) ){
			//Responded OK recently, might still be ok - wait for more confirms of dead
			long grace = this.nodemaster.getGracelistLong(url);
			if ( (System.currentTimeMillis() - grace) > SysValues.maxgrace*1000 ){
				this.nodemaster.removeFromGracelist(url);
				broadcast("Removing " + url + " from grace list.");
			}else{
				//this.gracelist.put( url, System.currentTimeMillis() );
				return;
			}
		}
		
		
		SocketHelper check = new SocketHelper ( );

		if ( check.CreateConnection( url, SysValues.statusport ) != 0){
			this.dead_servers.add( url );
			broadcast( url + " is DEAD! Socket fail.");
			check.CloseConnection();
			return;
		}
		check.SendMessage("{\"status\":true}");
		String finalstr = check.ReceiveMessage(SysValues.listentimeout );
		if ( finalstr == null ){
			dead_servers.add( url );
			broadcast( url + " is DEAD! Null reply.");
			return;
		}
		check.CloseConnection();

		
		// If we get here --  Must have just been slow?
		this.nodemaster.addToGracelist(url);
		broadcast("Adding " + url + " to grace list.");
		
	}
	
	
	public void attemptRepair(){
		Iterator<String> iter = this.dead_servers.iterator();
		
		while ( iter.hasNext() ){
			String url = iter.next();
			
			SocketHelper check = new SocketHelper ( );

			if ( check.CreateConnection( url, SysValues.statusport ) != 0){
				// Still likely dead
				check.CloseConnection();
				return;
			}
			
			check.SendMessage("{\"status\":true}");
			String finalstr = check.ReceiveMessage(SysValues.listentimeout );
			if ( finalstr == null ){
				// Still likely dead
				return;
			}
			check.CloseConnection();

			
			// If we get here -- they responded to our "ping". They are likely alive again.
			this.dead_servers.remove(url);
			this.nodemaster.addToGracelist(url);
			broadcast("Adding " + url + " to grace list as per Repair.");
		}
		
	}
}
