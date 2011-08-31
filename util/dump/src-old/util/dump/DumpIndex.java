package util.dump;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;

import util.dump.Dump.PositionIteratorCallback;
import util.dump.stream.ExternalizableObjectOutputStream;
import util.dump.stream.SingleTypeObjectOutputStream;
import util.reflection.FieldAccessor;
import util.reflection.FieldFieldAccessor;
import util.reflection.Reflection;


/**
 * Implements a persistent index lookup for usage with {@link Dump} instances. 
 * The index key can be of any type and must be accessable with a {@link FieldAccessor}, i.e. either a Field or a Method 
 * of your bean <code>E</code> must return the key.<p/>   
 * The indexed field should be either int, long, String or Externalizable for good initialization and writing performance.<p/>
 * <b>Beware</b>: Your key instances are used in a HashMap, so you probably want to implement <nobr><code>hashCode()</code></nobr> and 
 * <nobr><code>equals()</code></nobr> accordingly, if you use a custom key instance (i.e. not <code>int</code>, <code>long</code>, 
 * {@link String}, any {@link Number}, ...)<p/>
 */
public abstract class DumpIndex<E> implements Closeable {

   public static String getIndexFileName( File dumpFile, FieldAccessor fieldAccessor ) {
      return dumpFile.getName() + "." + fieldAccessor.getName() + ".lookup";
   }

   protected final Dump<E>       _dump;
   protected File                _lookupFile;
   protected File                _metaFile;
   protected DataOutputStream    _lookupOutputStream;
   protected final FieldAccessor _fieldAccessor;
   protected final boolean       _fieldIsInt;
   protected final boolean       _fieldIsIntObject;
   protected final boolean       _fieldIsLong;
   protected final boolean       _fieldIsLongObject;
   protected final boolean       _fieldIsString;
   protected final boolean       _fieldIsExternalizable;

   /**
      * Creates an index and adds it to the {@link Dump}.
      * @param dump the parent dump to add this index to
      * @param fieldAccessor the accessor to the field containing the index key
      */
   public DumpIndex( Dump<E> dump, FieldAccessor fieldAccessor ) {
      _dump = dump;
      String lookupFilename = getIndexFileName(_dump.getDumpFile(), fieldAccessor);
      _lookupFile = new File(_dump.getDumpFile().getParentFile(), lookupFilename);
      _metaFile = new File(_dump.getDumpFile().getParentFile(), lookupFilename.replaceAll("lookup$", "meta"));
      _fieldAccessor = fieldAccessor;
      Class fieldType = fieldAccessor.getType();
      _fieldIsInt = fieldType == int.class || fieldType == Integer.class;
      _fieldIsIntObject = fieldType == Integer.class;
      _fieldIsLong = fieldType == long.class || fieldType == Long.class;
      _fieldIsLongObject = fieldType == Long.class;
      _fieldIsString = fieldType == String.class;
      _fieldIsExternalizable = Externalizable.class.isAssignableFrom(_fieldAccessor.getType());
   }

   public DumpIndex( Dump<E> dump, String fieldName ) throws NoSuchFieldException {
      this(dump, new FieldFieldAccessor(Reflection.getField(dump._beanClass, fieldName)));
   }

   /**
    * Failing to close the index may result in data loss!
    */
   public void close() throws IOException {
      writeMeta();
      if ( _lookupOutputStream != null ) _lookupOutputStream.close();
      _dump.removeIndex(this);
   }

   public abstract boolean contains( int key );

   public abstract boolean contains( long key );

   public abstract boolean contains( Object key );

   @Override
   public boolean equals( Object obj ) {
      if ( this == obj ) return true;
      if ( obj == null ) return false;
      if ( getClass() != obj.getClass() ) return false;
      DumpIndex other = (DumpIndex)obj;
      if ( _fieldAccessor == null ) {
         if ( other._fieldAccessor != null ) return false;
      } else if ( !_fieldAccessor.equals(other._fieldAccessor) ) return false;
      return true;
   }

   public abstract long[] getAllPositions();

   public FieldAccessor getFieldAccessor() {
      return _fieldAccessor;
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((_fieldAccessor == null) ? 0 : _fieldAccessor.hashCode());
      return result;
   }

