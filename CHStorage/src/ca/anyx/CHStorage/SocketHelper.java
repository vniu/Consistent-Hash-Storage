package ca.anyx.CHStorage;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * 		A simple socket helper class, to externalize simple functions
 * 		of a socket such as sending and receiving.
 */
public class SocketHelper {

	private Socket TCP_socket;
	private OutputStream TCP_socket_os;
	private InputStream TCP_socket_is;
	private PrintWriter pw;
	
	public String myURL;
	
	/** An optional int attached to this object to determine which replica version we are if someone else needs this info */
	public int replica_number = -1;
	
	/**
	 * 		Default construction...
	 */
	public SocketHelper(){
	}

	/**
	 * 		In this case we're probably a sever.
	 * 		Connect the socket to our object.
	 * 
	 * @param TCP_socket	The socket to use our helper functions on.
	 */
	public SocketHelper( Socket TCP_socket ){
		try {
			this.TCP_socket = TCP_socket;
			this.TCP_socket_os = TCP_socket.getOutputStream();
			this.TCP_socket_is = TCP_socket.getInputStream();
			this.TCP_socket.setSoTimeout(SysValues.LISTEN_TIMEOUT*1000);
			this.pw = new PrintWriter(TCP_socket_os);
			
			this.myURL = "";
		} catch (IOException e) {
			//e.printStackTrace();	// invalid socket, shouldn't get here.
		}
	}
	
	public void broadcast( String s ){
		if ( !SysValues.DEBUG ) return;

		System.out.println( "SocketHelper> " + s );
	}

	/**
	 * 		Opens a socket connection to the given server and port.
	 * 
	 * @param server 	The server to connect to.
	 * @param port 		The port to connect on.
	 * @return 			0 if successful, 1 if connection was REFUSED, 2 if TIMED OUT, 3 otherwise
	 */
	public int CreateConnection( String server, int port ){
		this.myURL = server;
		
		try{
			this.TCP_socket = new Socket(server, port);
			this.TCP_socket_os = TCP_socket.getOutputStream();
			this.TCP_socket_is = TCP_socket.getInputStream();
			
			this.TCP_socket.setSoTimeout(SysValues.LISTEN_TIMEOUT*1000);
			this.pw = new PrintWriter(TCP_socket_os);
		} catch (UnknownHostException e) {
			//broadcast("NOTICE: Host exception on TCP socket creation. Host likely dead.\n");
			return 2;
		} catch (IOException e) {
			//broadcast(server + " : " + e.getLocalizedMessage() );
			if ( e.getLocalizedMessage().equals("Connection refused") ){
				return 1;
			}else if ( e.getLocalizedMessage().equals("Connection timed out") ){
				return 2;
			}else
				return 3;
		}
		return 0;
	}

	/**
	 * 		Closes this instance of the class's socket.
	 */
	public void CloseConnection() {
		try {
			if ( TCP_socket != null && TCP_socket.isConnected() ){
				TCP_socket.close();
				//broadcast("Connection closed.\n");
			}
		}catch (IOException e) {
			//TODO:
			// should ensure closure of socket
			//e.printStackTrace();
		}
	}


	/**		DEPRECIATED unfortunately
	 * 		Attempts to receive a message over the socket connection.
	 * 		Will wait for t seconds, and return null if it times out.
	 * @param t		How long to wait, in seconds
	 * @return 		The message, or null if we couldn't get a message, or end of stream was reached.
	 */
	public String __depreciated__ReceiveMessage( int t ){
		String s;
		BufferedReader br = null;

		try {
			br = new BufferedReader( new InputStreamReader(TCP_socket_is , "ISO-8859-1") ); // 1-1 byte mapping?
			
			int readattempts = 0;
			while( !br.ready() ){ // Check if we actually have stuff to read
				Thread.sleep(1);
				readattempts++;
				if (readattempts > t*1000 )
					return null;
			}
			s = new String(br.readLine().getBytes("ISO-8859-1"), "ISO-8859-1");
			
		} catch (IOException | InterruptedException e) {
			return null;
		}
		
		return s;
	}
	
	
	/**
	 * 		Attempts to receive a message over the socket connection.
	 * 		Will wait for t seconds, and return null if it times out.
	 * 
	 * 		If the message begins with '{' then it is assumed to be a JSON object,
	 * 		and assumed that the message ends with a '\n', aka, it was printed as a string.
	 * 		
	 * 		If not, then it's assumed to be in a byte-wise format that is horrible. 
	 * 		(It just gets converted to JSON in the layer above it)
	 * 
	 * @param t 	How long to wait, in seconds
	 * @return		The message, or null if we couldn't get a message, or end of stream was reached.
	 */
	public String ReceiveMessage( int t ){
		try {
			BufferedInputStream bis = new BufferedInputStream(this.TCP_socket_is);
			
			int readattempts = 0;
			while ( bis.available() == 0 ){
				Thread.sleep(1);
				readattempts++;
				if (readattempts > t*1000 )
					return null;
			}
			bis.mark(1);
			int byte1 = bis.read();
			bis.reset();

			if ( byte1 == "{".getBytes()[0] ){
				// First byte implies JSON formatting. Take all the data, it should be proper.
				int content;
				StringBuffer sb = new StringBuffer();
				while ((content = bis.read()) != '\n'){
					sb.append( (char)content ); 
				}
				return sb.toString();
			}
			
			// oh god here we go
			byte[] bytes;
			if (byte1 == (byte)1){
				bytes = new byte[1057];		// LOOK AT THAT HARDCODED NUMBER!
				bis.read(bytes, 0, 1057); 
			}else{
				bytes = new byte[33]; 		// ...
				bis.read(bytes, 0, 33); 
			}
			String s = new String(bytes, "ISO-8859-1");
			return s;
			
		} catch (IOException | InterruptedException e) { // TODO:: ????
			//broadcast( "READ ERROR: " + e.getLocalizedMessage());
			//e.printStackTrace();
			return null; // connection closed?
		}
	}

	/**
	 * 		Uses a PrintWriter to push out a string over the socket.
	 * 	
	 * @param m		The string to send.
	 */
	public void SendMessage(String m){
		if (TCP_socket_os == null) return;
		//PrintWriter pw = new PrintWriter(TCP_socket_os);
		pw.println(m);
		pw.flush();
	}

	/**
	 * 		Send a byte array over the socket.
	 * 	
	 * @param bytes		The byte array to send.
	 */
	public void SendBytes ( byte[] bytes ){
		try {
			if (this.TCP_socket_os != null)
				this.TCP_socket_os.write(bytes);
		} catch (IOException e) {
		}
	}

}
