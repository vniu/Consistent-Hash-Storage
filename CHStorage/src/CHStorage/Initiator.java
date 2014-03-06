package CHStorage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 		Responsible for initializing threads, and doing file IO.
 */
public class Initiator {
	private final static int port = 2324; 			//TODO: Change from hardcoded?
	private final static int maxconnections = 30;	// Limit to n clients at a time.
	private final static int fileIOtime = 5; 		// Write file to disk every n seconds.
	private final static int listentimeout = 30; 	// Allow a client connection to remain open for n seconds from last message.
	private final static int redundancylevel = 3;	// Store data on n other servers.
	private final static String serverfilename = "servers.txt";
	private final static String outputfilename = "kv.txt";


	/**
	 * 		The entry point for the project. Will start up all services, and output results to a file.
	 * 
	 * <br>	Example usage with nc and JSON strings:
	 * <br>		$ nc localhost {@value #port}
	 * <br>		$ { "put":true, "key":"cats", value:"meow"}
	 * <br>		$ { "get":true, "key":"cats"}
	 * <br>		$ { "remove":true, "key":"cats"}
	 * <br> 
	 * 		Note that you can use plain strings in JSON format.
	 * 
	 * 		Yes, there is an alternate way to put and get key/values. I dislike it.
	 * 
	 * 
	 * @param args	(Currently unused)
	 */
	public static void main(String[] args) {

		try {
			// Build our server list out of the .txt file
			File file = new File( serverfilename );
			FileInputStream fis = new FileInputStream(file);

			StringBuilder sb = new StringBuilder();
			int content;
			while ((content = fis.read()) != -1){
				sb.append( (char)content ); 
			} 

			fis.close();

			JSONObject j = new JSONObject(sb.toString());
			System.out.println("Main> Server List: " + j.toString());

			NodeMaster NM = new NodeMaster( j, port, redundancylevel );
			Thread th = new Thread( new ServerRunnable( port, maxconnections, listentimeout, NM ) );
			th.start();


			while(true){
				Thread.sleep( fileIOtime * 1000 );

				try {
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream( outputfilename ), "ISO-8859-1"));

					// Write node's storage data to text file for external viewing
					writer.write( NM.getStorage().toString() );
					writer.close();
					
				} catch (IOException ex) {
					System.out.println("Couldn't write to file.");
				}
			}

		}catch (JSONException | IOException e) {
			System.out.println("Error: Check " + serverfilename +  ". Exiting: " + e.getLocalizedMessage());
			return;
		}catch ( InterruptedException e ){}
	}
	
}
