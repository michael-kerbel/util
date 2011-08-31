package util.dump;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import util.collections.SoftLRUCache;
import util.dump.sort.InfiniteSorter;
import util.dump.stream.ObjectStreamProvider;
import util.dump.stream.SingleTypeObjectStreamProvider;
import bak.pcj.list.ByteArrayList;
import bak.pcj.list.ByteList;
import bak.pcj.set.LongOpenHashSet;
import bak.pcj.set.LongSet;


/**
 * <b>Beware</b>: Always close the dump properly after usage and before exiting the JVM.<p/>
 */
public class Dump<E> implements DumpInput<E> {

   // TODO add pack() and a threshold based repack mechanism which prunes deletions from dump and indexes 

   public static final int          DEFAULT_CACHE_SIZE               = 10000;
   public static final int          DEFAULT_SORT_MAX_ITEMS_IN_MEMORY = 10000;

   private static final Set<String> OPENED_DUMPS                     = new HashSet<String>();

   final Class                      _beanClass;
   ObjectStreamProvider             _streamProvider;
   File                             _dumpFile;
   File                             _deletionsFile;
   File                             _metaFile;
   Set<DumpIndex<E>>                _indexes                         = new HashSet<DumpIndex<E>>();

   DumpWriter<E>                    _writer;
   DumpReader<E>                    _reader;
   PositionAwareOutputStream        _outputStream;
   RandomAccessFile                 _raf;
   ResetableBufferedInputStream     _resetableBufferedInputStream;
   DataOutputStream                 _deletionsOutput;
   LongSet                          _deletedPositions                = new LongOpenHashSet();

   /** The keys are positions in the dump file and the values are the bytes of the serialized item stored there.
    * Appended to these bytes is a space efficient encoding (see <code>longToBytes(long)</code>) of the next 
    * item's position. */
   Map<Long, byte[]>                _cache;
   int                              _cacheSize;
   ObjectInput                      _cacheObjectInput;
   ResetableBufferedInputStream     _cacheByteInput;
   Map<Long, byte[]>                _singleItemCache                 = new HashMap<Long, byte[]>();

   ByteArrayOutputStream            _updateByteOutput;
   ObjectOutput                     _updateOut;
   RandomAccessFile                 _updateRaf;

   boolean                          _isClosed;

   ThreadLocal<Long>                _nextItemPos                     = new LongThreadLocal();
   ThreadLocal<Long>                _lastItemPos                     = new LongThreadLocal();

   /** incremented on each write operation */
   long                             _sequence                        = 0;


   /**
    * Constructs a new Dump with <code>beanClass</code> as instance class.
    * A {@link SingleTypeObjectStreamProvider} using <code>beanClass</code> is created for your convenience.
    * A {@link SoftLRUCache} is used for caching with <code>DEFAULT_CACHE_SIZE</code> as size. 
    * @param beanClass must implement {@link Externalizable} otherwise the {@link SingleTypeObjectStreamProvider} that is used will fail during runtime
    * @param dumpFile the dump file
    */
   @SuppressWarnings("unchecked")
   public Dump( Class beanClass, File dumpFile ) {
      this(beanClass, new SingleTypeObjectStreamProvider(beanClass), dumpFile);
   }

   public Dump( Class beanClass, ObjectStreamProvider streamProvider, File dumpFile ) {
      this(beanClass, streamProvider, dumpFile, DEFAULT_CACHE_SIZE);
   }

