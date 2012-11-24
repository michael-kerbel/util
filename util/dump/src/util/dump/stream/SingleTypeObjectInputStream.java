package util.dump.stream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;

import util.io.IOUtils;


public class SingleTypeObjectInputStream<E extends Externalizable> extends DataInputStream implements ObjectInput {

   public static <T extends Externalizable> T readSingleInstance( Class<T> instanceClass, byte[] bytes ) {
      ByteArrayInputStream bytesInput = new ByteArrayInputStream(bytes);

      SingleTypeObjectInputStream in = new SingleTypeObjectInputStream(bytesInput, instanceClass);
      try {
         return (T)in.readObject();
      }
      catch ( Exception argh ) {
         throw new RuntimeException(argh);
      }
      finally {
         IOUtils.close(in);
      }
   }


   private final Class          _class;
   private Compression      _compressionType       = Compression.None;
   private ByteArrayInputStream _compressionByteBuffer = null;
   private InputStream          _originalIn            = null;


   public SingleTypeObjectInputStream( InputStream in, Class c ) {
      super(in);
      _class = c;
   }

   public SingleTypeObjectInputStream( InputStream in, Class c, Compression compressionType ) {
      this(in, c);
      _compressionType = compressionType;
   }

   public Object readObject() throws ClassNotFoundException, IOException {
      boolean restore = false;
      try {
         if ( _compressionType != Compression.None && _originalIn == null ) {
            _originalIn = in;
            restore = true;
            boolean compressed = readBoolean();
            if ( compressed ) {
               int length = readShort();
               if ( length == 0xffff ) {
                  length = readInt();
               }

               byte[] bytes = new byte[length];
               readFully(bytes);
               byte[] uncompressedBytes = _compressionType.uncompress(bytes);

               _originalIn = in;
               _compressionByteBuffer = new ByteArrayInputStream(uncompressedBytes);
               in = _compressionByteBuffer;
            }
         }

         Object obj = _class.newInstance();
         ((Externalizable)obj).readExternal(this);
         return obj;
      }
      catch ( IllegalAccessException e ) {
         throw new RuntimeException("Failed to instantiate " + _class, e);
      }
      catch ( InstantiationException e ) {
         throw new RuntimeException("Failed to instantiate " + _class, e);
      }
      finally {
         if ( restore ) {
            in = _originalIn;
            _originalIn = null;
            _compressionByteBuffer = null;
         }
      }
   }

}
