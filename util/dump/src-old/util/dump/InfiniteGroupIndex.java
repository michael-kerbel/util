package util.dump;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map.Entry;

import util.dump.Dump.PositionIteratorCallback;
import util.dump.sort.InfiniteSorter;
import util.reflection.FieldAccessor;
import bak.pcj.LongIterator;
import bak.pcj.list.LongArrayList;
import bak.pcj.list.LongList;
import bak.pcj.map.IntKeyMapIterator;
import bak.pcj.map.LongKeyMapIterator;


/**
 * <b>Beware</b>: Your key instances <i>must</i> implement <nobr><code>hashCode()</code></nobr> and 
 * <nobr><code>equals()</code></nobr>, if you use a custom key instance (i.e. not <code>int</code>, <code>long</code>, 
 * {@link String}, any {@link Number}, ...)<p/>
 */
public class InfiniteGroupIndex<E> extends DumpIndex<E> implements NonUniqueIndex<E> {

   private static final int                DEFAULT_MAX_LOOKUP_SIZE_IN_MEMORY = 25000;
   protected static final byte[]           EMPTY_BYTES                       = new byte[0];

   private MyGroupIndex                    _groupIndex;

   private final int                       _maxLookupSizeInMemory;

   private File                            _objectKeyDumpFile;
   private int                             _currentLookupSize;
   private Dump<IntKeyPosition>            _intKeyDump;
   private Dump<LongKeyPosition>           _longKeyDump;
   private Dump<StringKeyPosition>         _stringKeyDump;
   private Dump<ExternalizableKeyPosition> _externalizableKeyDump;

   private long                            _lookupFileLength;

   public InfiniteGroupIndex( Dump dump, FieldAccessor fieldAccessor, int maxLookupSizeInMemory ) {
      super(dump, fieldAccessor);
      _maxLookupSizeInMemory = maxLookupSizeInMemory;
      init();
      isFieldCompatible();
   }

   public InfiniteGroupIndex( Dump dump, String fieldName ) throws NoSuchFieldException {
      this(dump, fieldName, DEFAULT_MAX_LOOKUP_SIZE_IN_MEMORY);
   }

   public InfiniteGroupIndex( Dump dump, String fieldName, int maxLookupSizeInMemory ) throws NoSuchFieldException {
      super(dump, fieldName);
      _maxLookupSizeInMemory = maxLookupSizeInMemory;
      init();
      isFieldCompatible();
   }

   @Override
   public void add( E o, long pos ) {
      _groupIndex.superAdd(o, pos);
      _currentLookupSize++;
      if ( _currentLookupSize > _maxLookupSizeInMemory ) {
         mergeOverflowIntoIndex();
         _currentLookupSize = 0;
      }
   }

   @Override
   public void close() throws IOException {
      super.close();
      if ( _groupIndex != null ) _groupIndex.close();
      if ( _intKeyDump != null ) _intKeyDump.close();
      if ( _longKeyDump != null ) _longKeyDump.close();
      if ( _stringKeyDump != null ) _stringKeyDump.close();
      if ( _externalizableKeyDump != null ) _externalizableKeyDump.close();
   }

   @Override
   public boolean contains( int key ) {
      return getPositions(key).length > 0;
   }

   @Override
   public boolean contains( long key ) {
      return getPositions(key).length > 0;
   }

   @Override
   public boolean contains( Object key ) {
      return getPositions(key).length > 0;
   }

   @Override
   public long[] getAllPositions() {
      LongList pos = new LongArrayList(100000, 10000);
      if ( _fieldIsInt ) {
         for ( IntKeyPosition ip : _intKeyDump ) {
            if ( !_dump._deletedPositions.contains(ip._pos) ) pos.add(ip._pos);
         }
      } else if ( _fieldIsLong ) {
         for ( LongKeyPosition ip : _longKeyDump ) {
            if ( !_dump._deletedPositions.contains(ip._pos) ) pos.add(ip._pos);
         }
      } else if ( _fieldIsExternalizable ) {
         for ( ExternalizableKeyPosition kp : _externalizableKeyDump ) {
            if ( !_dump._deletedPositions.contains(kp._pos) ) pos.add(kp._pos);
         }
      } else if ( _fieldIsString ) {
         for ( StringKeyPosition kp : _stringKeyDump ) {
            if ( !_dump._deletedPositions.contains(kp._pos) ) pos.add(kp._pos);
         }
      }
      return pos.toArray();
   }

