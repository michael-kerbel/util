package util.dump.stream;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;

import util.io.IOUtils;


public class SingleTypeObjectOutputStream<E extends Externalizable> extends DataOutputStream implements ObjectOutput {

   public static byte[] writeSingleInstance( Externalizable e ) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      SingleTypeObjectOutputStream out = new SingleTypeObjectOutputStream<Externalizable>(bytes, e.getClass());
      try {
         e.writeExternal(out);
      }
      catch ( IOException argh ) {
         throw new RuntimeException(argh);
      }
      finally {
         IOUtils.close(out);
      }

      return bytes.toByteArray();
   }


   private final Class<?>        _class;
   private Compression       _compressionType       = Compression.None;
   private ByteArrayOutputStream _compressionByteBuffer = null;
   private OutputStream          _originalOut           = null;


   public SingleTypeObjectOutputStream( OutputStream out, Class<?> c ) {
      super(out);
      _class = c;
   }

   public SingleTypeObjectOutputStream( OutputStream out, Class<?> c, Compression compressionType ) {
      this(out, c);
      _compressionType = compressionType;
      _compressionByteBuffer = new ByteArrayOutputStream();
   }

   public void writeObject( Object obj ) throws IOException {
      if ( obj == null ) {
         throw new IOException("Object is null");
      }

      Class<?> objClass = obj.getClass();

      if ( !(obj instanceof Externalizable) ) {
         throw new IOException("Object with class " + objClass.getName() + " is not Externalizable");
      }
      if ( objClass != _class ) {
         throw new IOException("Object has wrong class: " + objClass);
      }

      boolean restore = false;
      if ( _compressionType != Compression.None && _originalOut == null ) {
         restore = true;
         _originalOut = out;
         _compressionByteBuffer.reset();
         out = _compressionByteBuffer;
      }

      ((Externalizable)obj).writeExternal(this);

      if ( restore ) {
         byte[] bytes = _compressionByteBuffer.toByteArray();
         byte[] compressedBytes = _compressionType.compress(bytes);
         out = _originalOut;

         if ( compressedBytes.length + 6 < bytes.length ) {
            out.write(1);

            if ( compressedBytes.length >= 65535 ) {
               out.write(0xff);
               out.write(0xff);
               out.write((compressedBytes.length >>> 24) & 0xFF);
               out.write((compressedBytes.length >>> 16) & 0xFF);
            }
            out.write((compressedBytes.length >>> 8) & 0xFF);
            out.write((compressedBytes.length >>> 0) & 0xFF);

            out.write(compressedBytes);
         } else {
            out.write(0);
            out.write(bytes);
         }
         _originalOut = null;
      }
   }
}
