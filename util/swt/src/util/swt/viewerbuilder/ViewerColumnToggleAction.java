package util.swt.viewerbuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;


public class ViewerColumnToggleAction extends Action {

   public static void addToggleActions( MenuManager mm, ViewerColumnSelectable vcs ) {
      for ( ViewerColumn vc : vcs.getAllColumns() ) {
         mm.add(new ViewerColumnToggleAction(vcs, vc));
      }
      mm.addMenuListener(new IMenuListener() {

         public void menuAboutToShow( IMenuManager manager ) {
            for ( IContributionItem a : manager.getItems() ) {
               if ( a instanceof ActionContributionItem ) {
                  ActionContributionItem aci = (ActionContributionItem)a;
                  IAction aa = aci.getAction();
                  if ( aa instanceof ViewerColumnToggleAction ) {
                     ViewerColumnToggleAction va = ((ViewerColumnToggleAction)aa);
                     va.setChecked(va.isChecked());
                  }
               }
            }
         }
      });
   }


   private ViewerColumnSelectable _selectable;
   private ViewerColumn           _viewerColumn;

   public ViewerColumnToggleAction( ViewerColumnSelectable selectable, ViewerColumn viewerColumn ) {
      super(viewerColumn.getCaption(), AS_CHECK_BOX);
      _selectable = selectable;
      _viewerColumn = viewerColumn;
   }

   @Override
   public boolean isChecked() {
      for ( ViewerColumn vc : _selectable.getCurrentColumns() ) {
         if ( vc.getGetterName().equals(_viewerColumn.getGetterName()) ) {
            return true;
         }
      }
      return false;
   }

   @Override
   public void run() {
      boolean isChecked = isChecked();
      List<ViewerColumn> newVC = new ArrayList<ViewerColumn>(Arrays.asList(_selectable.getCurrentColumns()));
      if ( isChecked ) {
         for ( Iterator iterator = newVC.iterator(); iterator.hasNext(); ) {
            ViewerColumn vc = (ViewerColumn)iterator.next();
            if ( vc.getGetterName().equals(_viewerColumn.getGetterName()) ) {
               iterator.remove();
            }
         }
      }
      else {
         int pos = 0;
         for ( ViewerColumn vc : _selectable.getAllColumns() ) {
            if ( vc.getGetterName().equals(_viewerColumn.getGetterName()) ) {
               break;
            }
            for ( ViewerColumn cvc : _selectable.getCurrentColumns() ) {
               if ( cvc.getGetterName().equals(vc.getGetterName()) ) {
                  pos++;
               }
            }
         }
         newVC.add(pos, _viewerColumn);
      }
      _selectable.setColumns(newVC.toArray(new ViewerColumn[newVC.size()]));
      firePropertyChange("checked", isChecked, !isChecked);
   }
}
