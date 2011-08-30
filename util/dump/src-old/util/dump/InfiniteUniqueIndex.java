//package util.dump;
//
//import java.io.BufferedInputStream;
//import java.io.DataInputStream;
//import java.io.EOFException;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.ObjectInput;
//import java.io.ObjectOutput;
//
//import util.dump.stream.FastObjectInputStream;
//import util.dump.stream.PrimitiveObjectInputStream;
//import util.reflection.FieldAccessor;
//import bak.pcj.map.IntKeyLongMap;
//import bak.pcj.map.IntKeyLongOpenHashMap;
//import bak.pcj.map.LongKeyLongMap;
//import bak.pcj.map.LongKeyLongOpenHashMap;
//import bak.pcj.map.ObjectKeyLongMap;
//import bak.pcj.map.ObjectKeyLongOpenHashMap;
//
//
//public class InfiniteUniqueIndex<E>
//    extends DumpIndex<E> {
//
//  protected ObjectKeyLongMap _lookup;
//
//  public InfiniteUniqueIndex( Dump<E> dump, FieldAccessor fieldAccessor ) {
//    super( dump, fieldAccessor );
//  }
//
//  protected void initLookupMap() {
//    _lookup = new ObjectKeyLongOpenHashMap();
//  }
//
//  public synchronized boolean contains( int key ) {
//    return contains(key);
//  }
//  public synchronized boolean contains( long key ) {
//    return contains(key);
//  }
//  public synchronized boolean contains( Object key ) {
//    return _lookup.containsKey( key );
//  }
//
//  public synchronized E lookup( Object key ) {
//    if (!_lookup.containsKey( key )) return null;
//    long pos = _lookup.lget();
//    return _dump.get( pos );
//  }
//
//  @Override
//  public void add( E o, long pos ) {
//    try {
//        Object key = getObjectKey( o );
//        if (_lookup.containsKey( key ))
//          throw new DuplicateKeyException( "Dump already contains an instance with the key " + key );
//        _lookup.put( key, pos );
//        if (_fieldIsString) {
//          _lookupOutputStream.writeUTF( key.toString() );
//        } else {
//          ((ObjectOutput)_lookupOutputStream).writeObject( key );
//        }
//
//      _lookupOutputStream.writeLong( pos );
//
//    } catch (IOException argh) {
//      throw new RuntimeException( "Failed to add key to index " + _lookupFile, argh );
//    }
//  }
//
//  protected void load() {
//    if (!_lookupFile.exists() || _lookupFile.length() == 0) return;
//
//
//    if (_fieldIsString) {
//      int size = (int)(_lookupFile.length() / (10 + 8)); // let's assume an average length of the String keys of 10 bytes
//      size = Math.max( 10000, size );
//      _lookupObject = new ObjectKeyLongOpenHashMap( size, ObjectKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR, size / 20 );
//      DataInputStream in = null;
//      boolean mayEOF = true;
//      try {
//        in = new DataInputStream( new BufferedInputStream( new FileInputStream( _lookupFile ) ) );
//        while (true) {
//          String key = in.readUTF();
//          mayEOF = false;
//          long pos = in.readLong();
//          if (_lookupObject.containsKey( key ))
//            throw new DuplicateKeyException( "index lookup " + _lookupFile + " is broken - contains non unique key " + key );
//          _lookupObject.put( key, pos );
//          mayEOF = true;
//        }
//      } catch (EOFException argh) {
//        if (!mayEOF) { throw new RuntimeException( "Failed to read lookup from " + _lookupFile +
//                                                   ", file is unbalanced - unexpected EoF", argh ); }
//      } catch (IOException argh) {
//        throw new RuntimeException( "Failed to read lookup from " + _lookupFile, argh );
//      } finally {
//        if (in != null) try {
//          in.close();
//        } catch (IOException argh) {
//          throw new RuntimeException( "Failed to close input stream.", argh );
//        }
//      }
//    } else {
//      int size = (int)(_lookupFile.length() / (20 + 8)); // let's assume an average length of the keys of 20 bytes
//      size = Math.max( 10000, size );
//      _lookupObject = new ObjectKeyLongOpenHashMap( size, ObjectKeyLongOpenHashMap.DEFAULT_LOAD_FACTOR, size / 20 );
//      ObjectInput in = null;
//      boolean mayEOF = true;
//      try {
//        if (_fieldIsExternalizable)
//          in = new PrimitiveObjectInputStream( new BufferedInputStream( new FileInputStream( _lookupFile ) ), _fieldAccessor
//              .getType() );
//        else
//          in = new FastObjectInputStream( new BufferedInputStream( new FileInputStream( _lookupFile ) ) );
//      } catch (IOException argh) {
//        throw new RuntimeException( "Failed to initialize dump index with lookup file " + _lookupFile, argh );
//      }
//      try {
//        while (true) {
//          Object key = in.readObject();
//          mayEOF = false;
//          long pos = in.readLong();
//          if (_lookupObject.containsKey( key ))
//            throw new DuplicateKeyException( "index lookup " + _lookupFile + " is broken - contains non unique key " + key );
//          _lookupObject.put( key, pos );
//          mayEOF = true;
//        }
//      } catch (EOFException argh) {
//        if (!mayEOF) { throw new RuntimeException( "Failed to read lookup from " + _lookupFile +
//                                                   ", file is unbalanced - unexpected EoF", argh ); }
//      } catch (ClassNotFoundException argh) {
//        throw new RuntimeException( "Failed to read lookup from " + _lookupFile, argh );
//      } catch (IOException argh) {
//        throw new RuntimeException( "Failed to read lookup from " + _lookupFile, argh );
//      } finally {
//        if (in != null) try {
//          in.close();
//        } catch (IOException argh) {
//          throw new RuntimeException( "Failed to close input stream.", argh );
//        }
//      }
//    }
//  }
//  /**
//   * This Exception is thrown, when trying to add a non-unique index-value to a dump.   
//   */
//  public static class DuplicateKeyException
//      extends RuntimeException {
//
//    public DuplicateKeyException( String message ) {
//      super( message );
//    }
//  }
//
//}
