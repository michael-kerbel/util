package util.dump;

import static org.fest.assertions.Assertions.assertThat;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.Test;

import util.dump.Externalizer.externalize;


public class ExternalizerTest {

   private static final int NUMBER_OF_INSTANCES_TO_CREATE = 100;


   public static Externalizable newRandomInstance( Class testClass ) throws Exception {
      Random r = new Random();

      Externalizable t = (Externalizable)testClass.newInstance();
      for ( Field f : testClass.getFields() ) {
         Class type = f.getType();

         int mod = f.getModifiers();
         if ( Modifier.isFinal(mod) || Modifier.isStatic(mod) || Modifier.isVolatile(mod) ) {
            continue;
         }

         if ( type == int.class ) {
            f.setInt(t, r.nextInt());
         } else if ( type == int[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               int[] i = new int[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = r.nextInt();
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == boolean.class ) {
            f.setBoolean(t, r.nextBoolean());
         } else if ( type == byte.class ) {
            f.setByte(t, (byte)r.nextInt());
         } else if ( type == byte[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               byte[] i = new byte[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = (byte)r.nextInt(127);
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == byte[][].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               byte[][] d = new byte[r.nextInt(5)][];
               for ( int h = 0, llength = d.length; h < llength; h++ ) {
                  byte[] i = new byte[r.nextInt(10)];
                  for ( int j = 0, length = i.length; j < length; j++ ) {
                     i[j] = (byte)r.nextInt(127);
                  }
                  d[h] = i;
               }
               f.set(t, d);
            } else {
               f.set(t, null);
            }
         } else if ( type == char.class ) {
            f.setChar(t, (char)r.nextInt());
         } else if ( type == double.class ) {
            f.setDouble(t, r.nextDouble());
         } else if ( type == double[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               double[] i = new double[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = r.nextDouble();
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == float.class ) {
            f.setFloat(t, r.nextFloat());
         } else if ( type == float[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               float[] i = new float[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = r.nextFloat();
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == long.class ) {
            f.setLong(t, r.nextLong());
         } else if ( type == long[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               long[] i = new long[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = r.nextLong();
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == short.class ) {
            f.setShort(t, (short)r.nextInt());
         } else if ( type == String.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               String s = randomString(r);
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == String[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               String[] i = new String[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = randomString(r);
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == Date.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Date s = new Date(r.nextLong());
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == UUID.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               UUID s = new UUID(r.nextLong(), r.nextLong());
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Date[].class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Date[] i = new Date[r.nextInt(20)];
               for ( int j = 0, length = i.length; j < length; j++ ) {
                  i[j] = new Date(r.nextLong());
               }
               f.set(t, i);
            } else {
               f.set(t, null);
            }
         } else if ( type == Integer.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               int s = r.nextInt();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Boolean.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               boolean s = r.nextBoolean();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Byte.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               byte s = (byte)r.nextInt();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Character.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               char s = (char)r.nextInt();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Double.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               double s = r.nextDouble();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Float.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               float s = r.nextFloat();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Long.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               long s = r.nextLong();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == Short.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               short s = (short)r.nextInt();
               f.set(t, s);
            } else {
               f.set(t, null);
            }
         } else if ( type == List.class ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               List l = r.nextBoolean() ? new ArrayList() : new LinkedList();
               Class genericType = (Class)((ParameterizedType)f.getGenericType()).getActualTypeArguments()[0];
               if ( genericType == String.class ) {
                  for ( int j = 0, length = r.nextInt(2); j < length; j++ ) {
                     l.add(randomString(r));
                  }
               } else {
                  for ( int j = 0, length = r.nextInt(2); j < length; j++ ) {
                     l.add(newRandomInstance(testClass));
                  }
               }
               f.set(t, l);
            } else {
               f.set(t, null);
            }
         } else if ( Externalizable.class.isAssignableFrom(type) ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Externalizable d = newRandomInstance(type);
               f.set(t, d);
            } else {
               f.set(t, null);
            }
         } else if ( type.getComponentType() != null && Externalizable.class.isAssignableFrom(type.getComponentType()) ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Externalizable[] d = (Externalizable[])Array.newInstance(type.getComponentType(), r.nextInt(3));
               for ( int j = 0, length = d.length; j < length; j++ ) {
                  d[j] = newRandomInstance(type.getComponentType());
               }
               f.set(t, d);
            } else {
               f.set(t, null);
            }
         } else if ( type.getComponentType() != null && type.getComponentType().getComponentType() != null
            && Externalizable.class.isAssignableFrom(type.getComponentType().getComponentType()) ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Externalizable[][] d = (Externalizable[][])Array.newInstance(type.getComponentType(), r.nextInt(2));
               for ( int j = 0, length = d.length; j < length; j++ ) {
                  isNotNull = r.nextBoolean();
                  if ( isNotNull ) {
                     Externalizable[] sd = (Externalizable[])Array.newInstance(type.getComponentType().getComponentType(), r.nextInt(2));
                     for ( int k = 0, llength = sd.length; k < llength; k++ ) {
                        sd[k] = newRandomInstance(type.getComponentType().getComponentType());
                     }
                     d[j] = sd;
                  }
               }
               f.set(t, d);
            } else {
               f.set(t, null);
            }
         } else if ( EnumSet.class.isAssignableFrom(type) ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Class genericType = (Class)((ParameterizedType)f.getGenericType()).getActualTypeArguments()[0];
               EnumSet d = EnumSet.noneOf(genericType);
               Enum[] values = (Enum[])genericType.getEnumConstants();
               for ( int i = 0, length = values.length; i < length; i++ ) {
                  if ( r.nextBoolean() ) {
                     d.add(values[i]);
                  }
               }
               f.set(t, d);
            } else {
               f.set(t, null);
            }
         } else if ( Enum.class.isAssignableFrom(type) ) {
            boolean isNotNull = r.nextBoolean();
            if ( isNotNull ) {
               Enum[] values = (Enum[])type.getEnumConstants();
               Enum d = values[r.nextInt(values.length)];
               f.set(t, d);
            } else {
               f.set(t, null);
            }
         }
      }
      return t;
   }

   public static String randomString( Random r ) {
      int length = r.nextInt(50);
      StringBuilder sb = new StringBuilder(length);
      for ( int i = 0; i < length; i++ ) {
         sb.append((char)(r.nextInt(64) + 32));
      }
      return sb.toString();
   }

   @Test
   public void testCompatibility() throws Exception {
      testCompatibility(TestBean3.class, TestBean4.class);
      testCompatibility(TestBean4.class, TestBean3.class);
   }

   @Test
   public void testDownwardCompatibility() throws Exception {
      testCompatibility(TestBean2.class, TestBean.class);
   }

   @Test
   public void testSimple() throws Exception {

      Class testClass = TestBean.class; // put your class name here to test whether Externalization works

      Externalizable[] t = new Externalizable[NUMBER_OF_INSTANCES_TO_CREATE];
      for ( int i = 0, length = t.length; i < length; i++ ) {
         t[i] = newRandomInstance(testClass);
      }

      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      ObjectOutput o = new ObjectOutputStream(bo);
      for ( int i = 0, length = t.length; i < length; i++ ) {
         t[i].writeExternal(o);
      }
      o.close();

      ObjectInput i = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray()));
      Externalizable[] tt = new Externalizable[NUMBER_OF_INSTANCES_TO_CREATE];
      for ( int j = 0, length = t.length; j < length; j++ ) {
         tt[j] = (Externalizable)testClass.newInstance();
      }

      for ( int j = 0, length = t.length; j < length; j++ ) {
         tt[j].readExternal(i);
         assertThat(equals(testClass, t[j], tt[j])).isTrue();
      }
      i.close();
   }

   @Test
   public void testUpwardCompatibility() throws Exception {
      testCompatibility(TestBean.class, TestBean2.class);
   }

   private boolean equals( Class testClass, Externalizable t, Externalizable tt ) throws Exception {
      for ( Field f : testClass.getFields() ) {
         if ( f.getAnnotation(externalize.class) == null ) {
            continue;
         }
         Object ft = f.get(t);
         Object ftt = f.get(tt);
         if ( (ft == null && ftt != null) || (ft != null && ftt == null) || (ft != null && ftt != null && !equals(ft, ftt)) ) {
            System.err.println("Field " + f.getName() + " is not equal after de-externalization: " + ft + "!=" + ftt);
            return false;
         } else {
            //System.err.println(ft + "==" + ftt);
         }
      }
      BeanInfo info = Introspector.getBeanInfo(testClass);
      PropertyDescriptor pd[] = info.getPropertyDescriptors();
      for ( int k = 0; k < pd.length; k++ ) {
         if ( pd[k].getReadMethod() == null || pd[k].getWriteMethod() == null ) {
            continue;
         }
         if ( pd[k].getReadMethod().getAnnotation(externalize.class) == null && pd[k].getWriteMethod().getAnnotation(externalize.class) == null ) {
            continue;
         }
         Object ft = pd[k].getReadMethod().invoke(t);
         Object ftt = pd[k].getReadMethod().invoke(tt);
         if ( (ft == null && ftt != null) || (ft != null && ftt == null) || (ft != null && ftt != null && !equals(ft, ftt)) ) {
            System.err.println("Method " + pd[k].getReadMethod().getName() + " is not equal after de-externalization: " + ft + "!=" + ftt);
            return false;
         } else {
            //System.err.println(ft + "==" + ftt);
         }
      }

      return true;
   }

   private boolean equals( Object ft, Object ftt ) throws Exception {
      Class c = ft.getClass();
      Class cc = ftt.getClass();
      if ( !c.equals(cc) ) {
         return false;
      }
      if ( Externalizable.class.isAssignableFrom(c) ) {
         Externalizable e1 = (Externalizable)ft;
         Externalizable e2 = (Externalizable)ftt;
         return equals(e1.getClass(), e1, e2);
      }
      if ( List.class.isAssignableFrom(c) ) {
         List l1 = (List)ft;
         List l2 = (List)ftt;

         if ( l1.size() != l2.size() ) {
            return false;
         }
         for ( int i = 0, length = l1.size(); i < length; i++ ) {
            Object o1 = l1.get(i);
            Object o2 = l2.get(i);
            if ( (o1 == null && o2 != null) || (o1 == null && o2 != null) ) {
               return false;
            }
            if ( o1 == null && o2 == null ) {
               continue;
            }
            assert o1 != null;

            if ( o1 instanceof String ) {
               if ( !o1.equals(o2) ) {
                  return false;
               }
            } else if ( !equals(o1.getClass(), (Externalizable)o1, (Externalizable)o2) ) {
               return false;
            }
         }
         return true;
      }
      if ( c.isArray() ) {
         if ( c.equals(int[].class) ) {
            return Arrays.equals((int[])ft, (int[])ftt);
         }
         if ( c.equals(long[].class) ) {
            return Arrays.equals((long[])ft, (long[])ftt);
         }
         if ( c.equals(float[].class) ) {
            return Arrays.equals((float[])ft, (float[])ftt);
         }
         if ( c.equals(double[].class) ) {
            return Arrays.equals((double[])ft, (double[])ftt);
         }
         if ( c.equals(byte[].class) ) {
            return Arrays.equals((byte[])ft, (byte[])ftt);
         }
         if ( c.equals(byte[][].class) ) {
            byte[][] b = (byte[][])ft;
            byte[][] bb = (byte[][])ftt;
            if ( b.length != bb.length ) {
               return false;
            }

            for ( int i = 0, length = b.length; i < length; i++ ) {
               if ( !Arrays.equals(b[i], bb[i]) ) {
                  return false;
               }
            }

            return true;
         }
         if ( c.equals(String[].class) ) {
            return Arrays.equals((String[])ft, (String[])ftt);
         }
         if ( c.equals(Date[].class) ) {
            return Arrays.equals((Date[])ft, (Date[])ftt);
         }
         if ( c.getComponentType() != null && c.getComponentType().getComponentType() != null
            && Externalizable.class.isAssignableFrom(c.getComponentType().getComponentType()) ) {
            Externalizable[][] l1 = (Externalizable[][])ft;
            Externalizable[][] l2 = (Externalizable[][])ftt;
            if ( l1.length != l2.length ) {
               return false;
            }
            for ( int i = 0, length = l1.length; i < length; i++ ) {
               Externalizable[] e1 = l1[i];
               Externalizable[] e2 = l2[i];
               if ( (e1 == null && e2 != null) || (e1 == null && e2 != null) ) {
                  return false;
               }
               if ( e1 == null & e2 == null ) {
                  continue;
               }
               if ( !equals(e1, e2) ) {
                  return false;
               }
            }
            return true;
         }
         if ( Externalizable.class.isAssignableFrom(c.getComponentType()) ) {
            Externalizable[] l1 = (Externalizable[])ft;
            Externalizable[] l2 = (Externalizable[])ftt;
            if ( l1.length != l2.length ) {
               return false;
            }
            for ( int i = 0, length = l1.length; i < length; i++ ) {
               Externalizable e1 = l1[i];
               Externalizable e2 = l2[i];
               if ( (e1 == null && e2 != null) || (e1 == null && e2 != null) ) {
                  return false;
               }
               if ( e1 == null && e2 == null ) {
                  continue;
               }
               assert e1 != null;
               if ( !equals(e1.getClass(), e1, e2) ) {
                  return false;
               }
            }
            return true;
         }
         throw new RuntimeException("The array type " + c + " is unsupported by the equals method of this testcase.");
      }
      return ft.equals(ftt);
   }

   /**
    * Compares only the fields existing in both classes.
    */
   private boolean equalsIgnoreMissingFields( Externalizable t, Externalizable tt ) throws Exception {
      for ( Field f : t.getClass().getFields() ) {
         if ( f.getAnnotation(externalize.class) == null ) {
            continue;
         }
         Object ft = f.get(t);
         Field ff = null;
         try {
            ff = tt.getClass().getField(f.getName());
         }
         catch ( NoSuchFieldException e ) {
            continue;
         }
         Object ftt = ff.get(tt);
         if ( (ft == null && ftt != null) || (ft != null && ftt == null) || (ft != null && ftt != null && !equals(ft, ftt)) ) {
            System.err.println("Field " + f.getName() + " is not equal after de-externalization: " + ft + "!=" + ftt);
            return false;
         } else {
            //            System.err.println(ft + "==" + ftt);
         }
      }

      BeanInfo info = Introspector.getBeanInfo(t.getClass());
      PropertyDescriptor pd[] = info.getPropertyDescriptors();
      for ( int k = 0; k < pd.length; k++ ) {
         Method g = pd[k].getReadMethod();
         if ( g == null || pd[k].getWriteMethod() == null ) {
            continue;
         }
         if ( g.getAnnotation(externalize.class) == null && pd[k].getWriteMethod().getAnnotation(externalize.class) == null ) {
            continue;
         }
         Method gg = null;
         try {
            gg = tt.getClass().getMethod(g.getName());
         }
         catch ( NoSuchMethodException e ) {
            continue;
         }
         Object ft = g.invoke(t);
         Object ftt = gg.invoke(tt);
         if ( (ft == null && ftt != null) || (ft != null && ftt == null) || (ft != null && ftt != null && !equals(ft, ftt)) ) {
            System.err.println("Method " + g.getName() + " is not equal after de-externalization: " + ft + "!=" + ftt);
            return false;
         } else {
            //System.err.println(ft + "==" + ftt);
         }
      }
      return true;
   }

   private void testCompatibility( Class testClass1, Class testClass2 ) throws Exception {

      // externalize with one class
      Externalizable[] t = new Externalizable[NUMBER_OF_INSTANCES_TO_CREATE];
      for ( int i = 0, length = t.length; i < length; i++ ) {
         t[i] = newRandomInstance(testClass1);
      }

      ByteArrayOutputStream bo = new ByteArrayOutputStream();
      ObjectOutput o = new ObjectOutputStream(bo);
      for ( int i = 0, length = t.length; i < length; i++ ) {
         t[i].writeExternal(o);
      }
      o.close();

      // deserialize with the other class
      ObjectInput i = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray()));
      Externalizable[] tt = new Externalizable[NUMBER_OF_INSTANCES_TO_CREATE];
      for ( int j = 0, length = t.length; j < length; j++ ) {
         tt[j] = (Externalizable)testClass2.newInstance();
      }

      for ( int j = 0, length = t.length; j < length; j++ ) {
         tt[j].readExternal(i);
         assertThat(equalsIgnoreMissingFields(t[j], tt[j])).isTrue();
      }
      i.close();
   }


