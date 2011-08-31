package util.dump;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TIntObjectProcedure;
import gnu.trove.procedure.TLongObjectProcedure;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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


public class GroupIndex<E> extends DumpIndex<E> implements NonUniqueIndex<E> {

   protected static long[] removePosition( long[] positions, long pos ) {

      if ( positions == null ) {
         return new long[0];
      }
      int length = positions.length;
      if ( length == 0 ) {
         return positions;
      }

      long[] newPositions = new long[length - 1];

      if ( newPositions.length == 0 ) {
         return pos == positions[0] ? newPositions : positions;
      }

      int idx = length - 1;
      for ( ; idx >= 0; idx-- ) {
         if ( positions[idx] == pos ) break;
      }

      if ( idx < 0 ) {
         // no element found
         return positions;
      }

      System.arraycopy(positions, 0, newPositions, 0, idx);
      if ( idx < length - 1 ) {
         System.arraycopy(positions, idx + 1, newPositions, idx, length - idx - 1);
      }

      return newPositions;
   }


   protected Map<Object, long[]>    _lookupObject;
   protected TLongObjectMap<long[]> _lookupLong;
   protected TIntObjectMap<long[]>  _lookupInt;


   public GroupIndex( Dump<E> dump, FieldAccessor fieldAccessor ) {
      super(dump, fieldAccessor);
      init();
   }

   public GroupIndex( Dump<E> dump, String fieldName ) throws NoSuchFieldException {
      super(dump, fieldName);
      init();
   }

   GroupIndex( Dump<E> dump, FieldAccessor fieldAccessor, File lookupFile ) {
      super(dump, fieldAccessor, lookupFile);
      init();
   }

