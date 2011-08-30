package util.dump;

import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.reflection.FieldAccessor;
import util.reflection.FieldFieldAccessor;
import util.reflection.MethodFieldAccessor;


/**
 * This class provides an easy way to make beans <code>Externalizable</code>.<p/>
 * 
 * All you have to do is extends this class with your bean and add the <code>@externalize</code> annotation 
 * for each field or getter setter pair. This annotation has a parameter where you set unique, non-reusable indexes. 
 *  
 * The serialization and deserialization works even with different revisions of your bean. It is both downward and upward 
 * compatible, i.e. you can add and remove fields or getter setter pairs as you like and your binary representation will
 * stay readable by both the new and the old version of your bean.<p/>
 * 
 * <b>Limitations:</b>
 * <ul><li>
 * Downward and upward compatibility will not work, if you reuse indexes between different revisions of your bean.
 * </li><li>
 * While externalization with this method is about 3-6 times faster than serialization (depending on the amount 
 * of non-primitive or array members), hand written externalization is still about 40% faster, because no reflection 
 * has to take place. This method of serialization is as fast as Google's protobuffers. For optimal performance use 
 * jre 1.6 and the <code>-server</code> switch.
 * </li><li>
 * All types are allowed for your members, but if your member is not included in the following list of supported
 * types, serialization falls back to normal java.io.Serializable mechanisms by using {@link ObjectOutput.writeObject(Object)},
 * which are slow and break downward and upward compatibility. 
 * These are the supported types (see also {@link Externalizer.FieldType}):
 * <ul><li>
 * primitive fields (<code>int</code>, <code>float</code>, ...) and single-dimensional arrays containing primitives   
 * </li><li>
 * all <code>Number</code> classes (<code>Integer</code>, <code>Float</code>, ...)
 * </li><li>
 * <code>String</code> and <code>String[]</code> 
 * </li><li>
 * <code>Date</code> and <code>Date[]</code>
 * </li><li>
 * single and two-dimensional arrays of any <code>Externalizable</code>
 * </li><li>
 * generic Lists of any <code>Externalizable</code> type, i.e. <code>List&lt;Externalizable&gt;</code>
 * </li>
 * </ul>
 * Currently unsupported (i.e. slow and not compatible with {@link util.dump.stream.SingleTypeObjectStreamProvider}) 
 * are multi-dimensional primitive arrays, any array of <code>Numbers</code>, multi-dim <code>String</code> or 
 * <code>Date</code> arrays, and <code>Maps</code>.<p/>
 * For ever unsupported are types not implementing <code>Externalizable</code> or any arrays of collections with such types. 
 * </li><li>
 * While annotated fields can be any of public, protected, package protected or private, annotated methods must be public. 
 * </li>
 * </ul>
 * @see {@link util.dump.ExternalizerTest} 
 */
public class Externalizer implements Externalizable {

   protected static final long            serialVersionUID = -1816997029156670474L;

   private static Map<Class, ClassConfig> CLASS_CONFIGS    = new HashMap<Class, ClassConfig>();

   private Class                          _clientClass;
   private ClassConfig                    _config;

   public Externalizer() {
      _clientClass = getClass();
      init();
   }

