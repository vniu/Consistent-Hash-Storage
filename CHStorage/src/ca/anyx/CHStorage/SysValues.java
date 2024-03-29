package ca.anyx.CHStorage;

public class SysValues {	//TODO: Change all these from hardcoded? Load from text file perhaps?
	public final static String VERSION = "5.1.5";
	
	public final static int CLIENT_PORT = 2324; 			
	public final static int INTERNAL_PORT = 3333; 
	public final static int STATUS_PORT = 2325; 
	
	/** Limit to n clients at a time. --note that each spawns @{value #redundancylevel} of internal node connections-- */
	public final static int MAX_CLIENT_CONNECTIONS = 40;
	
	/** Limit to n internal connections at a time.
	 *
	 *  Max internal should be equal to client*redundancylevel.
	 *  This is due to the logic that over (n) nodes, 1/(n) of the keys will map to our node. 
	 *  If each node is fully engaged, then (n) nodes will provide
	 *  (redundancylevel)/(n) clients each, and thus should be a (redundancylevel):1 ratio with the max clients of a node.
	 */
	public final static int MAX_INTERNAL_CONNECTIONS = 125;	
	
	/** Allow a socket connection to remain open for n seconds. */
	public final static int LISTEN_TIMEOUT = 5; 				
	
	/** Amount of seconds to wait before waiting for redundant responses times out */
	public final static int REDUNDANCY_TIMEOUT = 2;	
	
	/** Write file to disk every n seconds. */
	public final static int FILE_IO_TIME = 5; 				
	
	/** Store data on n servers. This includes the first server to be mapped to! So a value of 1 is "not redundant". */
	public final static int REDUNDANCY_LEVEL = 3;			
	
	/** Require M of $redundancylevel responses to match before moving ahead. */
	public final static int MOFN_REDUNDANT = 2;				
	
	/** Time in seconds that a server can be on the grace list before it is re-polled on failure to communicate via redundancy. */
	public final static int MAX_GRACE = 3;					
	
	/** The max values that can be stored on this node. */
	public final static int MAX_STORAGE = 40000;	
	
	/** How often to check the repair status of other nodes, seconds, as a maximum */
	public final static int REPAIR_TIMER = 60;
	
	public final static String SERVER_FILE_NAME = "servers.txt";
	public final static String OUTPUT_FILE_NAME = "index.html";
	
	/** Used to shut down the program. */
	public static volatile boolean shutdown = false;
	
	public static volatile int repairs_ran = 0;
	public static volatile Boolean repair_running = false;
	
	
	//Debug options
	
	/** Output info to stdout */
	public final static Boolean DEBUG = true; 	// TODO: Add log option		
	
	/** Enable or disable socket read output to stdout (lots of spam!) */
	public final static Boolean SHOW_VALUES = false;		
	
	/** INTERNAL_ONLY	will ignore Consistent hashing, store and retrieve only from local storage.
	  *	FAILOVER_ONLY	will ignore redundancy options, and connect to the next node in the sequence if the connection failed. 
	  *	REDUNDANCY 		will implement the full redundant based strategy.
	  */
	public final static StorageStrategy STORAGE_STRATEGY = StorageStrategy.REDUNDANCY;
	
	/** Immediately close a connection to a client after we have finished their request
		In this sense, they must make a new connection to issue their next request. */
	public final static Boolean FORCE_STOP = true;		
	
	public final static Boolean EXPERIMENTAL_FINALIZER = false;
	public final static Boolean EXPERIMENTAL_ANY_SUCCESS = true;
	
}
