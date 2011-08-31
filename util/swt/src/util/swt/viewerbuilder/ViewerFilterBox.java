
package util.swt.viewerbuilder;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;

import util.swt.FilterBox;


public class ViewerFilterBox extends FilterBox {

   protected final boolean         _cacheToStringCalls;
   protected ViewerFilterBoxFilter _filter;

   
   public ViewerFilterBox( Composite parent, boolean cacheToStringCalls, int style, String text, String tooltip) {
      super(parent, style, text, tooltip);
      _cacheToStringCalls = cacheToStringCalls;
   }
   public ViewerFilterBox( Composite parent, boolean cacheToStringCalls, int style) {
	   this(parent, cacheToStringCalls, style, null, null);
   }
   public ViewerFilterBox( Composite parent, boolean cacheToStringCalls) {
	   this(parent, cacheToStringCalls, SWT.NONE);
   }

   @Override
   public void setSlaveViewer( StructuredViewer slaveViewer ) {
      super.setSlaveViewer(slaveViewer);
      _filter = createFilterBoxFilter(slaveViewer);
      _slaveViewer.addFilter(_filter);
   }

   protected ViewerFilterBoxFilter createFilterBoxFilter( StructuredViewer slaveViewer ) {
      return new ViewerFilterBoxFilter(this, slaveViewer, _cacheToStringCalls);
   }

   @Override
   public boolean filter( String element, boolean isLowerCase ) {
      _filter.reset();
      return super.filter(element, isLowerCase);
   }

   public void resetToStringCache() {
      if ( _filter != null )
         _filter.resetToStringCache();
   }

}



