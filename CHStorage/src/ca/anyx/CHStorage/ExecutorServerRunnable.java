package ca.anyx.CHStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**	
 * 		Responsible for allocating clients to new threads.
 */
public class ExecutorServerRunnable implements Runnable {
	private NodeMaster NM;
	private int port;
	private final ExecutorService pool;

	private volatile Boolean running = true;


	/**
	 * 		Constructor - only links resources. Start it as a thread to begin listening.
	 * 
	 * @param nodemaster			The NodeMaster to be passed along to clients.
	 * @param _port					The port to listen on.
	 * @param _maxconnections		The amount of clients allowed at a time for this server.			
	 */
	public ExecutorServerRunnable( NodeMaster nodemaster, int _port, int _maxconnections ) {
		this.NM = nodemaster;
		this.port = _port;
		pool = Executors.newFixedThreadPool(_maxconnections);
	}

	private void broadcast ( String m ){
		if ( !SysValues.DEBUG ) return;

		System.out.println( "ExServer> " + m );
	}

	@Override
	public void run() {
		ServerSocket serverSocket = null;

		try {
			serverSocket = new ServerSocket( this.port );
		} catch (IOException e2) {
			broadcast("Port in use?");
			e2.printStackTrace();
		}

		while ( running && SysValues.shutdown == false ) {
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
			
			pool.execute(new ListenRunnable( clientSocket, NM ));
		} // End of while
		
		pool.shutdown();
		try {
			serverSocket.close();
		} catch (IOException e) {}
	}

}
