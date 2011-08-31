package util.swt;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;


public abstract class FilterBoxFilter extends ViewerFilter {


   protected final FilterBox _filterBox;

   public FilterBoxFilter( FilterBox filterBox ) {
      _filterBox = filterBox;
   }

   public void reset() {
       //give the filter the chance to reset before each search!
   }
   
   public boolean select( Viewer viewer, Object parentElement, Object element ) {
      return !_filterBox.filter(toLowerCaseString(element), true);
   }

   /**
    * Muss einen lowerCase String liefern!
    */
   protected abstract String toLowerCaseString(Object element);
}
