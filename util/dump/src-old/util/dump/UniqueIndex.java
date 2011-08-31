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

import util.dump.stream.ExternalizableObjectInputStream;
import util.dump.stream.SingleTypeObjectInputStream;
import util.reflection.FieldAccessor;
import bak.pcj.LongCollection;
import bak.pcj.LongIterator;
import bak.pcj.list.LongArrayList;
import bak.pcj.list.LongList;
import bak.pcj.map.IntKeyLongMap;
import bak.pcj.map.IntKeyLongOpenHashMap;
import bak.pcj.map.LongKeyLongMap;
import bak.pcj.map.LongKeyLongOpenHashMap;
import bak.pcj.map.ObjectKeyLongMap;
import bak.pcj.map.ObjectKeyLongOpenHashMap;


public class UniqueIndex<E> extends DumpIndex<E> {

   protected ObjectKeyLongMap _lookupObject;
   protected LongKeyLongMap   _lookupLong;
   protected IntKeyLongMap    _lookupInt;

   protected File             _updatesFile;
   protected DataOutputStream _updatesOutput;

   public UniqueIndex( Dump<E> dump, FieldAccessor fieldAccessor ) {
      super(dump, fieldAccessor);
      init();
      initUpdatesFile();
   }

   public UniqueIndex( Dump<E> dump, String fieldName ) throws NoSuchFieldException {
      super(dump, fieldName);
      init();
      initUpdatesFile();
   }

