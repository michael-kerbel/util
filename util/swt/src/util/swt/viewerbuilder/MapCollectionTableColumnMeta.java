   
package util.swt.viewerbuilder;

import java.lang.reflect.Method;

import org.eclipse.swt.graphics.Image;

class MapCollectionTableColumnMeta implements TableColumnMeta {
		
	Method getter;
	String caption;
	String fieldName[] = new String[1];
	Comparable nullVal;
	int width;
	boolean sortable;

	Image image;
	
	public Object invoke(Object obj) {
		try {
			return getter.invoke(obj, (Object[])fieldName);
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}		
	}
	
	public String toString(Object obj) {
		try {
			Object res = getter.invoke(obj, (Object[])fieldName);
			return res != null ? res.toString() : nullVal.toString();
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}
	}

	public Comparable toComparable(Object obj) {
		try {
			Comparable comp = (Comparable) getter.invoke(obj, (Object[])fieldName);
			return comp != null ? comp : nullVal;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getLocalizedMessage();
		}
	}
	
	public boolean isSortable() {
		return sortable;
	}
	
	public String getCaption() {
		return caption;
	}
	
	public int getWidth() {
		return width;
	}

	public Image getImage() {
		return image;
	}
}