   public static class TestBean extends Externalizer {

      @externalize(1)
      public int                  _int;
      @externalize(2)
      public boolean              _boolean;
      @externalize(3)
      public byte                 _byte;
      @externalize(4)
      public char                 _char;
      @externalize(5)
      public double               _double;
      @externalize(6)
      public float                _float;
      @externalize(7)
      public long                 _long;
      @externalize(8)
      public short                _short;
      @externalize(9)
      public String               _string;
      @externalize(10)
      public Date                 _date;
      @externalize(11)
      public Integer              _Integer;
      @externalize(12)
      public Boolean              _Boolean;
      @externalize(13)
      public Byte                 _Byte;
      @externalize(14)
      public Character            _Char;
      @externalize(15)
      public Double               _Double;
      @externalize(16)
      public Float                _Float;
      @externalize(17)
      public Long                 _Long;
      @externalize(18)
      public Short                _Short;
      @externalize(19)
      public String[]             _strings;
      @externalize(20)
      public int[]                _ints;
      @externalize(21)
      public long[]               _longs;
      @externalize(22)
      public byte[]               _bytes;
      @externalize(23)
      public float[]              _floats;
      @externalize(24)
      public double[]             _doubles;
      @externalize(26)
      public Date[]               _dates;
      @externalize(27)
      public List<Externalizable> _list;
      @externalize(28)
      public TestBeanSimple       _testBeanSimple;
      @externalize(29)
      public TestBeanSimple[]     _testBeanSimpleArray;
      @externalize(30)
      public TestBeanSimple[][]   _testBeanSimpleArrayArray;
      @externalize(31)
      public UUID                 _uuid;
      @externalize(32)
      public byte[][]             _bytesBytes;
      @externalize(33)
      public EnumSet<TestEnum>    _enumSet;
      @externalize(34)
      public TestEnum             _enum;
      @externalize(35)
      public List<String>         _listOfStrings;

