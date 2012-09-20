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


   private final Class _class;


   public SingleTypeObjectInputStream( InputStream in, Class c ) {
      super(in);
      _class = c;
   }

   public Object readObject() throws ClassNotFoundException, IOException {
      try {
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
   }

}
