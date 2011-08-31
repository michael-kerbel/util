package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;


public class MessageDialog extends DialogShell {

   private String    _title;
   private String    _description;
   private Image[]   _images;
   private Throwable _exception;


   public MessageDialog( String title, String description, Image[] images ) {
      this(title, description, images, SWT.CANCEL);
   }

   public MessageDialog( String title, String description, Image[] images, int style ) {
      super(style);
      _title = title;
      _description = description;
      _images = images;
   }

   public MessageDialog( Throwable exception, Image[] images ) {
      this(
         exception, // 
         (exception instanceof OutOfMemoryError) ? "Nicht genügend Speicher" : "Anwendungsfehler", //
         (exception instanceof OutOfMemoryError) ? // 
         "Es ist nicht genügend Speicher vorhanden. Es wurde automatisch eine Email an den Entwickler verschickt. Bitte für Rückfragen die Umstände notieren, die dazu führten. Gegebenenfalls mehr Speicher zuweisen und neustarten." //
               : "Ein Anwendungsfehler ist aufgetreten. Es wurde automatisch eine Email mit der Ursache verschickt. Bitte für Rückfragen die Umstände des Fehlers notieren. Um keinen Datenverlust zu riskieren, empfiehlt sich ein Neustart.", //
         images);
   }

   public MessageDialog( Throwable exception, String title, String messageText, Image[] images ) {
      super(SWT.CANCEL);
      _exception = exception;
      _title = title;
      _description = messageText;
      _images = images;
   }

   @Override
   public void cancel() {}

   @Override
   public void createContent( Composite parent ) {
      if ( _exception == null ) return;

      while ( _exception.getCause() != null && _exception.getCause() != _exception )
         _exception = _exception.getCause();
      StackTraceElement[] stackTrace = _exception.getStackTrace();
      String details = stackTrace[0].toString();
      if ( !stackTrace[0].toString().startsWith("util") ) {
         for ( int i = 1, length = stackTrace.length; i < length; i++ ) {
            if ( stackTrace[i].toString().startsWith("util") ) {
               details += "\n" + (i > 1 ? "...\n" : "") + stackTrace[i].toString();
               break;
            }
         }
      }
      _description += details;

      Composite c = new Composite(parent, SWT.NONE);
      c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL));
      GridLayout gridLayout = new GridLayout(2, false);
      gridLayout.marginHeight = 0;
      gridLayout.marginWidth = 0;
      gridLayout.marginTop = 0;
      gridLayout.marginBottom = 0;
      gridLayout.verticalSpacing = 0;
      c.setLayout(gridLayout);
      Label t = new Label(c, SWT.WRAP);
      t.setText(" Details:");
      t.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
      //      t.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
      t.setFont(ResourceManager.getBoldFont(t.getFont()));
      t.setForeground(ResourceManager.getColor(SWT.COLOR_DARK_GRAY));
      t = new Label(c, SWT.WRAP);
      t.setText(_exception.getClass().getName() + " - " + _exception.getMessage());
      t.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL));
      //      t.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);

      t = new Label(c, SWT.WRAP);
      t.setText(details);
      //      t.setForeground(ResourceManager.getColor(SWT.COLOR_DARK_GRAY));
      GridData gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_BEGINNING);
      gridData.horizontalSpan = 2;
      gridData.horizontalIndent = 3;
      t.setLayoutData(gridData);
      //      t.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);
   }

   @Override
   public String getDescriptionText() {
      return _description;
   }

   @Override
   public Image[] getImages() {
      return _images;
   }

   @Override
   public String getTitle() {
      return _title;
   }

   @Override
   public int getWidth() {
      return 400;
   }

   @Override
   public void ok() {}

}