      public int                  _i;                       // this member var gets initialized randomly only if the field is public - a limitation of this testcase


      @externalize(25)
      public int getInt() {
         return _i;
      }

      public void setInt( int i ) {
         _i = i;
      }
   }

   public static class TestBean2 extends TestBean {

      @externalize(50)
      public int _int2;
   }

   public static class TestBean3 extends Externalizer {

      // the member vars get initialized randomly only if the field is public - a limitation of this testcase

      public int               _int;
      public boolean           _booleanPrimitive;
      public byte              _bytePrimitive;
      public char              _char;
      public double            _doublePrimitive;
      public float             _floatPrimitive;
      public long              _longPrimitive;
      public short             _shortPrimitive;
      public String            _string;
      public Date              _date;
      public Integer           _Integer;
      public Boolean           _Boolean;
      public Byte              _Byte;
      public Character         _Character;
      public Double            _Double;
      public Float             _Float;
      public Long              _Long;
      public Short             _Short;
      @externalize(28)
      public TestBeanSimple    _testBeanSimple;
      public EnumSet<TestEnum> _enumSet;
      public TestEnum          _enum;


      @externalize(1)
      public Boolean getBoolean() {
         return _Boolean;
      }

      @externalize(2)
      public Byte getByte() {
         return _Byte;
      }

