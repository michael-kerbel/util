package util.usagetracking;

import gnu.trove.list.TLongList;

import java.util.List;
import java.util.TimeZone;


public class FlotHelper {

   public static long offset( long t ) {
      return TimeZone.getDefault().getOffset(t);
   }

   public static String toString( TLongList keys, List<int[]> data, TrackingId id ) {
      long timeOffset = offset(keys.size() == 0 ? 0 : keys.get(0));
      final StringBuilder s = new StringBuilder("[");
      for ( int i = 0, length = keys.size(); i < length; i++ ) {
         long t = keys.get(i);
         int v = data.get(i)[id.getId()];
         long value = v < 0 ? v & 0xFFFFFFFFL : v;
         s.append(s.length() > 1 ? "," : "").append("[").append(t + timeOffset).append(",").append(value).append("]");
      }

      return s.append("]").toString();
   }

   public static String toStringAvg( TLongList keys, List<int[]> data, TrackingId sum, TrackingId n ) {
      long timeOffset = offset(keys.size() == 0 ? 0 : keys.get(0));
      final StringBuilder s = new StringBuilder("[");
      for ( int i = 0, length = keys.size(); i < length; i++ ) {
         long t = keys.get(i);
         int a = data.get(i)[sum.getId()];
         int b = data.get(i)[n.getId()];
         int c = Math.round(a / (float)b);
         if ( b == 0 ) {
            c = 0;
         }
         s.append(s.length() > 1 ? "," : "").append("[").append(t + timeOffset).append(",").append(c).append("]");
      }

      return s.append("]").toString();
   }
}
