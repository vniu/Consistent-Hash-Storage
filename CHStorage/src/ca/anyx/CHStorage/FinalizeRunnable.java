package ca.anyx.CHStorage;

import java.util.Iterator;
import java.util.Vector;

/**
 * 		Intended to provide a single location to finalize connections,
 * 		after a message has recieved an agreeable response.
 * 		This will prevent many threads from waiting for a reply that the no longer care about.
 */
public class FinalizeRunnable implements Runnable {

	public Vector<RequestObject> add_to_finalize;
	private Vector<RequestObject> working_finalize;
	private  ServerListsInfo link_serverinfo;
	private Vector<RequestObject> failed_replicas;

	/**
	 * 	Default construction..
	 * @param serverinfo	The server information, as usual
	 */
	FinalizeRunnable( ServerListsInfo serverinfo ){
		add_to_finalize = new Vector<RequestObject>();
		working_finalize = new Vector<RequestObject>();
		this.link_serverinfo = serverinfo;
	}

	@Override
	public void run() {
		while (SysValues.shutdown == false){
			synchronized (this){
				working_finalize.addAll(add_to_finalize);
				add_to_finalize.clear();
			}

			if (working_finalize.size() == 0 ) continue; // Nothing to do!

			// do a read check on every Request Object
			// (add new ones if old fail)
			readCheck( );

			// remove any Request Objects who have timed out
			// (add new ones if old fail)
			removeExpired();

			if ( failed_replicas.size() == 0 ){
				// We're done, no failed replicas!
				continue;
			}

			//TODO: Is this opening too many concurrent connections?
			working_finalize.addAll( failed_replicas );
			failed_replicas.clear();

			//repeat
		}
	}

	// TODO: pretty up
	// remove any Request Objects who have timed out
	void removeExpired( ){
		if (working_finalize.size() == 0 ) return; // Nothing to do!

		Iterator<RequestObject> ro_iter = this.working_finalize.iterator();

		while ( ro_iter.hasNext() ){
			RequestObject ro = ro_iter.next();
			Iterator<SocketHelper> shs_iter = ro.shs.iterator();
			while ( shs_iter.hasNext() ){
				SocketHelper working_sh = shs_iter.next();

				if ( (System.currentTimeMillis() - ro.startTime) > SysValues.LISTEN_TIMEOUT){
					// This specific RequestObject ran out of time
					// add all null responses to the dead list.
					String response = working_sh.ReceiveMessage(0);
					if ( response == null ){
						link_serverinfo.dead_servers.add( working_sh.myURL );

						RequestObject new_ro = buildFailed( ro, working_sh.replica_number );
						failed_replicas.add( new_ro );
					}
					working_sh.SendMessage("{\"stop\":true}");
					working_sh.CloseConnection();
					shs_iter.remove();
				}
				// Else, it can stay in the working list

				// Check if all shs dealt with in RO
				if (ro.shs.size() == 0){
					ro_iter.remove();
				}
			} // End of shs iteration
		}// End of ro iteration


	}
	
	// TODO: pretty up
	// do a read check on every Request Object
	void readCheck( ){
		if (working_finalize.size() == 0 ) return; // Nothing to do!

		Iterator<RequestObject> ro_iter = this.working_finalize.iterator();

		while ( ro_iter.hasNext() ){
			RequestObject ro = ro_iter.next();
			Iterator<SocketHelper> shs_iter = ro.shs.iterator();
			while ( shs_iter.hasNext() ){
				SocketHelper working_sh = shs_iter.next();

				if ( link_serverinfo.dead_servers.contains( working_sh.myURL ) ) { 
					// Already marked as dead --
					// This could happen if another thread found the url to be dead.
					RequestObject new_ro =  buildFailed( ro, working_sh.replica_number );
					failed_replicas.add( new_ro );

					working_sh.SendMessage("{\"stop\":true}");
					working_sh.CloseConnection();
					shs_iter.remove();	// Close and remove the SocketHelper

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
					shs_iter.remove();	// Close and remove the SocketHelper
				}
			} // End of shs iteration

			// Check if all shs dealt with in RO
			if (ro.shs.size() == 0){
				ro_iter.remove();
			}
		}  // End of ro iteration
	}

	/**
	 * 		Creates a new connection to the next replica in line
	 * 
	 * @param ro				The original request -- need its data
	 * @param replica_number	Where exactly the original request was (part of the sh)
	 * @return					The new request object based on the old one, with the next location in line
	 */
	RequestObject buildFailed( RequestObject ro, int replica_number ){
		Vector<SocketHelper> new_shs = RedundancyRunnable.openRequests(replica_number, 1, SysValues.REDUNDANCY_LEVEL, ro.message, link_serverinfo, ro.sentLocations);
		RequestObject new_ro = new RequestObject(new_shs, ro.message, ro.sentLocations);
		return new_ro;
	}
}
