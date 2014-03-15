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
			
			if ( makePutRequest() ){
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
	 * 		Does a simple put request to the defined server with the defined value.
	 * 
	 * @return	True if successful, false if not.
	 */
	static boolean makePutRequest(){
		try {
			Socket TCP_socket = new Socket( SysValues.url, SysValues.port );
			TCP_socket.setSoTimeout(10000);

			PrintWriter pw = new PrintWriter ( TCP_socket.getOutputStream(), true );
			
			pw.println("{\"put\":true, \"key\":\"" + SysValues.key + "\", value:\"" + SysValues.value + "\"}");

			BufferedReader br = new BufferedReader( new InputStreamReader( TCP_socket.getInputStream() ) );

			String response = br.readLine(); // Can print or return the response as necessary

			TCP_socket.close();
			return true;
			
		} catch (IOException e) {
			return false; // Socket failure or timeout
		}
	}
	
	
	// TODO: make a function for GET
	//			pw.println("{\"get\":true, \"key\":\"cats\"}");
				

	// TODO: make a function for REMOVE
	// 			pw.println("{\"remove\":true, \"key\":\"cats\"}");
				
	
}
