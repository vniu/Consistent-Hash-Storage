/**
 * 
 */
package ca.anyx.CHStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public class GossipListener implements Runnable{
	int port;
	private ServerListsInfo link_serverinfo;
	private final ExecutorService pool;

	public GossipListener( ServerListsInfo serverinfo, int theport) {
		this.link_serverinfo = serverinfo;
		this.port = theport;
		pool = Executors.newFixedThreadPool(10); //TODO: sys
	}

	private void broadcast( String s ){
		System.out.println("GossipListener> "+s);
	}

	class GossipRunnable implements Runnable{
		Socket clientSocket;

		GossipRunnable( Socket client){
			clientSocket = client;
		}

		@Override
		public void run() {
			SocketHelper sh = null;
			try{
				sh = new SocketHelper(clientSocket);
				sh.myURL = clientSocket.getInetAddress().toString();

				//broadcast("Sending a response.");

				// send our current nodes' data
				sh.SendMessage( link_serverinfo.BuildMyData().toString() );

				//broadcast("Sent a response.");

				// get the resulting hash from them
				String s = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT /2 );
				if ( s == null ){
					sh.CloseConnection();
					return;
				}

				//broadcast("Starting hash check");
				/*
				if ( s.equals( link_serverinfo.getSHA()) ){

					broadcast("Same hash");

					JSONObject j = new JSONObject();
					j.put("same", true);
					assert (sh != null) : "Fail! Null 3.";
					sh.SendMessage( j.toString() );

				}else{ // its not the same hash
				 */
				//broadcast("Different hash.");
				JSONObject j = new JSONObject(s);
				link_serverinfo.RebuildData( j );

				//send our data
				sh.SendMessage( link_serverinfo.dead_data.toString() );

				//broadcast("Sent our data, waiting to recieve.");

				//receive their data
				//String theirdata = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT /2 );

				//if (theirdata == null ){
				//	//broadcast("Their data was null!");
				//	sh.CloseConnection();
				//	return;
				//}

				//broadcast("Starting rebuild.");
				//rebuild our data
				//JSONObject j = new JSONObject(theirdata);
				
				broadcast("Serviced a poll request successfully.");
			//}

			sh.CloseConnection();
			return;

		} catch ( JSONException e) {
			broadcast("WARN: Failed to service a poll request: " + e.getLocalizedMessage() );
			if (sh != null){
				sh.CloseConnection();
			}
			return;
		} 
	}
} // End of GossipRunnable

@Override
public void run() {
	ServerSocket serverSocket = null;

	try {
		serverSocket = new ServerSocket( this.port );
	} catch (IOException e2) {
		broadcast("Port in use?");
		e2.printStackTrace();
	}

	while ( SysValues.shutdown == false ) {
		Socket clientSocket = null;

		try {	// Wait to receive new connection	
			clientSocket = serverSocket.accept();
		} catch (IOException e) {
			broadcast("Bad client connection, ignoring: " + clientSocket.getInetAddress().toString());
			continue;
		}

		if ( SysValues.shutdown ){
			try {
				clientSocket.close();
				pool.shutdown();
				serverSocket.close();
			} catch (IOException e) {}
			return;
		}

		broadcast ( "Got connection from: " + clientSocket.getInetAddress().toString() );

		pool.execute(new GossipRunnable( clientSocket ) );
	} // End of while

	pool.shutdown();
	try {
		serverSocket.close();
	} catch (IOException e) {}

}// End of run
}
