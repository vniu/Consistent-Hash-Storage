package ca.anyx.CHStorage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

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
		ServerListsInfo serverinfo;
		long tick = 0;
		
		try {
			InetAddress iAddress = InetAddress.getLocalHost();

	        System.out.println("Host Name:" + iAddress.getHostName() );
	        System.out.println("Canonical Host Name:" + iAddress.getCanonicalHostName() );
		} catch (UnknownHostException e) {}
		
		try {
			// Build our server list out of the .txt file
			File file = new File( SysValues.SERVER_FILE_NAME );
			FileInputStream fis = new FileInputStream(file);

			StringBuilder sb = new StringBuilder();
			int content;
			while ((content = fis.read()) != -1){
				sb.append( (char)content ); 
			} 

			fis.close();

			JSONObject serverlist = new JSONObject( sb.toString() );
			System.out.println("Main> Server List: " + serverlist.toString());
			
			serverinfo = new ServerListsInfo( serverlist );
			
			NM = new NodeMaster( serverinfo );
			
			Thread clientServer = new Thread( new ExecutorServerRunnable( NM, SysValues.CLIENT_PORT, SysValues.MAX_CLIENT_CONNECTIONS ) );
			clientServer.start(); // One server to accept incoming client connections
			
			Thread internalServer = new Thread( new ExecutorServerRunnable( NM, SysValues.INTERNAL_PORT, SysValues.MAX_INTERNAL_CONNECTIONS ) );
			internalServer.start(); // Another server to accept other node connections, i.e. internal connections

			//Thread statusServer = new Thread( new ExecutorServerRunnable( NM, SysValues.STATUS_PORT, 2 ) );
			//statusServer.start(); // Another server for status updates
			// Depreciated with new gossip listener - started by the repair service
			
		}catch (JSONException | IOException e) {
			System.out.println("Error: Check " + SysValues.SERVER_FILE_NAME +  ". Exiting: " + e.getLocalizedMessage());
			return;
		}

		while( SysValues.shutdown == false ){
			try {
				Thread.sleep( SysValues.FILE_IO_TIME * 1000 );
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
						new FileOutputStream( SysValues.OUTPUT_FILE_NAME ), "ISO-8859-1"));

				// Write node's storage data to text file for external viewing
				writer.write( 	"<html>" +
								//"<meta http-equiv=\"refresh\" content=\"1\">" +
								"Version: " + SysValues.VERSION + "\n<br>"+
								"Web update tick ("+Integer.toString(SysValues.FILE_IO_TIME)+"s): "+ Long.toString(tick) + "\n<br>"+
								"Repairs ran (limit 1 per " + Integer.toString(SysValues.REPAIR_TIMER) + "s): " + Integer.toString(SysValues.repairs_ran) + 
								" Repair in progress: " + SysValues.repair_running.toString() + "\n<br>"+
								"\n\n<br><br>" +
								"Format: { \"key\":[\"value\", \"intended_location\", \"replication_level\", \"is_intended_location\", \"replica_location_0\" . . . \"replica_location_MAX\" ] . . . }" +"\n<br>"+
								"\n\n<br><br>" +
								//"Dead Servers: ("+ Integer.toString(serverinfo.dead_servers.size())+" of "+Integer.toString(serverinfo.servers.length())+" servers)"+"\n<br>" +
								//		serverinfo.dead_servers.toString() + 
								"Dead Servers: \n" +
								serverinfo.dead_data.toString(3) +
								"\n\n\n<br><br><br>" +
								"Local Key Value pairs: \n<br>" + 
								NM.my_storage.getStorageString()
								+ "</html>"
								
							);
				writer.close();
				tick++;

			} catch (IOException | InterruptedException ex) {
				System.out.println("Couldn't write to file.");
			} catch (JSONException e) {
				System.out.println("Couldn't write to file.");
			}
		}
		
		try {
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream( SysValues.OUTPUT_FILE_NAME ), "ISO-8859-1"));

			writer.write(
							"I'm dead!"
						);
			writer.close();

		} catch (IOException ex) {
			System.out.println("Couldn't write to file.");
		}
		
		
		System.out.println("Shutdown.");

	}

}