      @externalize(3)
      public byte getBytePrimitive() {
         return _bytePrimitive;
      }

      @externalize(4)
      public char getChar() {
         return _char;
      }

      @externalize(5)
      public Character getCharacter() {
         return _Character;
      }

      @externalize(6)
      public Date getDate() {
         return _date;
      }

      @externalize(7)
      public Double getDouble() {
         return _Double;
      }

      @externalize(8)
      public double getDoublePrimitive() {
         return _doublePrimitive;
      }

      @externalize(20)
      public TestEnum getEnum() {
         return _enum;
      }

      @externalize(19)
      public EnumSet<TestEnum> getEnumSet() {
         return _enumSet;
      }

      @externalize(9)
      public Float getFloat() {
         return _Float;
      }

      @externalize(10)
      public float getFloatPrimitive() {
         return _floatPrimitive;
      }

      @externalize(11)
      public int getInt() {
         return _int;
      }

      @externalize(12)
      public Integer getInteger() {
         return _Integer;
      }

      @externalize(13)
      public Long getLong() {
         return _Long;
      }

      @externalize(14)
      public long getLongPrimitive() {
         return _longPrimitive;
      }

      @externalize(15)
      public Short getShort() {
         return _Short;
      }

      @externalize(16)
      public short getShortPrimitive() {
         return _shortPrimitive;
      }