   public Dump( Class beanClass, ObjectStreamProvider streamProvider, File dumpFile, int cacheSize ) {
      _beanClass = beanClass;
      _streamProvider = streamProvider;
      _dumpFile = dumpFile;
      _deletionsFile = new File(dumpFile + ".deletions");
      _metaFile = new File(dumpFile + ".meta");
      if ( OPENED_DUMPS.contains(_dumpFile.getAbsolutePath()) )
         throw new IllegalStateException("There is already an opened Dump instance for file " + _dumpFile + ". Having two instances is hazardous...");
      OPENED_DUMPS.add(_dumpFile.getAbsolutePath());
      _cacheSize = cacheSize;
      try {
         FileOutputStream fileOutputStream = new FileOutputStream(_dumpFile, true);
         BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream, DumpWriter.DEFAULT_BUFFER_SIZE);
         _outputStream = new PositionAwareOutputStream(bufferedOutputStream, _dumpFile.length());
         _writer = new DumpWriter<E>(_outputStream, 0, _streamProvider);
         _raf = new RandomAccessFile(_dumpFile, "r");
         _updateRaf = new RandomAccessFile(_dumpFile, "rw");
         _reader = new DumpReader<E>(_dumpFile, false, _streamProvider);

         readDeletions();
         _deletionsOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(_deletionsFile, true), DumpWriter.DEFAULT_BUFFER_SIZE));

         initMeta();

         _updateByteOutput = new ByteArrayOutputStream(1024);
         _updateOut = _streamProvider.createObjectOutput(_updateByteOutput);
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to initialize dump using dump file " + _dumpFile, argh);
      }
      if ( cacheSize > 0 ) {
         try {
            _cache = new SoftLRUCache(cacheSize); // no synchronization needed, since get(.) is synchronized
            _cacheByteInput = new ResetableBufferedInputStream(null, 0, false);
            _cacheObjectInput = _streamProvider.createObjectInput(_cacheByteInput);
         }
         catch ( IOException argh ) {
            throw new RuntimeException("Failed to create cache", argh);
         }
      }
   }

   public synchronized void add( E o ) throws IOException {
      assertOpen();
      long pos = _outputStream._n;
      _writer.write(o);
      _sequence++;
      for ( DumpIndex<E> index : _indexes ) {
         index.add(o, pos);
      }
   }

   public void addAll( Iterable<E> i ) throws IOException {
      for ( E e : i ) {
         add(e);
      }
   }

   /**
    * Failing to close the dump may result in data loss!
    */
   public void close() throws IOException {
      if ( _isClosed ) return;
      _writer.flush();
      _writer.close();
      _raf.close();
      // _resetableBufferedInputStream doesn't have to be closed, since it only closes the _raf 
      _reader.close();
      _deletionsOutput.close();
      _updateOut.close();
      _updateRaf.close();
      writeMeta();
      for ( DumpIndex index : new ArrayList<DumpIndex<E>>(_indexes) ) {
         index.close();
      }
      OPENED_DUMPS.remove(_dumpFile.getAbsolutePath());
      _isClosed = true;
   }

   /**
    * @return the deleted object
    */
   public synchronized E delete( long pos ) {
      assertOpen();

      E e = get(pos);
      if ( e == null )
         throw new RuntimeException("Failed to delete item on position " + pos + ". There was no instance on that position - maybe it was already deleted?");

      _deletedPositions.add(pos);
      _cache.remove(pos);
      try {
         _deletionsOutput.writeLong(pos);
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to add deletion to " + _deletionsFile, argh);
      }

      _sequence++;

      for ( DumpIndex index : _indexes )
         index.delete(e, pos);

      return e;
   }

   /**
    * Deletes the item which was acquired lastly from this dump instance by the current thread.
    * It doesn't play a role, in which way the last access to the dump took place: Whether via get(.), 
    * iteration or index access. An exception is thrown, if the current thread did not acquire 
    * anything from this dump yet.   
    * @return the deleted object
    */
   public E deleteLast() {
      return delete(_lastItemPos.get());
   }

   /**
    * Changes to the returned instance are not reflected in the dump. You always get a fresh instance
    * when invoking this method.  
    */
   public synchronized E get( long pos ) {
      assertOpen();

      boolean positionIsDeleted = _deletedPositions.contains(pos);
      if ( _cache != null ) {
         byte[] bytes = _cache.get(Long.valueOf(pos)); // ugly boxing of pos necessary here, since _cache is a regular Map - the cost of beauty is ugliness
         if ( bytes != null ) {
            _lastItemPos.set(pos);
            _nextItemPos.set(getNextItemPos(bytes));
            if ( positionIsDeleted ) return null;
            return readFromBytes(bytes);
         }
      }

      try {
         _outputStream.flush(); // flush any bytes in the buffer, just in case - this is cheap, if the buffer is empty

         if ( _resetableBufferedInputStream == null || _resetableBufferedInputStream._rafPos != pos ) {
            // only seek if we don't do sequential reads
            _raf.seek(pos);

            if ( _resetableBufferedInputStream == null ) {
               _resetableBufferedInputStream = new ResetableBufferedInputStream(Channels.newInputStream(_raf.getChannel()), pos, true);
               _resetableBufferedInputStream._lastElementBytes = new byte[1024];
               _reader.reset(_resetableBufferedInputStream, 0, _streamProvider);
            } else {
               _resetableBufferedInputStream.reset(Channels.newInputStream(_raf.getChannel()), pos);
            }
         }

         if ( _reader.hasNext() ) {
            E value = _reader.next();
            _nextItemPos.set(_resetableBufferedInputStream._rafPos);
            if ( _cache != null ) {
               // we don't cache E instances to prevent the user from changing the cached instances
               byte[] nextItemPos = longToBytes(_nextItemPos.get());
               byte[] lastElementBytes = new byte[_resetableBufferedInputStream._lastElementBytesLength + nextItemPos.length];
               System.arraycopy(_resetableBufferedInputStream._lastElementBytes, 0, lastElementBytes, 0, _resetableBufferedInputStream._lastElementBytesLength);
               appendNextItemPos(lastElementBytes, nextItemPos);
               _cache.put(Long.valueOf(pos), lastElementBytes); // ugly boxing of pos necessary here, since _cache is a regular Map - the cost of beauty is ugliness
            }
            _resetableBufferedInputStream._lastElementBytesLength = 0;
            _lastItemPos.set(pos);
            if ( positionIsDeleted ) return null;
            return value;
         } else {
            // reset reader state if EOF
            _reader.next();
         }

         return null;
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to read from dump " + _dumpFile + " at position " + pos, argh);
      }
   }

   public File getDumpFile() {
      return _dumpFile;
   }

   /**
    * Always returns the number of bytes contained in the dump, even if a flush operation is still missing
    * and the file does not contain all data yet. This method has no IO cost.
    * @return The current dump size in bytes. 
    */
   public long getDumpSize() {
      return _outputStream._n;
   }

   public ObjectStreamProvider getStreamProvider() {
      return _streamProvider;
   }

   public void iterateElementPositions( PositionIteratorCallback callback ) {
      assertOpen();

      DeletionAwareDumpReader dumpReader = null;
      try {
         _outputStream.flush(); // flush any bytes in the buffer, just in case - this is cheap, if the buffer is empty

         dumpReader = new DeletionAwareDumpReader(_dumpFile, false, _streamProvider);
         for ( E e : dumpReader ) {
            callback.element(e, dumpReader._lastPos);
         }
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to create a DumpReader.", argh);
      }
      finally {
         if ( dumpReader != null ) {
            try {
               // this is really only neccessary on exceptions
               dumpReader.close();
            }
            catch ( IOException argh ) {
               // ignore, maybe add logging
            }
         }
      }
   }

   public Iterator<E> iterator() {
      assertOpen();
      try {
         _outputStream.flush(); // flush any bytes in the buffer, just in case - this is cheap, if the buffer is empty
         return new DeletionAwareDumpReader(_dumpFile, false, _streamProvider).iterator();
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to create a DumpReader.", argh);
      }
   }

   public void setCache( Map<Long, byte[]> cache ) {
      _cache = cache;
   }

   public InfiniteSorter<E> sort() throws Exception {
      return sort(null, DEFAULT_SORT_MAX_ITEMS_IN_MEMORY);
   }

   /**
    * @param comparator If null, E must be Comparable and its natural order will be used
    * @param maxItemsInMemory The maximal number of items held in memory 
    */
   public InfiniteSorter<E> sort( Comparator<E> comparator, int maxItemsInMemory ) throws Exception {
      assertOpen();
      InfiniteSorter<E> sorter = new InfiniteSorter<E>(maxItemsInMemory, new File("."));
      if ( comparator != null ) sorter.setComparator(comparator);
      if ( Externalizable.class.isAssignableFrom(_beanClass) ) sorter.setObjectStreamProvider(new SingleTypeObjectStreamProvider(_beanClass));
      sorter.addAll(this);
      return sorter;
   }

   /**
    * <b>BEWARE</b>: After updates an item might be situated at the end of the dump.  
    * In other words: The sort order is not stable. <p/>
    * 
    * If the length of the byte representation of the item changes, the old version of the item will be
    * deleted and the new will be appended to the end.
    * 
    * @return the old version of the updated object
    */
   public synchronized E update( long pos, E newItem ) throws IOException {
      assertOpen();

      try {
         if ( _cache == null ) {
            _singleItemCache.clear();
            _cache = _singleItemCache;
         }
         E oldItem = get(pos);
         if ( oldItem == null )
            throw new RuntimeException("Failed to delete item on position " + pos + ". There was no instance on that position - maybe it was already deleted?");
         byte[] oldBytes = _cache.get(pos);

         _updateByteOutput.reset();
         _updateOut.writeObject(newItem);
         byte[] nb = _updateByteOutput.toByteArray();
         int suffixLength = oldBytes[oldBytes.length - 1] + 1;

         if ( oldBytes.length == nb.length + suffixLength ) {
            byte[] newBytes = new byte[nb.length + suffixLength];
            System.arraycopy(nb, 0, newBytes, 0, nb.length);
            System.arraycopy(oldBytes, nb.length, newBytes, nb.length, suffixLength);

            boolean indexesUpdatable = true;
            for ( DumpIndex index : _indexes ) {
               indexesUpdatable &= index.isUpdatable(oldItem, newItem);
            }

            if ( indexesUpdatable ) {
               overwrite(pos, nb);
               for ( DumpIndex index : _indexes ) {
                  index.update(pos, oldItem, newItem);
               }
               if ( _cache != null ) {
                  _cache.put(pos, newBytes);
               }
               return oldItem;
            }
         }
      }
      finally {
         if ( _cache == _singleItemCache ) _cache = null;
      }

      // overwriting was not possible -> delete old version and add new version
      E old = delete(pos);
      add(newItem);

      _sequence++;
      return old;
   }

   /**
    * Updates the item which was acquired lastly from this dump instance by the current thread.
    * It doesn't play a role, in which way the last access to the dump took place: Whether via get(.), 
    * iteration or index access. An exception is thrown, if the current thread did not acquire 
    * anything from this dump yet.   
    * @return the old version of the updated object
    */
   public E updateLast( E item ) throws IOException {
      return update(_lastItemPos.get(), item);
   }

   protected void assertOpen() {
      if ( _isClosed ) throw new RuntimeException("This dump instance (" + getDumpFile() + ") is already closed and cannot be used.");
   }

   @Override
   protected void finalize() throws Throwable {
      if ( !_isClosed )
         System.err.println("Dump instance " + getDumpFile() + " was not closed correctly. This is a bug in its usage and might result in data loss!");
      close();
      super.finalize();
   }

   void addIndex( DumpIndex index ) {
      assertOpen();
      _indexes.add(index);
   }

   void initMeta() throws IOException {
      if ( _metaFile.exists() ) {
         _sequence = new DataInputStream(new FileInputStream(_metaFile)).readLong();
      }
   }

   void overwrite( long pos, byte[] newBytes ) {
      try {
         _updateRaf.seek(pos);
         _updateRaf.write(newBytes);
      }
      catch ( IOException argh ) {
         throw new RuntimeException("Failed to update dump.", argh);
      }
   }

   void readDeletions() throws IOException {
      if ( _deletionsFile.exists() ) {
         if ( _deletionsFile.length() % 8 != 0 ) throw new RuntimeException("Dump corrupted: " + _deletionsFile + " has unbalanced size.");

         long dumpFileLength = _dumpFile.length();
         DataInputStream dataInputStream = null;
         try {
            dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(_deletionsFile), DumpReader.DEFAULT_BUFFER_SIZE));
            while ( true ) { // read until EOF
               long pos = dataInputStream.readLong();
               if ( pos < 0 || pos >= dumpFileLength ) {
                  throw new RuntimeException("Dump corrupted: " + _deletionsFile + " contains illegal data.");
               }
               _deletedPositions.add(pos);
            }
         }
         catch ( EOFException argh ) {
            // ignore
         }
         finally {
            if ( dataInputStream != null ) dataInputStream.close();
         }
      }
   }

   E readFromBytes( byte[] bytes ) {
      if ( bytes.length == 0 ) {
         System.err.println();
      }
      try {
         _cacheByteInput.buf = bytes;
         _cacheByteInput.count = bytes.length;
         _cacheByteInput.pos = 0;
         return (E)_cacheObjectInput.readObject();
      }
      catch ( Exception argh ) {
         throw new RuntimeException("Failed to read from internal cache", argh);
      }
   }

   void removeIndex( DumpIndex index ) {
      _indexes.remove(index);
   }

   void writeMeta() throws IOException {
      DataOutputStream dataOutputStream = null;
      try {
         dataOutputStream = new DataOutputStream(new FileOutputStream(_metaFile, false));
         dataOutputStream.writeLong(_sequence);
      }
      finally {
         if ( dataOutputStream != null ) dataOutputStream.close();
      }
   }

   private void appendNextItemPos( byte[] bytes, byte[] nextItemPos ) {
      int l = bytes.length;
      for ( int i = 1, length = nextItemPos.length; i <= length; i++ ) {
         bytes[l - i] = nextItemPos[length - i];
      }
   }

   private long getNextItemPos( byte[] bytes ) {
      byte n = bytes[bytes.length - 1];
      if ( n > 9 || n < 0 ) {
         throw new RuntimeException("invalid cache data.");
      }
      long l = 0L;
      for ( int i = bytes.length - n - 1, b = bytes.length - 1, j = 0; i < b; i++, j++ ) {
         l |= (bytes[i] & 0xff) << (j << 3);
      }
      return l;
   }

   private byte[] longToBytes( long l ) {
      // BEWARE Doesn't work with negative longs, which we don't need in this class. Would be easy to extend though.
      // TODO reuse bytes
      // TODO use a reusable byte[] instead of ByteArrayList 
      ByteList bytes = new ByteArrayList(9);
      byte n = 0;
      while ( l != 0L ) {
         bytes.add((byte)l);
         l = l >>> 8;
         n++;
      }
      bytes.add(n);
      return bytes.toArray();
   }

   public static class PositionIteratorCallback {

      public void element( Object o, long pos ) {}
   }

   class PositionAwareOutputStream extends OutputStream {

      long                       _n;

      private final OutputStream _out;

      public PositionAwareOutputStream( OutputStream out, long n ) {
         _out = out;
         _n = n;
      }

      @Override
      public void close() throws IOException {
         _out.close();
      }

      @Override
      public void flush() throws IOException {
         _out.flush();
      }

      @Override
      public void write( byte[] b ) throws IOException {
         _out.write(b);
         _n += b.length;
      }

      @Override
      public void write( byte[] b, int off, int len ) throws IOException {
         _out.write(b, off, len);
         _n += len;
      }

      @Override
      public void write( int b ) throws IOException {
         _out.write(b);
         _n++;
      }
   }

   static class ResetableBufferedInputStream extends InputStream {

      private static int      defaultBufferSize       = 256 * 1024;

      /**
       * The internal buffer array where the data is stored. When necessary,
       * it may be replaced by another array of
       * a different size.
       */
      protected volatile byte buf[];

      /**
       * The index one greater than the index of the last valid byte in 
       * the buffer. 
       * This value is always
       * in the range <code>0</code> through <code>buf.length</code>;
       * elements <code>buf[0]</code>  through <code>buf[count-1]
       * </code>contain buffered input data obtained
       * from the underlying  input stream.
       */
      protected int           count;

      /**
       * The current position in the buffer. This is the index of the next 
       * character to be read from the <code>buf</code> array. 
       * <p>
       * This value is always in the range <code>0</code>
       * through <code>count</code>. If it is less
       * than <code>count</code>, then  <code>buf[pos]</code>
       * is the next byte to be supplied as input;
       * if it is equal to <code>count</code>, then
       * the  next <code>read</code> or <code>skip</code>
       * operation will require more bytes to be
       * read from the contained  input stream.
       *
       * @see     java.io.BufferedInputStream#buf
       */
      protected int           pos;

      InputStream             _in;

      long                    _rafPos;

      byte[]                  _lastElementBytes       = null;
      int                     _lastElementBytesLength = 0;

      boolean                 _suppressClose          = false;

      /**
       * Creates a <code>BufferedInputStream</code>
       * with the specified buffer size,
       * and saves its  argument, the input stream
       * <code>in</code>, for later use.  An internal
       * buffer array of length  <code>size</code>
       * is created and stored in <code>buf</code>.
       *
       * @param   in     the underlying input stream.
       * @param   size   the buffer size.
       * @exception IllegalArgumentException if size <= 0.
       */
      public ResetableBufferedInputStream( InputStream in, int size, long rafPos, boolean suppressClose ) {
         _in = in;
         if ( size <= 0 ) {
            throw new IllegalArgumentException("Buffer size <= 0");
         }
         buf = new byte[size];
         _rafPos = rafPos;
         _suppressClose = suppressClose;
      }

      /**
       * Creates a <code>BufferedInputStream</code>
       * and saves its  argument, the input stream
       * <code>in</code>, for later use. An internal
       * buffer array is created and  stored in <code>buf</code>.
       *
       * @param   in   the underlying input stream.
       */
      public ResetableBufferedInputStream( InputStream in, long rafPos, boolean suppressClose ) {
         this(in, defaultBufferSize, rafPos, suppressClose);
      }

      /**
       * Returns an estimate of the number of bytes that can be read (or
       * skipped over) from this input stream without blocking by the next
       * invocation of a method for this input stream. The next invocation might be
       * the same thread or another thread.  A single read or skip of this
       * many bytes will not block, but may read or skip fewer bytes.
       * <p>
       * This method returns the sum of the number of bytes remaining to be read in
       * the buffer (<code>count&nbsp;- pos</code>) and the result of calling the
       * {@link java.io.FilterInputStream#in in}.available().
       *
       * @return     an estimate of the number of bytes that can be read (or skipped
       *             over) from this input stream without blocking.
       * @exception  IOException  if this input stream has been closed by
       *                          invoking its {@link #close()} method,
       *                          or an I/O error occurs.
       */
      @Override
      public synchronized int available() throws IOException {
         return getInIfOpen().available() + (count - pos);
      }

      /**
       * Closes this input stream and releases any system resources 
       * associated with the stream. 
       * Once the stream has been closed, further read(), available(), reset(),
       * or skip() invocations will throw an IOException.
       * Closing a previously closed stream has no effect.
       *
       * @exception  IOException  if an I/O error occurs.
       */
      @Override
      public void close() throws IOException {
         if ( _suppressClose ) return;

         buf = null;
         InputStream input = _in;
         _in = null;
         if ( input != null ) input.close();
         return;
      }

      /**
       * Tests if this input stream supports the <code>mark</code> 
       * and <code>reset</code> methods. The <code>markSupported</code> 
       * method of <code>BufferedInputStream</code> returns 
       * <code>true</code>. 
       *
       * @return  a <code>boolean</code> indicating if this stream type supports
       *          the <code>mark</code> and <code>reset</code> methods.
       * @see     java.io.InputStream#mark(int)
       * @see     java.io.InputStream#reset()
       */
      @Override
      public boolean markSupported() {
         return true;
      }

      /**
       * See
       * the general contract of the <code>read</code>
       * method of <code>InputStream</code>.
       *
       * @return     the next byte of data, or <code>-1</code> if the end of the
       *             stream is reached.
       * @exception  IOException  if this input stream has been closed by
       *              invoking its {@link #close()} method,
       *              or an I/O error occurs. 
       * @see        java.io.FilterInputStream#in
       */
      @Override
      public synchronized int read() throws IOException {
         if ( pos >= count ) {
            fill();
            if ( pos >= count ) {
               return -1;
            }
         }
         _rafPos++;
         int b = getBufIfOpen()[pos++] & 0xff;
         if ( b >= 0 && _lastElementBytes != null ) {
            if ( _lastElementBytesLength + 1 >= _lastElementBytes.length ) {
               growLastElementBytes(1);
            }
            _lastElementBytes[_lastElementBytesLength] = (byte)b;
            _lastElementBytesLength++;
         }
         return b;
      }

      /**
       * Reads bytes from this byte-input stream into the specified byte array,
       * starting at the given offset.
       *
       * <p> This method implements the general contract of the corresponding
       * <code>{@link InputStream#read(byte[], int, int) read}</code> method of
       * the <code>{@link InputStream}</code> class.  As an additional
       * convenience, it attempts to read as many bytes as possible by repeatedly
       * invoking the <code>read</code> method of the underlying stream.  This
       * iterated <code>read</code> continues until one of the following
       * conditions becomes true: <ul>
       *
       *   <li> The specified number of bytes have been read,
       *
       *   <li> The <code>read</code> method of the underlying stream returns
       *   <code>-1</code>, indicating end-of-file, or
       *
       *   <li> The <code>available</code> method of the underlying stream
       *   returns zero, indicating that further input requests would block.
       *
       * </ul> If the first <code>read</code> on the underlying stream returns
       * <code>-1</code> to indicate end-of-file then this method returns
       * <code>-1</code>.  Otherwise this method returns the number of bytes
       * actually read.
       *
       * <p> Subclasses of this class are encouraged, but not required, to
       * attempt to read as many bytes as possible in the same fashion.
       *
       * @param      b     destination buffer.
       * @param      off   offset at which to start storing bytes.
       * @param      len   maximum number of bytes to read.
       * @return     the number of bytes read, or <code>-1</code> if the end of
       *             the stream has been reached.
       * @exception  IOException  if this input stream has been closed by
       *              invoking its {@link #close()} method,
       *              or an I/O error occurs. 
       */
      @Override
      public synchronized int read( byte b[], int off, int len ) throws IOException {
         getBufIfOpen(); // Check for closed stream
         if ( (off | len | (off + len) | (b.length - (off + len))) < 0 ) {
            throw new IndexOutOfBoundsException();
         } else if ( len == 0 ) {
            return 0;
         }

         int n = read0(b, off, len);
         if ( n > 0 && _lastElementBytes != null ) {
            if ( _lastElementBytesLength + len >= _lastElementBytes.length ) {
               growLastElementBytes(len);
            }
            System.arraycopy(b, off, _lastElementBytes, _lastElementBytesLength, n);
            _lastElementBytesLength += n;
         }
         return n;
      }

      /**
       * See the general contract of the <code>reset</code>
       * method of <code>InputStream</code>.
       * <p>
       * If <code>markpos</code> is <code>-1</code>
       * (no mark has been set or the mark has been
       * invalidated), an <code>IOException</code>
       * is thrown. Otherwise, <code>pos</code> is
       * set equal to <code>markpos</code>.
       *
       * @exception  IOException  if this stream has not been marked or,
       *          if the mark has been invalidated, or the stream 
       *          has been closed by invoking its {@link #close()}
       *          method, or an I/O error occurs.
       * @see        java.io.BufferedInputStream#mark(int)
       */
      public synchronized void reset( InputStream in, long rafPos ) throws IOException {
         getBufIfOpen(); // Cause exception if closed
         _in = in;
         pos = 0;
         count = 0;
         _rafPos = rafPos;
      }

      int read0( byte b[], int off, int len ) throws IOException {
         int n = 0;
         for ( ;; ) {
            int nread = read1(b, off + n, len - n);
            if ( nread <= 0 ) {
               int r = (n == 0) ? nread : n;
               _rafPos += r;
               return r;
            }
            n += nread;
            if ( n >= len ) {
               _rafPos += n;
               return n;
            }
            // if not closed but no bytes available, return
            InputStream input = _in;
            if ( input != null && input.available() <= 0 ) {
               _rafPos += n;
               return n;
            }
         }
      }

      /**
       * Fills the buffer with more data, taking into account
       * shuffling and other tricks for dealing with marks.
       * Assumes that it is being called by a synchronized method.
       * This method also assumes that all data has already been read in,
       * hence pos > count.
       */
      private void fill() throws IOException {
         byte[] buffer = getBufIfOpen();
         pos = 0; /* no mark: throw away the buffer */
         count = pos;
         int n = getInIfOpen().read(buffer, pos, buffer.length - pos);
         if ( n > 0 ) {
            count = n + pos;
         }
      }

      /**
       * Check to make sure that buffer has not been nulled out due to
       * close; if not return it;
       */
      private byte[] getBufIfOpen() throws IOException {
         byte[] buffer = buf;
         if ( buffer == null ) {
            throw new IOException("Stream closed");
         }
         return buffer;
      }

      /**
       * Check to make sure that underlying input stream has not been
       * nulled out due to close; if not return it;
       */
      private InputStream getInIfOpen() throws IOException {
         InputStream input = _in;
         if ( input == null ) {
            throw new IOException("Stream closed");
         }
         return input;
      }

      private void growLastElementBytes( int minGrowSize ) {
         int newSize = Math.max(_lastElementBytes.length + 1024, _lastElementBytesLength + minGrowSize + 1);
         byte[] nb = new byte[newSize];
         System.arraycopy(_lastElementBytes, 0, nb, 0, _lastElementBytes.length);
         _lastElementBytes = nb;
      }

      /**
       * Read characters into a portion of an array, reading from the underlying
       * stream at most once if necessary.
       */
      private int read1( byte[] b, int off, int len ) throws IOException {
         int avail = count - pos;
         if ( avail <= 0 ) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, do not bother to copy the
               bytes into the local buffer.  In this way buffered streams will
               cascade harmlessly. */
            if ( len >= getBufIfOpen().length ) {
               return getInIfOpen().read(b, off, len);
            }
            fill();
            avail = count - pos;
            if ( avail <= 0 ) {
               return -1;
            }
         }
         int cnt = (avail < len) ? avail : len;
         System.arraycopy(getBufIfOpen(), pos, b, off, cnt);
         pos += cnt;
         return cnt;
      }
   }

   private class DeletionAwareDumpReader extends DumpReader<E> {

      private ResetableBufferedInputStream _positionAwareInputStream;
      private long                         _lastPos;
      private long                         _maxPos;

      public DeletionAwareDumpReader( File dumpFile, boolean b, ObjectStreamProvider streamProvider ) throws IOException {
         super(new ResetableBufferedInputStream(new FileInputStream(_dumpFile), 0, false), 0, streamProvider);
         _positionAwareInputStream = (ResetableBufferedInputStream)primitiveInputStream;
         _positionAwareInputStream._lastElementBytes = new byte[1024];
         _maxPos = _outputStream._n;
      }

      @Override
      public boolean hasNext() {
         long pos = -1;
         boolean hasNext = false;
         do {
            _positionAwareInputStream._lastElementBytesLength = 0;
            pos = _positionAwareInputStream._rafPos;
            hasNext = _maxPos > pos && super.hasNext();
         }
         while ( hasNext && _deletedPositions.contains(pos) && super.next() != null ); // condition 'super.next() != null' is just to move the Iterator on
         _lastPos = pos;

         _nextItemPos.set(_positionAwareInputStream._rafPos);
         if ( hasNext && _cache != null ) {
            // we don't cache E instances to prevent the user from changing the cached instances
            byte[] nextItemPos = longToBytes(_nextItemPos.get());
            byte[] lastElementBytes = new byte[_positionAwareInputStream._lastElementBytesLength + nextItemPos.length];
            System.arraycopy(_positionAwareInputStream._lastElementBytes, 0, lastElementBytes, 0, _positionAwareInputStream._lastElementBytesLength);
            appendNextItemPos(lastElementBytes, nextItemPos);
            _cache.put(Long.valueOf(pos), lastElementBytes); // ugly boxing of pos necessary here, since _cache is a regular Map - the cost of beauty is ugliness
         }
         if ( !hasNext ) super.closeStreams(true);

         _positionAwareInputStream._lastElementBytesLength = 0;

         return hasNext;
      }

      @Override
      public E next() {
         _lastItemPos.set(_lastPos);
         return super.next();
      }
   }

   private final class LongThreadLocal extends ThreadLocal<Long> {

      @Override
      protected Long initialValue() {
         return Long.valueOf(-1);
      }
   }
}
