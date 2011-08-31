package util.dump.externalization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import util.dump.Externalizer;
import util.dump.ExternalizerTest;
import util.dump.ExternalizerTest.TestBeanSimple;
import util.dump.stream.SingleTypeObjectInputStream;
import util.dump.stream.SingleTypeObjectOutputStream;
import util.time.StopWatch;


public class ExternalizerBenchmark {

   public static void main( String[] args ) throws Exception {
      new ExternalizerBenchmark().benchmark();
   }

   private void benchmark() throws Exception {
      System.err.println("---------- simple instances");

      {
         TestBeanSimple[] beans = new TestBeanSimple[1000000];
         for ( int i = 0, length = beans.length; i < length; i++ ) {
            beans[i] = (TestBeanSimple)ExternalizerTest.newRandomInstance(TestBeanSimple.class);
         }

         for ( int k = 0; k < 10; k++ ) {
            System.gc();

            StopWatch t = new StopWatch();

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutput o = new SingleTypeObjectOutputStream<TestBeanSimple>(bo, TestBeanSimple.class);
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               o.writeObject(beans[i]);
            }
            o.close();

            ObjectInput i = new SingleTypeObjectInputStream<TestBeanSimple>(new ByteArrayInputStream(bo.toByteArray()), TestBeanSimple.class);
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               TestBeanSimple dd = (TestBeanSimple)i.readObject();
               assert (dd.equals(beans[j]));
            }
            i.close();

            System.err.println("java Serialization: " + t);

            t = new StopWatch();

            bo = new ByteArrayOutputStream();
            o = new SingleTypeObjectOutputStream<TestBeanSimple>(bo, TestBeanSimple.class);
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               beans[j].writeExternal(o);
            }
            o.close();

