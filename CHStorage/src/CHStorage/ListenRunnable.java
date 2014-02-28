package CHStorage;

import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
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

			try {
				// A JSON object comes from another node, or a user typing it as a string
				JSONObject j = new JSONObject(message);
				
				broadcast( "Got: " + message );
				
				if (j.has("stop")){
					this.seppuku("Stop");
					return;
				}

				JSONObject response = NM.keycommand( j );
				
				broadcast("Response ErrorCode: " + response.getInt("ErrorCode") );
				sh.SendMessage( response.toString() );

				continue;

			} catch (JSONException e) {
				
				try{ 	// If the user gives the silly required format, we can convert it to JSON
					
					ByteBuffer bb = null;
					JSONObject command = new JSONObject();

					bb = ByteBuffer.wrap( message.getBytes("ISO-8859-1") );
					broadcast("Got in hex: " + SocketHelper.byteArrayToHexString(message.getBytes("ISO-8859-1")));
					
					// get the command - 1 byte
					byte firstbyte = bb.get();
					
					// get the key - 32 bytes
					byte[] dst = new byte[32];
					bb.get(dst);
					String key = new String(dst, "ISO-8859-1");
					
					command.put("key", key);
					
					switch ( firstbyte ){
					case 1: 
						command.put("put", true);
						
						// get the value - 1024 bytes
						byte[] bytesvalue = new byte[1024];
						bb.get(bytesvalue);
						String stringvalue = new String(bytesvalue, "ISO-8859-1");
						command.put("value", stringvalue );
						break;
					case 2:
						command.put("get", true);
						break;
					case 3:
						command.put("remove", true);
						break;
					default:
						broadcast("Bad command?");
						byte[] bytes = new byte[1];
						bytes[0] = 5;
						//Tell the user its an invalid command
						sh.SendBytes(bytes);
						continue;
					}
					
					// Okay, we have constructed the command, send it into the system
					JSONObject response = NM.keycommand( command );
					
					byte ErrorCodeByte = (byte) response.getInt("ErrorCode");
					
					// Respond to the silly user with an equally silly format
					byte[] bytes;
					if (command.has("get") && ErrorCodeByte == 0 ){
						bytes = new byte[1025];
						bytes[0] = ErrorCodeByte;
						for (int i = 0; i < 1024; i++){
							bytes[i+1] = (byte) response.getString("value").charAt(i);
						}
					}else{
						bytes = new byte[1];
						bytes[0] = ErrorCodeByte;
					}
					
					broadcast("Response ErrorCode: " + response.getInt("ErrorCode") );
					sh.SendBytes(bytes);
					continue;
					
				}catch ( BufferUnderflowException e2) {
					broadcast("Poorly formatted message");
					byte[] bytes = new byte[1];
					bytes[0] = 5;
					//Tell the user its an invalid command
					sh.SendBytes(bytes);
					continue;
				}catch (JSONException | UnsupportedEncodingException e3 ){
					// Shouldn't get here? All hardcoded?
					broadcast("Poorly formatted message - JSON conversion fail?");
					byte[] bytes = new byte[1];
					bytes[0] = 5;
					//Tell the user its an invalid command
					sh.SendBytes(bytes);
					continue;
				}
				
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
