package util.swt.viewerbuilder;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.graphics.Image;


public abstract class ViewerBuilder {

   public static final String     COLUMN_META_KEY    = "columnMeta";
   public static final String     COLUMN_ID_KEY      = "columnId";

   public static final String     SORT_COLUMN_ID_KEY = "sortColumnId";
   public static final String     SORT_DESC          = "sortDesc";

   private static final Class     mapGetterSig[]     = { String.class };

   protected CollectionTableModel _tableModel;
   protected boolean              _allowSorting      = true;
   protected Map                  _classGetter       = new HashMap();
   protected Class                _dispClass;


   public void addColumn( int width, Method getter, Method sortGetter, String caption, Comparable nullVal, int columnStyle, Image image, boolean useInFilter ) {

      CollectionTableColumnMeta gcd = createColumnMeta();
      gcd.getter = getter;
      gcd.sortGetter = sortGetter;
      gcd.caption = caption;
      gcd.width = width;
      gcd.sortable = true && _allowSorting;
      gcd.nullVal = (nullVal == null) ? "" : nullVal;
      gcd.image = image;
      gcd.useInFilter = useInFilter;

      postAddColumn(gcd, columnStyle);
   }

   public void addColumn( int width, String property, String sortProperty, String caption, Comparable nullVal, int columnStyle, Image image, boolean useInFilter ) {
      Method getter = (Method)_classGetter.get(property);
      if ( getter == null ) throw new Error("no such property:" + property + " in " + _dispClass.getSimpleName());
      Method sortGetter = null;
      if ( sortProperty != null && !sortProperty.equals(property) ) {
         sortGetter = (Method)_classGetter.get(sortProperty);
         if ( sortGetter == null ) throw new Error("no such property:" + sortProperty + " in " + _dispClass.getSimpleName());
      }
      addColumn(width, getter, sortGetter, caption, nullVal, columnStyle, image, useInFilter);
   }

   public void addColumn( ViewerColumn col ) {
      if ( col.getProperty() != null && col.getPropertyGetterName() != null )
         addMapColumn(col.getWidth(), col.getPropertyGetterName(), col.getProperty(), col.getCaption(), col.getNullValue(), col.getStyle(), col.getImage());
      else if ( col.getGetter() != null ) {
         addColumn(col.getWidth(), col.getGetter(), col.getSortGetter(), col.getCaption(), col.getNullValue(), col.getStyle(), col.getImage(), col
               .isUseInFilter());
      } else
         addColumn(col.getWidth(), col.getGetterName(), col.getSortGetterName(), col.getCaption(), col.getNullValue(), col.getStyle(), col.getImage(), col
               .isUseInFilter());
   }

   public void addMapColumn( int width, String mapProp, String mapField, String caption, Comparable nullVal, int columnStyle, Image image ) {

      MapCollectionTableColumnMeta mc = new MapCollectionTableColumnMeta();
      _tableModel.addColumnMeta(mc);
      try {
         mc.getter = _dispClass.getMethod(mapProp, mapGetterSig);
      }
      catch ( Exception ex ) {
         throw new Error(ex);
      }
      mc.fieldName[0] = mapField;
      mc.caption = caption;
      mc.width = width;
      mc.sortable = true && _allowSorting;
      mc.nullVal = nullVal;

      mc.image = image;

      postAddColumn(mc, columnStyle);
   }

   public CollectionTableModel getModel() {
      return _tableModel;
   }

   public abstract StructuredViewer getStructuredViewer();

   public boolean isAllowSorting() {
      return _allowSorting;
   }

   /**
    * Removes all columns from both the viewer's control and the model. After this call you have to re-add columns and refresh the viewer.
    */
   public abstract void reset();

   public void setAllowSorting( boolean allowSorting ) {
      this._allowSorting = allowSorting;
   }

   protected CollectionTableColumnMeta createColumnMeta() {
      return _tableModel.addColumnMeta();
   }

   @SuppressWarnings("unchecked")
   protected void initGetter( Class dispClass ) {
      try {
         BeanInfo info = Introspector.getBeanInfo(dispClass);
         PropertyDescriptor pd[] = info.getPropertyDescriptors();
         for ( int k = 0; k < pd.length; k++ ) {
            _classGetter.put(pd[k].getName(), pd[k].getReadMethod());
         }
         for ( Class c : dispClass.getInterfaces() ) {
            initGetter(c);
         }
      }
      catch ( Exception ex ) {
         ex.printStackTrace();
      }
      this._dispClass = dispClass;
   }

   protected abstract void postAddColumn( TableColumnMeta gcd, int columnStyle );
}
