package util.dump.stream;

import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.OutputStream;


public class SingleTypeObjectOutputStream<E extends Externalizable> extends DataOutputStream implements ObjectOutput {

   private final Class<?> _class;

   public SingleTypeObjectOutputStream( OutputStream out, Class<?> c ) {
      super(out);
      _class = c;
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

      ((Externalizable)obj).writeExternal(this);
   }
}
