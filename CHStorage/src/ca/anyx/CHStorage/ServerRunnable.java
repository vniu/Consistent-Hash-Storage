package ca.anyx.CHStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**		DEPRECIATED.
 * 		Responsible for allocating clients to new threads.
 */
public class ServerRunnable implements Runnable {
	private Thread[] runningthreads;
	private NodeMaster NM;
	private int port;
	private int maxconnections;
	
	private volatile Boolean running;


	/**
	 * 		Constructor - only links resources. Start it as a thread to begin listening.
	 * 
	 * @param nodemaster			The NodeMaster to be passed along to clients.
	 * @param _port					The port to listen on.
	 * @param _maxconnections		The amount of clients allowed at a time for this server.			
	 */
	public ServerRunnable( NodeMaster nodemaster, int _port, int _maxconnections ) {
		this.NM = nodemaster;
		this.port = _port;
		this.maxconnections = _maxconnections;
		runningthreads = new Thread[maxconnections];
		running = true;
	}

	private void broadcast ( String m ){
		if ( !SysValues.DEBUG ) return;
		
		System.out.println( "Server> " + m );
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

		while ( running && SysValues.shutdown == false ){

			Socket clientSocket = null;
			int freeslot = -1;

			try {	// Wait to receive new connection	
				clientSocket = serverSocket.accept();
			} catch (IOException e) {
				broadcast("Bad client connection, ignoring: " + clientSocket.getInetAddress().toString());
				continue;
			}
			
			if ( SysValues.shutdown ){
				try {
					clientSocket.close();
					serverSocket.close();
				} catch (IOException e) {}
				return;
			}
			
			broadcast ( "Got connection from: " + clientSocket.getInetAddress().toString() );
			
			int timer = 0;
			while ( (freeslot = cleanthreads()) < 0 ){
				broadcast("No room for new connection...");
				try {
					Thread.sleep(10);
				} catch (InterruptedException e1) {}
				
				//TODO: Do something if stuck here for too long?
				//		Also inform this new client that we can't help them?
				if ( timer > SysValues.LISTEN_TIMEOUT*100 ){
					broadcast("Server is overloaded.");
					byte[] bytes = new byte[1];
					bytes[0] = 3;	//Overloaded
					SocketHelper sh = new SocketHelper( clientSocket );
					sh.SendBytes(bytes);
					sh.CloseConnection();
					continue;
				}
				timer++;
			}
			
			if (timer <= SysValues.LISTEN_TIMEOUT ){
				Thread client = new Thread( new ListenRunnable( clientSocket, NM ) );
				
				client.start();
				
				runningthreads[freeslot] = client;
			}
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
