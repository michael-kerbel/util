package util.time;

/**
 * <h2>Usage example:</h2>
 *
 * <pre>
 * StopWatch interval = new StopWatch();
 * TimeUtils.sleepQuietlySeconds(62); <em>// code that needs some time</em>
 * System.out.println(interval);
 *
 * interval.reset();
 * TimeUtils.sleepQuietlySeconds(2);
 * System.out.println(interval);
 * </pre>
 *
 * <h3>Output looks like:</h3>
 * <pre>
 *  01:02.001 min
 *  2.003 s
 * </pre>
 *
 * @see org.apache.commons.lang.time.StopWatch
 * @see TimeUtils
 */
public class StopWatch {

   private long _start;

   public StopWatch() {
      reset();
   }

   /**
    * resets the start time to the current time
    */
   public void reset() {
      _start = System.currentTimeMillis();
   }

   /** returns the current interval in milliseconds */
   public long getInterval() {
      return System.currentTimeMillis() - _start;
   }

   @Override
   public String toString() {
      long milliseconds = System.currentTimeMillis() - _start;
      return TimeUtils.toHumanReadableFormat(milliseconds);
   }
}
