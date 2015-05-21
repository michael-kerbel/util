package util.usagetracking;

import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.dump.Dump;
import util.dump.DumpUtils;
import util.dump.ExternalizableBean;
import util.time.TimeUtils;


public class UsageTrackingService {

   private static Logger                                        _log      = LoggerFactory.getLogger(UsageTrackingService.class);
   private static final DateFormat                              DAY       = new SimpleDateFormat("yyyy-MM-dd");

   private static UsageTrackingService                          INSTANCE;

   private static boolean                                       TRACKDATA = false;
   private static int                                           MAX_ID;

   /* if multiple Actions are executed in a Request, all of them get the same request time - all Actions executed during a single Request are collected here */
   private static ThreadLocal<EnumSet<? extends Enum>>          SAME_TIMEMEASUREMENT_GROUP;
   private static ThreadLocal<EnumMap<? extends Enum, Long>>    PENDING_TIME_MEASUREMENTS;
   private static ThreadLocal<EnumMap<? extends Enum, Integer>> PENDING_MEASUREMENTS;


   /** Registers a TrackingId for later measurement, finished by a call to <code>finishGroupTimeMeasurements(.)</code> */
   public static void addForGroupTimeMeasurement( TrackingId id ) {
      UsageTrackingService instance = getInstance();
      if ( instance != null && id != null && id.getClass().equals(instance._exampleTrackingIdInstance.getClass()) ) {
         ((Set)SAME_TIMEMEASUREMENT_GROUP.get()).add(id);
      }
   }

   /** Adds the value for the TrackingId immediately, unless it has a slave. In that case it is added
    * only on the mandatory call to <code>finishSlaveTimeMeasurement(sameId)</code>, which also adds 
    * the time measurement for the slave id. */
   public static void addMeasurement( TrackingId id, int value ) {
      UsageTrackingService instance = getInstance();
      if ( instance != null && id != null && id.getClass().equals(instance._exampleTrackingIdInstance.getClass()) ) {
         if ( id.getSlave() != null ) {
            ((Map)PENDING_MEASUREMENTS.get()).put(id, value);
            ((Map)PENDING_TIME_MEASUREMENTS.get()).put(id.getSlave(), System.currentTimeMillis());
         } else {
            instance.add(id, value);
         }
      }
   }

   /**
    * Adds +1 to all TrackingIds previously registered with addForGroupTimeMeasurement(.) if they have a
    * slave, for which a measurement with <code>t</code> is added, too. If the registered TrackingId has
    * no slave, the time is added directly to the id.  
    * @param t the time to be added 
    */
   public static void finishGroupTimeMeasurements( int t ) {
      UsageTrackingService instance = getInstance();
      if ( instance != null ) {
         for ( TrackingId id : (Set<? extends TrackingId>)SAME_TIMEMEASUREMENT_GROUP.get() ) {
            if ( id.getSlave() != null ) {
               instance.add(id, 1);
               instance.add(id.getSlave(), Math.max(t, 0));
            } else {
               instance.add(id, Math.max(t, 0));
            }
         }
      }
      SAME_TIMEMEASUREMENT_GROUP.get().clear();
   }

   /** A call to this method is mandatory, in order to finalize the addMeasurement for any TrackingId which 
    * has a slave! */
   public static void finishSlaveTimeMeasurement( TrackingId id ) {
      UsageTrackingService instance = getInstance();
      if ( instance != null && id != null && id.getClass().equals(instance._exampleTrackingIdInstance.getClass()) ) {
         Integer v = PENDING_MEASUREMENTS.get().remove(id);
         if ( v != null ) {
            instance.add(id, v);
         }
         if ( id.getSlave() != null ) {
            id = id.getSlave();
         }
         Long t = PENDING_TIME_MEASUREMENTS.get().remove(id);
         if ( t != null ) {
            instance.add(id, Math.max((int)(System.currentTimeMillis() - t), 0));
         }
      }
   }

   public static UsageTrackingService getInstance() {
      if ( TRACKDATA ) {
         return INSTANCE;
      }
      return null;
   }