      @externalize(17)
      public String getString() {
         return _string;
      }

      @externalize(18)
      public boolean isBooleanPrimitive() {
         return _booleanPrimitive;
      }

      public void setBoolean( Boolean b ) {
         _Boolean = b;
      }

      public void setBooleanPrimitive( boolean booleanPrimitive ) {
         _booleanPrimitive = booleanPrimitive;
      }

      public void setByte( Byte b ) {
         _Byte = b;
      }

      public void setBytePrimitive( byte bytePrimitive ) {
         _bytePrimitive = bytePrimitive;
      }

      public void setChar( char c ) {
         _char = c;
      }

      public void setCharacter( Character character ) {
         _Character = character;
      }

      public void setDate( Date date ) {
         _date = date;
      }

      public void setDouble( Double d ) {
         _Double = d;
      }

      public void setDoublePrimitive( double doublePrimitive ) {
         _doublePrimitive = doublePrimitive;
      }

      public void setEnum( TestEnum enum1 ) {
         _enum = enum1;
      }

      public void setEnumSet( EnumSet<TestEnum> enumSet ) {
         _enumSet = enumSet;
      }

      public void setFloat( Float f ) {
         _Float = f;
      }

      public void setFloatPrimitive( float floatPrimitive ) {
         _floatPrimitive = floatPrimitive;
      }

