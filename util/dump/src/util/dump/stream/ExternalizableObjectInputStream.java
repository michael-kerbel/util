package util.dump.stream;

import java.io.DataInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.HashMap;
import java.util.Map;


public class ExternalizableObjectInputStream extends DataInputStream implements ObjectInput {

   private Map<String, Class> _classes = new HashMap<String, Class>();
   private ObjectInputStream  _objectInputStream;


   public ExternalizableObjectInputStream( InputStream in ) throws IOException {
      super(in);
      _objectInputStream = new ObjectInputStream(in) {

         @Override
         protected void readStreamHeader() throws IOException, StreamCorruptedException {
         // do nothing
         }
      };
   }

   @Override
   public void close() throws IOException {
      super.close();
      _objectInputStream.close();
   }

   public Object readObject() throws ClassNotFoundException, IOException {
      boolean externalizable = readBoolean();
      if ( externalizable ) {
         String className = readUTF();
         try {
            Object obj = getClass(className).newInstance();
            ((Externalizable)obj).readExternal(this);
            return obj;
         }
         catch ( IllegalAccessException e ) {
            throw new RuntimeException("Failed to instantiate " + className, e);
         }
         catch ( InstantiationException e ) {
            throw new RuntimeException("Failed to instantiate " + className, e);
         }
      } else {
         return _objectInputStream.readObject();
      }
   }

   private Class getClass( String className ) throws ClassNotFoundException {
      Class c = _classes.get(className);
      if ( c == null ) {
         c = Class.forName(className);
         _classes.put(className, c);
      }
      return c;
   }

}