   public static UsageTrackingData getUsageTrackingData( List<StatData> d ) {
      if ( INSTANCE != null ) {
         UsageTrackingData ret = INSTANCE.createUsageTrackingData();
         ret._keys = new TLongArrayList();
         ret._data = new ArrayList<int[]>();
         for ( StatData dd : d ) {
            if ( dd._data != null ) {
               ret._keys.add(dd._t);
               ret._data.add(ensureSize(dd._data));
            }
         }
         return ret;
      }
      return null;
   }

   /**
     * Turn usage data tracking on or off. Default state is off!
     */
   public static void setTrackData( boolean trackData ) {
      TRACKDATA = trackData;
   }

   private static long[] addToArray( long[] array, long element ) {
      long[] newArray = new long[array.length + 1];
      System.arraycopy(array, 0, newArray, 0, array.length);
      newArray[newArray.length - 1] = element;
      return newArray;
   }

   private static int[] ensureSize( int[] data ) {
      if ( data.length < MAX_ID + 1 ) {
         int[] d = new int[MAX_ID + 1];
         System.arraycopy(data, 0, d, 0, data.length);
         data = d;
      }
      return data;
   }


   private TrackingId              _exampleTrackingIdInstance;

   private long                    _timeResolution    = 1 * TimeUtils.MINUTE_IN_MILLIS;

   private String                  _dumpFolder;

   private TLongList               _keys              = new TLongArrayList();

   private List<int[]>             _data              = new ArrayList<int[]>();

   private TIntObjectMap<TIntList> _percentileData    = new TIntObjectHashMap<TIntList>();

   private long[]                  _startupTimestamps = new long[0];

   private DataCollectionThread    _dataCollectionThread;

   private DumpWriteThread         _dumpWriteThread;

   private boolean                 _destroyed;

   private int                     _dayOfMonth        = -1;

   private Thread                  _shutdownThread    = new Thread() {

                                                         @Override
                                                         public void run() {
                                                            try {
                                                               _dumpWriteThread.writeNewStats(_keys.size());
                                                            }
                                                            catch ( IOException argh ) {
                                                               _log.error("Failed to write stats to dump", argh);
                                                            }
                                                         }
                                                      };


   public void add( TrackingId id, int value ) {
      long t = System.currentTimeMillis();
      int[] values = getOrCreateValues(t);
      switch ( id.getAggregation() ) {
      case Max:
         values[id.getId()] = Math.max(values[id.getId()], value);
         break;
      case Sum:
         values[id.getId()] += value;
         break;
      case Percentile90:
      case Percentile99:
         TIntList percentileValues = _percentileData.get(id.getId());
         if ( percentileValues == null ) {
            percentileValues = new TIntArrayList();
            _percentileData.put(id.getId(), percentileValues);
         }
         synchronized ( percentileValues ) {
            percentileValues.add(value);
         }
         break;
      }
   }

   public UsageTrackingData createUsageTrackingData() {
      return new UsageTrackingData(_exampleTrackingIdInstance);
   }

   public void destroy() {
      _destroyed = true;
      _dataCollectionThread.interrupt();
      _dumpWriteThread.interrupt();
      try {
         Runtime.getRuntime().removeShutdownHook(_shutdownThread);
      }
      catch ( IllegalStateException argh ) {
         _log.debug("JVM shutdown in progress", argh);
      }
   }

   /** Data structure for collected data: the data values are int arrays which belong to a timestamp key, provided by {@link #getKeys()}.
    * The indices of the int array correspond to the constants defined in UsageTracking. */
   public List<int[]> getData() {
      return _data;
   }

   public String getDumpFileName( long t ) {
      return "stats-" + getDay(t) + ".dmp";
   }

   public List<String> getDumpFileNames() {
      File[] files = new File(_dumpFolder).listFiles(new FilenameFilter() {

         @Override
         public boolean accept( File dir, String name ) {
            return name.startsWith("stats-") && name.endsWith(".dmp");
         }
      });
      List<String> filenames = new ArrayList<String>();
      for ( File f : files ) {
         filenames.add(f.getName());
      }
      Collections.sort(filenames, Collections.reverseOrder());
      return filenames;
   }

