/**
 * 
 */
package ca.anyx.CHStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public class GossipListener implements Runnable{
	int port;
	private ServerListsInfo link_serverinfo;

	public GossipListener( ServerListsInfo serverinfo, int theport) {
		this.link_serverinfo = serverinfo;
		this.port = theport;
	}

	private void broadcast( String s ){
		System.out.println("GossipListener> "+s);
	}

	@Override
	public void run() {
		broadcast("Gossip listening service started.");
		
		while (true){
			ServerSocket serverSocket = null;
			SocketHelper sh = null;
			
			try {
				serverSocket = new ServerSocket(port);

				//wait until we get a connection
				Socket clientSocket = serverSocket.accept();
				//broadcast("Received a connection.");
				sh = new SocketHelper(clientSocket);
				
				// send our current nodes' data
				sh.SendMessage( link_serverinfo.BuildMyData().toString() );
				
				//broadcast("sent a response");
				// get the resulting hash from them
				String s = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT / 2);

				if ( s.equals( link_serverinfo.getSHA()) ){

					//broadcast("Same hash");

					JSONObject j = new JSONObject();
					j.put("same", true);

					sh.SendMessage( j.toString() );

				}else{ // its not the same hash

					//send our data
					sh.SendMessage( link_serverinfo.dead_data.toString() );

					//receive their data
					String theirdata = sh.ReceiveMessage( SysValues.LISTEN_TIMEOUT / 2);

					//rebuild our data
					link_serverinfo.RebuildData( new JSONObject(theirdata) );
				}

				sh.CloseConnection();
				serverSocket.close();


			} catch (IOException | JSONException e) {
				if (sh != null){
					sh.CloseConnection();
				}
				if (serverSocket != null){
					try {
						serverSocket.close();
					} catch (IOException e1) {}
				}
				continue;
			}

		}// End of while
	}// End of run
}