   protected boolean checkMeta() {
      try {
         DataInputStream in = null;
         try {
            in = new DataInputStream(new FileInputStream(_metaFile));
            long dumpSequence = in.readLong();
            String beanClassName = in.readUTF();
            String indexType = in.readUTF();
            return dumpSequence == _dump._sequence && _dump._beanClass.getName().equals(beanClassName) && getIndexType().equals(indexType);
         }
         finally {
            if ( in != null ) in.close();
         }
      }
      catch ( IOException argh ) {
         return false;
      }
   }

   protected void deleteAllIndexFiles() {
      final String indexPrefix = _lookupFile.getName().replaceAll("lookup$", "");
      File dir = _dump.getDumpFile().getAbsoluteFile().getParentFile();
      File[] indexFiles = dir.listFiles(new FilenameFilter() {

         public boolean accept( File dir, String name ) {
            return name.startsWith(indexPrefix);
         }
      });
      for ( File f : indexFiles ) {
         if ( !f.delete() ) {
            System.err.println("Failed to delete invalid index file " + f);
         }
      }
   }

   protected abstract String getIndexType();

   protected int getIntKey( E o ) {
      int key;
      try {
         key = _fieldIsIntObject ? ((Integer)_fieldAccessor.get(o)) : _fieldAccessor.getInt(o);
      }
      catch ( Exception argh ) {
         throw new RuntimeException(argh);
      }
      return key;
   }

   protected long getLongKey( E o ) {
      long key;
      try {
         key = _fieldIsLongObject ? ((Long)_fieldAccessor.get(o)) : _fieldAccessor.getLong(o);
      }
      catch ( Exception argh ) {
         throw new RuntimeException(argh);
      }
      return key;
   }

   protected Object getObjectKey( E o ) {
      Object key;
      try {
         key = _fieldAccessor.get(o);
      }
      catch ( Exception argh ) {
         throw new RuntimeException(argh);
      }
      return key;
   }

   protected void init() {
      initLookupMap();

      if ( !_dump.getDumpFile().exists() || _dump.getDumpFile().length() == 0 ) _lookupFile.delete();
      boolean indexInvalid = !_lookupFile.exists() || _lookupFile.length() == 0 || !checkMeta();
      if ( indexInvalid ) deleteAllIndexFiles();

      initLookupOutputStream();

      if ( _dump.getDumpFile().length() > 0 && indexInvalid ) {
         // rebuild index if it is not current
         initFromDump();
      } else {
         load();
      }

      _dump.addIndex(this);
   }

   protected void initFromDump() {
      _dump.iterateElementPositions(new PositionIteratorCallback() {

         @Override
         public void element( Object o, long pos ) {
            add((E)o, pos);
         }
      });
   }

   protected abstract void initLookupMap();

   protected void initLookupOutputStream() {
      try {
         if ( !_fieldIsInt && !_fieldIsLong && !_fieldIsString ) {
            if ( _fieldIsExternalizable ) {
               _lookupOutputStream = new SingleTypeObjectOutputStream(new BufferedOutputStream(new FileOutputStream(_lookupFile, true)), _fieldAccessor
                     .getType());
            } else {
               _lookupOutputStream = new ExternalizableObjectOutputStream(new DataOutputStream(
                  new BufferedOutputStream(new FileOutputStream(_lookupFile, true))));
            }
         } else {
            _lookupOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(_lookupFile, true)));
         }
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to initialize dump index with lookup file " + _lookupFile, argh);
      }
   }

   protected abstract void load();

   protected void writeMeta() throws IOException {
      DataOutputStream out = null;
      try {
         out = new DataOutputStream(new FileOutputStream(_metaFile));
         long dumpSequence = _dump._sequence;
         out.writeLong(dumpSequence);
         out.writeUTF(_dump._beanClass.getName());
         out.writeUTF(getIndexType());
      }
      finally {
         if ( out != null ) out.close();
      }
   }

   abstract void add( E o, long pos );

   abstract void delete( E o, long pos );

   boolean isUpdatable( E oldItem, E newItem ) {
      return getObjectKey(oldItem).equals(getObjectKey(newItem));
   };

   abstract void update( long pos, E oldItem, E newItem );
}
