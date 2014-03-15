package TestClient;

import java.util.Iterator;
import java.util.Vector;

public class Initiator {
	
	public static void main (String args[]){
		
		Vector<Float> averages = new Vector<Float>();
		
		// Make some amount of threads
		Thread[] threads = new Thread[SysValues.threadcount];
		for (int i = 0; i< SysValues.threadcount; i++){
			threads[i] = new Thread ( new RunnableRequest( averages) );
		}
		
		// Start!
		long startTime = System.currentTimeMillis();
		for (int i = 0; i< SysValues.threadcount; i++){
			threads[i].start();
		}
		
		// Wait until the averages are all populated
		while ( averages.size() < SysValues.threadcount ){
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {}
		}
		
		long endTime = System.currentTimeMillis();
		
		// Sum all the partial sums
		double sum = 0;
		Iterator<Float> i = averages.iterator();
		while ( i.hasNext() ){
			sum += i.next();
		}
		
		// Find the total average
		float totalaverage = (float) (sum / SysValues.threadcount);
		
		System.out.println("Finished requests: " + (SysValues.threadcount*SysValues.tests_per_thread) + " in (millis): " + (endTime - startTime) );
		System.out.println("Total average response time across all threads (millis): " + (totalaverage) );
		System.out.println("Responses / second: " + (SysValues.threadcount*SysValues.tests_per_thread)*1000/ (endTime - startTime) ); // the 1000 to convert to seconds

	}
}