   /** Data structure for collected data: the keys contain timestamps rounded by {@link #round(long)}, in ascending order. 
    * The corresponding values for each timestamp are to be found in the List from {@link #getData()}, using the same index. */
   public TLongList getKeys() {
      return _keys;
   }

   public long[] getStartupTimestamps() {
      return _startupTimestamps;
   }

   public void init() {
      INSTANCE = this;

      // read today's data from dump
      List<StatData> data = readFromDump(getDumpFileName(System.currentTimeMillis()));
      UsageTrackingData utd = getUsageTrackingData(data);
      _keys = utd._keys;
      _data = utd._data;

      _startupTimestamps = getStartupTimestamps(data);
      _startupTimestamps = addToArray(_startupTimestamps, System.currentTimeMillis());
      addStartupMarkerToDump();

      _dumpWriteThread = new DumpWriteThread();
      if ( _keys.size() > 0 ) {
         _dumpWriteThread._lastWrittenT = _keys.get(_keys.size() - 1);
      }
      _dumpWriteThread.start();
      _dataCollectionThread = new DataCollectionThread();
      _dataCollectionThread.start();

      _dayOfMonth = TimeUtils.getCalendarFieldValue(new Date(), Calendar.DAY_OF_MONTH);

      Runtime.getRuntime().addShutdownHook(_shutdownThread);
   }

   public List<StatData> readFromDump( String filename ) {
      List<StatData> data = new ArrayList<StatData>();
      File dumpFile = new File(_dumpFolder, filename);
      Dump<StatData> dump = null;
      try {
         dump = new Dump<StatData>(StatData.class, dumpFile, Dump.SHARED_MODE);
         for ( StatData d : dump ) {
            data.add(d);
         }
         return data;
      }
      finally {
         DumpUtils.closeSilently(dump);
      }
   }

   public long round( long t ) {
      return (t / _timeResolution) * _timeResolution;
   }

   public void setDumpFolder( String dumpFolder ) {
      _dumpFolder = dumpFolder;
      new File(_dumpFolder).mkdirs();
   }

   public void setExampleTrackingIdInstance( TrackingId exampleTrackingIdInstance ) {
      _exampleTrackingIdInstance = exampleTrackingIdInstance;
      MAX_ID = _exampleTrackingIdInstance.getMaxId();

      SAME_TIMEMEASUREMENT_GROUP = new ThreadLocal<EnumSet<? extends Enum>>() {

         @Override
         protected EnumSet<? extends Enum> initialValue() {
            return EnumSet.noneOf((Class<? extends Enum>)_exampleTrackingIdInstance.getClass());
         }
      };
      PENDING_TIME_MEASUREMENTS = new ThreadLocal<EnumMap<? extends Enum, Long>>() {

         @Override
         protected EnumMap<? extends Enum, Long> initialValue() {
            return new EnumMap(_exampleTrackingIdInstance.getClass());
         }
      };
      PENDING_MEASUREMENTS = new ThreadLocal<EnumMap<? extends Enum, Integer>>() {

         @Override
         protected EnumMap<? extends Enum, Integer> initialValue() {
            return new EnumMap(_exampleTrackingIdInstance.getClass());
         }
      };
   }

   String getDay( long t ) {
      synchronized ( DAY ) {
         return DAY.format(new Date(t));
      }
   }