            i = new SingleTypeObjectInputStream<TestBeanSimple>(new ByteArrayInputStream(bo.toByteArray()), TestBeanSimple.class);
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               TestBeanSimple dd = new TestBeanSimple();
               dd.readExternal(i);
               assert (dd.equals(beans[j]));
            }
            i.close();

            System.err.println("Externalizer: " + t);
         }
      }

      System.err.println("---------- single-dim array");

      {
         TestBeanSimple[][] beans = new TestBeanSimple[1000][1000];
         //      beans[i][j] = (TestBeanExternalizer)ExternalizerTest.newRandomInstance(TestBeanExternalizer.class);
         //      beansSerializable[i][j] = new TestBeanSerializable(beans[i][j]);
         for ( int i = 0; i < 1000; i++ ) {
            for ( int j = 0; j < 1000; j++ ) {
               beans[i][j] = (TestBeanSimple)ExternalizerTest.newRandomInstance(TestBeanSimple.class);
            }
         }

         for ( int i = 0; i < 10; i++ ) {
            StopWatch t = new StopWatch();
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               ExternalizerBean externalizerBean = new ExternalizerBean();
               externalizerBean._beans = beans[j];
               byte[] b = externalize(externalizerBean);
               externalizerBean = new ExternalizerBean();
               readExternalized(b, externalizerBean);
            }
            System.err.println("Externalizer 1-dim array: " + t);

            t = new StopWatch();
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               SerializationBean serializationBean = new SerializationBean();
               serializationBean._beans = beans[j];
               byte[] b = serialize(serializationBean);
               serializationBean = (SerializationBean)readSerialized(b);
            }
            System.err.println("java Serialization 1-dim array: " + t);
         }
      }

      System.err.println("---------- two-dim array");
      {
         TestBeanSimple[][] beans = new TestBeanSimple[1000][1000];
         //      beans[i][j] = (TestBeanExternalizer)ExternalizerTest.newRandomInstance(TestBeanExternalizer.class);
         //      beansSerializable[i][j] = new TestBeanSerializable(beans[i][j]);
         for ( int i = 0; i < 1000; i++ ) {
            for ( int j = 0; j < 1000; j++ ) {
               beans[i][j] = (TestBeanSimple)ExternalizerTest.newRandomInstance(TestBeanSimple.class);
            }
         }

         for ( int i = 0; i < 10; i++ ) {
            StopWatch t = new StopWatch();
            ExternalizerBean2dim externalizerBean = new ExternalizerBean2dim();
            externalizerBean._beans = beans;
            byte[] b = externalize(externalizerBean);
            externalizerBean = new ExternalizerBean2dim();
            readExternalized(b, new ExternalizerBean2dim());
            System.err.println("Externalizer 2-dim array: " + t);

            t = new StopWatch();
            SerializationBean2dim serializationBean = new SerializationBean2dim();
            serializationBean._beans = beans;
            b = serialize(serializationBean);
            serializationBean = (SerializationBean2dim)readSerialized(b);
            System.err.println("java Serialization 2-dim array: " + t);
         }
      }

      System.err.println("---------- list");
      {
         {
            List<TestBeanSimple>[] beans = new List[1000];
            //      beans[i][j] = (TestBeanExternalizer)ExternalizerTest.newRandomInstance(TestBeanExternalizer.class);
            //      beansSerializable[i][j] = new TestBeanSerializable(beans[i][j]);
            for ( int i = 0; i < 1000; i++ ) {
               beans[i] = new ArrayList<TestBeanSimple>();
               for ( int j = 0; j < 1000; j++ ) {
                  beans[i].add((TestBeanSimple)ExternalizerTest.newRandomInstance(TestBeanSimple.class));
               }
            }

            for ( int i = 0; i < 10; i++ ) {
               StopWatch t = new StopWatch();
               for ( int j = 0, length = beans.length; j < length; j++ ) {
                  ExternalizerBeanList externalizerBean = new ExternalizerBeanList();
                  externalizerBean._beans = beans[j];
                  byte[] b = externalize(externalizerBean);
                  externalizerBean = new ExternalizerBeanList();
                  readExternalized(b, externalizerBean);
               }
               System.err.println("Externalizer list: " + t);

               t = new StopWatch();
               for ( int j = 0, length = beans.length; j < length; j++ ) {
                  SerializationBeanList serializationBean = new SerializationBeanList();
                  serializationBean._beans = beans[j];
                  byte[] b = serialize(serializationBean);
                  serializationBean = (SerializationBeanList)readSerialized(b);
               }
               System.err.println("java Serialization list: " + t);
            }
         }
      }

   }

   private byte[] externalize( Externalizer e ) throws Exception {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream o = new ObjectOutputStream(bout);
      e.writeExternal(o);
      o.close();
      return bout.toByteArray();
   }

   private Externalizer readExternalized( byte[] b, Externalizer e ) throws Exception {
      ByteArrayInputStream bin = new ByteArrayInputStream(b);
      ObjectInput i = new ObjectInputStream(bin);
      e.readExternal(i);
      i.close();
      return e;
   }

   private Object readSerialized( byte[] b ) throws Exception {
      ByteArrayInputStream bin = new ByteArrayInputStream(b);
      ObjectInput i = new ObjectInputStream(bin);
      Object o = i.readObject();
      i.close();
      return o;
   }

   private byte[] serialize( Object o ) throws Exception {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutput oos = new ObjectOutputStream(bout);
      oos.writeObject(o);
      oos.close();
      return bout.toByteArray();
   }


   public static class ExternalizerBean extends Externalizer {

      @externalize(1)
      public TestBeanSimple[] _beans;
   }

   public static class ExternalizerBean2dim extends Externalizer {

      @externalize(1)
      public TestBeanSimple[][] _beans;
   }

   public static class ExternalizerBeanList extends Externalizer {

      @externalize(1)
      public List<TestBeanSimple> _beans;
   }

   public static class SerializationBean implements Serializable {

      private static final long serialVersionUID = -1741743127544910454L;
      public TestBeanSimple[]   _beans;
   }

   public static class SerializationBean2dim implements Serializable {

      private static final long serialVersionUID = 1600762672331989544L;
      public TestBeanSimple[][] _beans;
   }

   public static class SerializationBeanList implements Serializable {

      private static final long   serialVersionUID = -4521752962217897005L;
      public List<TestBeanSimple> _beans;
   }

   public static class TestBeanExternalizer extends Externalizer {

      // the member vars get initialized randomly only if the field is public - a limitation of this testcase

      @externalize(1)
      public Integer _int;
      @externalize(2)
      public Boolean _booleanPrimitive;
      @externalize(3)
      public Byte    _bytePrimitive;
   }

   public static class TestBeanSerializable implements Serializable {

      private static final long serialVersionUID = 6187967184738411300L;
      public Integer            _int;
      public Boolean            _booleanPrimitive;
      public Byte               _bytePrimitive;


      TestBeanSerializable( TestBeanExternalizer b ) {
         _int = b._int;
         _booleanPrimitive = b._booleanPrimitive;
         _bytePrimitive = b._bytePrimitive;
      }
   }
}