   public synchronized Iterable<E> lookup( int key ) {
      long[] pos = getPositions(key);
      return new GroupIterable(pos);
   }

   public synchronized Iterable<E> lookup( long key ) {
      long[] pos = getPositions(key);
      return new GroupIterable(pos);
   }

   public synchronized Iterable<E> lookup( Object key ) {
      long[] pos = getPositions(key);
      return new GroupIterable(pos);
   }

   @Override
   protected void deleteAllIndexFiles() {
      final String indexPrefix = _lookupFile.getName().replaceAll("lookup$", "");
      File dir = _dump.getDumpFile().getAbsoluteFile().getParentFile();
      File[] indexFiles = dir.listFiles(new FilenameFilter() {

         public boolean accept( File dir, String name ) {
            return name.startsWith(indexPrefix) && !name.contains(".overflow");
         }
      });
      for ( File f : indexFiles ) {
         if ( !f.delete() ) {
            System.err.println("Failed to delete invalid index file " + f);
         }
      }
   }

   @Override
   protected String getIndexType() {
      return InfiniteGroupIndex.class.getSimpleName();
   }

   protected long[] getPositions( int key ) {
      if ( !(_fieldIsInt || _fieldIsExternalizable || _fieldIsString) )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      LongList pos = new LongArrayList(_groupIndex.getPositions(key));

      int keyLength = 4 + 8; // in bytes

      int firstIndex = findIntKey(key, keyLength);
      if ( firstIndex >= 0 ) {
         for ( long p = firstIndex * keyLength; p < _lookupFileLength; p += keyLength ) {
            IntKeyPosition ip = _intKeyDump.get(p);
            if ( ip._key != key ) break;
            if ( ip._pos >= 0 && !_dump._deletedPositions.contains(ip._pos) ) pos.add(ip._pos);
         }
      }

      return pos.toArray();
   }

   protected long[] getPositions( long key ) {
      if ( !_fieldIsLong )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      LongList pos = new LongArrayList(_groupIndex.getPositions(key));

      int keyLength = 8 + 8; // in bytes

      int firstIndex = findLongKey(key, keyLength);
      if ( firstIndex >= 0 ) {
         for ( long p = firstIndex * keyLength; p < _lookupFileLength; p += keyLength ) {
            LongKeyPosition ip = _longKeyDump.get(p);
            if ( ip._key != key ) break;
            if ( ip._pos >= 0 && !_dump._deletedPositions.contains(ip._pos) ) pos.add(ip._pos);
         }
      }

      return pos.toArray();
   }

