package util.swt.viewerbuilder;

import java.lang.reflect.Method;

import org.eclipse.swt.graphics.Image;


public class CollectionTableColumnMeta implements TableColumnMeta {

   Method     getter;
   Method     sortGetter;
   String     caption;
   Comparable nullVal;
   int        width;
   boolean    sortable;
   Image      image;
   boolean    useInFilter;


   public String getCaption() {
      return caption;
   }

   public Image getImage() {
      return image;
   }

   public int getWidth() {
      return width;
   }

   public Object invoke( Object obj ) {
      try {
         return getter.invoke(obj, (Object[])null);
      }
      catch ( Exception e ) {
         e.printStackTrace();
         return e.getLocalizedMessage();
      }
   }

   public boolean isSortable() {
      return sortable;
   }

   public boolean isUseInFilter() {
      return useInFilter;
   }

   public Comparable toComparable( Object obj ) {
      try {
         Comparable comp = (Comparable)(sortGetter == null ? getter : sortGetter).invoke(obj, (Object[])null);
         return comp != null ? comp : nullVal;
      }
      catch ( Exception e ) {
         e.printStackTrace();
         return e.getLocalizedMessage();
      }
   }

   public String toString( Object obj ) {
      try {
         Object res = getter.invoke(obj, (Object[])null);
         return res != null ? res.toString() : nullVal.toString();
      }
      catch ( Exception e ) {
         e.printStackTrace();
         return e.getLocalizedMessage();
      }
   }
}