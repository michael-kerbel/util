package util.swt.viewerbuilder;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.graphics.Image;

import util.reflection.Reflection;


/**
 * Creates {@link ViewerColumnCreator} arrays from {@link Column} annotations of a {@link GetterFacade}. 
 * 
 * You can annotate any getter method with <code>@Column</code>. To optionally set the sort value, the
 * null value or the column header image, add a getter method with the same name and the appropriate 
 * suffix. These supplemental getters may not be annotated.
 * 
 * @see GETTER_SUFFIX_SORT_VALUE
 * @see GETTER_SUFFIX_NULL_VALUE
 * @see GETTER_SUFFIX_COLUMN_IMAGE
 */
public class ViewerColumnCreator {

   /** the getter named [getterName+GETTER_SUFFIX_SORT_VALUE] must return a <code>Comparable</code> */
   public static final String GETTER_SUFFIX_SORT_VALUE   = "SortValue";

   public static final String GETTER_SUFFIX_NULL_VALUE   = "NullValue";

   /** the getter named [getterName+GETTER_SUFFIX_COLUMN_IMAGE] must return an <code>Image</code> */
   public static final String GETTER_SUFFIX_COLUMN_IMAGE = "ColumnImage";

   private ViewerColumn[]     _allColumns;
   private ViewerColumn[]     _defaultColumns;


   public ViewerColumnCreator( GetterFacade facade ) {
      init(facade);
   }

   public ViewerColumn[] getAllColumns() {
      return _allColumns;
   }

   public ViewerColumn[] getDefaultColumns() {
      return _defaultColumns;
   }

   private void init( GetterFacade facade ) {
      List<ViewerColumn> allColumns = new ArrayList<ViewerColumn>();
      List<ViewerColumn> defaultColumns = new ArrayList<ViewerColumn>();

      Class facadeClass = facade.getClass();

      for ( Method m : facadeClass.getMethods() ) {
         int mod = m.getModifiers();
         if ( Modifier.isStatic(mod) ) {
            continue;
         }
         if ( !Modifier.isPublic(mod) ) {
            continue;
         }

         Column annotation = m.getAnnotation(Column.class);
         if ( annotation == null ) continue;

         String getterName = m.getName();
         if ( m.getParameterTypes().length > 0 ) {
            throw new RuntimeException(facadeClass + " has an annotated method " + getterName + " with a parameter. No parameter is allowed.");
         }
         if ( getterName.endsWith(GETTER_SUFFIX_NULL_VALUE) || getterName.endsWith(GETTER_SUFFIX_SORT_VALUE) ) {
            throw new RuntimeException(facadeClass + " has an annotated method " + getterName
               + " which ends with a reserved suffix. Most likely you did not want to annotate it.");
         }
         Method nullGetter = Reflection.getMethodQuietly(facadeClass, getterName + GETTER_SUFFIX_NULL_VALUE);
         Method sortGetter = Reflection.getMethodQuietly(facadeClass, getterName + GETTER_SUFFIX_SORT_VALUE);
         Method imageGetter = Reflection.getMethodQuietly(facadeClass, getterName + GETTER_SUFFIX_COLUMN_IMAGE);

         Comparable nullValue = null;
         if ( nullGetter != null ) nullValue = (Comparable)Reflection.invokeQuietly(nullGetter, facade);
         Image image = null;
         if ( imageGetter != null ) image = (Image)Reflection.invokeQuietly(imageGetter, facade);

         ViewerColumn column = new ViewerColumn(annotation.index(), annotation.width(), m, annotation.caption(), nullValue, annotation.style());
         column.setUseInFilter(annotation.useInFilter());
         if ( sortGetter != null ) column.setSortGetter(sortGetter);
         if ( image != null ) column.setImage(image);

         allColumns.add(column);
         if ( annotation.defaultColumn() ) {
            defaultColumns.add(column);
         }
      }

      _allColumns = allColumns.toArray(new ViewerColumn[allColumns.size()]);
      _defaultColumns = defaultColumns.toArray(new ViewerColumn[defaultColumns.size()]);

      Arrays.sort(_allColumns);
      Arrays.sort(_defaultColumns);
   }
}
