package util.dump.stream;

import java.io.DataInputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.StreamCorruptedException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import util.dump.stream.ExternalizableObjectStreamProvider.InstanceType;


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
      boolean isNotNull = readBoolean();
      if ( isNotNull ) {
         byte instanceTypeId = readByte();
         InstanceType instanceType = InstanceType.forId(instanceTypeId);
         switch ( instanceType ) {
         case Externalizable:
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
         case String:
            return readUTF();
         case Date:
            return new Date(readLong());
         case UUID:
            return new UUID(readLong(), readLong());
         case Integer:
            return Integer.valueOf(readInt());
         case Double:
            return Double.valueOf(readDouble());
         case Float:
            return Float.valueOf(readFloat());
         case Long:
            return Long.valueOf(readLong());
         default:
            return _objectInputStream.readObject();
         }
      } else {
         return null;
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
