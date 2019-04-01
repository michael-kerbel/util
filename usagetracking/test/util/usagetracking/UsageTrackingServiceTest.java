package util.usagetracking;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

import org.junit.Test;

import gnu.trove.list.array.TLongArrayList;
import util.usagetracking.UsageTrackingService.UsageTrackingData;


public class UsageTrackingServiceTest {

   @Test
   public void testUsageTrackingDataAddAll(){
      UsageTrackingData data1 = usageTrackingData(new long[] { 2, 4, 5 }, 2, 4, 5);
      UsageTrackingData data2 = usageTrackingData(new long[] { 1, 2, 3, 4 }, 1, 2, 3, 4);
      data1.addAll(Collections.singleton(data2));
      assertArrayEquals(new long[] { 1, 2, 3, 4, 5 }, data1._keys.toArray());
      assertArrayEquals(new int[] { 1, 4, 3, 8, 5 }, data1._data.stream().mapToInt(i -> i[SimpleTrackingId.Requests.getId()]).toArray());
   }

   private UsageTrackingData usageTrackingData(long[] timestamps, int... values){
      UsageTrackingData data = new UsageTrackingData(SimpleTrackingId.UsedMemory);
      data._keys = new TLongArrayList(timestamps);
      data._data = Arrays.stream(values).mapToObj(this::buildValueArray).collect(Collectors.toList());
      return data;
   }

   private int[] buildValueArray( int v ) {
      int[] vv = new int[SimpleTrackingId.UsedMemory.getMaxId()];
      Arrays.setAll(vv, i -> v);
      return vv;
   }

}