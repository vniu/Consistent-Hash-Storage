package CHStorage;

public class SysValues {
	final static int port = 2324; 			//TODO: Change all these from hardcoded?
	final static int maxconnections = 60;		// Limit to n clients at a time.
	final static int fileIOtime = 5; 			// Write file to disk every n seconds.
	final static int listentimeout = 10; 		// Allow a client connection to remain open for n seconds from last message.
	final static int redundancylevel = 3;		// Store data on n other servers.
	final static Boolean ForceStop = true;		// Immediately close a connection to a client after we have finished their request
													// 		In this sense, they must make a new connection to issue their next request.
	
	final static Boolean debug = true; 			//Output info to stdout
	final static Boolean InternalOnly = false; 	//Ignore CH

	final static String serverfilename = "servers.txt";
	final static String outputfilename = "kv.txt";
}
