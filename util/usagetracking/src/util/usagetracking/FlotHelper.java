package util.usagetracking;

import gnu.trove.list.TLongList;

import java.util.List;

import util.time.TimeUtils;


public class FlotHelper {

   public static long fix( long t ) {
      return t + 2 * TimeUtils.HOUR_IN_MILLIS;
   }

   public static String toString( TLongList keys, List<int[]> data, TrackingId id ) {
      final StringBuilder s = new StringBuilder("[");
      for ( int i = 0, length = keys.size(); i < length; i++ ) {
         long t = keys.get(i);
         s.append(s.length() > 1 ? "," : "").append("[").append(fix(t)).append(",").append(data.get(i)[id.getId()]).append("]");
      }

      return s.append("]").toString();
   }

   public static String toStringAvg( TLongList keys, List<int[]> data, TrackingId sum, TrackingId n ) {
      final StringBuilder s = new StringBuilder("[");
      for ( int i = 0, length = keys.size(); i < length; i++ ) {
         long t = keys.get(i);
         int a = data.get(i)[sum.getId()];
         int b = data.get(i)[n.getId()];
         int c = Math.round(a / (float)b);
         if ( b == 0 ) c = 0;
         s.append(s.length() > 1 ? "," : "").append("[").append(fix(t)).append(",").append(c).append("]");
      }

      return s.append("]").toString();
   }
}
