package CHStorage;

import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import javax.xml.bind.DatatypeConverter;

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

	/**
	 * 		Constructor - will allocate a connection to clientSocket. Start it as a thread to begin listening.
	 * 
	 * @param clientsocket	The client we will connect to.
	 * @param nodemaster	The executor to send our clients' requests to.
	 */
	public ListenRunnable( Socket clientsocket, NodeMaster nodemaster ) {
		this.sh = new SocketHelper(clientsocket);
		this.theirip = clientsocket.getInetAddress().toString();
		this.NM = nodemaster;

		running = true;
	}

	private void broadcast( String m ){
		if ( !SysValues.debug ) return;
		// Add in thread ID so we can see who's who
		String ID = Long.toString( Thread.currentThread().getId() ); 
		System.out.println( "ServerThread(" + ID + ")> " + m );
	}

	@Override
	public void run() {
		while ( running ){
			long starttime = System.currentTimeMillis();
			String message = sh.ReceiveMessage( SysValues.listentimeout );
			
			long endtime = System.currentTimeMillis();
			
			if (message == null) {
				this.seppuku("Null or timeout in: " + (endtime - starttime) );
				return;
			}
			
			try {
				// A JSON object comes from another node, or a user typing it as a string
				JSONObject j = new JSONObject(message);
				
				if ( SysValues.ShowValues ) 
					broadcast( "Got: " + message );
				
				if (j.has("stop")){
					this.seppuku("Stop");
					return;
				}
				
				if (SysValues.InternalOnly) j.put("internal", true);
				
				JSONObject response = NM.keycommand( j ); // Send the response into the system
				
				broadcast("Response ErrorCode: " + response.getInt("ErrorCode") );
				sh.SendMessage( response.toString() );
				
				if (SysValues.ForceStop) this.seppuku("ForceStop");
				continue;

			} catch (JSONException e) {
				
				try{ 	// If the user gives the silly required format, we can convert it to JSON
					
					ByteBuffer bb = null;
					JSONObject command = new JSONObject();

					bb = ByteBuffer.wrap( message.getBytes("ISO-8859-1") );
					if ( SysValues.ShowValues ) 
						broadcast("Got in hex: " + DatatypeConverter.printHexBinary(message.getBytes("ISO-8859-1")));
					
					// get the command - 1 byte
					byte firstbyte = bb.get();
					
					// get the key - 32 bytes
					if ( firstbyte != 4 ){
						byte[] dst = new byte[32];
						bb.get(dst);
										
						command.put( "key", DatatypeConverter.printHexBinary(dst) );
					}
				
					switch ( firstbyte ){
					case 1: 
						command.put("put", true);
						
						// get the value - 1024 bytes
						byte[] bytesvalue = new byte[1024];
						bb.get(bytesvalue);
						String hexval = DatatypeConverter.printHexBinary(bytesvalue);
						command.put("value", hexval);
						break;
					case 2:
						command.put("get", true);
						break;
					case 3:
						command.put("remove", true);
						break;
					case 4:
						command.put("shutdown", true);
						break;
					default:
						broadcast("Bad command?");
						byte[] bytes = new byte[1];
						bytes[0] = 5;	//Tell the user its an invalid command
						sh.SendBytes(bytes);
						if (SysValues.ForceStop) this.seppuku("ForceStop");
						continue;
					}
					
					if (SysValues.InternalOnly) command.put("internal", true);
					// Okay, we have constructed the command, send it into the system
					JSONObject response = NM.keycommand( command );
					
					byte ErrorCodeByte = (byte) response.getInt("ErrorCode");
					
					// Respond to the silly user with an equally silly format
					byte[] bytes;
					if (command.has("get") && ErrorCodeByte == 0 ){
						bytes = new byte[1025];
						bytes[0] = ErrorCodeByte;
						byte[] hexval = DatatypeConverter.parseHexBinary(response.getString("value"));
						
						for (int i = 0; i < 1024; i++){
							bytes[i+1] = hexval[i];
						}
					}else{
						bytes = new byte[1];
						bytes[0] = ErrorCodeByte;
					}
					
					broadcast("Response ErrorCode: " + response.getInt("ErrorCode") );
					broadcast( "Sent in hex:" + DatatypeConverter.printHexBinary(bytes) );
					sh.SendBytes(bytes);
					if (SysValues.ForceStop) this.seppuku("ForceStop");
					continue;
					
				}catch ( BufferUnderflowException e2) {
					broadcast("Poorly formatted message");
					byte[] bytes = new byte[1];
					bytes[0] = 5;	//Tell the user its an invalid command
					sh.SendBytes(bytes);
					if (SysValues.ForceStop) this.seppuku("ForceStop");
					continue;
					
				}catch (JSONException | UnsupportedEncodingException e3 ){
					// JSON Array failed... try string?
					broadcast("Poorly formatted message - JSON conversion fail?");
					byte[] bytes = new byte[1];
					bytes[0] = 5;
					//Tell the user its an invalid command
					sh.SendBytes(bytes);
					if (SysValues.ForceStop) this.seppuku("ForceStop");
					continue;
				}
				
			}

		} // End of while
	} // End of run


	/**
	 *  Closes the connection and stops the thread.
	 */
	private void seppuku( String m ){
		broadcast( m + ", closing: " + theirip);
		sh.CloseConnection();
		running = false;
	}
}