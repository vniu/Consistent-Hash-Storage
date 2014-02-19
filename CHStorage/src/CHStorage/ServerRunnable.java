package CHStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 		Responsible for allocating clients to new threads.
 */
public class ServerRunnable implements Runnable {
	private Thread[] runningthreads;
	private int maxconnections;
	private int serverport;
	private int timeout;
	private NodeMaster NM;
	private volatile Boolean running;

	/**
	 * 		Constructor - only links resources. Start it as a thread to begin listening.
	 * 
	 * @param port					The port to listen on
	 * @param totalmaxconnections	How many connections can be allocated at once
	 * @param nodemaster			The NM to be passed along to clients
	 */
	public ServerRunnable( int port, int totalmaxconnections, int listentimeout, NodeMaster nodemaster ) {
		this.serverport = port;
		this.NM = nodemaster;
		this.timeout = listentimeout;
		runningthreads = new Thread[totalmaxconnections];
		this.maxconnections = totalmaxconnections;
		running = true;
	}

	private void broadcast ( String m ){
		System.out.println( "Server> " + m );
	}

	@Override
	public void run() {
		ServerSocket serverSocket = null;

		try {
			serverSocket = new ServerSocket( serverport );
		} catch (IOException e2) {
			broadcast("Port in use?");
			e2.printStackTrace();
		}

		while ( running ){

			Socket clientSocket = null;
			int freeslot = -1;

			try {	// Wait to receive new connection	
				clientSocket = serverSocket.accept();
			} catch (IOException e) {
				broadcast("Bad client connection, ignoring: " + clientSocket.getInetAddress().toString());
				continue;
			}
			broadcast ( "Got connection from: " + clientSocket.getInetAddress().toString() );

			freeslot = cleanthreads();
			while ( freeslot < 0 ){
				broadcast("No room for new connection...");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {}
				freeslot = cleanthreads();
				
				//TODO: Do something if stuck here for too long?
				//		Also inform this new client that we can't help them?
			}

			Thread client = new Thread( new ListenRunnable( clientSocket, NM, timeout ) );
			client.start();
			runningthreads[freeslot] = client;

		} // End of while

		try {
			serverSocket.close();
		} catch (IOException e) {}

	} // End of run

	/**
	 * 		Frees threads who have died.
	 * 
	 * @return	A free connection slot we can work with. (Will be highest most index)
	 */
	private int cleanthreads(){
		int freeslot = -1;

		// Look for any threads that have ended
		for ( int i = 0; i < maxconnections; i++  ){

			if ( runningthreads[i] == null ){
				freeslot = i; // We can use any slot thats free
			}
			else if ( runningthreads[i].isAlive() == false ){
				// TODO: Is this the proper way to remove thread resources?
				runningthreads[i] = null;
			}
		}

		return freeslot;
	}

}
