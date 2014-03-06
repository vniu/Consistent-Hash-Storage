package CHStorage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
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
			this.TCP_socket.setSoTimeout(7000);

		} catch (IOException e) {
			e.printStackTrace();	// invalid socket, shouldn't get here.
		}
	}

	/**
	 * Opens a socket connection to the given server and port.
	 * 
	 * @param server 	The server to connect to.
	 * @param port 		The port to connect on.
	 * @return 			0 if successful, 1 if connection was REFUSED, 2 if there was an unknown host or io exception.
	 */
	public int CreateConnection( String server, int port ){
		try{
			this.TCP_socket = new Socket(server, port);
			this.TCP_socket_os = TCP_socket.getOutputStream();
			this.TCP_socket_is = TCP_socket.getInputStream();
			this.TCP_socket.setSoTimeout(7000);
		} catch (UnknownHostException e) {
			System.out.printf("NOTICE: Host exception on TCP socket creation. Host likely dead.\n");
			return 2;
		} catch (IOException e) {
			if (e.getLocalizedMessage().equals("Connection refused") )
				return 1;
			System.out.printf("NOTICE:" + e.getLocalizedMessage() + ", IO E on socket creation.\n" );
			return 2;
		}
		return 0;
	}

	/**
	 * Closes this instance of the class's socket.
	 */
	public void CloseConnection() {
		try {
			if ( TCP_socket != null && TCP_socket.isConnected() ){
				TCP_socket.close();
				//System.out.printf("Connection closed.\n");
			}
		}catch (IOException e) {
			//TODO:
			// should ensure closure of socket
			//e.printStackTrace();
		}
	}

	/**
	 * 		Converts a byte array into a hex string.
	 * 
	 * @param bytes 	Byte array to be converted.
	 * @return 			A string of hex values.
	 */
	public static String byteArrayToHexString(byte[] bytes) {
		StringBuffer buf = new StringBuffer();
		String str;
		int val;

		for (int i=0; i<bytes.length; i++) {
			val = (int)bytes[i] & 0x000000FF;
			str = Integer.toHexString(val);
			while ( str.length() < 2 )
				str = "0" + str;
			buf.append( str );
		}
		return buf.toString().toUpperCase();
	}

	/**
	 * 		Attempts to receive a message over the socket connection.
	 * 		Will wait for t seconds, and return null if it times out.
	 * @param t		How long to wait, in seconds
	 * @return 		The message, or null if we couldn't get a message, or end of stream was reached.
	 */
	public String ReceiveMessage( int t ){
		String s;
		BufferedReader br = null;
		//StringBuilder sb = new StringBuilder();
		try {
			br = new BufferedReader( new InputStreamReader(TCP_socket_is , "ISO-8859-1") ); // 1-1 byte mapping?
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace(); //hardcoded encoding, won't get here
		}

		try {
			int readattempts = 0;
			while( !br.ready() ){ // Check if we actually have stuff to read
				Thread.sleep(100);
				readattempts++;
				if (readattempts > t*10 )
					return null;
			}
			s = new String(br.readLine().getBytes("ISO-8859-1"), "ISO-8859-1");
			//int content;
			//while ((content = br.read()) != -1){
			//	sb.append( (char)content ); 
			//} 
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			return null;
		}
		return s;
		//return sb.toString();
	}

	/**
	 * 		Uses a PrintWriter to push out a string over the socket.
	 * 	
	 * @param m		The string to send.
	 */
	public void SendMessage(String m){
		PrintWriter pw = new PrintWriter(TCP_socket_os);
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
			SendMessage ( new String(bytes, "ISO-8859-1") ); // this should work...
		} catch (UnsupportedEncodingException e) {}
	}

}
