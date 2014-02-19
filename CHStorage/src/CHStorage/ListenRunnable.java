package CHStorage;

import java.net.Socket;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 		Responsible for receiving and directing requests from a client.
 */
public class ListenRunnable implements Runnable {
	private SocketHelper sh;
	private volatile Boolean running;
	private NodeMaster NM;
	private String theirip;
	private int timeout;
	
	/**
	 * 		Constructor - will allocate a connection to clientSocket. Start it as a thread to begin listening.
	 * 
	 * @param clientsocket	The client we will connect to.
	 * @param nodemaster	The executor to send our clients' requests to.
	 * @param listentimeout	How long we wait before we consider a client no longer active
	 */
	public ListenRunnable( Socket clientsocket, NodeMaster nodemaster, int listentimeout ) {
		this.sh = new SocketHelper(clientsocket);
		this.theirip = clientsocket.getInetAddress().toString();
		this.NM = nodemaster;
		this.timeout = listentimeout;
		
		running = true;
	}
	
	private void broadcast( String m ){
		// Add in thread ID so we can see who's who
		String ID = Long.toString( Thread.currentThread().getId() ); 
		System.out.println( "ServerThread(" + ID + ")> " + m );
	}

	@Override
	public void run() {
		while ( running ){
			
			String message = sh.ReceiveMessage( timeout );
			
			if (message == null) {
				this.seppuku("Null or timeout");
				return;
			}
			broadcast( "Got: " + message );
			
			try {

				JSONObject j = new JSONObject(message);
				
				if (j.has("stop")){
					this.seppuku("Stop");
					return;
				}
				
				JSONObject response = NM.keycommand( j );
				
				sh.SendMessage( response.toString() );
				
				continue;
				
			} catch (JSONException e) {
				
				//TODO: Parse non-JSON form of commands, give reply
				
				//for now, tell the user its an invalid command
				JSONObject response = NM.keycommand( null );
				sh.SendMessage( response.toString() );
				
				broadcast("Not JSON... I'll pretend I didn't hear that.");
			}
			
		} // End of while
	} // End of run
	
	
	/**
	 *  hnnnnnnnng
	 */
	private void seppuku( String m ){
		broadcast( m + ", closing: " + theirip);
		sh.CloseConnection();
		//TODO: more research on thread killing?
		running = false;
	}
}
