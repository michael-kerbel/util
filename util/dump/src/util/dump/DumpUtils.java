package util.dump;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.procedure.TIntIntProcedure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;


public class DumpUtils {

   private static final Logger LOG = Logger.getLogger(DumpUtils.class);


   /**
    * Calls <code>dump.add(e)</code> and catches any IOException.
    * @return true if the add operation did not throw any IOException, false otherwise
    */
   public static final <E> boolean addSilently( Dump<E> dump, E e ) {
      if ( dump != null ) {
         try {
            dump.add(e);
            return true;
         }
         catch ( IOException ex ) {}
      }
      return false;
   }

   /**
    * Copies non deleted elements from source to target. Tries to ignore and skip all corrupt elements.<p/>
    * <b>Beware:</b> Repairing corrupt elements only works if the element size in bytes is stable and not too many elements were deleted from the dump.
    * Also some retained elements might contain broken data afterwards, if the binary representation of the element is still externalizable. I.e. there
    * is no checksum mechanism in the dump (yet).
    * @throws RuntimeException if the cleanup fails
    */
   public static final <E> void cleanup( final Dump<E> source, final Dump<E> destination ) {
      try {
         source.flush();
         Iterator<E> iterator = source.new DeletionAwareDumpReader(source._dumpFile, source._streamProvider) {

            TIntIntMap _elementSizes = new TIntIntHashMap();


            @Override
            public boolean hasNext() {
               while ( true ) {
                  try {
                     long oldLastPos = _lastPos;
                     boolean hasNext = super.hasNext();
                     int size = (int)(_lastPos - oldLastPos);
                     _elementSizes.put(size, _elementSizes.get(size) + 1);
                     return hasNext;
                  }
                  catch ( Throwable e ) {
                     if ( e instanceof OutOfMemoryError || e.getMessage().contains("Failed to read externalized instance") ) {
                        // let's guess the next pos which is hopefully not also corrupt
                        long bytesRead = _positionAwareInputStream._rafPos - _lastPos;
                        long mostFrequentElementSize = getMostFrequentElementSize();
                        int bytesToSkip = (int)(mostFrequentElementSize - (bytesRead % mostFrequentElementSize));
                        try {
                           for ( int i = 0; i < bytesToSkip; i++ ) {
                              if ( _positionAwareInputStream.read() < 0 ) {
                                 return false;
                              }
                           }
                        }
                        catch ( IOException ee ) {
                           throw new RuntimeException("Failed to cleanup dump " + source.getDumpFile(), ee);
                        }
                        _lastPos += bytesRead + bytesToSkip;
                     } else {
                        throw new RuntimeException("Failed to cleanup dump " + source.getDumpFile(), e);
                     }
                  }
               }
            }

            private long getMostFrequentElementSize() {
               final int[] maxNumber = { 0 };
               final int[] mostFrequentSize = { 1 };
               _elementSizes.forEachEntry(new TIntIntProcedure() {

                  public boolean execute( int size, int number ) {
                     if ( number > maxNumber[0] ) {
                        mostFrequentSize[0] = size;
                        maxNumber[0] = number;
                     }
                     return true;
                  }
               });
               return Math.max(1, mostFrequentSize[0]);
            };
         }.iterator();

         for ( ; iterator.hasNext(); ) {
            E e = iterator.next();
            destination.add(e);
         }
      }
      catch ( IOException e ) {
         throw new RuntimeException("Failed to cleanup dump " + source.getDumpFile(), e);
      }
   }

   /**
    * Calls <code>dump.close()</code> and catches any IOException.
    */
   public static final void closeSilently( Dump<?> dump ) {
      if ( dump != null ) {
         try {
            dump.close();
         }
         catch ( IOException e ) {
            LOG.warn("Failed to close dump " + dump.getDumpFile(), e);
         }
      }
   }

   /**
    * <p>remove all files that belong to the dump (i.e. all indices, meta file, deletions file)</p>
    *
    * <p>Note: The dump must be closed before!</p>
    *
    * @throws IllegalArgumentException if the dump wasn't closed before
    * @throws IOException if the deletion failed
    */
   public static final void deleteDumpFiles( Dump<?> dump ) throws IOException {
      if ( !dump._isClosed ) {
         throw new IllegalArgumentException("dump wasn't closed");
      }

      if ( dump._dumpFile != null && dump._dumpFile.exists() ) {
         if ( !dump._dumpFile.delete() ) {
            throw new IOException("dump file couldn't be deleted");
         }
      }

      if ( dump._metaFile != null && dump._metaFile.exists() ) {
         if ( !dump._metaFile.delete() ) {
            throw new IOException("meta file couldn't be deleted");
         }
      }

      if ( dump._deletionsFile != null && dump._deletionsFile.exists() ) {
         if ( !dump._deletionsFile.delete() ) {
            throw new IOException("deletions file couldn't be deleted");
         }
      }
   }

   /**
    * delete all files that belong to the dump on exit ({@link java.io.File#deleteOnExit()})
    */
   public static final void deleteDumpFilesOnExit( Dump<?> dump ) {
      if ( dump._dumpFile != null ) {
         dump._dumpFile.deleteOnExit();
      }

      if ( dump._metaFile != null ) {
         dump._metaFile.deleteOnExit();
      }

      if ( dump._deletionsFile != null ) {
         dump._deletionsFile.deleteOnExit();
      }
   }

   /**
    * <p>removes and deletes all indices that are open for the given dump</p>
    *
    * <p>Note: The dump must be opened!</p>
    *
    * @throws IllegalArgumentException if the dump is closed
    * @throws IOException if an index couldn't be removed
    */
   public static final void deleteDumpIndexFiles( Dump<?> dump ) throws IOException {
      if ( dump._isClosed ) {
         throw new IllegalArgumentException("dump is closed");
      }

      for ( DumpIndex<?> index : new ArrayList<DumpIndex<?>>(dump._indexes) ) {
         index.close();
         index.deleteAllIndexFiles();
      }
      if ( dump._indexes.size() > 0 ) {
         throw new IllegalStateException("not all indices could be closed and deleted");
      }
   }

   /**
    * Calls <code>dump.updateLast(e)</code> and catches any IOException.
    * @return null if update was not successfull
    */
   public static final <E> E updateLastSilently( Dump<E> dump, E e ) {
      if ( dump != null ) {
         try {
            return dump.updateLast(e);
         }
         catch ( IOException ex ) {}
      }
      return null;
   }
}
