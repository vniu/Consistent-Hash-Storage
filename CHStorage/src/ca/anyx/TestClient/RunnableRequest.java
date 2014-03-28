package ca.anyx.TestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Vector;

public class RunnableRequest implements Runnable  {
	private Vector<Float> averagevector;
	int reqnum = 0;
	
	/**
	 * 		Constructor
	 * 
	 * @param _averagevector 	The common vector to place the resulting average into.
	 */
	RunnableRequest ( Vector<Float> _averagevector ){
		this.averagevector = _averagevector;
	}

	@Override
	public void run() {
		double sum = 0;
		
		for (int i = 0; i < SysValues.tests_per_thread; i++){
			
			// For each test, take initial time, then make the request
			long startTime = System.currentTimeMillis();
			
			if ( makeSequentialRequest( i ) 
			//if ( makeRequest( "{\"put\":true,    \"key\":\"" + SysValues.key + "\", value:\"" + SysValues.value + "\"}" )
			//if ( makeRandomRequest() 
				){
			// If the request was successful, add to the sum the amount of time it took for us to get our response
				long endTime = System.currentTimeMillis();
				sum = sum + (endTime - startTime);
			}else{
				sum = sum + 10000;  // If it was a failure, lets say it took 10 seconds, the max time allowed
									// TODO: Check the failure code
			}
		}
		
		// Average all of the tests in this thread
		float average = (float) (sum / SysValues.tests_per_thread);
		
		System.out.println("Average response time (millis): " + average);
		
		// Add the average to the vector
		averagevector.add(average);
	}
	
	
	/**
	 * 		Will cycle put get and remove depending on last call.
	 * 		Appends thread id to the key, to ensure other threads don't use the same key.
	 * 
	 * @return			True if successful, false if not.
	 */
	boolean makeSequentialRequest( int i ){
		
		switch( reqnum ){
		case 0:
			reqnum ++;
			if (reqnum > 2) reqnum = 0;
			return makeRequest( "{\"put\":true,    \"key\":\"" + SysValues.key + Integer.toString(i) + Thread.currentThread().getId() + "\", \"value\":\"" + SysValues.value + "\"}" );
		case 1:
			reqnum ++;
			if (reqnum > 2) reqnum = 0;
			return makeRequest( "{\"get\":true,    \"key\":\"" + SysValues.key + Integer.toString(i-1) + Thread.currentThread().getId() + "\"}" );
		default:
			reqnum ++;
			if (reqnum > 2) reqnum = 0;
			return makeRequest( "{\"remove\":true, \"key\":\"" + SysValues.key + Integer.toString(i-2) + Thread.currentThread().getId() + "\"}" );
		}		
	}
	
	/**
	 * 	A random request
	 * 
	 * @return			True if successful, false if not.
	 */
	boolean makeRandomRequest(){
		int req = (int) Math.floor( Math.random() * 3 );
		
		switch (req){
		case 0:
			//System.out.println("Making put");
			return makeRequest( "{\"put\":true,    \"key\":\"" + SysValues.key + "\", value:\"" + SysValues.value + "\"}" );
		case 1:
			//System.out.println("Making get");
			return makeRequest( "{\"get\":true,    \"key\":\"" + SysValues.key + "\"}" );
		default:
			//System.out.println("Making remove");
			return makeRequest( "{\"remove\":true, \"key\":\"" + SysValues.key + "\"}" );
		}
	}
	
	
	/**
	 * 		Does a simple request to the defined server with the defined string request.
	 * 
	 * @param request 	The JSON request.
	 * @return			True if successful, false if not.
	 */
	static boolean makeRequest( String request ){
		try {
			Socket TCP_socket = new Socket( SysValues.url, SysValues.port );
			TCP_socket.setSoTimeout(10000);

			PrintWriter pw = new PrintWriter ( TCP_socket.getOutputStream(), true );
			
			pw.println( request );

			BufferedReader br = new BufferedReader( new InputStreamReader( TCP_socket.getInputStream() ) );

			String response = 
			br.readLine(); // Can print or perhaps change to return the response as necessary

			System.out.println( response );
			
			TCP_socket.close();
			return true;
			
		} catch (IOException e) {
			System.out.println( "Failure! " + e.getLocalizedMessage() );
			return false; // Socket failure or timeout
		}
	}
	
}