package util.swt.editor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;


public class EditorTest {

   public static void main( String[] args ) {
      final Display display = new Display();
      Font font = new Font(display, "Courier New", 12, SWT.NORMAL);
      final Shell shell = new Shell(display);
      shell.setLayout(new FillLayout());
      StyledText styledText = new StyledText(shell, SWT.WRAP | SWT.BORDER);
      styledText.setFont(font);
      styledText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
      styledText.setText("<?xml>\r\n" + "<root>\r\n" + "  ifICould\r\n" + "  \r\n" + "</root>");

      new EditorHelper(styledText, "xsl");

      shell.setSize(400, 400);
      shell.open();
      while ( !shell.isDisposed() ) {
         if ( !display.readAndDispatch() ) display.sleep();
      }
      font.dispose();
      display.dispose();

   }
}