   public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException {
      try {
         int fieldNumberToRead = in.readByte();

         FieldAccessor[] fieldAccessors = _config._fieldAccessors;
         byte[] fieldIndexes = _config._fieldIndexes;
         FieldType[] fieldTypes = _config._fieldTypes;
         int j = 0;
         for ( int i = 0; i < fieldNumberToRead; i++ ) {
            byte fieldIndex = in.readByte();
            byte fieldTypeId = in.readByte();

            /* We expect fields to be stored in ascending fieldIndex order. 
             * That's why we can find the appropriate fieldIndex in our sorted fieldIndexes array by skipping. */
            while ( fieldIndexes[j] < fieldIndex && j < fieldIndexes.length - 1 ) {
               j++;
            }

            FieldAccessor f = null;
            FieldType ft = null;
            if ( fieldIndexes[j] == fieldIndex ) {
               f = fieldAccessors[j];
               ft = fieldTypes[j];
            } else { // unknown field
               ft = FieldType.forId(fieldTypeId);
            }

            switch ( ft ) {
            case pInt: {
               int d = in.readInt();
               if ( f != null ) {
                  f.setInt(this, d);
               }
               break;
            }
            case pBoolean: {
               boolean d = in.readBoolean();
               if ( f != null ) {
                  f.setBoolean(this, d);
               }
               break;
            }
            case pByte: {
               byte d = in.readByte();
               if ( f != null ) {
                  f.setByte(this, d);
               }
               break;
            }
            case pChar: {
               char d = in.readChar();
               if ( f != null ) {
                  f.setChar(this, d);
               }
               break;
            }
            case pDouble: {
               double d = in.readDouble();
               if ( f != null ) {
                  f.setDouble(this, d);
               }
               break;
            }
            case pFloat: {
               float d = in.readFloat();
               if ( f != null ) {
                  f.setFloat(this, d);
               }
               break;
            }
            case pLong: {
               long d = in.readLong();
               if ( f != null ) {
                  f.setLong(this, d);
               }
               break;
            }
            case pShort: {
               short d = in.readShort();
               if ( f != null ) {
                  f.setShort(this, d);
               }
               break;
            }
            case String: {
               String s = readString(in);
               if ( f != null ) {
                  f.set(this, s);
               }
               break;
            }
            case Date: {
               Date d = readDate(in);
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Externalizable: {
               Externalizable instance = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  Class<Externalizable> c = (Class<Externalizable>)Class.forName(in.readUTF());
                  instance = c.newInstance();
                  instance.readExternal(in);
               }
               if ( f != null ) {
                  f.set(this, instance);
               }
               break;
            }
            case Integer: {
               Integer d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readInt();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Boolean: {
               Boolean d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readBoolean();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Byte: {
               Byte d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readByte();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Character: {
               Character d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readChar();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Double: {
               Double d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readDouble();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Float: {
               Float d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readFloat();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Long: {
               Long d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readLong();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case Short: {
               Short d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = in.readShort();
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case pByteArray: {
               byte[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new byte[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = in.readByte();
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case pDoubleArray: {
               double[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new double[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = in.readDouble();
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case pFloatArray: {
               float[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new float[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = in.readFloat();
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case pIntArray: {
               int[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new int[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = in.readInt();
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case pLongArray: {
               long[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new long[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = in.readLong();
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case StringArray: {
               String[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new String[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = readString(in);
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case DateArray: {
               Date[] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  d = new Date[in.readInt()];
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = readDate(in);
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case ListOfExternalizables: {
               readListOfExternalizables(in, f);
               break;
            }
            case ExternalizableArray: {
               Externalizable[] d = readExternalizableArray(in);
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            case ExternalizableArrayArray: {
               Externalizable[][] d = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  int size = in.readInt();
                  // TODO [MKR 12.10.2008] improvable by caching, but beware of threading issues (link to in?) and unneccessary loss of performance with hash lookups 
                  String className = in.readUTF();
                  Class externalizableClass = Class.forName(className);
                  d = (Externalizable[][])Array.newInstance(externalizableClass, size);
                  for ( int k = 0, length = d.length; k < length; k++ ) {
                     d[k] = readExternalizableArray(in);
                  }
               }
               if ( f != null ) {
                  f.set(this, d);
               }
               break;
            }
            default: {
               Object o = null;
               boolean isNotNull = in.readBoolean();
               if ( isNotNull ) {
                  o = in.readObject();
               }
               if ( f != null ) {
                  f.set(this, o);
               }
               //               throw new IllegalArgumentException("The field type " + fieldTypes[i] + " in class " + _clientClass
               //                  + " is unsupported by util.dump.Externalizer.");
            }
            }
         }
      }
      catch ( EOFException e ) {
         throw e;
      }
      catch ( Exception e ) {
         throw new RuntimeException("Failed to read externalized instance. Maybe the field order was changed? class " + _clientClass, e);
      }
   }

   public void writeExternal( ObjectOutput out ) throws IOException {
      try {
         FieldAccessor[] fieldAccessors = _config._fieldAccessors;
         byte[] fieldIndexes = _config._fieldIndexes;
         FieldType[] fieldTypes = _config._fieldTypes;
         out.writeByte(fieldAccessors.length);
         for ( int i = 0, length = fieldAccessors.length; i < length; i++ ) {
            FieldAccessor f = fieldAccessors[i];
            FieldType ft = fieldTypes[i];

            out.writeByte(fieldIndexes[i]);
            out.writeByte(ft._id);

            switch ( ft ) {
            case pInt: {
               out.writeInt(f.getInt(this));
               break;
            }
            case pBoolean: {
               out.writeBoolean(f.getBoolean(this));
               break;
            }
            case pByte: {
               out.writeByte(f.getByte(this));
               break;
            }
            case pChar: {
               out.writeChar(f.getChar(this));
               break;
            }
            case pDouble: {
               out.writeDouble(f.getDouble(this));
               break;
            }
            case pFloat: {
               out.writeFloat(f.getFloat(this));
               break;
            }
            case pLong: {
               out.writeLong(f.getLong(this));
               break;
            }
            case pShort: {
               out.writeShort(f.getShort(this));
               break;
            }
            case String: {
               String s = (String)f.get(this);
               writeString(out, s);
               break;
            }
            case Date: {
               Date s = (Date)f.get(this);
               writeDate(out, s);
               break;
            }
            case Externalizable: {
               Externalizable instance = (Externalizable)f.get(this);
               out.writeBoolean(instance != null);
               if ( instance != null ) {
                  Class c = instance.getClass();
                  // we have to write the class name, because the reading class might not know this field and therefore cannot know the defaultType 
                  out.writeUTF(c.getName());
                  instance.writeExternal(out);
               }
               break;
            }
            case Integer: {
               Integer s = (Integer)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeInt(s);
               }
               break;
            }
            case Boolean: {
               Boolean s = (Boolean)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeBoolean(s);
               }
               break;
            }
            case Byte: {
               Byte s = (Byte)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeByte(s);
               }
               break;
            }
            case Character: {
               Character s = (Character)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeChar(s);
               }
               break;
            }
            case Double: {
               Double s = (Double)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeDouble(s);
               }
               break;
            }
            case Float: {
               Float s = (Float)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeFloat(s);
               }
               break;
            }
            case Long: {
               Long s = (Long)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeLong(s);
               }
               break;
            }
            case Short: {
               Short s = (Short)f.get(this);
               out.writeBoolean(s != null);
               if ( s != null ) {
                  out.writeShort(s);
               }
               break;
            }
            case pByteArray: {
               byte[] d = (byte[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     out.writeByte(d[j]);
                  }
               }
               break;
            }
            case pDoubleArray: {
               double[] d = (double[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     out.writeDouble(d[j]);
                  }
               }
               break;
            }
            case pFloatArray: {
               float[] d = (float[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     out.writeFloat(d[j]);
                  }
               }
               break;
            }
            case pIntArray: {
               int[] d = (int[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     out.writeInt(d[j]);
                  }
               }
               break;
            }
            case pLongArray: {
               long[] d = (long[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     out.writeLong(d[j]);
                  }
               }
               break;
            }
            case StringArray: {
               String[] d = (String[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     writeString(out, d[j]);
                  }
               }
               break;
            }
            case DateArray: {
               Date[] d = (Date[])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     writeDate(out, d[j]);
                  }
               }
               break;
            }
            case ListOfExternalizables: {
               writeListOfExternalizables(out, f);
               break;
            }
            case ExternalizableArray: {
               Externalizable[] d = (Externalizable[])f.get(this);
               writeExternalizableArray(out, d);
               break;
            }
            case ExternalizableArrayArray: {
               Externalizable[][] d = (Externalizable[][])f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeInt(d.length);
                  Class defaultType = f.getType().getComponentType();
                  out.writeUTF(defaultType.getName());
                  for ( int j = 0, llength = d.length; j < llength; j++ ) {
                     writeExternalizableArray(out, d[j]);
                  }
               }
               break;
            }
            default:
               Object d = f.get(this);
               out.writeBoolean(d != null);
               if ( d != null ) {
                  out.writeObject(d);
               }
               //               throw new IllegalArgumentException("The field type " + fieldTypes[i] + " in class " + _clientClass
               //                  + " is unsupported by util.dump.Externalizer.");
            }
         }
      }
      catch ( Exception e ) {
         throw new RuntimeException("Failed to externalize. class " + _clientClass, e);
      }
   }

   private void init() {
      _config = CLASS_CONFIGS.get(_clientClass);
      if ( _config == null ) {
         _config = new ClassConfig(_clientClass);
         synchronized ( CLASS_CONFIGS ) {
            CLASS_CONFIGS.put(_clientClass, _config);
         }
      }
   }

   private Date readDate( ObjectInput in ) throws IOException {
      Date d = null;
      boolean isNotNull = in.readBoolean();
      if ( isNotNull ) {
         d = new Date(in.readLong());
      }
      return d;
   }

   private Externalizable[] readExternalizableArray( ObjectInput in ) throws Exception {
      Externalizable[] d = null;
      boolean isNotNull = in.readBoolean();
      if ( isNotNull ) {
         int size = in.readInt();
         // TODO [MKR 12.10.2008] improvable by caching, but beware of threading issues (link to in?) and unneccessary loss of performance with hash lookups 
         String className = in.readUTF();
         Class<? extends Externalizable> externalizableClass = (Class<? extends Externalizable>)Class.forName(className);
         d = (Externalizable[])Array.newInstance(externalizableClass, size);
         Class lastNonDefaultClass = null;
         for ( int k = 0; k < size; k++ ) {
            Externalizable instance = null;
            isNotNull = in.readBoolean();
            if ( isNotNull ) {
               boolean isDefaultType = in.readBoolean();
               if ( isDefaultType ) {
                  instance = externalizableClass.newInstance();
               } else {
                  boolean isSameAsLastNonDefault = in.readBoolean();
                  Class c;
                  if ( isSameAsLastNonDefault ) {
                     c = lastNonDefaultClass;
                  } else {
                     c = Class.forName(in.readUTF());
                     lastNonDefaultClass = c;
                  }
                  instance = (Externalizable)c.newInstance();
               }
               instance.readExternal(in);
            }
            d[k] = instance;
         }
      }
      return d;
   }

   private void readListOfExternalizables( ObjectInput in, FieldAccessor f ) throws Exception {
      List d = null;
      boolean isNotNull = in.readBoolean();
      if ( isNotNull ) {
         boolean isArrayList = in.readBoolean();
         int size = in.readInt();
         if ( isArrayList ) {
            d = new ArrayList(size);
         } else {
            // TODO [MKR 12.10.2008] improvable by caching, but beware of threading issues (link to in?) and unneccessary loss of performance with hash lookups 
            String className = in.readUTF();
            Class c = Class.forName(className);
            d = (List)c.newInstance();
         }
         String defaultContentClassName = in.readUTF();
         Class<? extends Externalizable> externalizableClass = (Class<Externalizable>)Class.forName(defaultContentClassName);

         Class lastNonDefaultClass = null;
         for ( int k = 0; k < size; k++ ) {
            Externalizable instance = null;
            isNotNull = in.readBoolean();
            if ( isNotNull ) {
               boolean isDefaultType = in.readBoolean();
               if ( isDefaultType ) {
                  instance = externalizableClass.newInstance();
               } else {
                  boolean isSameAsLastNonDefault = in.readBoolean();
                  Class c;
                  if ( isSameAsLastNonDefault ) {
                     c = lastNonDefaultClass;
                  } else {
                     c = Class.forName(in.readUTF());
                     lastNonDefaultClass = c;
                  }
                  instance = (Externalizable)c.newInstance();
               }
               instance.readExternal(in);
            }
            d.add(instance);
         }
      }
      if ( f != null ) {
         f.set(this, d);
      }
   }

   private String readString( ObjectInput in ) throws IOException {
      String s = null;
      boolean isNotNull = in.readBoolean();
      if ( isNotNull ) {
         s = in.readUTF();
      }
      return s;
   }

   private void writeDate( ObjectOutput out, Date s ) throws IOException {
      out.writeBoolean(s != null);
      if ( s != null ) {
         out.writeLong(s.getTime());
      }
   }

   private void writeExternalizableArray( ObjectOutput out, Externalizable[] d ) throws Exception, IOException {
      out.writeBoolean(d != null);
      if ( d != null ) {
         out.writeInt(d.length);
         Class defaultType = d.getClass().getComponentType();
         out.writeUTF(defaultType.getName());

         Class lastNonDefaultClass = null;
         for ( int j = 0, llength = d.length; j < llength; j++ ) {
            Externalizable instance = d[j];
            out.writeBoolean(instance != null);
            if ( instance != null ) {
               Class c = instance.getClass();
               boolean isDefaultType = c.equals(defaultType);
               out.writeBoolean(isDefaultType);
               if ( !isDefaultType ) {
                  boolean isSameAsLastNonDefault = c.equals(lastNonDefaultClass);
                  out.writeBoolean(isSameAsLastNonDefault);
                  if ( !isSameAsLastNonDefault ) {
                     out.writeUTF(c.getName());
                     lastNonDefaultClass = c;
                  }
               }
               instance.writeExternal(out);
            }
         }
      }

   }

   private void writeListOfExternalizables( ObjectOutput out, FieldAccessor f ) throws Exception, IOException {
      List d = (List)f.get(this);
      out.writeBoolean(d != null);
      if ( d != null ) {
         Class listClass = d.getClass();
         boolean isArrayList = listClass.equals(ArrayList.class);
         out.writeBoolean(isArrayList);
         out.writeInt(d.size());
         if ( !isArrayList ) {
            out.writeUTF(listClass.getName());
         }
         Class defaultType = f.getGenericTypes()[0];
         out.writeUTF(defaultType.getName());

         Class lastNonDefaultClass = null;
         for ( int j = 0, llength = d.size(); j < llength; j++ ) {
            Externalizable instance = (Externalizable)d.get(j);
            out.writeBoolean(instance != null);
            if ( instance != null ) {
               Class c = instance.getClass();
               boolean isDefaultType = c.equals(defaultType);
               out.writeBoolean(isDefaultType);
               if ( !isDefaultType ) {
                  boolean isSameAsLastNonDefault = c.equals(lastNonDefaultClass);
                  out.writeBoolean(isSameAsLastNonDefault);
                  if ( !isSameAsLastNonDefault ) {
                     out.writeUTF(c.getName());
                     lastNonDefaultClass = c;
                  }
               }
               instance.writeExternal(out);
            }
         }
      }
   }

   private void writeString( ObjectOutput out, String s ) throws IOException {
      out.writeBoolean(s != null);
      if ( s != null ) {
         out.writeUTF(s);
      }
   }

   /**
    * It's enough to annotate either the getter or the setter. Of course you can also annotate both, but the indexes must match.
    */
   @Retention(RetentionPolicy.RUNTIME)
   @Target( { ElementType.FIELD, ElementType.METHOD })
   public @interface externalize {

      public byte value();
   }

   public enum FieldType {
      pInt(int.class, 0), // 
      pBoolean(boolean.class, 1), // 
      pByte(byte.class, 2), // 
      pChar(char.class, 3), // 
      pDouble(double.class, 4), // 
      pFloat(float.class, 5), // 
      pLong(long.class, 6), // 
      pShort(short.class, 7), // 
      String(String.class, 8), // 
      Date(Date.class, 9), // 
      Integer(Integer.class, 10), // 
      Boolean(Boolean.class, 11), // 
      Byte(Byte.class, 12), // 
      Character(Character.class, 13), // 
      Double(Double.class, 14), // 
      Float(Float.class, 15), // 
      Long(Long.class, 16), // 
      Short(Short.class, 17), // 
      Externalizable(Externalizable.class, 18), // 
      StringArray(String[].class, 19), // 
      DateArray(Date[].class, 20), // 
      pIntArray(int[].class, 21), // 
      pByteArray(byte[].class, 22), // 
      pDoubleArray(double[].class, 23), // 
      pFloatArray(float[].class, 24), // 
      pLongArray(long[].class, 25), // 
      ListOfExternalizables(List.class, 26), // 
      ExternalizableArray(Externalizable[].class, 27), //
      ExternalizableArrayArray(Externalizable[][].class, 28), //
      Object(Object.class, 29), //
      // TODO add Set, Map (beware of Treemaps & -sets using custom Comparators!)
      ;

      private static final Map<Class, FieldType> _classLookup = new HashMap<Class, FieldType>(FieldType.values().length);
      private static final FieldType[]           _idLookup    = new FieldType[127];
      static {
         for ( FieldType ft : FieldType.values() ) {
            if ( _classLookup.get(ft._class) != null ) {
               throw new Error("Implementation mistake: FieldType._class must be unique! " + ft._class);
            }
            _classLookup.put(ft._class, ft);
            if ( _idLookup[ft._id] != null ) {
               throw new Error("Implementation mistake: FieldType._id must be unique! " + ft._id);
            }
            _idLookup[ft._id] = ft;
         }
      }

      public static final FieldType forClass( Class c ) {
         return _classLookup.get(c);
      }

      public static final FieldType forId( byte id ) {
         return _idLookup[id];
      }

      private final Class _class;
      private final byte  _id;

      private FieldType( Class c, int id ) {
         _class = c;
         _id = (byte)id;
      }
   }

   private class ClassConfig {

      Class           _class;
      FieldAccessor[] _fieldAccessors;
      byte[]          _fieldIndexes;
      FieldType[]     _fieldTypes;

      public ClassConfig( Class clientClass ) {
         try {
            clientClass.getConstructor();
         }
         catch ( NoSuchMethodException argh ) {
            throw new RuntimeException(_class + " extends Externalizer, but does not have a public nullary constructor.");
         }

         _class = clientClass;
         List<FieldInfo> fieldInfos = new ArrayList<FieldInfo>();

         Class c = _clientClass;
         while ( c != Object.class ) {
            for ( Field f : c.getDeclaredFields() ) {
               int mod = f.getModifiers();
               if ( Modifier.isFinal(mod) || Modifier.isStatic(mod) ) {
                  continue;
               }
               externalize annotation = f.getAnnotation(externalize.class);
               if ( annotation == null ) {
                  continue;
               }

               if ( !Modifier.isPublic(mod) ) {
                  f.setAccessible(true); // enable access to the field - ...hackity hack
               }

               FieldInfo fi = new FieldInfo();
               fi._fieldAccessor = new FieldFieldAccessor(f);
               Class type = f.getType();
               FieldType ft = getFieldType(type);
               if ( ft == null ) {
                  ft = FieldType.Object;
                  //               throw new RuntimeException(_class + " extends Externalizer, but the member variable " + f.getName() + " has an unsupported type: " + type);
               }
               if ( ft == FieldType.ListOfExternalizables
                  && (fi._fieldAccessor.getGenericTypes().length != 1 || !Externalizable.class.isAssignableFrom(fi._fieldAccessor.getGenericTypes()[0])) ) {
                  ft = FieldType.Object;
                  //               throw new RuntimeException(_class + " extends Externalizer, but the member variable " + f.getName()
                  //                  + " has a generic List with an unsupported type: " + type + " - the generic type of a list must be Externalizable");
               }
               fi._fieldType = ft;

               byte index = annotation.value();
               for ( FieldInfo ffi : fieldInfos ) {
                  if ( ffi._fieldIndex == index ) {
                     throw new RuntimeException(_class + " extends Externalizer, but the externalizable field " + f.getName() + " has a non-unique index "
                        + index);
                  }
               }
               fi._fieldIndex = index;
               fieldInfos.add(fi);
            }
            c = c.getSuperclass();
         }

         methodLoop: for ( Method m : _clientClass.getMethods() ) {
            int mod = m.getModifiers();
            if ( Modifier.isStatic(mod) ) {
               continue;
            }

            Method getter = null, setter = null;
            if ( m.getName().startsWith("get") || (m.getName().startsWith("is") && m.getReturnType() == boolean.class) ) {
               getter = m;
            } else if ( m.getName().startsWith("set") ) {
               setter = m;
            } else {
               continue;
            }

            if ( getter != null ) {
               for ( FieldInfo ffi : fieldInfos ) {
                  if ( ffi._fieldAccessor instanceof MethodFieldAccessor ) {
                     MethodFieldAccessor mfa = (MethodFieldAccessor)ffi._fieldAccessor;
                     if ( mfa.getGetter().getName().equals(getter.getName()) ) {
                        continue methodLoop;
                     }
                  }
               }

               Class type = getter.getReturnType();
               if ( getter.getParameterTypes().length > 0 ) {
                  externalize getterAnnotation = getter.getAnnotation(externalize.class);
                  if ( getterAnnotation != null ) {
                     throw new RuntimeException(_class + " extends Externalizer, but the annotated getter method " + getter.getName() + " has a parameter.");
                  } else {
                     continue;
                  }
               }

               try {
                  String name = getter.getName();
                  name = getter.getName().startsWith("is") ? name.substring(2) : name.substring(3);
                  setter = _clientClass.getMethod("set" + name, type);
               }
               catch ( NoSuchMethodException e ) {
                  externalize getterAnnotation = getter.getAnnotation(externalize.class);
                  if ( getterAnnotation != null ) {
                     throw new RuntimeException(_class + " extends Externalizer, but the annotated getter method " + getter.getName()
                        + " has no appropriate setter with the correct parameter.");
                  } else {
                     continue;
                  }
               }
            } else if ( setter != null ) {
               for ( FieldInfo ffi : fieldInfos ) {
                  if ( ffi._fieldAccessor instanceof MethodFieldAccessor ) {
                     MethodFieldAccessor mfa = (MethodFieldAccessor)ffi._fieldAccessor;
                     if ( mfa.getSetter().getName().equals(setter.getName()) ) {
                        continue methodLoop;
                     }
                  }
               }

               if ( setter.getParameterTypes().length != 1 ) {
                  externalize setterAnnotation = setter.getAnnotation(externalize.class);
                  if ( setterAnnotation != null ) {
                     throw new RuntimeException(_class + " extends Externalizer, but the annotated setter method " + setter.getName()
                        + " does not have a single parameter.");
                  } else {
                     continue;
                  }
               }
               Class type = setter.getParameterTypes()[0];

               try {
                  getter = _clientClass.getMethod("get" + setter.getName().substring(3));
               }
               catch ( NoSuchMethodException e ) {
                  externalize setterAnnotation = setter.getAnnotation(externalize.class);
                  if ( setterAnnotation != null ) {
                     throw new RuntimeException(_class + " extends Externalizer, but the annotated setter method " + setter.getName()
                        + " has no appropriate getter.");
                  } else {
                     continue;
                  }
               }

               if ( getter.getReturnType() != type ) {
                  externalize setterAnnotation = setter.getAnnotation(externalize.class);
                  externalize getterAnnotation = getter.getAnnotation(externalize.class);
                  if ( getterAnnotation != null || setterAnnotation != null ) {
                     throw new RuntimeException(_class + " extends Externalizer, but the annotated setter method " + setter.getName()
                        + " has no getter with the correct return type.");
                  } else {
                     continue;
                  }
               }
            }

            Class type = getter.getReturnType();

            externalize getterAnnotation = getter.getAnnotation(externalize.class);
            externalize setterAnnotation = setter.getAnnotation(externalize.class);
            if ( getterAnnotation == null && setterAnnotation == null ) {
               continue;
            }
            if ( getterAnnotation != null && setterAnnotation != null && getterAnnotation.value() != setterAnnotation.value() ) {
               throw new RuntimeException(_class + " extends Externalizer, but the getter/setter pair " + getter.getName()
                  + " has different indexes in the externalize annotations.");
            }
            externalize annotation = getterAnnotation == null ? setterAnnotation : getterAnnotation;

            FieldInfo fi = new FieldInfo();
            fi._fieldAccessor = new MethodFieldAccessor(getter, setter);
            FieldType ft = getFieldType(type);
            if ( ft == null ) {
               ft = FieldType.Object;
               //               throw new RuntimeException(_class + " extends Externalizer, but the getter/setter pair " + getter.getName() + " has an unsupported type: "
               //                  + type);
            }
            if ( ft == FieldType.ListOfExternalizables
               && (fi._fieldAccessor.getGenericTypes().length != 1 || !Externalizable.class.isAssignableFrom(fi._fieldAccessor.getGenericTypes()[0])) ) {
               ft = FieldType.Object;
               //                           throw new RuntimeException(_class + " extends Externalizer, but the getter/setter pair " + getter.getName()
               //                              + " has a generic List with an unsupported type: " + type + " - the generic type of a list must be Externalizable");
            }
            fi._fieldType = ft;

            byte index = annotation.value();
            for ( FieldInfo ffi : fieldInfos ) {
               if ( ffi._fieldIndex == index ) {
                  throw new RuntimeException(_class + " extends Externalizer, but the getter/setter pair " + getter.getName() + " has a non-unique index "
                     + index);
               }
            }
            fi._fieldIndex = index;
            fieldInfos.add(fi);
         }

         Collections.sort(fieldInfos);

         _fieldAccessors = new FieldAccessor[fieldInfos.size()];
         _fieldIndexes = new byte[fieldInfos.size()];
         _fieldTypes = new FieldType[fieldInfos.size()];
         for ( int i = 0, length = fieldInfos.size(); i < length; i++ ) {
            FieldInfo fi = fieldInfos.get(i);
            _fieldAccessors[i] = fi._fieldAccessor;
            _fieldIndexes[i] = fi._fieldIndex;
            _fieldTypes[i] = fi._fieldType;
         }
         if ( _fieldAccessors.length == 0 ) {
            throw new RuntimeException(_class + " extends Externalizer, but it has no externalizable fields or methods. "
               + "This is most probably a bug. Externalizable fields and methods must be public.");
         }
      }

      private FieldType getFieldType( Class type ) {
         FieldType ft = FieldType.forClass(type);
         if ( ft == null ) {
            if ( Externalizable.class.isAssignableFrom(type) ) {
               ft = FieldType.Externalizable;
            }
         }
         if ( ft == null ) {
            Class arrayType = type.getComponentType();
            if ( arrayType != null && Externalizable.class.isAssignableFrom(arrayType) ) {
               ft = FieldType.ExternalizableArray;
            }
         }
         if ( ft == null ) {
            Class arrayType = type.getComponentType();
            if ( arrayType != null ) {
               arrayType = arrayType.getComponentType();
               if ( arrayType != null && Externalizable.class.isAssignableFrom(arrayType) ) {
                  ft = FieldType.ExternalizableArrayArray;
               }
            }
         }
         return ft;
      }
   }

   private class FieldInfo implements Comparable<FieldInfo> {

      FieldAccessor _fieldAccessor;
      FieldType     _fieldType;
      byte          _fieldIndex;

      public int compareTo( FieldInfo o ) {
         return (_fieldIndex < o._fieldIndex ? -1 : (_fieldIndex == o._fieldIndex ? 0 : 1));
      }
   }

}
