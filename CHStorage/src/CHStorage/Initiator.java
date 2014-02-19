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
	private final static int port = 2222; 			//TODO: Change from hardcoded?
	private final static int maxconnections = 5;	// Limit to 5 clients at a time.
	private final static int fileIOtime = 10; 		// Write file to disk every 10 seconds.
	private final static int listentimeout = 20; 	// Allow a connection to remain open for 20 seconds from last message.


	/**
	 * 		The entry point for the project. Will start up all services, and output results to a file.
	 * 
	 * @param args	(Currently unused)
	 */
	public static void main(String[] args) {

		try {
			// Build our server list out of the .txt file
			File file = new File("servers.txt");
			FileInputStream fis = new FileInputStream(file);

			StringBuilder sb = new StringBuilder();
			int content;
			while ((content = fis.read()) != -1){
				sb.append( (char)content ); 
			} 

			fis.close();

			JSONObject j = new JSONObject(sb.toString());
			System.out.println("Main> Server List: " + j.toString());

			NodeMaster NM = new NodeMaster( j );
			Thread th = new Thread( new ServerRunnable( port, maxconnections, listentimeout, NM ) );
			th.start();


			while(true){
				Thread.sleep( fileIOtime * 1000 );

				try {
					BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
							new FileOutputStream("json.txt"), "utf-8"));

					// Write node's storage data to text file for external viewing
					writer.write( NM.getStorage().toString() );
					writer.close();
					
				} catch (IOException ex) {
					System.out.println("Couldn't write to file.");
				}
			}

		}catch (JSONException | IOException e) {
			System.out.println("Error: Check server.txt. Exiting: " + e.getLocalizedMessage());
			return;
		}catch ( InterruptedException e ){}
	}
	
}