      public void setInt( int i ) {
         _int = i;
      }

      public void setInteger( Integer integer ) {
         _Integer = integer;
      }

      public void setLong( Long l ) {
         _Long = l;
      }

      public void setLongPrimitive( long longPrimitive ) {
         _longPrimitive = longPrimitive;
      }

      public void setShort( Short s ) {
         _Short = s;
      }

      public void setShortPrimitive( short shortPrimitive ) {
         _shortPrimitive = shortPrimitive;
      }

      public void setString( String string ) {
         _string = string;
      }
   }

   public static class TestBean4 extends Externalizer {

      // this member var gets initialized randomly only if the field is public - a limitation of this testcase

      public int              _int;
      public boolean          _booleanPrimitive;
      public byte             _bytePrimitive;
      public char             _char;
      public double           _doublePrimitive;
      public float            _floatPrimitive;
      public long             _longPrimitive;
      public short            _shortPrimitive;
      public String           _string;
      public Date             _date;
      public Integer          _Integer;
      public Boolean          _Boolean;
      public Byte             _Byte;
      public Character        _Character;
      public Double           _Double;
      public Float            _Float;
      public Long             _Long;
      public Short            _Short;
      @externalize(29)
      public TestBeanSimple[] _testBeanSimpleArray;


      @externalize(21)
      public Boolean getBoolean2() {
         return _Boolean;
      }