   int[] getOrCreateValues( long t ) {
      t = round(t);
      int size = _keys.size();
      if ( size != 0 ) {
         long currentMaxT = _keys.get(size - 1);
         if ( currentMaxT == t ) {
            return _data.get(size - 1);
         }
         if ( currentMaxT > t ) {
            // a latecomer
            for ( int i = size - 2; i >= 0; i-- ) {
               if ( _keys.get(i) == t ) {
                  return _data.get(i);
               }
            }
            _log.error("Failed to find values for latecomer usage tracking. latecomer's t=" + t + ", current t=" + currentMaxT);
            return new int[MAX_ID + 1]; // fake a return value
         }
      }

      // rare case, only once per minute: add element to key/data, aggregate percentiles, collect   
      synchronized ( _keys ) {
         int ssize = _keys.size();
         if ( ssize == size ) {
            int[] data = new int[MAX_ID + 1];
            _data.add(data);
            _keys.add(t);

            if ( size > 0 ) {
               calcPercentiles(_data.get(size - 1));
            }

            int dayOfMonth = TimeUtils.getCalendarFieldValue(new Date(), Calendar.DAY_OF_MONTH);
            if ( dayOfMonth != _dayOfMonth ) {
               _dayOfMonth = dayOfMonth;
               clearOldData();
            }

            synchronized ( _dataCollectionThread ) {
               _dataCollectionThread.notifyAll();
            }
            synchronized ( _dumpWriteThread ) {
               _dumpWriteThread.notifyAll();
            }
            return data;
         }

         return getOrCreateValues(t);
      }
   }

   private void addStartupMarkerToDump() {
      try {
         StatData startupMarker = new StatData();
         startupMarker._t = System.currentTimeMillis();
         addToDump(startupMarker);
      }
      catch ( Exception argh ) {
         _log.error("Failed to store stat data to dump", argh);
      }
   }

   private void addToDump( StatData data ) throws IOException {
      File dumpFile = new File(_dumpFolder, getDumpFileName(data._t));
      Dump<StatData> dump = null;
      try {
         dump = new Dump<StatData>(StatData.class, dumpFile);
         dump.setWillBeClosedDuringShutdown(true);
         dump.add(data);
      }
      finally {
         DumpUtils.closeSilently(dump);
      }
   }

   private void calcPercentiles( final int[] data ) {
      _percentileData.forEachEntry(new TIntObjectProcedure<TIntList>() {

         @Override
         public boolean execute( int id, TIntList percentileData ) {
            if ( percentileData.size() == 0 ) {
               return false;
            }

            TrackingId trackingId = _exampleTrackingIdInstance.getForId(id);
            Aggregation aggregation = trackingId.getAggregation();

            percentileData.sort();

            int index = 0;
            if ( aggregation == Aggregation.Percentile90 ) {
               index = (int)(percentileData.size() * 0.9);
            } else if ( aggregation == Aggregation.Percentile99 ) {
               index = (int)(percentileData.size() * 0.99);
            }
            data[id] = percentileData.get(index);

            percentileData.clear();

            return true;
         }
      });
   }

   private void clearOldData() {
      for ( int i = _keys.size() - 1; i >= 0; i-- ) {
         long t = _keys.get(i);
         int dayOfMonth = TimeUtils.getCalendarFieldValue(new Date(t), Calendar.DAY_OF_MONTH);
         if ( dayOfMonth != _dayOfMonth ) {
            TLongList keys = new TLongArrayList();
            List<int[]> data = new ArrayList<int[]>();
            for ( int j = i + 1, length = _keys.size(); j < length; j++ ) {
               keys.add(_keys.get(j));
               data.add(_data.get(j));
            }
            _keys = keys;
            _data = data;

            break;
         }
      }
   }

   private long[] getStartupTimestamps( List<StatData> data ) {
      TLongArrayList timestamps = new TLongArrayList();
      for ( StatData dd : data ) {
         if ( dd._data == null ) {
            timestamps.add(dd._t);
         }
      }
      return timestamps.toArray();
   }


   public static class StatData extends ExternalizableBean {

      private static final long serialVersionUID = -1816997029156670474L;

      @externalize(1)
      long                      _t;

      @externalize(2)
      int[]                     _data;


      public StatData() {}
   }

   public static class UsageTrackingData implements Serializable {

      public TLongList     _keys;
      public List<int[]>   _data;

      transient TrackingId _exampleTrackingIdInstance;


      UsageTrackingData( TrackingId exampleTrackingIdInstance ) {
         _exampleTrackingIdInstance = exampleTrackingIdInstance;
      }

