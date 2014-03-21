package CHStorage;

public class SysValues {	//TODO: Change all these from hardcoded? Load from text file perhaps?
	
	public final static int port = 2324; 			
	public final static int internalport = 3333; 
	public final static int statusport = 2325; 
	
	public final static int maxclientconnections = 125;		// Limit to n clients at a time. --note that each spawns $redundancylevel of internal node connections--
	public final static int maxinternalconnections = 125;	// Limit to n internal connections at a time.
															/*
															 *  Max internal should be equal to client*redundancylevel.
															 *  This is due to the logic that over (n) nodes, 1/(n) of the keys will map to our node. 
															 *  If each node is fully engaged, then (n) nodes will provide
															 *  (redundancylevel)/(n) clients each, and thus should be a (redundancylevel):1 ratio with the max clients of a node.
															 */
						
	public final static int listentimeout = 5; 				// Allow a client connection to remain open for n seconds from last message.
	public static volatile boolean shutdown = false;		// Used to shut down the program.
	
	public final static int fileIOtime = 5; 				// Write file to disk every n seconds.
	
	public final static int redundancylevel = 1;			// Store data on n servers. This includes the first server to be mapped to! So a value of 1 is "not redundant".
	public final static int mofnredundant = 1;				// Require M of $redundancylevel responses to match before moving ahead.
	public final static int maxstorage = 40000;				// n max values can be stored on this node.
	
	public final static int maxgrace = 3;					// Time in seconds that a server can be on the grace list before it is re-polled on failure to communicate via redundancy.
	
	
	public final static String serverfilename = "servers.txt";
	public final static String outputfilename = "index.html";
	
	
	//Debug options
	public final static Boolean debug = true; 				// Output info to stdout
	public final static Boolean ShowValues = false;			// Enable or disable socket read output to stdout (lots of spam)
	public final static Boolean InternalOnly = false; 		// Ignore Consistent hashing, store and retrieve only from local storage.
	public final static Boolean FailoverOnly = true; 		// Will ignore redundancy options, and connect to the next node in the sequence if the connection failed.
	public final static Boolean ForceStop = true;			// Immediately close a connection to a client after we have finished their request
															// 		In this sense, they must make a new connection to issue their next request.
	
}
