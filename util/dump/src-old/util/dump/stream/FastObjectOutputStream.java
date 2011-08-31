package util.dump.stream;

import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;


public class FastObjectOutputStream extends DataOutputStream implements ObjectOutput {

   private ObjectOutputStream _objectOutputStream;
   private boolean            _resetPending = false;

   public FastObjectOutputStream( OutputStream out ) throws IOException {
      super(out);
      _objectOutputStream = new ObjectOutputStream(out);
   }

   @Override
   public void close() throws IOException {
      super.close();
      _objectOutputStream.close();
   }

   @Override
   public void flush() throws IOException {
      super.flush();
      _objectOutputStream.flush();
   }

   public void reset() throws IOException {
      _resetPending = true;
   }

   public void writeObject( Object obj ) throws IOException {
      if ( obj != null && obj instanceof Externalizable ) {
         writeBoolean(true);
         writeUTF(obj.getClass().getName());
         ((Externalizable)obj).writeExternal(this);
      } else {
         writeBoolean(false);
         if ( _resetPending ) {
            _objectOutputStream.reset();
            _resetPending = false;
         }
         _objectOutputStream.writeObject(obj);
      }
   }
}
