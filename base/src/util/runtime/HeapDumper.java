package util.runtime;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import com.sun.management.HotSpotDiagnosticMXBean;


public class HeapDumper {

   // This is the name of the HotSpot Diagnostic MBean
   private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

   // field to store the hotspot diagnostic MBean 
   private static volatile HotSpotDiagnosticMXBean _hotspotMBean;

   /**
    * Call this method from your application whenever you
    * want to dump the heap snapshot into a file.
    *
    * @param fileName name of the heap dump file
    * @param live flag that tells whether to dump
    *             only the live objects
    */
   public static void dumpHeap( String fileName, boolean live ) {
      // initialize hotspot diagnostic MBean
      initHotspotMBean();
      try {
         _hotspotMBean.dumpHeap(fileName, live);
      }
      catch ( RuntimeException re ) {
         throw re;
      }
      catch ( Exception exp ) {
         throw new RuntimeException(exp);
      }
   }

   public static String getMemoryUsage( boolean forceGc ) {
      if ( forceGc ) {
         System.gc();
      }
      long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
      return mem / (1024 * 1024) + " MB";
   }

   // get the hotspot diagnostic MBean from the
   // platform MBean server
   private static HotSpotDiagnosticMXBean getHotspotMBean() {
      try {
         MBeanServer server = ManagementFactory.getPlatformMBeanServer();
         return ManagementFactory.newPlatformMXBeanProxy(server, HOTSPOT_BEAN_NAME, HotSpotDiagnosticMXBean.class);
      }
      catch ( RuntimeException re ) {
         throw re;
      }
      catch ( Exception exp ) {
         throw new RuntimeException(exp);
      }
   }

   // initialize the hotspot diagnostic MBean field
   private static void initHotspotMBean() {
      if ( _hotspotMBean == null ) {
         synchronized ( HeapDumper.class ) {
            if ( _hotspotMBean == null ) {
               _hotspotMBean = getHotspotMBean();
            }
         }
      }
   }
}