   @Override
   public void add( E o, long pos ) {
      try {
         if ( _fieldIsInt ) {
            int key = getIntKey(o);
            if ( _lookupInt.containsKey(key) ) throw new DuplicateKeyException("Dump already contains an instance with the key " + key);
            _lookupInt.put(key, pos);
            _lookupOutputStream.writeInt(key);
         } else if ( _fieldIsLong ) {
            long key = getLongKey(o);
            if ( _lookupLong.containsKey(key) ) throw new DuplicateKeyException("Dump already contains an instance with the key " + key);
            _lookupLong.put(key, pos);
            _lookupOutputStream.writeLong(key);
         } else {
            Object key = getObjectKey(o);
            if ( _lookupObject.containsKey(key) ) {
               throw new DuplicateKeyException("Dump already contains an instance with the key " + key);
            }
            _lookupObject.put(key, pos);
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
      return _lookupInt.containsKey(key) && !_dump._deletedPositions.contains(_lookupInt.lget());
   }

   @Override
   public synchronized boolean contains( long key ) {
      if ( !_fieldIsLong )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      return _lookupLong.containsKey(key) && !_dump._deletedPositions.contains(_lookupLong.lget());
   }

   @Override
   public synchronized boolean contains( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) return contains(((Long)key).longValue());
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) return contains(((Integer)key).intValue());
      if ( _fieldIsLong || _fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate contains(.) method.");
      return _lookupObject.containsKey(key) && !_dump._deletedPositions.contains(_lookupObject.lget());
   }

   @Override
   public long[] getAllPositions() {
      LongList pos = new LongArrayList(100000, 10000);
      LongCollection c = _fieldIsInt ? _lookupInt.values() : (_fieldIsLong ? _lookupLong.values() : _lookupObject.values());
      for ( LongIterator iterator = c.iterator(); iterator.hasNext(); ) {
         long p = iterator.next();
         if ( !_dump._deletedPositions.contains(p) ) pos.add(p);
      }
      long[] positions = pos.toArray();
      Arrays.sort(positions);
      return positions;
   }

   public synchronized E lookup( int key ) {
      if ( !_fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      long pos = getPosition(key);
      if ( pos < 0 ) return null;
      return _dump.get(pos);
   }

   public synchronized E lookup( long key ) {
      if ( !_fieldIsLong )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      long pos = getPosition(key);
      if ( pos < 0 ) return null;
      return _dump.get(pos);
   }

   public synchronized E lookup( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) return lookup(((Long)key).longValue());
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) return lookup(((Integer)key).intValue());
      if ( _fieldIsLong || _fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate lookup(.) method.");
      long pos = getPosition(key);
      if ( pos < 0 ) return null;
      return _dump.get(pos);
   }

   @Override
   protected String getIndexType() {
      return UniqueIndex.class.getSimpleName();
   }

   protected long getPosition( int key ) {
      if ( !_fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate getPosition(.) method.");
      if ( !_lookupInt.containsKey(key) ) return -1;
      return _lookupInt.lget(); // TODO lget() is not threadsafe. Handle this in the whole API!  
   }

   protected long getPosition( long key ) {
      if ( !_fieldIsLong )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate getPosition(.) method.");
      if ( !_lookupLong.containsKey(key) ) return -1;
      return _lookupLong.lget();
   }

   protected long getPosition( Object key ) {
      if ( (_fieldIsLong || _fieldIsLongObject) && key instanceof Long ) return getPosition(((Long)key).longValue());
      if ( (_fieldIsInt || _fieldIsIntObject) && key instanceof Integer ) return getPosition(((Integer)key).intValue());
      if ( _fieldIsLong || _fieldIsInt )
         throw new IllegalArgumentException("The type of the used key class of this index is " + _fieldAccessor.getType()
            + ". Please use the appropriate getPosition(.) method.");
      if ( !_lookupObject.containsKey(key) ) return -1;
      return _lookupObject.lget();
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
         _lookupInt = new IntKeyLongOpenHashMap();
      else if ( _fieldIsLong )
         _lookupLong = new LongKeyLongOpenHashMap();
      else
         _lookupObject = new ObjectKeyLongOpenHashMap();
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
            int size = (int)(_lookupFile.length() / (4 + 8));
            size = Math.max(10000, size);
            _lookupInt = new IntKeyLongOpenHashMap((int)(size / IntKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR) + 1, IntKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR,
               size / 20);
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
                  if ( _lookupInt.containsKey(key) )
                     throw new DuplicateKeyException("index lookup " + _lookupFile + " is broken - contains non unique key " + key);
                  if ( !_dump._deletedPositions.contains(pos) ) _lookupInt.put(key, pos);
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
         } else if ( _fieldIsLong ) {
            int size = (int)(_lookupFile.length() / (8 + 8));
            size = Math.max(10000, size);
            _lookupLong = new LongKeyLongOpenHashMap((int)(size / LongKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR) + 1, LongKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR,
               size / 20);
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
                  if ( _lookupLong.containsKey(key) )
                     throw new DuplicateKeyException("index lookup " + _lookupFile + " is broken - contains non unique key " + key);
                  if ( !_dump._deletedPositions.contains(pos) ) _lookupLong.put(key, pos);
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
         } else if ( _fieldIsString ) {
            int size = (int)(_lookupFile.length() / (10 + 8)); // let's assume an average length of the String keys of 10 bytes
            size = Math.max(10000, size);
            _lookupObject = new ObjectKeyLongOpenHashMap((int)(size / ObjectKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR) + 1,
               ObjectKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR, size / 20);
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
                  if ( _lookupObject.containsKey(key) )
                     throw new DuplicateKeyException("index lookup " + _lookupFile + " is broken - contains non unique key " + key);
                  if ( !_dump._deletedPositions.contains(pos) ) _lookupObject.put(key, pos);
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
         } else {
            int size = (int)(_lookupFile.length() / (20 + 8)); // let's assume an average length of the keys of 20 bytes
            size = Math.max(10000, size);
            _lookupObject = new ObjectKeyLongOpenHashMap(size, ObjectKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR, size / 20);
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
                  if ( _lookupObject.containsKey(key) )
                     throw new DuplicateKeyException("index lookup " + _lookupFile + " is broken - contains non unique key " + key);
                  if ( !_dump._deletedPositions.contains(pos) ) _lookupObject.put(key, pos);
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
         long p = _lookupInt.get(key);
         if ( p == pos ) _lookupInt.remove(key);
      } else if ( _fieldIsLong ) {
         long key = getLongKey(o);
         long p = _lookupLong.get(key);
         if ( p == pos ) _lookupLong.remove(key);
      } else {
         Object key = getObjectKey(o);
         long p = _lookupObject.get(key);
         if ( p == pos ) _lookupObject.remove(key);
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
   };

   /**
    * This Exception is thrown, when trying to add a non-unique index-value to a dump.   
    */
   public static class DuplicateKeyException extends RuntimeException {

      public DuplicateKeyException( String message ) {
         super(message);
      }
   }


}
