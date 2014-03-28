package CHStorage;

public class SysValues {	//TODO: Change all these from hardcoded? Load from text file perhaps?
	
	public final static int CLIENT_PORT = 2324; 			
	public final static int INTERNAL_PORT = 3333; 
	public final static int STATUS_PORT = 2325; 
	
	/** Limit to n clients at a time. --note that each spawns @{value #redundancylevel} of internal node connections-- */
	public final static int MAX_CLIENT_CONNECTIONS = 125;
	
	/** Limit to n internal connections at a time.
	 *
	 *  Max internal should be equal to client*redundancylevel.
	 *  This is due to the logic that over (n) nodes, 1/(n) of the keys will map to our node. 
	 *  If each node is fully engaged, then (n) nodes will provide
	 *  (redundancylevel)/(n) clients each, and thus should be a (redundancylevel):1 ratio with the max clients of a node.
	 */
	public final static int MAX_INTERNAL_CONNECTIONS = 125;	
	
	/** Allow a client connection to remain open for n seconds from last message. */
	public final static int LISTEN_TIMEOUT = 5; 				
	
	/** Amount of seconds to wait before waiting for redundant responses times out */
	public final static int REDUNDANCY_TIMEOUT = 2;	
	
	/** Used to shut down the program. */
	public static volatile boolean shutdown = false;
	
	/** Write file to disk every n seconds. */
	public final static int FILE_IO_TIME = 5; 				
	
	/** Store data on n servers. This includes the first server to be mapped to! So a value of 1 is "not redundant". */
	public final static int REDUNDANCY_LEVEL = 3;			
	
	/** Require M of $redundancylevel responses to match before moving ahead. */
	public final static int MOFN_REDUNDANT = 1;				
	
	/** Time in seconds that a server can be on the grace list before it is re-polled on failure to communicate via redundancy. */
	public final static int MAX_GRACE = 3;					
	
	/** The max values that can be stored on this node. */
	public final static int MAX_STORAGE = 40000;	
	
	public final static String SERVER_FILE_NAME = "servers.txt";
	public final static String OUTPUT_FILE_NAME = "index.html";
	
	
	//Debug options
	
	/** Output info to stdout */
	public final static Boolean DEBUG = true; 	// TODO: Add log option		
	
	/** Enable or disable socket read output to stdout (lots of spam!) */
	public final static Boolean SHOW_VALUES = true;		
	
	/** Ignore Consistent hashing, store and retrieve only from local storage. */
	public final static Boolean INTERNAL_ONLY = false;
	
	/** Will ignore redundancy options, and connect to the next node in the sequence if the connection failed. */
	public final static Boolean FAILOVER_ONLY = false; 		
	
	/** Immediately close a connection to a client after we have finished their request
		In this sense, they must make a new connection to issue their next request. */
	public final static Boolean FORCE_STOP = true;			
	
}
