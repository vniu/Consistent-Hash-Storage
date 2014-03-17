package TestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Vector;

public class RunnableRequest implements Runnable  {
	private Vector<Float> averagevector;
	
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
			
			if ( makeRandomRequest() ){
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

			String response = br.readLine(); // Can print or perhaps change to return the response as necessary

			System.out.println( response );
			
			TCP_socket.close();
			return true;
			
		} catch (IOException e) {
			return false; // Socket failure or timeout
		}
	}
	
}