      public void addAll( TLongList otherKeys, List<int[]> otherData ) {
         int otherI = 0;
         for ( int myI = 0, myLength = _keys.size(), otherLength = otherKeys.size(); myI < myLength; myI++ ) {
            while ( otherI < otherLength && otherKeys.get(otherI) < _keys.get(myI) ) {
               _keys.insert(myI, otherKeys.get(otherI));
               _data.add(myI, otherData.get(otherI));
               myI++;
               myLength++;
               otherI++;
            }
            if ( otherI >= otherLength || otherKeys.get(otherI) != _keys.get(myI) ) {
               continue;
            }
            addAll(_data.get(myI), otherData.get(otherI));
            otherI++;
         }
      }

      public void addAll( UsageTrackingData utd ) {
         addAll(utd._keys, utd._data);
      }

      private void addAll( int[] target, int[] source ) {
         for ( int i = 0, length = Math.min(target.length, source.length); i < length; i++ ) {
            TrackingId id = _exampleTrackingIdInstance.getForId(i);
            if ( id == null ) {
               continue;
            }
            switch ( id.getAggregation() ) {
            case Max:
               target[i] = Math.max(target[i], source[i]);
               break;
            case Sum:
               target[i] += source[i];
               break;
            case Percentile90:
            case Percentile99:
               target[i] = Math.round((target[i] + source[i]) / 2.0f);
            }
         }
      }
   }

   private class DataCollectionThread extends Thread {

      public DataCollectionThread() {
         setName("Stats-DataCollectionThread");
         setDaemon(true);
      }

      @Override
      public void run() {
         while ( !_destroyed ) {
            if ( TRACKDATA ) {
               try {
                  long t = System.currentTimeMillis();

                  int[] values = getOrCreateValues(t);

                  _exampleTrackingIdInstance.collectCyclicValues(values);
               }
               catch ( Error argh ) {
                  _log.error("Failed to collect stats data", argh);
               }
            }
            try {
               synchronized ( this ) {
                  this.wait(_timeResolution / 2);
               }
            }
            catch ( InterruptedException argh ) {
               Thread.interrupted(); // reset interrupted state
            }
         }
         _log.info("UsageTrackingService.DataCollectionThread terminated");
      }

   }

   private class DumpWriteThread extends Thread {

      long _lastWrittenT = 0;


      public DumpWriteThread() {
         setName(UsageTrackingService.class.getSimpleName() + "-" + getClass().getSimpleName());
         setDaemon(true);
      }

      @Override
      public void run() {
         while ( !_destroyed ) {

            try {
               synchronized ( this ) {
                  this.wait();
               }
            }
            catch ( InterruptedException argh ) {
               Thread.interrupted(); // reset interrupted state
            }

            try {
               if ( _lastWrittenT == 0 ) {
                  _lastWrittenT = getMaxTFromDump();
               }
               // check for all keys if they are already written
               writeNewStats(_keys.size() - 2);
            }
            catch ( Exception argh ) {
               _log.error("Failed to store stat data to dump", argh);
            }
         }
         _log.info("UsageTrackingService.DumpWriteThread terminated");
      }

      private long getMaxTFromDump() throws IOException {
         long lastWrittenT = 0;
         File dumpFile = new File(_dumpFolder, getDay(System.currentTimeMillis()));
         if ( dumpFile.exists() ) {
            Dump<StatData> dump = null;
            try {
               dump = new Dump<StatData>(StatData.class, dumpFile, Dump.SHARED_MODE);
               for ( StatData data : dump ) {
                  lastWrittenT = data._t;
               }
            }
            catch ( Exception argh ) {
               _log.error("Failed to read max t from stat dump " + dumpFile, argh);
            }
            finally {
               DumpUtils.closeSilently(dump);
            }
         }
         return lastWrittenT;
      }

      /**
       * @param toIndex exclusive!
       */
      private void writeNewStats( int toIndex ) throws IOException {
         for ( int i = 0; i < toIndex; i++ ) {
            if ( _lastWrittenT < _keys.get(i) ) {
               StatData data = new StatData();
               data._t = _keys.get(i);
               data._data = _data.get(i);

               addToDump(data);

               _lastWrittenT = data._t;
            }
         }
      }
   }

}
