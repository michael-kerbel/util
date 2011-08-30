package util.dump;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import util.dump.stream.ExternalizableObjectInputStream;
import util.dump.stream.SingleTypeObjectInputStream;
import util.reflection.FieldAccessor;
import bak.pcj.list.LongArrayList;
import bak.pcj.list.LongList;
import bak.pcj.map.IntKeyMap;
import bak.pcj.map.IntKeyMapIterator;
import bak.pcj.map.IntKeyOpenHashMap;
import bak.pcj.map.LongKeyMap;
import bak.pcj.map.LongKeyMapIterator;
import bak.pcj.map.LongKeyOpenHashMap;


public class GroupIndex<E> extends DumpIndex<E> implements NonUniqueIndex<E> {

   protected Map<Object, long[]> _lookupObject;
   protected LongKeyMap          _lookupLong;
   protected IntKeyMap           _lookupInt;

   protected File                _updatesFile;
   protected DataOutputStream    _updatesOutput;


   public GroupIndex( Dump<E> dump, FieldAccessor fieldAccessor ) {
      super(dump, fieldAccessor);
      init();
      initUpdatesFile();
   }

   public GroupIndex( Dump<E> dump, String fieldName ) throws NoSuchFieldException {
      super(dump, fieldName);
      init();
      initUpdatesFile();
   }

   @Override
   public void add( E o, long pos ) {
      try {
         if ( _fieldIsInt ) {
            int key = getIntKey(o);
            long[] positions = (long[])_lookupInt.get(key);
            positions = addPosition(positions, pos);
            _lookupInt.put(key, positions);
            _lookupOutputStream.writeInt(key);
         } else if ( _fieldIsLong ) {
            long key = getLongKey(o);
            long[] positions = (long[])_lookupLong.get(key);
            positions = addPosition(positions, pos);
            _lookupLong.put(key, positions);
            _lookupOutputStream.writeLong(key);
         } else {
            Object key = getObjectKey(o);
            long[] positions = _lookupObject.get(key);
            positions = addPosition(positions, pos);
            _lookupObject.put(key, positions);
            if ( _fieldIsString ) {
               _lookupOutputStream.writeUTF(key.toString());
            } else {
               ((ObjectOutput)_lookupOutputStream).writeObject(key);
            }
         }

         _lookupOutputStream.writeLong(pos);
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to add key to index " + _lookupFile, argh);
      }
   }

   @Override
   public void close() throws IOException {
      _updatesOutput.close();
      super.close();
   }

   @Override
   public synchronized boolean contains( int key ) {
      if ( !_fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      long[] pos = (long[])_lookupInt.get(key);
      return contains(pos);
   }

   @Override
   public synchronized boolean contains( long key ) {
      if ( !_fieldIsLong )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      long[] pos = (long[])_lookupLong.get(key);
      return contains(pos);
   }

   @Override
   public synchronized boolean contains( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) return contains(((Long)key).longValue());
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) return contains(((Integer)key).intValue());
      if ( _fieldIsLong || _fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      long[] pos = _lookupObject.get(key);
      return contains(pos);
   }

