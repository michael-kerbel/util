package util.swt.viewerbuilder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;


public class ViewerColumn implements Comparable<ViewerColumn> {

   public static Map<String, ViewerColumn> toMap( ViewerColumn[] columns ) {
      Map<String, ViewerColumn> map = new HashMap<String, ViewerColumn>(columns.length * 2);
      for ( ViewerColumn col : columns ) {
         map.put(col.getGetterName(), col);
      }
      return map;
   }


   private int        _width;
   private Method     _getter;
   private String     _getterName;
   private Method     _sortGetter;
   private String     _sortGetterName;
   private String     _propertyGetterName;
   private String     _property;
   private String     _caption;
   private Comparable _nullValue;
   private int        _style;

   private boolean    _isShow      = true;
   private boolean    _isSort      = false;
   private boolean    _isSortDesc  = false;
   private int        _order;
   private Image      _image;
   private boolean    _useInFilter = true;


   public ViewerColumn( int index, int width, Method getter, String caption, Comparable nullValue, int style ) {
      _order = index;
      _width = width;
      _getter = getter;
      _getterName = getter.getName();
      _caption = caption;
      _nullValue = nullValue;
      _style = style;
   }

   public ViewerColumn( int width, String getterName, String caption ) {
      _width = width;
      _getterName = getterName;
      _caption = caption;
      _style = SWT.LEFT;
   }

   public ViewerColumn( int width, String getterName, String caption, Comparable nullValue ) {
      _width = width;
      _getterName = getterName;
      _caption = caption;
      _nullValue = nullValue;
      _style = SWT.LEFT;
   }

   public ViewerColumn( int width, String getterName, String caption, Comparable nullValue, int style ) {
      _width = width;
      _getterName = getterName;
      _caption = caption;
      _nullValue = nullValue;
      _style = style;
   }

   public ViewerColumn( int width, String getterName, String caption, Comparable nullValue, int style, String sortGetterName ) {
      _width = width;
      _getterName = getterName;
      _caption = caption;
      _nullValue = nullValue;
      _sortGetterName = sortGetterName;
      _style = style;
   }

   public ViewerColumn( int width, String propertyGetterName, String property, String caption, Comparable nullValue, int style ) {
      _width = width;
      _propertyGetterName = propertyGetterName;
      _property = property;
      _caption = caption;
      _nullValue = nullValue;
      _style = style;
   }

   public int compareTo( ViewerColumn o ) {
      return (_order < o._order ? -1 : (_order == o._order ? 0 : 1));
   }

   @Override
   public boolean equals( Object obj ) {
      if ( !(obj instanceof ViewerColumn) ) {
         return false;
      }
      ViewerColumn other = (ViewerColumn)obj;

      boolean equals = _width == other._width;
      equals &= (_getterName == null && other._getterName == null) || (_getterName != null && _getterName.equals(other._getterName));
      equals &= (_propertyGetterName == null && other._propertyGetterName == null)
         || (_propertyGetterName != null && _propertyGetterName.equals(other._propertyGetterName));
      equals &= (_sortGetterName == null && other._sortGetterName == null) || (_sortGetterName != null && _sortGetterName.equals(other._sortGetterName));
      equals &= (_property == null && other._property == null) || (_property != null && _property.equals(other._property));
      equals &= (_caption == null && other._caption == null) || (_caption != null && _caption.equals(other._caption));
      equals &= (_nullValue == null && other._nullValue == null) || (_nullValue != null && _nullValue.equals(other._nullValue));
      equals &= _style == other._style;
      equals &= _useInFilter == other._useInFilter;
      return equals;
   }

   public String getCaption() {
      return _caption;
   }

   public Method getGetter() {
      return _getter;
   }

   public String getGetterName() {
      return _getterName;
   }

   public Image getImage() {
      return _image;
   }

   public Comparable getNullValue() {
      return _nullValue;
   }

   public int getOrder() {
      return _order;
   }

   public String getProperty() {
      return _property;
   }

   public String getPropertyGetterName() {
      return _propertyGetterName;
   }

   public Method getSortGetter() {
      return _sortGetter;
   }

   public String getSortGetterName() {
      return _sortGetterName;
   }

   public int getStyle() {
      return _style;
   }

   public int getWidth() {
      return _width;
   }

   public boolean isShow() {
      return _isShow;
   }

   public boolean isSort() {
      return _isSort;
   }

   public boolean isSortDesc() {
      return _isSortDesc;
   }

   public boolean isUseInFilter() {
      return _useInFilter;
   }

   public void setImage( Image image ) {
      this._image = image;
   }

   public void setNullValue( Comparable nullValue ) {
      _nullValue = nullValue;
   }

   public void setOrder( int order ) {
      this._order = order;
   }

   public void setShow( boolean show ) {
      _isShow = show;
   }

   public void setSort( boolean sort ) {
      _isSort = sort;
   }

   public void setSortDesc( boolean sortDesc ) {
      _isSortDesc = sortDesc;
   }

   public void setSortGetter( Method sortGetter ) {
      _sortGetter = sortGetter;
   }

   public void setSortGetterName( String sortGetterName ) {
      _sortGetterName = sortGetterName;
   }

   public void setUseInFilter( boolean useInFilter ) {
      _useInFilter = useInFilter;
   }

   public void setWidth( int width ) {
      _width = width;
   }

   @Override
   public String toString() {
      return "ViewerColumn(caption: " + _caption + ", getter: " + _getterName + ", width: " + _width + ")";
   }
}
