package CHStorage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 		Responsible for allocating clients to new threads.
 */
public class ServerRunnable implements Runnable {
	private Thread[] runningthreads;
	private NodeMaster NM;
	private volatile Boolean running;

	/**
	 * 		Constructor - only links resources. Start it as a thread to begin listening.
	 * 
	 * @param port					The port to listen on
	 * @param totalmaxconnections	How many connections can be allocated at once
	 * @param nodemaster			The NM to be passed along to clients
	 * @param listentimeout			The amount of time to wait until we determine a connection is invalid.
	 * @param _ForceStop			Whether or not to have persistent connections.
	 */
	public ServerRunnable( NodeMaster nodemaster ) {
		this.NM = nodemaster;
		runningthreads = new Thread[SysValues.maxconnections];
		running = true;
	}

	private void broadcast ( String m ){
		if ( !SysValues.debug ) return;
		
		System.out.println( "Server> " + m );
	}

	@Override
	public void run() {
		ServerSocket serverSocket = null;

		try {
			serverSocket = new ServerSocket( SysValues.port );
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
			
			int timer = 0;
			while ( (freeslot = cleanthreads()) < 0 ){
				broadcast("No room for new connection...");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {}
				
				//TODO: Do something if stuck here for too long?
				//		Also inform this new client that we can't help them?
				if ( timer/10 > SysValues.listentimeout ){
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
			
			if (timer <= SysValues.listentimeout ){
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
		for ( int i = 0; i < SysValues.maxconnections; i++  ){

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
