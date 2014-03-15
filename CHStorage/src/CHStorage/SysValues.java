package CHStorage;

public class SysValues {	//TODO: Change all these from hardcoded? Load from text file perhaps?
	
	public final static int port = 2324; 			
	public final static int internalport = 3333; 
	public final static int maxclientconnections = 200;		// Limit to n clients at a time. --note that each spawns $redundancylevel of internal node connections--
	public final static int maxinternalconnections = 50;	// Limit to n internal connections at a time.
	public final static int listentimeout = 5; 				// Allow a client connection to remain open for n seconds from last message.
	public static volatile boolean shutdown = false;		// Used to shut down the program.
	
	public final static int fileIOtime = 5; 				// Write file to disk every n seconds.
	
	public final static int redundancylevel = 1;			// Store data on n servers. This includes the first server to be mapped to! So a value of 1 is "not redundant".
	public final static int mofnredundant = 1;				// Require M of $redundancylevel responses to match before moving ahead.
	public final static int maxstorage = 40000;				// n max values can be stored on this node.
	
	
	public final static Boolean ForceStop = true;			// Immediately close a connection to a client after we have finished their request
															// 		In this sense, they must make a new connection to issue their next request.
	
	public final static String serverfilename = "servers.txt";
	public final static String outputfilename = "kv.txt";
	
	
	//Debug options
	public final static Boolean debug = true; 				// Output info to stdout
	public final static Boolean InternalOnly = false; 		// Ignore Consistent hashing, store and retrieve only from local storage.
	public final static Boolean FailoverOnly = false; 		// Will ignore redundancy options, and connect to the next node in the sequence if the connection failed.
}
