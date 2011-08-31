
package util.swt.viewerbuilder;

import org.eclipse.swt.graphics.Image;

interface TableColumnMeta {

	Object invoke(Object obj);
	public String toString(Object obj);
	public Comparable toComparable(Object obj);
	public boolean isSortable();
	public String getCaption();
    public int getWidth();

    public Image getImage();
}


