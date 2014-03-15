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

	/**
	 * 		The entry point for the project. Will start up all services, and output results to a file.
	 * 
	 * <br>	Example usage with nc and JSON strings:
	 * <br>		$ nc localhost {@value #port}
	 * <br>		$ {"put":true, "key":"cats", value:"meow"}
	 * <br>		$ {"get":true, "key":"cats"}
	 * <br>		$ {"remove":true, "key":"cats"}
	 * <br> 
	 * <br> 	Note that you can use plain strings in JSON format.
	 * <br> 	
	 * <br> The following will shut down the system:
	 * <br> 	$ {"shutdown":true}
	 * 
	 * 
	 * 		Yes, there is an alternate way to put and get key/values. 
	 * 		I dislike it. I'm not even going to bother explaining how to use it.
	 * 
	 * 
	 * @param args	(Currently unused)
	 */
	public static void main(String[] args) {
		NodeMaster NM;
		
		try {
			// Build our server list out of the .txt file
			File file = new File( SysValues.serverfilename );
			FileInputStream fis = new FileInputStream(file);

			StringBuilder sb = new StringBuilder();
			int content;
			while ((content = fis.read()) != -1){
				sb.append( (char)content ); 
			} 

			fis.close();

			JSONObject j = new JSONObject(sb.toString());
			System.out.println("Main> Server List: " + j.toString());

			NM = new NodeMaster( j );
			
			Thread clientServer = new Thread( new ServerRunnable( NM, SysValues.port, SysValues.maxclientconnections ) );
			clientServer.start(); // One server to accept incoming client connections
			
			Thread internalServer = new Thread( new ServerRunnable( NM, SysValues.internalport, SysValues.maxinternalconnections ) );
			internalServer.start(); // Another server to accept other node connections, i.e. internal connections

		}catch (JSONException | IOException e) {
			System.out.println("Error: Check " + SysValues.serverfilename +  ". Exiting: " + e.getLocalizedMessage());
			return;
		}

		while( SysValues.shutdown == false ){
			try {
				Thread.sleep( SysValues.fileIOtime * 1000 );
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream( SysValues.outputfilename ), "ISO-8859-1"));

				// Write node's storage data to text file for external viewing
				writer.write( NM.getStorageString() );
				writer.close();

			} catch (IOException | InterruptedException ex) {
				System.out.println("Couldn't write to file.");
			}
		}
		System.out.println("Shutdown.");

	}

}
