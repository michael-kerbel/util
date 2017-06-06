
package util.swt.viewerbuilder;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;


public class LabelProviderWrapper implements ITableLabelProvider {

   protected ITableLabelProvider _provider;

   public LabelProviderWrapper( ITableLabelProvider provider ) {
      _provider = provider;
   }

   public void addListener( ILabelProviderListener listener ) {
      _provider.addListener(listener);
   }

   public void dispose() {
      _provider.dispose();
   }

   public Image getColumnImage( Object element, int columnIndex ) {
      return _provider.getColumnImage(element, columnIndex);
   }

   public String getColumnText( Object element, int columnIndex ) {
      return _provider.getColumnText(element, columnIndex);
   }

   public boolean isLabelProperty( Object element, String property ) {
      return _provider.isLabelProperty(element, property);
   }

   public void removeListener( ILabelProviderListener listener ) {
      _provider.removeListener(listener);
   }

}