   @Override
   public long[] getAllPositions() {
      LongList pos = new LongArrayList(100000, 10000);
      Collection c = _fieldIsInt ? _lookupInt.values() : (_fieldIsLong ? _lookupLong.values() : _lookupObject.values());
      for ( Object o : c ) {
         long[] p = (long[])o;
         for ( long pp : p ) {
            if ( !_dump._deletedPositions.contains(pp) ) pos.add(pp);
         }
      }
      long[] positions = pos.toArray();
      Arrays.sort(positions);
      return positions;
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
   protected String getIndexType() {
      return GroupIndex.class.getSimpleName();
   }

   /**
    * @return <b>BEWARE</b>: the position may contain deleted dump positions!
    */
   protected long[] getPositions( int key ) {
      if ( !_fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      long[] pos = (long[])_lookupInt.get(key);
      if ( pos == null ) pos = new long[0];
      return pos;
   }

   /**
    * @return <b>BEWARE</b>: the position may contain deleted dump positions!
    */
   protected long[] getPositions( long key ) {
      if ( !_fieldIsLong )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      long[] pos = (long[])_lookupLong.get(key);
      if ( pos == null ) pos = new long[0];
      return pos;
   }

   /**
    * @return <b>BEWARE</b>: the position may contain deleted dump positions!
    */
   protected long[] getPositions( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) return getPositions(((Long)key).longValue());
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) return getPositions(((Integer)key).intValue());
      if ( _fieldIsLong || _fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      long[] pos = _lookupObject.get(key);
      if ( pos == null ) pos = new long[0];
      return pos;
   }

   @Override
   protected void initFromDump() {
      super.initFromDump();

      try {
         initUpdatesFile();
         _updatesOutput.close();
         _updatesFile.delete();
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to delete updates file " + _updatesFile, argh);
      }
   }

   @Override
   protected void initLookupMap() {
      if ( _fieldIsInt )
         _lookupInt = new IntKeyOpenHashMap();
      else if ( _fieldIsLong )
         _lookupLong = new LongKeyOpenHashMap();
      else
         _lookupObject = new HashMap<Object, long[]>();
   }

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
   protected void load() {
      if ( !_lookupFile.exists() || _lookupFile.length() == 0 ) return;

      DataInputStream updatesInput = null;
      try {
         initUpdatesFile();
         if ( _updatesFile.exists() ) {
            if ( _updatesFile.length() % 8 != 0 ) throw new RuntimeException("Index corrupted: " + _updatesFile + " has unbalanced size.");
            try {
               updatesInput = new DataInputStream(new BufferedInputStream(new FileInputStream(_updatesFile), DumpReader.DEFAULT_BUFFER_SIZE));
            }
            catch ( FileNotFoundException argh ) {
               // since we do a _updatesFile.exists() this is most unlikely 
               throw new RuntimeException("Failed read updates from " + _updatesFile, argh);
            }
         }

         boolean mayEOF = true;
         long nextPositionToIgnore = readNextPosition(updatesInput);
         if ( _fieldIsInt ) {
            _lookupInt = new IntKeyOpenHashMap(10000, IntKeyOpenHashMap.DEFAULT_LOAD_FACTOR, 10000);
            DataInputStream in = null;
            try {
               in = new DataInputStream(new BufferedInputStream(new FileInputStream(_lookupFile)));
               while ( true ) {
                  int key = in.readInt();
                  mayEOF = false;
                  long pos = in.readLong();
                  mayEOF = true;
                  if ( pos == nextPositionToIgnore ) {
                     nextPositionToIgnore = readNextPosition(updatesInput);
                     continue;
                  }
                  if ( !_dump._deletedPositions.contains(pos) ) {
                     LongList positions = (LongList)_lookupInt.get(key);
                     if ( positions == null ) {
                        positions = new LongArrayList();
                        _lookupInt.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + _lookupFile + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + _lookupFile, argh);
            }
            finally {
               if ( in != null ) try {
                  in.close();
               }
               catch ( IOException argh ) {
                  throw new RuntimeException("Failed to close input stream.", argh);
               }
            }
            // optimize memory consumption of lookup 
            IntKeyOpenHashMap lookupInt = new IntKeyOpenHashMap((int)(_lookupInt.size() / IntKeyOpenHashMap.DEFAULT_LOAD_FACTOR) + 10,
               IntKeyOpenHashMap.DEFAULT_LOAD_FACTOR, Math.max(1000, _lookupInt.size() / 20));
            for ( IntKeyMapIterator iterator = _lookupInt.entries(); iterator.hasNext(); ) {
               iterator.next();
               int key = iterator.getKey();
               LongList positions = (LongList)iterator.getValue();
               long[] pos = positions.toArray();
               Arrays.sort(pos);
               lookupInt.put(key, pos);
               iterator.remove(); // for gc
            }
            _lookupInt = lookupInt;

         } else if ( _fieldIsLong ) {
            _lookupLong = new LongKeyOpenHashMap(10000, LongKeyOpenHashMap.DEFAULT_LOAD_FACTOR, 10000);
            DataInputStream in = null;
            try {
               in = new DataInputStream(new BufferedInputStream(new FileInputStream(_lookupFile)));
               while ( true ) {
                  long key = in.readLong();
                  mayEOF = false;
                  long pos = in.readLong();
                  mayEOF = true;
                  if ( pos == nextPositionToIgnore ) {
                     nextPositionToIgnore = readNextPosition(updatesInput);
                     continue;
                  }
                  if ( !_dump._deletedPositions.contains(pos) ) {
                     LongList positions = (LongList)_lookupLong.get(key);
                     if ( positions == null ) {
                        positions = new LongArrayList();
                        _lookupLong.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + _lookupFile + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + _lookupFile, argh);
            }
            finally {
               if ( in != null ) try {
                  in.close();
               }
               catch ( IOException argh ) {
                  throw new RuntimeException("Failed to close input stream.", argh);
               }
            }
            // optimize memory consumption of lookup 
            LongKeyOpenHashMap lookupLong = new LongKeyOpenHashMap((int)(_lookupLong.size() / LongKeyOpenHashMap.DEFAULT_LOAD_FACTOR) + 10,
               LongKeyOpenHashMap.DEFAULT_LOAD_FACTOR, Math.max(1000, _lookupLong.size() / 20));
            for ( LongKeyMapIterator iterator = _lookupLong.entries(); iterator.hasNext(); ) {
               iterator.next();
               Long key = iterator.getKey();
               LongList positions = (LongList)iterator.getValue();
               long[] pos = positions.toArray();
               Arrays.sort(pos);
               lookupLong.put(key, pos);
               iterator.remove(); // for gc
            }
            _lookupLong = lookupLong;

         } else if ( _fieldIsString ) {
            HashMap<Object, LongList> lookupObject = new HashMap<Object, LongList>(10000);
            DataInputStream in = null;
            try {
               in = new DataInputStream(new BufferedInputStream(new FileInputStream(_lookupFile)));
               while ( true ) {
                  String key = in.readUTF();
                  mayEOF = false;
                  long pos = in.readLong();
                  mayEOF = true;
                  if ( pos == nextPositionToIgnore ) {
                     nextPositionToIgnore = readNextPosition(updatesInput);
                     continue;
                  }
                  if ( !_dump._deletedPositions.contains(pos) ) {
                     LongList positions = lookupObject.get(key);
                     if ( positions == null ) {
                        positions = new LongArrayList();
                        lookupObject.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + _lookupFile + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + _lookupFile, argh);
            }
            finally {
               if ( in != null ) try {
                  in.close();
               }
               catch ( IOException argh ) {
                  throw new RuntimeException("Failed to close input stream.", argh);
               }
            }
            // optimize memory consumption of lookup 
            _lookupObject = new HashMap<Object, long[]>((int)(lookupObject.size() / 0.75f) + 10); // 0.75f is the default for HashMap
            for ( Iterator<Map.Entry<Object, LongList>> iterator = lookupObject.entrySet().iterator(); iterator.hasNext(); ) {
               Map.Entry<Object, LongList> e = iterator.next();
               Object key = e.getKey();
               LongList positions = e.getValue();
               long[] pos = positions.toArray();
               Arrays.sort(pos);
               _lookupObject.put(key, pos);
               iterator.remove(); // for gc
            }

         } else {
            HashMap<Object, LongList> lookupObject = new HashMap<Object, LongList>(10000);
            ObjectInput in = null;
            try {
               if ( _fieldIsExternalizable )
                  in = new SingleTypeObjectInputStream(new BufferedInputStream(new FileInputStream(_lookupFile)), _fieldAccessor.getType());
               else
                  in = new ExternalizableObjectInputStream(new BufferedInputStream(new FileInputStream(_lookupFile)));
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to initialize dump index with lookup file " + _lookupFile, argh);
            }
            try {
               while ( true ) {
                  Object key = in.readObject();
                  mayEOF = false;
                  long pos = in.readLong();
                  mayEOF = true;
                  if ( pos == nextPositionToIgnore ) {
                     nextPositionToIgnore = readNextPosition(updatesInput);
                     continue;
                  }
                  if ( !_dump._deletedPositions.contains(pos) ) {
                     LongList positions = lookupObject.get(key);
                     if ( positions == null ) {
                        positions = new LongArrayList();
                        lookupObject.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + _lookupFile + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( ClassNotFoundException argh ) {
               throw new RuntimeException("Failed to read lookup from " + _lookupFile, argh);
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + _lookupFile, argh);
            }
            finally {
               if ( in != null ) try {
                  in.close();
               }
               catch ( IOException argh ) {
                  throw new RuntimeException("Failed to close input stream.", argh);
               }
            }
            // optimize memory consumption of lookup 
            _lookupObject = new HashMap<Object, long[]>((int)(lookupObject.size() / 0.75f) + 10); // 0.75f is the default for HashMap
            for ( Iterator<Map.Entry<Object, LongList>> iterator = lookupObject.entrySet().iterator(); iterator.hasNext(); ) {
               Map.Entry<Object, LongList> e = iterator.next();
               Object key = e.getKey();
               LongList positions = e.getValue();
               long[] pos = positions.toArray();
               Arrays.sort(pos);
               _lookupObject.put(key, pos);
               iterator.remove(); // for gc
            }
         }
      }
      finally {
         if ( updatesInput != null ) {
            try {
               updatesInput.close();
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to close updates stream.", argh);
            }
         }
      }
   };

   protected long readNextPosition( DataInputStream updatesInput ) {
      if ( updatesInput == null ) return -1;
      try {
         return updatesInput.readLong();
      }
      catch ( EOFException argh ) {
         return -1;
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to read updates from " + _updatesFile, argh);
      }
   }

   @Override
   void delete( E o, long pos ) {
      if ( _fieldIsInt ) {
         int key = getIntKey(o);
         long[] positions = (long[])_lookupInt.get(key);
         positions = removePosition(positions, pos);
         if ( positions.length == 0 )
            _lookupInt.remove(key);
         else
            _lookupInt.put(key, positions);
      } else if ( _fieldIsLong ) {
         long key = getLongKey(o);
         long[] positions = (long[])_lookupLong.get(key);
         positions = removePosition(positions, pos);
         if ( positions.length == 0 )
            _lookupLong.remove(key);
         else
            _lookupLong.put(key, positions);
      } else {
         Object key = getObjectKey(o);
         long[] positions = _lookupObject.get(key);
         positions = removePosition(positions, pos);
         if ( positions.length == 0 )
            _lookupObject.remove(key);
         else
            _lookupObject.put(key, positions);
      }
   }


   @Override
   boolean isUpdatable( E oldItem, E newItem ) {
      return true;
   }

   @Override
   void update( long pos, E oldItem, E newItem ) {
      boolean noChange = super.isUpdatable(oldItem, newItem);
      if ( noChange ) return;
      delete(oldItem, pos); // remove from memory

      try {
         // we add this position to the stream of ignored positions used during load()
         _updatesOutput.writeLong(pos);
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to append to updates file " + _updatesFile, argh);
      }

      add(newItem, pos);
      /* This position is now twice in the index on disk, under different keys. 
       * This is handled during load() using _updatesFile */
   }

   private long[] addPosition( long[] positions, long pos ) {
      if ( positions == null ) {
         positions = new long[] { pos };
      } else {
         long[] newPositions = new long[positions.length + 1];
         System.arraycopy(positions, 0, newPositions, 0, positions.length);
         newPositions[positions.length] = pos;
         Arrays.sort(newPositions);
         positions = newPositions;
      }
      return positions;
   }

   private boolean contains( long[] pos ) {
      if ( pos != null ) {
         for ( int i = 0, length = pos.length; i < length; i++ ) {
            if ( !_dump._deletedPositions.contains(pos[i]) ) return true;
         }
      }
      return false;
   }

   private long[] removePosition( long[] positions, long pos ) {
      if ( positions == null ) return new long[0];
      if ( positions.length == 0 ) return positions;
      long[] newPositions = new long[positions.length - 1];
      if ( newPositions.length == 0 ) return newPositions;
      int j = 0;
      for ( int i = 0, length = positions.length; i < length; i++ ) {
         long p = positions[i];
         if ( p != pos ) {
            newPositions[i + j] = p;
         } else {
            j--;
         }
      }
      return newPositions;
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

}
