package util.dump.externalization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintStream;

import util.dump.stream.JavaObjectStreamProvider;
import util.dump.stream.SingleTypeObjectInputStream;
import util.dump.stream.SingleTypeObjectOutputStream;
import util.time.StopWatch;


public class ExternalizationBenchmark {

   /*
     results for a run on 9.10.2008 with jre 1.6.0_04 on WinXP and "-server" option:

   hand written externalization: 875 ms
   CachableObject serialization: 4.188 s
   Externalizer using fields: 1.188 s
   Externalizer using methods: 1.438 s
   Serialization with Google ProtoBuffer: 2.109 s

     results for a run on 9.10.2008 with jre 1.6.0_04 on WinXP and "-client" option:

   hand written externalization: 1.109 s
   CachableObject serialization: 6.922 s
   Externalizer using fields: 2.547 s
   Externalizer using methods: 2.297 s
   Serialization with Google ProtoBuffer: 2.562 s

     results for a run on 9.10.2008 with jre 1.5.0_13 on WinXP and "-server" option:

   hand written externalization: 1.5 s
   CachableObject serialization: 9.656 s
   Externalizer using fields: 2.281 s
   Externalizer using methods: 2.391 s
   Serialization with Google ProtoBuffer: 2.531 s

     results for a run on 9.10.2008 with jre 1.5.0_13 on WinXP and "-client" option:

   hand written externalization: 2.031 s
   CachableObject serialization: 10.844 s
   Externalizer using fields: 4.469 s
   Externalizer using methods: 4.063 s
   Serialization with Google ProtoBuffer: 3.812 s

   ----
     results for a run on 9.10.2008 with jre 1.6.0_04 on Linux and "-server" option:

   hand written externalization: 1.211 s
   CachableObject serialization: 7.809 s
   Externalizer using fields: 1.639 s
   Externalizer using methods: 1.976 s
   Serialization with Google ProtoBuffer: 3.119 s

     results for a run on 9.10.2008 with jre 1.6.0_04 on Linux and "-client" option:

   hand written externalization: 1.632 s
   CachableObject serialization: 12.715 s
   Externalizer using fields: 3.309 s
   Externalizer using methods: 3.217 s
   Serialization with Google ProtoBuffer: 4.187 s

     results for a run on 9.10.2008 with jre 1.5.0_13 on Linux and "-server" option:

   hand written externalization: 2.534 s
   CachableObject serialization: 9.725 s
   Externalizer using fields: 4.214 s
   Externalizer using methods: 4.297 s
   Serialization with Google ProtoBuffer: 3.835 s

     results for a run on 9.10.2008 with jre 1.5.0_13 on Linux and "-client" option:

   hand written externalization: 3.138 s
   CachableObject serialization: 11.346 s
   Externalizer using fields: 6.513 s
   Externalizer using methods: 6.376 s
   Serialization with Google ProtoBuffer: 5.129 s

    */
   public static void main( String[] args ) throws Exception {
      PrintStream err = System.err;
      //System.setErr(new PrintStream(new ByteArrayOutputStream())); // deactivate logging for first run
      for ( int h = 0; h <= 3; h++ ) {
         {
            DumpOfferBeanSerializable[] beans = new DumpOfferBeanSerializable[100000];
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i] = DumpOfferBeanSerializable.createRandomBean();
            }

            System.gc();

            StopWatch t = new StopWatch();

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutput o = new JavaObjectStreamProvider().createObjectOutput(bo);
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               o.writeObject(beans[i]);
            }
            o.close();

            ObjectInput i = new JavaObjectStreamProvider().createObjectInput(new ByteArrayInputStream(bo.toByteArray()));
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               DumpOfferBeanSerializable dd = (DumpOfferBeanSerializable)i.readObject();
               assert (dd.equals(beans[j]));
            }
            i.close();

            System.err.println("java Serialization: " + t);
         }

         {
            DumpOfferBeanExternalizable[] beans = new DumpOfferBeanExternalizable[100000];
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i] = DumpOfferBeanExternalizable.createRandomBean();
            }

            System.gc();

            StopWatch t = new StopWatch();

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutput o = new SingleTypeObjectOutputStream<DumpOfferBeanExternalizable>(bo, DumpOfferBeanExternalizable.class);
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i].writeExternal(o);
            }
            o.close();

            ObjectInput i = new SingleTypeObjectInputStream<DumpOfferBeanExternalizable>(new ByteArrayInputStream(bo.toByteArray()),
               DumpOfferBeanExternalizable.class);
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               DumpOfferBeanExternalizable dd = new DumpOfferBeanExternalizable();
               dd.readExternal(i);
               assert (dd.equals(beans[j]));
            }
            i.close();

            System.err.println("hand written externalization: " + t);
         }

         {
            DumpOfferBeanExternalizerFields[] beans = new DumpOfferBeanExternalizerFields[100000];
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i] = DumpOfferBeanExternalizerFields.createRandomBean();
            }

            System.gc();

            StopWatch t = new StopWatch();

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutput o = new SingleTypeObjectOutputStream<DumpOfferBeanExternalizerFields>(bo, DumpOfferBeanExternalizerFields.class);
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i].writeExternal(o);
            }
            o.close();

            ObjectInput i = new SingleTypeObjectInputStream<DumpOfferBeanExternalizerFields>(new ByteArrayInputStream(bo.toByteArray()),
               DumpOfferBeanExternalizerFields.class);
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               DumpOfferBeanExternalizerFields dd = new DumpOfferBeanExternalizerFields();
               dd.readExternal(i);
               assert (dd.equals(beans[j]));
            }
            i.close();

            System.err.println("Externalizer using fields: " + t);
         }

         {
            DumpOfferBeanExternalizerMethods[] beans = new DumpOfferBeanExternalizerMethods[100000];
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i] = DumpOfferBeanExternalizerMethods.createRandomBean();
            }

            System.gc();

            StopWatch t = new StopWatch();

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutput o = new SingleTypeObjectOutputStream<DumpOfferBeanExternalizerMethods>(bo, DumpOfferBeanExternalizerMethods.class);
            for ( int i = 0, length = beans.length; i < length; i++ ) {
               beans[i].writeExternal(o);
            }
            o.close();

            ObjectInput i = new SingleTypeObjectInputStream<DumpOfferBeanExternalizerMethods>(new ByteArrayInputStream(bo.toByteArray()),
               DumpOfferBeanExternalizerMethods.class);
            for ( int j = 0, length = beans.length; j < length; j++ ) {
               DumpOfferBeanExternalizerMethods dd = new DumpOfferBeanExternalizerMethods();
               dd.readExternal(i);
               assert (dd.equals(beans[j]));
            }
            i.close();

            System.err.println("Externalizer using methods: " + t);

         }

         // enable logging after first run
         System.setErr(err);
      }
   }
}
