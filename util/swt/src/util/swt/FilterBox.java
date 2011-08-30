package util.swt;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import util.string.StringFilter;


public class FilterBox {

   private static final String     DEFAULT_TOOLTIP          = "Schränkt die anzuzeigende Menge ein auf die Zeilen, die alle eingetippten Tokens enthalten.\n" //
                                                               + "Tokens, die mit '-' anfangen, dürfen NICHT enthalten sein.\n\n" //
                                                               + "Default ist eine Substring-Suche, d.h. 'eng' findet auch 'englisch'.\n" // 
                                                               + "Für eine Wort- oder Phrasensuche das Wort in doppelte Anführungszeichen setzen.\n" //
                                                               + "Beispiel: \"eng\" findet nicht 'englisch', \"engl. Version\" findet nicht 'Version engl.'.\n\n" //
                                                               + "Für eine Oder-Suche die einzelnen Tokens mit '|' getrennt schreiben.\n" //
                                                               + "Beispiel: 'gebraucht|b-ware' findet sowohl 'gebrauchte Socken' als auch 'Socken B-ware'.\n" //
                                                               + "Eine Oder-Suche ist nur als Substring-Suche möglich, nicht als Phrasensuche.\n" //
                                                               + "Vor oder-verknüpfte Substrings ein '-' zu schreiben liefert nichts Sinnvolles.";

   private static final String     DEFAULT_TEXT             = "Einschränken nach ...";

   protected Text                  _filter;
   protected String                _filterString            = "";
   protected StringFilter          _stringFilter            = new StringFilter();

   protected StructuredViewer      _slaveViewer;

   protected Map<String, String[]> _orSearchCache           = new HashMap<String, String[]>();
   private String                  _boxText;
   private String                  _boxTooltip;
   private int                     _inactiveForegroundColor = SWT.COLOR_WIDGET_NORMAL_SHADOW;

   private int                     _inactiveBackgroundColor = SWT.COLOR_WHITE;

   private int                     _activeForegroundColor   = SWT.COLOR_BLACK;

   private int                     _activeBackgroundColor   = SWT.COLOR_WHITE;


   public FilterBox( Composite parent ) {
      this(parent, SWT.NONE);
   }

   public FilterBox( Composite parent, int style ) {
      this(parent, style, DEFAULT_TEXT, DEFAULT_TOOLTIP);
   }

   public FilterBox( Composite parent, int style, String text, String tooltip ) {
      _boxText = text == null ? DEFAULT_TEXT : text;
      _boxTooltip = tooltip == null ? DEFAULT_TOOLTIP : tooltip;

      _filter = new Text(parent, SWT.SINGLE | style);
      _filter.setText(_boxText);
      _filter.setToolTipText(_boxTooltip);
      _filter.setForeground(ResourceManager.getColor(_inactiveForegroundColor));
      _filter.setBackground(ResourceManager.getColor(_inactiveBackgroundColor));
      _filter.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      _filter.addListener(SWT.Activate, new Listener() {

         public void handleEvent( Event event ) {
            if ( !_filter.getText().equals(_boxText) ) {
               return;
            }
            _filter.setForeground(ResourceManager.getColor(_activeForegroundColor));
            _filter.setBackground(ResourceManager.getColor(_activeBackgroundColor));
            _filter.setText("");
         }
      });
      _filter.addModifyListener(new ModifyListener() {

         private WaiterThread _waiter;


         public void modifyText( ModifyEvent e ) {
            String oldFilterString = _filterString;
            _filterString = _filter.getText().toLowerCase();
            if ( _filterString.equals(_boxText) ) {
               _filterString = "";
            }
            if ( _slaveViewer != null && !oldFilterString.equals(_filterString) ) {
               filterStringModified(_filterString);
               if ( _waiter != null && _waiter.isAlive() ) {
                  _waiter.interrupt();
               }
               _waiter = new WaiterThread();
               _waiter.start();
            }
         }
      });
   }

   public boolean filter( String element ) {
      return filter(element, false);
   }

   public boolean filter( String element, boolean isLowerCase ) {
      _stringFilter.setFilterString(_filterString);
      return _stringFilter.filter(element, isLowerCase);
   }

   public String getDefaultBoxText() {
      return _boxText;
   }

   public Text getFilterControl() {
      return _filter;
   }

   public String getFilterString() {
      return _filterString;
   }

   public StructuredViewer getSlaveViewer() {
      return _slaveViewer;
   }

   public void reset() {
      _filter.setText(_boxText);
      _filterString = "";
      _filter.setForeground(ResourceManager.getColor(_inactiveForegroundColor));
      _filter.setBackground(ResourceManager.getColor(_inactiveBackgroundColor));
   }

   public void setActiveBackgroundColor( int backgroundColor ) {
      _activeBackgroundColor = backgroundColor;
   }

   public void setActiveForegroundColor( int foregroundColor ) {
      _activeForegroundColor = foregroundColor;
   }

   public void setSlaveViewer( StructuredViewer slaveViewer ) {
      _slaveViewer = slaveViewer;
   }

   protected void filterModified() {
      _slaveViewer.getControl().setRedraw(false);
      _slaveViewer.refresh();
      _slaveViewer.getControl().setRedraw(true);
   }

   /** subclasses can override this to get notified on changes */
   protected void filterStringModified( String filterString ) {}


   private class WaiterThread extends Thread {

      public WaiterThread() {
         setName("FilterBox-Waiter");
      }

      @Override
      public void run() {
         try {
            Thread.sleep(200);
         }
         catch ( InterruptedException e ) {
            return;
         }
         _slaveViewer.getControl().getDisplay().asyncExec(new Runnable() {

            public void run() {
               filterModified();
            }
         });
      }
   }

}