   @Override
   public void add( E o, long pos ) {
      try {
         if ( _fieldIsInt ) {
            int key = getIntKey(o);
            long[] positions = _lookupInt.get(key);
            positions = addPosition(positions, pos);
            _lookupInt.put(key, positions);
            _lookupOutputStream.writeInt(key);
         } else if ( _fieldIsLong ) {
            long key = getLongKey(o);
            long[] positions = _lookupLong.get(key);
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
         throw new RuntimeException("Failed to add key to index " + getLookupFile(), argh);
      }
   }

   @Override
   public synchronized boolean contains( int key ) {
      if ( !_fieldIsInt ) {
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      }
      long[] pos = _lookupInt.get(key);
      return contains(pos);
   }

   @Override
   public synchronized boolean contains( long key ) {
      if ( !_fieldIsLong ) {
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      }
      long[] pos = _lookupLong.get(key);
      return contains(pos);
   }

   @Override
   public synchronized boolean contains( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) {
         return contains(((Long)key).longValue());
      }
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) {
         return contains(((Integer)key).intValue());
      }
      if ( _fieldIsLong || _fieldIsInt ) {
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      }
      long[] pos = _lookupObject.get(key);
      return contains(pos);
   }

   @Override
   public TLongList getAllPositions() {
      TLongList pos = new TLongArrayList(100000);
      Collection<long[]> c = _fieldIsInt ? _lookupInt.valueCollection() : (_fieldIsLong ? _lookupLong.valueCollection() : _lookupObject.values());
      for ( long[] p : c ) {
         for ( long pp : p ) {
            if ( !_dump._deletedPositions.contains(pp) ) {
               pos.add(pp);
            }
         }
      }
      pos.sort();
      return pos;
   }

   @Override
   public int getNumKeys() {
      if ( _lookupObject != null ) {
         return _lookupObject.size();
      }
      if ( _lookupLong != null ) {
         return _lookupLong.size();
      }
      if ( _lookupInt != null ) {
         return _lookupInt.size();
      }
      throw new IllegalStateException("weird, all lookup maps are null");
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
      if ( !_fieldIsInt ) {
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      }
      long[] pos = _lookupInt.get(key);
      if ( pos == null ) {
         pos = new long[0];
      }
      return pos;
   }

   /**
    * @return <b>BEWARE</b>: the position may contain deleted dump positions!
    */
   protected long[] getPositions( long key ) {
      if ( !_fieldIsLong ) {
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      }
      long[] pos = _lookupLong.get(key);
      if ( pos == null ) {
         pos = new long[0];
      }
      return pos;
   }

   /**
    * @return <b>BEWARE</b>: the position may contain deleted dump positions!
    */
   protected long[] getPositions( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) {
         return getPositions(((Long)key).longValue());
      }
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) {
         return getPositions(((Integer)key).intValue());
      }
      if ( _fieldIsLong || _fieldIsInt ) {
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      }
      long[] pos = _lookupObject.get(key);
      if ( pos == null ) {
         pos = new long[0];
      }
      return pos;
   }

   @Override
   protected void initFromDump() {
      super.initFromDump();

      try {
         closeAndDeleteUpdatesOutput();
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to delete updates file " + getUpdatesFile(), argh);
      }
   }

   @Override
   protected void initLookupMap() {
      if ( _fieldIsInt ) {
         _lookupInt = new TIntObjectHashMap<long[]>();
      } else if ( _fieldIsLong ) {
         _lookupLong = new TLongObjectHashMap<long[]>();
      } else {
         _lookupObject = new HashMap<Object, long[]>();
      }
   };

   @Override
   protected void load() {
      if ( !getLookupFile().exists() || getLookupFile().length() == 0 ) {
         return;
      }

      DataInputStream updatesInput = null;
      try {
         if ( getUpdatesFile().exists() ) {
            if ( getUpdatesFile().length() % 8 != 0 ) {
               throw new RuntimeException("Index corrupted: " + getUpdatesFile() + " has unbalanced size.");
            }
            try {
               updatesInput = new DataInputStream(new BufferedInputStream(new FileInputStream(getUpdatesFile()), DumpReader.DEFAULT_BUFFER_SIZE));
            }
            catch ( FileNotFoundException argh ) {
               // since we do a getUpdatesFile().exists() this is most unlikely
               throw new RuntimeException("Failed read updates from " + getUpdatesFile(), argh);
            }
         }

         boolean mayEOF = true;
         long nextPositionToIgnore = readNextPosition(updatesInput);
         if ( _fieldIsInt ) {
            TIntObjectMap<TLongList> dynamicLookupInt = new TIntObjectHashMap<TLongList>(10000);
            DataInputStream in = null;
            try {
               in = new DataInputStream(new BufferedInputStream(new FileInputStream(getLookupFile())));
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
                     TLongList positions = dynamicLookupInt.get(key);
                     if ( positions == null ) {
                        positions = new TLongArrayList();
                        dynamicLookupInt.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + getLookupFile() + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + getLookupFile(), argh);
            }
            finally {
               if ( in != null ) {
                  try {
                     in.close();
                  }
                  catch ( IOException argh ) {
                     throw new RuntimeException("Failed to close input stream.", argh);
                  }
               }
            }
            // optimize memory consumption of lookup
            final TIntObjectMap<long[]> lookupInt = new TIntObjectHashMap<long[]>(Math.max(1000, dynamicLookupInt.size()));
            dynamicLookupInt.forEachEntry(new TIntObjectProcedure<TLongList>() {

               public boolean execute( int key, TLongList positions ) {
                  long[] pos = positions.toArray();
                  Arrays.sort(pos);
                  lookupInt.put(key, pos);
                  positions.clear(); // for gc
                  return true;
               }
            });
            _lookupInt = lookupInt;

         } else if ( _fieldIsLong ) {
            TLongObjectMap<TLongList> dynamicLookupLong = new TLongObjectHashMap<TLongList>(10000);
            DataInputStream in = null;
            try {
               in = new DataInputStream(new BufferedInputStream(new FileInputStream(getLookupFile())));
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
                     TLongList positions = dynamicLookupLong.get(key);
                     if ( positions == null ) {
                        positions = new TLongArrayList();
                        dynamicLookupLong.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + getLookupFile() + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + getLookupFile(), argh);
            }
            finally {
               if ( in != null ) {
                  try {
                     in.close();
                  }
                  catch ( IOException argh ) {
                     throw new RuntimeException("Failed to close input stream.", argh);
                  }
               }
            }
            // optimize memory consumption of lookup
            final TLongObjectMap<long[]> lookupLong = new TLongObjectHashMap<long[]>(Math.max(1000, dynamicLookupLong.size()));
            dynamicLookupLong.forEachEntry(new TLongObjectProcedure<TLongList>() {

               public boolean execute( long key, TLongList positions ) {
                  long[] pos = positions.toArray();
                  Arrays.sort(pos);
                  lookupLong.put(key, pos);
                  positions.clear(); // for gc
                  return true;
               }
            });
            _lookupLong = lookupLong;

         } else if ( _fieldIsString ) {
            HashMap<Object, TLongList> lookupObject = new HashMap<Object, TLongList>(10000);
            DataInputStream in = null;
            try {
               in = new DataInputStream(new BufferedInputStream(new FileInputStream(getLookupFile())));
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
                     TLongList positions = lookupObject.get(key);
                     if ( positions == null ) {
                        positions = new TLongArrayList();
                        lookupObject.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + getLookupFile() + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + getLookupFile(), argh);
            }
            finally {
               if ( in != null ) {
                  try {
                     in.close();
                  }
                  catch ( IOException argh ) {
                     throw new RuntimeException("Failed to close input stream.", argh);
                  }
               }
            }
            // optimize memory consumption of lookup
            _lookupObject = new HashMap<Object, long[]>((int)(lookupObject.size() / 0.75f) + 10); // 0.75f is the default for HashMap
            for ( Iterator<Map.Entry<Object, TLongList>> iterator = lookupObject.entrySet().iterator(); iterator.hasNext(); ) {
               Map.Entry<Object, TLongList> e = iterator.next();
               Object key = e.getKey();
               TLongList positions = e.getValue();
               long[] pos = positions.toArray();
               Arrays.sort(pos);
               _lookupObject.put(key, pos);
               iterator.remove(); // for gc
            }

         } else {
            HashMap<Object, TLongList> lookupObject = new HashMap<Object, TLongList>(10000);
            ObjectInput in = null;
            try {
               if ( _fieldIsExternalizable ) {
                  in = new SingleTypeObjectInputStream(new BufferedInputStream(new FileInputStream(getLookupFile())), _fieldAccessor.getType());
               } else {
                  in = new ExternalizableObjectInputStream(new BufferedInputStream(new FileInputStream(getLookupFile())));
               }
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to initialize dump index with lookup file " + getLookupFile(), argh);
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
                     TLongList positions = lookupObject.get(key);
                     if ( positions == null ) {
                        positions = new TLongArrayList();
                        lookupObject.put(key, positions);
                     }
                     positions.add(pos);
                  }
               }
            }
            catch ( EOFException argh ) {
               if ( !mayEOF ) {
                  throw new RuntimeException("Failed to read lookup from " + getLookupFile() + ", file is unbalanced - unexpected EoF", argh);
               }
            }
            catch ( ClassNotFoundException argh ) {
               throw new RuntimeException("Failed to read lookup from " + getLookupFile(), argh);
            }
            catch ( IOException argh ) {
               throw new RuntimeException("Failed to read lookup from " + getLookupFile(), argh);
            }
            finally {
               try {
                  in.close();
               }
               catch ( IOException argh ) {
                  throw new RuntimeException("Failed to close input stream.", argh);
               }
            }
            // optimize memory consumption of lookup
            _lookupObject = new HashMap<Object, long[]>((int)(lookupObject.size() / 0.75f) + 10); // 0.75f is the default for HashMap
            for ( Iterator<Map.Entry<Object, TLongList>> iterator = lookupObject.entrySet().iterator(); iterator.hasNext(); ) {
               Map.Entry<Object, TLongList> e = iterator.next();
               Object key = e.getKey();
               TLongList positions = e.getValue();
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
   }

   protected long readNextPosition( DataInputStream updatesInput ) {
      if ( updatesInput == null ) {
         return -1;
      }
      try {
         return updatesInput.readLong();
      }
      catch ( EOFException argh ) {
         return -1;
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to read updates from " + getUpdatesFile(), argh);
      }
   }

   @Override
   void delete( E o, long pos ) {
      if ( _fieldIsInt ) {
         int key = getIntKey(o);
         long[] positions = _lookupInt.get(key);
         positions = removePosition(positions, pos);
         if ( positions.length == 0 ) {
            _lookupInt.remove(key);
         } else {
            _lookupInt.put(key, positions);
         }
      } else if ( _fieldIsLong ) {
         long key = getLongKey(o);
         long[] positions = _lookupLong.get(key);
         positions = removePosition(positions, pos);
         if ( positions.length == 0 ) {
            _lookupLong.remove(key);
         } else {
            _lookupLong.put(key, positions);
         }
      } else {
         Object key = getObjectKey(o);
         long[] positions = _lookupObject.get(key);
         positions = removePosition(positions, pos);
         if ( positions.length == 0 ) {
            _lookupObject.remove(key);
         } else {
            _lookupObject.put(key, positions);
         }
      }
   }

   @Override
   boolean isUpdatable( E oldItem, E newItem ) {
      return true;
   }

   @Override
   void update( long pos, E oldItem, E newItem ) {
      boolean noChange = super.isUpdatable(oldItem, newItem);
      if ( noChange ) {
         return;
      }
      delete(oldItem, pos); // remove from memory

      try {
         // we add this position to the stream of ignored positions used during load()
         getUpdatesOutput().writeLong(pos);
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to append to updates file " + getUpdatesFile(), argh);
      }

      add(newItem, pos);
      /* This position is now twice in the index on disk, under different keys.
       * This is handled during load() using getUpdatesFile() */
   }

   private long[] addPosition( long[] positions, long pos ) {
      if ( positions == null ) {
         positions = new long[] { pos };
      } else {
         long[] newPositions = new long[positions.length + 1];
         System.arraycopy(positions, 0, newPositions, 0, positions.length);
         newPositions[positions.length] = pos;
         if ( pos < newPositions[positions.length - 1] ) {
            Arrays.sort(newPositions);
         }
         positions = newPositions;
      }
      return positions;
   }

   private boolean contains( long[] pos ) {
      if ( pos != null ) {
         for ( int i = 0, length = pos.length; i < length; i++ ) {
            if ( !_dump._deletedPositions.contains(pos[i]) ) {
               return true;
            }
         }
      }
      return false;
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
         if ( _i >= _pos.length ) {
            throw new NoSuchElementException();
         }
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