      @externalize(22)
      public Byte getByte2() {
         return _Byte;
      }

      @externalize(23)
      public byte getBytePrimitive2() {
         return _bytePrimitive;
      }

      @externalize(4)
      public char getChar() {
         return _char;
      }

      @externalize(5)
      public Character getCharacter() {
         return _Character;
      }

      @externalize(6)
      public Date getDate() {
         return _date;
      }

      @externalize(7)
      public Double getDouble() {
         return _Double;
      }

      @externalize(8)
      public double getDoublePrimitive() {
         return _doublePrimitive;
      }

      @externalize(9)
      public Float getFloat() {
         return _Float;
      }

      @externalize(10)
      public float getFloatPrimitive() {
         return _floatPrimitive;
      }

      @externalize(11)
      public int getInt() {
         return _int;
      }

      @externalize(12)
      public Integer getInteger() {
         return _Integer;
      }

      @externalize(13)
      public Long getLong() {
         return _Long;
      }

      @externalize(14)
      public long getLongPrimitive() {
         return _longPrimitive;
      }

      @externalize(15)
      public Short getShort() {
         return _Short;
      }

      @externalize(16)
      public short getShortPrimitive() {
         return _shortPrimitive;
      }

      @externalize(17)
      public String getString() {
         return _string;
      }

      @externalize(18)
      public boolean isBooleanPrimitive() {
         return _booleanPrimitive;
      }

      public void setBoolean2( Boolean b ) {
         _Boolean = b;
      }

      public void setBooleanPrimitive( boolean booleanPrimitive ) {
         _booleanPrimitive = booleanPrimitive;
      }

      public void setByte2( Byte b ) {
         _Byte = b;
      }

      public void setBytePrimitive2( byte bytePrimitive ) {
         _bytePrimitive = bytePrimitive;
      }

      public void setChar( char c ) {
         _char = c;
      }

      public void setCharacter( Character character ) {
         _Character = character;
      }

      public void setDate( Date date ) {
         _date = date;
      }

      public void setDouble( Double d ) {
         _Double = d;
      }

      public void setDoublePrimitive( double doublePrimitive ) {
         _doublePrimitive = doublePrimitive;
      }

      public void setFloat( Float f ) {
         _Float = f;
      }

      public void setFloatPrimitive( float floatPrimitive ) {
         _floatPrimitive = floatPrimitive;
      }

      public void setInt( int i ) {
         _int = i;
      }

      public void setInteger( Integer integer ) {
         _Integer = integer;
      }

      public void setLong( Long l ) {
         _Long = l;
      }

      public void setLongPrimitive( long longPrimitive ) {
         _longPrimitive = longPrimitive;
      }

      public void setShort( Short s ) {
         _Short = s;
      }

      public void setShortPrimitive( short shortPrimitive ) {
         _shortPrimitive = shortPrimitive;
      }

      public void setString( String string ) {
         _string = string;
      }
   }

   public static class TestBeanSimple extends Externalizer {

      // the member vars get initialized randomly only if the field is public - a limitation of this testcase

      @externalize(1)
      public int     _int;
      @externalize(2)
      public boolean _booleanPrimitive;
      @externalize(3)
      public byte    _bytePrimitive;
   }

   public enum TestEnum {
      EnumValue1, EnumValue2, EnumValue3, EnumValue4, EnumValue5;
   }
}