package util.dump;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.OptionalDataException;
import java.util.Iterator;

import util.dump.stream.ExternalizableObjectStreamProvider;
import util.dump.stream.ObjectStreamProvider;


/**
 * 
 * <p>This class implements a type safe object input stream.</p>
 * 
 * @author Martin, Michael
 *
 */
public class DumpReader<E> implements DumpInput<E>, Iterator<E> {

   BufferedInputStream     inputbuffer;
   InputStream             primitiveInputStream;
   ObjectInput             objectInputStream;
   E                       nextObject          = null;
   boolean                 nextPrepared        = false;
   File                    sourceFile          = null;
   boolean                 deleteFileOnEOF     = false;

   // default configuration for the buffered output stream  
   public static final int DEFAULT_BUFFER_SIZE = 65536;

   /**
    * 
    * Convenience constructor allowing you to specify a File Object as source for 
    * the deserialization. The class will internally create the needed InputStream
    * obejcts. 
    * 
    * The class will retain a reference to the file object until it is closed. You can
    * also use the extended constructor in order to indicate this class to delete the
    * file automatically when its EOF is reached 
    * 
    * @param sourceFile source file to use in order to store the serialized objects
    * @throws FileNotFoundException
    * @throws IOException
    */
   public DumpReader( File sourceFile ) throws FileNotFoundException, IOException {
      this(sourceFile, false, DEFAULT_BUFFER_SIZE, null);
   }

   public DumpReader( File sourceFile, boolean deleteFileOnEOF, int buffersize, ObjectStreamProvider objectStreamProvider ) throws FileNotFoundException,
         IOException {
      initFile(sourceFile, deleteFileOnEOF, buffersize, objectStreamProvider);
   }

   public DumpReader( File sourceFile, boolean deleteFileOnEOF, ObjectStreamProvider objectStreamProvider ) throws FileNotFoundException, IOException {
      this(sourceFile, deleteFileOnEOF, DEFAULT_BUFFER_SIZE, objectStreamProvider);
   }

   /**
    * 
    * Convenience constructor allowing you to specify a File instance as source for 
    * the deserialization. The class will internally create the needed InputStreams. 
    * 
    * The class will retain a reference to the File until it is closed. You can
    * also use the extended constructor in order to indicate this class to delete the
    * file automatically when its EOF is reached 
    * 
    * @param sourceFile source file to use in order to store the serialized objects
    * @param buffersize size in bytes for the input stream, use "0" for no input buffering
    * @throws FileNotFoundException
    * @throws IOException
    */
   public DumpReader( File sourceFile, int buffersize ) throws FileNotFoundException, IOException {
      this(sourceFile, false, buffersize, null);
   }

   /**
    * Generic constructor accepting a standard InputStream to use in order to deserialize the 
    * type safe objects. 
    * 
    * The DumpReader instance will use a 64Kb input buffer. 
    *  
    * @param primitiveInputStream generic input stream to use in order to serialize the objects
    * @throws IOException
    */
   public DumpReader( InputStream primitiveInputStream ) throws IOException {
      this(primitiveInputStream, DEFAULT_BUFFER_SIZE);
   }

   /**
    * Generic constructor accepting a standard InputStream to use in order to deserialize the 
    * type safe objects. 
    * 
    * @param primitiveInputStream generic input stream to use in order to serialize the objects
    * @param buffersize size in bytes for the input stream, use "0" for no input buffering
    * @throws IOException
    */
   public DumpReader( InputStream primitiveInputStream, int buffersize ) throws IOException {
      this(primitiveInputStream, buffersize, null);
   }

   public DumpReader( InputStream primitiveInputStream, int buffersize, ObjectStreamProvider objectStreamProvider ) throws IOException {
      reset(primitiveInputStream, buffersize, objectStreamProvider);
   }

   /**
    * Closes the internal streams and, if present, releases the reference to the provided file.
    * 
    * By invoking this method the provided file will not be deleted, no matter which
    * value was assigned to the boolean parameter <code>deleteFileOnEOF</code> in the constructor - 
    * this happends automatically only when the stream reaches its EOF.
    * 
    * You only need using this method when a premature interruption of the reading process
    * is needed.
    * 
    * This method is invoqued automatically when <code>hasNext()</code> returns <code>false</code>.
    * 
    */
   public void close() throws IOException {
      closeStreams(false);
   }

   /**
    * Use at your own risk. Hacking here may corrupt the stream!
    */
   public ObjectInput getObjectInput() {
      return objectInputStream;
   }

   @SuppressWarnings("unchecked")
   public boolean hasNext() {
      if ( nextPrepared ) return nextObject != null;

      try {
         // reads the object and demands a object validation
         nextObject = (E)objectInputStream.readObject();
         nextPrepared = true;
         return true;
      }
      catch ( OptionalDataException e ) {
         nextObject = null;
         if ( e.eof ) {
            closeStreams(true);
            nextPrepared = true;
            return false;
         } else {
            throw new RuntimeException(e);
         }
      }
      catch ( EOFException e ) {
         nextObject = null;
         nextPrepared = true;
         closeStreams(true);
         return false;
      }
      catch ( Exception e ) {
         nextObject = null;
         throw new RuntimeException(e);
      }
   }

   public Iterator<E> iterator() {
      return this;
   }

   public E lastRead() {
      return nextObject;
   }

   public E next() {
      nextPrepared = false;
      return nextObject;
   }

   public void remove() {
      throw new UnsupportedOperationException("remove() not supported by DumpReader!");
   }

   public void reset( InputStream primitiveInputStream, int bufferSize, ObjectStreamProvider objectStreamProvider ) throws IOException {

      if ( objectInputStream != null ) objectInputStream.close();

      objectStreamProvider = objectStreamProvider == null ? new ExternalizableObjectStreamProvider() : objectStreamProvider;

      this.primitiveInputStream = primitiveInputStream;

      if ( bufferSize == 0 ) {
         this.inputbuffer = null;
         this.objectInputStream = objectStreamProvider.createObjectInput(primitiveInputStream);
      } else {
         this.inputbuffer = new BufferedInputStream(this.primitiveInputStream, bufferSize);
         this.objectInputStream = objectStreamProvider.createObjectInput(inputbuffer);
      }
   }

   @Override
   protected void finalize() throws Throwable {
      close();
      super.finalize();
   }

   // close streams and delete file if necessary
   void closeStreams( boolean isEOF ) {

      try {
         objectInputStream.close();

         if ( inputbuffer != null ) {
            inputbuffer.close();
         }

         primitiveInputStream.close();

         // and on demand deletes the file
         if ( isEOF && deleteFileOnEOF && this.sourceFile != null && !this.sourceFile.delete() ) { // 
            throw new IOException("could not delete file " + sourceFile.getAbsolutePath());
         }

         this.sourceFile = null;
      }
      catch ( IOException argh ) {
         throw new RuntimeException(argh);
      }
   }

   private void initFile( File fileForSource, boolean deleteFileOnEOF, int bufferSize, ObjectStreamProvider objectStreamProvider )
         throws FileNotFoundException, IOException {

      this.sourceFile = fileForSource;
      this.deleteFileOnEOF = deleteFileOnEOF;

      // optimizes memory by reducing the input buffer to the file size, only when applicable
      long fileLen = fileForSource.length();
      if ( fileLen > 0L && fileLen < bufferSize ) {
         bufferSize = (int)fileLen;
      }

      reset(new FileInputStream(this.sourceFile), bufferSize, objectStreamProvider);

   }
}