   protected long[] getPositions( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) return getPositions(((Long)key).longValue());
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) return getPositions(((Integer)key).intValue());
      if ( _fieldIsLong || _fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      if ( (_fieldIsExternalizable && !(key instanceof Externalizable)) || (_fieldIsString && !(key instanceof String)) )
         throw new IllegalArgumentException("Incompatible key type. The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". You tried to using the index with a key of type " + key.getClass() + ".");

      LongList keyPositions = getObjectKeyPositions(key);

      LongList positions = new LongArrayList(_groupIndex.getPositions(key));
      for ( LongIterator iterator = keyPositions.iterator(); iterator.hasNext(); ) {
         long pos = iterator.next();
         if ( _fieldIsExternalizable ) {
            ExternalizableKeyPosition keyPosition = _externalizableKeyDump.get(pos);
            long kp = keyPosition._pos;
            if ( kp >= 0 && keyPosition._key.equals(key) && !_dump._deletedPositions.contains(kp) ) {
               positions.add(kp);
            }
         } else if ( _fieldIsString ) {
            StringKeyPosition keyPosition = _stringKeyDump.get(pos);
            long kp = keyPosition._pos;
            if ( kp >= 0 && keyPosition._key.equals(key) && !_dump._deletedPositions.contains(kp) ) {
               positions.add(kp);
            }
         }
      }

      return positions.toArray();
   }

   @Override
   protected void initFromDump() {

      try {
         if ( _fieldIsInt ) {
            final InfiniteSorter<IntKeyPosition> sorter = new InfiniteSorter<IntKeyPosition>(_maxLookupSizeInMemory, _dump.getDumpFile().getParentFile());
            _dump.iterateElementPositions(new PositionIteratorCallback() {

               @Override
               public void element( Object o, long pos ) {
                  try {
                     sorter.add(new IntKeyPosition(getIntKey((E)o), pos));
                  }
                  catch ( IOException argh ) {
                     throw new RuntimeException("Failed to sort InfiniteGroupIndex on disk", argh);
                  }
               }
            });
            _intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, _lookupFile);
            _intKeyDump.addAll(sorter);
            _intKeyDump._outputStream.flush();
            _lookupFileLength = _lookupFile.length();
         } else if ( _fieldIsLong ) {
            final InfiniteSorter<LongKeyPosition> sorter = new InfiniteSorter<LongKeyPosition>(_maxLookupSizeInMemory, _dump.getDumpFile().getParentFile());
            _dump.iterateElementPositions(new PositionIteratorCallback() {

               @Override
               public void element( Object o, long pos ) {
                  try {
                     sorter.add(new LongKeyPosition(getLongKey((E)o), pos));
                  }
                  catch ( IOException argh ) {
                     throw new RuntimeException("Failed to sort InfiniteGroupIndex on disk", argh);
                  }
               }
            });
            _longKeyDump = new Dump<LongKeyPosition>(LongKeyPosition.class, _lookupFile);
            _longKeyDump.addAll(sorter);
            _longKeyDump._outputStream.flush();
            _lookupFileLength = _lookupFile.length();
         } else if ( _fieldIsString || _fieldIsExternalizable ) {
            if ( _fieldIsExternalizable ) {
               _externalizableKeyDump = new Dump<ExternalizableKeyPosition>(ExternalizableKeyPosition.class, _objectKeyDumpFile);
            } else if ( _fieldIsString ) {
               _stringKeyDump = new Dump<StringKeyPosition>(StringKeyPosition.class, _objectKeyDumpFile);
            }
            final InfiniteSorter sorter = new InfiniteSorter(_maxLookupSizeInMemory, _dump.getDumpFile().getParentFile());
            _dump.iterateElementPositions(new PositionIteratorCallback() {

               @Override
               public void element( Object o, long pos ) {
                  try {
                     long keyPos = -1;
                     Object objectKey = getObjectKey((E)o);
                     if ( _fieldIsExternalizable ) {
                        keyPos = _externalizableKeyDump._outputStream._n;
                        _externalizableKeyDump.add(new ExternalizableKeyPosition((Externalizable)objectKey, pos));
                     } else if ( _fieldIsString ) {
                        keyPos = _stringKeyDump._outputStream._n;
                        _stringKeyDump.add(new StringKeyPosition((String)objectKey, pos));
                     }
                     sorter.add(new IntKeyPosition(objectKey.hashCode(), keyPos));
                  }
                  catch ( IOException argh ) {
                     throw new RuntimeException("Failed to sort InfiniteGroupIndex on disk", argh);
                  }
               }
            });
            _intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, _lookupFile);
            _intKeyDump.addAll(sorter);
            _intKeyDump._outputStream.flush();
            _lookupFileLength = _lookupFile.length();
         } else {
            throw new UnsupportedOperationException("unsupported key type: " + _fieldAccessor.getType());
         }
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to init InfiniteGroupIndex from dump", argh);
      }
   }

   @Override
   protected void initLookupMap() {
      _groupIndex = new MyGroupIndex(_dump, _fieldAccessor);
      _dump.removeIndex(_groupIndex);
      _currentLookupSize = _fieldIsInt ? _groupIndex._lookupInt.size() : (_fieldIsLong ? _groupIndex._lookupLong.size() : _groupIndex._lookupObject.size());

      if ( _fieldIsExternalizable || _fieldIsString ) {
         _objectKeyDumpFile = new File(_dump.getDumpFile().getParentFile(), _lookupFile.getName().replaceAll("lookup$", "keys"));
      }
   }

   @Override
   protected void initLookupOutputStream() {
   // we don't need _lookupOutputStream
   }

   @Override
   protected void load() {
      // we load nothing, since this is a disk-based index.
      if ( _fieldIsInt )
         _intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, _lookupFile);
      else if ( _fieldIsLong )
         _longKeyDump = new Dump<LongKeyPosition>(LongKeyPosition.class, _lookupFile);
      else if ( _fieldIsExternalizable || _fieldIsString )
         _intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, _lookupFile);
      else
         throw new UnsupportedOperationException("unsupported key type: " + _fieldAccessor.getType());

      if ( _fieldIsExternalizable ) {
         _externalizableKeyDump = new Dump<ExternalizableKeyPosition>(ExternalizableKeyPosition.class, _objectKeyDumpFile);
      } else if ( _fieldIsString ) {
         _stringKeyDump = new Dump<StringKeyPosition>(StringKeyPosition.class, _objectKeyDumpFile);
      }

      _lookupFileLength = _lookupFile.length();
   }

   protected void mergeOverflowIntoIndex() {
      try {
         String tmpLookupFileName = _lookupFile.getName() + ".tmp";
         if ( _fieldIsInt ) {
            InfiniteSorter<IntKeyPosition> sorter = new InfiniteSorter<IntKeyPosition>(_maxLookupSizeInMemory, _dump.getDumpFile().getParentFile()) {

               @Override
               protected List<DumpInput<IntKeyPosition>> getSegments() throws IOException {
                  List<DumpInput<IntKeyPosition>> segments = super.getSegments();
                  if ( _intKeyDump._outputStream._n > 0 ) segments.add(_intKeyDump);
                  return segments;
               }
            };
            for ( IntKeyMapIterator iterator = _groupIndex._lookupInt.entries(); iterator.hasNext(); ) {
               iterator.next();
               int key = iterator.getKey();
               long[] positions = (long[])iterator.getValue();
               for ( long pos : positions )
                  sorter.add(new IntKeyPosition(key, pos));
            }
            File tmpLookupFile = new File(_lookupFile.getParent(), tmpLookupFileName);
            Dump<IntKeyPosition> intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, tmpLookupFile);
            intKeyDump.addAll(sorter);
            intKeyDump.close();
            _intKeyDump.close();
            renameTmpLookupFile(tmpLookupFileName, tmpLookupFile);
            _intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, _lookupFile);
         } else if ( _fieldIsLong ) {
            InfiniteSorter<LongKeyPosition> sorter = new InfiniteSorter<LongKeyPosition>(_maxLookupSizeInMemory, _dump.getDumpFile().getParentFile()) {

               @Override
               protected List<DumpInput<LongKeyPosition>> getSegments() throws IOException {
                  List<DumpInput<LongKeyPosition>> segments = super.getSegments();
                  if ( _longKeyDump._outputStream._n > 0 ) segments.add(_longKeyDump);
                  return segments;
               }
            };
            for ( LongKeyMapIterator iterator = _groupIndex._lookupLong.entries(); iterator.hasNext(); ) {
               iterator.next();
               long key = iterator.getKey();
               long[] positions = (long[])iterator.getValue();
               for ( long pos : positions )
                  sorter.add(new LongKeyPosition(key, pos));
            }
            File tmpLookupFile = new File(_lookupFile.getParent(), tmpLookupFileName);
            Dump<LongKeyPosition> longKeyDump = new Dump<LongKeyPosition>(LongKeyPosition.class, tmpLookupFile);
            longKeyDump.addAll(sorter);
            longKeyDump.close();
            _longKeyDump.close();
            renameTmpLookupFile(tmpLookupFileName, tmpLookupFile);
            _longKeyDump = new Dump<LongKeyPosition>(LongKeyPosition.class, _lookupFile);
         } else {
            InfiniteSorter<IntKeyPosition> sorter = new InfiniteSorter<IntKeyPosition>(_maxLookupSizeInMemory, _dump.getDumpFile().getParentFile()) {

               @Override
               protected List<DumpInput<IntKeyPosition>> getSegments() throws IOException {
                  List<DumpInput<IntKeyPosition>> segments = super.getSegments();
                  if ( _intKeyDump._outputStream._n > 0 ) segments.add(_intKeyDump);
                  return segments;
               }
            };
            for ( Entry<Object, long[]> e : _groupIndex._lookupObject.entrySet() ) {
               Object objectKey = e.getKey();
               long[] positions = e.getValue();
               for ( long pos : positions ) {
                  long keyPos = -1;
                  if ( _fieldIsExternalizable ) {
                     keyPos = _externalizableKeyDump._outputStream._n;
                     _externalizableKeyDump.add(new ExternalizableKeyPosition((Externalizable)objectKey, pos));
                  } else if ( _fieldIsString ) {
                     keyPos = _stringKeyDump._outputStream._n;
                     _stringKeyDump.add(new StringKeyPosition((String)objectKey, pos));
                  }
                  sorter.add(new IntKeyPosition(objectKey.hashCode(), keyPos));
               }
            }
            File tmpLookupFile = new File(_lookupFile.getParent(), tmpLookupFileName);
            Dump<IntKeyPosition> intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, tmpLookupFile);
            intKeyDump.addAll(sorter);
            intKeyDump.close();
            _intKeyDump.close();
            renameTmpLookupFile(tmpLookupFileName, tmpLookupFile);
            _intKeyDump = new Dump<IntKeyPosition>(IntKeyPosition.class, _lookupFile);
         }

         _lookupFileLength = _lookupFile.length();

         _groupIndex.close();
         _groupIndex._lookupFile.delete();
         _groupIndex._updatesFile.delete();
         _groupIndex = new MyGroupIndex(_dump, _fieldAccessor);
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to re-organize InfiniteGroupIndex", argh);
      }
   }

   @Override
   void delete( E o, long pos ) {
   // we have no cache in memory, so there's nothing to do
   }

   @Override
   boolean isUpdatable( E oldItem, E newItem ) {
      return true;
   }

   @Override
   void update( long pos, E oldItem, E newItem ) {
      if ( !super.isUpdatable(oldItem, newItem) ) {
         try {
            long[] overflowPositions = _fieldIsInt ? _groupIndex.getPositions(getIntKey(oldItem)) : (_fieldIsLong ? _groupIndex
                  .getPositions(getLongKey(oldItem)) : _groupIndex.getPositions(getObjectKey(oldItem)));
            for ( long p : overflowPositions ) {
               if ( p == pos )
               // only update overflow index if the pos exists there
                  _groupIndex.superUpdate(pos, oldItem, newItem);
            }

            if ( _fieldIsInt ) {
               int key = getIntKey(oldItem);
               int keyLength = 4 + 8; // in bytes

               int firstIndex = findIntKey(key, keyLength);
               if ( firstIndex >= 0 ) {
                  for ( long p = firstIndex * keyLength; p < _lookupFileLength; p += keyLength ) {
                     IntKeyPosition ip = _intKeyDump.get(p);
                     if ( ip._key != key ) break;
                     if ( ip._pos == pos ) {
                        ip._pos = -1;
                        _intKeyDump.update(p, ip);
                     }
                  }
               }
            } else if ( _fieldIsLong ) {
               long key = getLongKey(oldItem);
               int keyLength = 8 + 8; // in bytes

               int firstIndex = findLongKey(key, keyLength);
               if ( firstIndex >= 0 ) {
                  for ( long p = firstIndex * keyLength; p < _lookupFileLength; p += keyLength ) {
                     LongKeyPosition ip = _longKeyDump.get(p);
                     if ( ip._key != key ) break;
                     if ( ip._pos == pos ) {
                        ip._pos = -1;
                        _longKeyDump.update(p, ip);
                     }
                  }
               }
            } else {
               Object key = getObjectKey(oldItem);
               LongList keyPositions = getObjectKeyPositions(key);

               for ( LongIterator iterator = keyPositions.iterator(); iterator.hasNext(); ) {
                  long p = iterator.next();
                  if ( _fieldIsExternalizable ) {
                     ExternalizableKeyPosition keyPosition = _externalizableKeyDump.get(p);
                     if ( keyPosition._key.equals(key) && keyPosition._pos == pos ) {
                        keyPosition._pos = -1;
                        _externalizableKeyDump.update(p, keyPosition);
                     }
                  } else if ( _fieldIsString ) {
                     StringKeyPosition keyPosition = _stringKeyDump.get(p);
                     if ( keyPosition._key.equals(key) && keyPosition._pos == pos ) {
                        keyPosition._pos = -1;
                        _stringKeyDump.update(p, keyPosition);
                     }
                  }
               }
            }

            add(newItem, pos);
         }
         catch ( IOException argh ) {
            throw new RuntimeException("Failed to update InfiniteGroupIndex.", argh);
         }
      }
   }

   private int findFirst( int key, int keyLength, int low, int high ) {
      int mid = -1;
      while ( low <= high ) {
         mid = (low + high) >>> 1;
         IntKeyPosition midVal = _intKeyDump.get(mid * keyLength);

         if ( midVal._key < key )
            low = mid + 1;
         else if ( midVal._key == key ) high = mid - 1;
      }

      return low;
   };

   private int findFirst( long key, int keyLength, int low, int high ) {
      int mid = -1;
      while ( low <= high ) {
         mid = (low + high) >>> 1;
         LongKeyPosition midVal = _longKeyDump.get(mid * keyLength);

         if ( midVal._key < key )
            low = mid + 1;
         else if ( midVal._key == key ) high = mid - 1;
      }

      return low;
   }

   private int findIntKey( int key, int keyLength ) {
      int low = 0;
      int high = (int)_lookupFileLength / keyLength - 1;

      while ( low <= high ) {
         int mid = (low + high) >>> 1;
         IntKeyPosition midVal = _intKeyDump.get(mid * keyLength);

         if ( midVal._key < key )
            low = mid + 1;
         else if ( midVal._key > key )
            high = mid - 1;
         else
            return findFirst(key, keyLength, low, mid);
      }
      return -1; // not found
   }

   private int findLongKey( long key, int keyLength ) {
      int low = 0;
      int high = (int)_lookupFileLength / keyLength - 1;

      while ( low <= high ) {
         int mid = (low + high) >>> 1;
         LongKeyPosition midVal = _longKeyDump.get(mid * keyLength);

         if ( midVal._key < key )
            low = mid + 1;
         else if ( midVal._key > key )
            high = mid - 1;
         else
            return findFirst(key, keyLength, low, mid);
      }
      return -1; // not found
   }

   private LongList getObjectKeyPositions( Object key ) {
      LongList keyPositions = new LongArrayList();
      int keyLength = 4 + 8; // in bytes

      int keyHashCode = key.hashCode();
      int firstIndex = findIntKey(keyHashCode, keyLength);
      if ( firstIndex >= 0 ) {
         for ( long p = firstIndex * keyLength; p < _lookupFileLength; p += keyLength ) {
            IntKeyPosition ip = _intKeyDump.get(p);
            if ( ip._key != keyHashCode ) break;
            keyPositions.add(ip._pos);
         }
      }
      return keyPositions;
   }

   private void isFieldCompatible() {
      if ( !_fieldIsInt && !_fieldIsLong && !_fieldIsExternalizable && !_fieldIsString )
         throw new IllegalArgumentException("For usage in an InfiniteGroupIndex the key field must be either int, long, String or Externalizable .");
      if ( _fieldIsExternalizable ) {
         try {
            Object o1 = _fieldAccessor.getType().newInstance();
            Object o2 = _fieldAccessor.getType().newInstance();
            if ( o1.hashCode() == System.identityHashCode(o1) && o2.hashCode() == System.identityHashCode(o2) )
               throw new IllegalArgumentException("For usage in an InfiniteGroupIndex a key type must provide a hashCode() implementation! "
                  + _fieldAccessor.getType() + " doesn't.");
            if ( !o1.equals(o2) )
               throw new IllegalArgumentException("For usage in an InfiniteGroupIndex a key type must provide an equals() implementation! "
                  + _fieldAccessor.getType() + " doesn't.");
         }
         catch ( Exception argh ) {
            if ( argh instanceof IllegalArgumentException ) throw (IllegalArgumentException)argh;
            // else ignore
         }
      }
   };

   private void renameTmpLookupFile( String tmpLookupFileName, File tmpLookupFile ) {
      if ( _lookupFile.exists() && !_lookupFile.delete() ) {
         throw new RuntimeException("Failed to delete old InfiniteGroupIndex lookup " + _lookupFile);
      }
      if ( !tmpLookupFile.renameTo(_lookupFile) ) {
         throw new RuntimeException("Failed to rename temporary lookup " + tmpLookupFile);
      }
      File meta = new File(_lookupFile.getParent(), tmpLookupFileName + ".meta");
      if ( meta.exists() && !meta.delete() ) {
         throw new RuntimeException("Failed to delete temporary lookup meta file " + meta);
      }
   }

   public static class ExternalizableKeyPosition extends Externalizer implements Comparable<ExternalizableKeyPosition> {

      @externalize(1)
      Externalizable _key;

      @externalize(2)
      long           _pos;

      public ExternalizableKeyPosition() {}

      public ExternalizableKeyPosition( Externalizable key, long pos ) {
         _key = key;
         _pos = pos;
      }

      public int compareTo( ExternalizableKeyPosition o ) {
         int r = _key.hashCode() < o._key.hashCode() ? -1 : (_key.hashCode() == o._key.hashCode() ? 0 : 1);
         if ( r != 0 ) return r;
         return _pos < o._pos ? -1 : (_pos == o._pos ? 0 : 1);
      }
   }

   public static class IntKeyPosition implements Externalizable, Comparable<IntKeyPosition> {

      int  _key;
      long _pos;

      public IntKeyPosition() {}

      public IntKeyPosition( int key, long pos ) {
         _key = key;
         _pos = pos;
      }

      public int compareTo( IntKeyPosition o ) {
         int r = _key < o._key ? -1 : (_key == o._key ? 0 : 1);
         if ( r != 0 ) return r;
         return _pos < o._pos ? -1 : (_pos == o._pos ? 0 : 1);
      }

      public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
         _key = in.readInt();
         _pos = in.readLong();
      }

      public void writeExternal( ObjectOutput out ) throws IOException {
         out.writeInt(_key);
         out.writeLong(_pos);
      }

   }

   public static class LongKeyPosition implements Externalizable, Comparable<LongKeyPosition> {

      long _key;

      long _pos;

      public LongKeyPosition() {}

      public LongKeyPosition( long key, long pos ) {
         _key = key;
         _pos = pos;
      }

      public int compareTo( LongKeyPosition o ) {
         int r = _key < o._key ? -1 : (_key == o._key ? 0 : 1);
         if ( r != 0 ) return r;
         return _pos < o._pos ? -1 : (_pos == o._pos ? 0 : 1);
      }

      public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
         _key = in.readLong();
         _pos = in.readLong();
      }

      public void writeExternal( ObjectOutput out ) throws IOException {
         out.writeLong(_key);
         out.writeLong(_pos);
      }

   }

   public static class StringKeyPosition extends Externalizer implements Comparable<StringKeyPosition> {

      @externalize(1)
      String _key;

      @externalize(2)
      long   _pos;

      public StringKeyPosition() {}

      public StringKeyPosition( String key, long pos ) {
         _key = key;
         _pos = pos;
      }

      public int compareTo( StringKeyPosition o ) {
         int r = _key.hashCode() < o._key.hashCode() ? -1 : (_key.hashCode() == o._key.hashCode() ? 0 : 1);
         if ( r != 0 ) return r;
         return _pos < o._pos ? -1 : (_pos == o._pos ? 0 : 1);
      }
   }

   private final class GroupIterable implements Iterable<E> {

      private final long[] _pos;

      public GroupIterable( long[] pos ) {
         _pos = pos;
      }

      public Iterator<E> iterator() {
         return new GroupIterator(_pos);
      }

   }

   private final class GroupIterator implements Iterator<E> {

      private final long[] _pos;
      int                  _i = 0;

      private GroupIterator( long[] pos ) {
         _pos = pos;
         while ( _i < _pos.length && _dump._deletedPositions.contains(_pos[_i]) ) {
            _i++;
         }
      }

      public boolean hasNext() {
         return _i < _pos.length;
      }

      public E next() {
         if ( _i >= _pos.length ) throw new NoSuchElementException();
         E e = _dump.get(_pos[_i]);
         do {
            _i++;
         }
         while ( _i < _pos.length && _dump._deletedPositions.contains(_pos[_i]) );
         return e;
      }

      public void remove() {
         throw new UnsupportedOperationException();
      }
   }

   /**
    * The 'overflow' index of this InfiniteGroupIndex, where all keys not yet added to the InfiniteGroupIndex are kept.
    */
   private class MyGroupIndex extends GroupIndex<E> {

      public MyGroupIndex( Dump<E> dump, FieldAccessor fieldAccessor ) {
         super(dump, fieldAccessor);
      }

      @Override
      public void add( E o, long pos ) {
      // do nothing, since the work is being done in InfiniteGroupIndex.this.add(.) 
      };

      public void superAdd( E o, long pos ) {
         super.add(o, pos);
      };

      @Override
      protected boolean checkMeta() {
         // TODO implement
         return true;
      }

      @Override
      protected void initFromDump() {
         // don't initFromDump, we want only the overflow in this index
         load();
      }

      @Override
      protected void initLookupMap() {
         _lookupFile = new File(_lookupFile.getParentFile(), _lookupFile.getName().replaceFirst("lookup$", "overflow.lookup"));
         super.initLookupMap();
      };

      @Override
      protected void initUpdatesFile() {
         try {
            _updatesFile = new File(_dump.getDumpFile().getParentFile(), _lookupFile.getName().replaceAll("lookup$", "updatedPositions"));
            if ( _updatesOutput != null ) _updatesOutput.close();
            _updatesOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(_updatesFile, true), DumpWriter.DEFAULT_BUFFER_SIZE));
         }
         catch ( IOException argh ) {
            throw new RuntimeException("Failed to open updates file " + _updatesFile, argh);
         }
      }

      @Override
      protected void writeMeta() throws IOException {
      // TODO implement
      }

      void superUpdate( long pos, E oldItem, E newItem ) {
         super.update(pos, oldItem, newItem);
      }

      @Override
      void update( long pos, E oldItem, E newItem ) {
      // do nothing, since the work is being done in InfiniteGroupIndex.this.update(.) 
      }
   }
}
