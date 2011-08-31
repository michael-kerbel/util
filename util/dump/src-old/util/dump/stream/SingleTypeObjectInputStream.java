package util.dump.stream;

import java.io.DataInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;

public class SingleTypeObjectInputStream<E extends Externalizable> extends DataInputStream implements ObjectInput {

   private final Class _class;

   public SingleTypeObjectInputStream( InputStream in, Class c ) throws IOException {
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



