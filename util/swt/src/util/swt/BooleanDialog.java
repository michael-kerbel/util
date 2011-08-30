package util.swt;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;


public class BooleanDialog extends MessageDialog {

   private static final int STYLE = SWT.CANCEL | SWT.OK;

   protected boolean        _userAnswer;

   /**
    * Shows a dialog asking the user for yes or no. Blocks on constructor, you get the answer with getUserAnswer().
    */
   public BooleanDialog( String title, String description, Image[] images ) {
      super(title, description, images, STYLE);
      open(true);
   }

   @Override
   public void cancel() {
      _userAnswer = false;
   }

   public boolean getUserAnswer() {
      return _userAnswer;
   }

   @Override
   public void ok() {
      _userAnswer = true;
   }
}
