package util.usagetracking;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;


public enum SimpleTrackingId implements TrackingId {

   UsedMemory(0, Aggregation.Max), //
   GCTime(1), // 
   FullGCTime(2), // 
   Requests(3), //
   RequestTimes(4, Aggregation.Sum), //
   ;

   private static final Map<String, TrackingId> _nameLookup          = new HashMap<String, TrackingId>();
   static {
      for ( SimpleTrackingId id : values() ) {
         _nameLookup.put(id.name(), id);
      }
   }

   static TrackingId[]                          LOOKUP;
   static {
      int maxId = UsedMemory.getMaxId();
      LOOKUP = new TrackingId[maxId + 1];

      for ( TrackingId m : values() ) {
         LOOKUP[m.getId()] = m;
      }
   }

   private final int                            _id;

   private Aggregation                          _aggregation         = Aggregation.Sum;

   private TrackingId                           _slave;

   private long                                 _lastGCTimeTotal     = 0;
   private long                                 _lastFullGCTimeTotal = 0;


   private SimpleTrackingId( int id ) {
      _id = id;
   }

   private SimpleTrackingId( int id, Aggregation aggregation ) {
      _id = id;
      _aggregation = aggregation;
   }

   private SimpleTrackingId( int id, TrackingId slave ) {
      _id = id;
      _slave = slave;
      if ( _slave.getAggregation() != Aggregation.Sum ) {
         throw new IllegalStateException(
            "A slave TrackingId must be of Aggregation.Sum, since it currently only makes sense for RequestTime TrackingId to be slaves!");
      }
   }

   @Override
   public void collectCyclicValues( int[] values ) {
      long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      values[UsedMemory.getId()] = (int)(usedMemory / (1024L * 1024L));

      long gcTimeTotal = 0;
      long fullGcTimeTotal = 0;
      for ( GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans() ) {
         long collectionTime = b.getCollectionTime();
         if ( collectionTime > 0 ) {
            gcTimeTotal += collectionTime;
            if ( b.getName().equals("PS MarkSweep") || b.getName().equals("ConcurrentMarkSweep") ) {
               fullGcTimeTotal += collectionTime;
            }
         }
      }
      if ( values[GCTime.getId()] == 0 ) {
         if ( _lastGCTimeTotal != 0 ) {
            values[GCTime.getId()] = (int)(gcTimeTotal - _lastGCTimeTotal);
         }
         _lastGCTimeTotal = gcTimeTotal;
      }
      if ( values[FullGCTime.getId()] == 0 ) {
         if ( _lastFullGCTimeTotal != 0 ) {
            values[FullGCTime.getId()] = (int)(fullGcTimeTotal - _lastFullGCTimeTotal);
         }
         _lastFullGCTimeTotal = fullGcTimeTotal;
      }
   }

   @Override
   public Aggregation getAggregation() {
      return _aggregation;
   }

   @Override
   public TrackingId getForId( int id ) {
      return id < LOOKUP.length && id >= 0 ? LOOKUP[id] : null;
   }

   @Override
   public TrackingId getForName( String name ) {
      return _nameLookup.get(name);
   }

   @Override
   public int getId() {
      return _id;
   }

   @Override
   public int getMaxId() {
      int maxId = 0;
      for ( TrackingId id : values() ) {
         maxId = Math.max(id.getId(), maxId);
      }
      return maxId;
   }

   @Override
   public TrackingId getSlave() {
      return _slave;
   }

}
