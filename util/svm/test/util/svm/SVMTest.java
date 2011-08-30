package util.svm;

import static org.fest.assertions.Assertions.assertThat;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import libsvm.svm_node;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import util.io.IOUtils;
import util.string.StringTool;


@RunWith(Parameterized.class)
public class SVMTest {

   @org.junit.runners.Parameterized.Parameters
   public static Collection<Object[]> testParams() {
      List<Object[]> parameters = new ArrayList<Object[]>();
      parameters.add(new Object[] { "splice" });
      parameters.add(new Object[] { "svmguide1" });
      parameters.add(new Object[] { "svmguide3" });
      parameters.add(new Object[] { "diabetes" });
      return parameters;
   }


   String _datasetName;

   File   _targetDir = new File("target/test");


   public SVMTest( String datasetName ) {
      _datasetName = datasetName;
      System.out.println("---- testing " + datasetName + " ----");
   }

   @Before
   public void setup() throws Exception {
      IOUtils.deleteDirContent(_targetDir);
      _targetDir.mkdirs();
      IOUtils.copy(new File(getClass().getResource("/resources/" + _datasetName + ".train").toURI()), new File(_targetDir, "trainingdata.txt"));
      IOUtils.copy(new File(getClass().getResource("/resources/" + _datasetName + ".test").toURI()), new File(_targetDir, "testdata.txt"));
   }

   @Test
   public void testScaled() throws Exception {
      /* our wrapper code */
      TrainingData trainingData = readData(new File(_targetDir, "trainingdata.txt"));
      ScaleData scaleData = SVM.scale(trainingData, -1, 1);
      Parameters parameters = createDefaultSettingsProblem(trainingData);
      Model model = SVM.train(trainingData, parameters);

      int correctNumber = 0;
      TDoubleList results = new TDoubleArrayList();
      TrainingData testData = readData(new File(_targetDir, "testdata.txt"));
      for ( int i = 0, length = testData.x.length; i < length; i++ ) {
         Node[] instance = SVM.scale(scaleData, (Node[])testData.x[i]);
         testData.x[i] = instance;
         double predictClass = SVM.predictClass(model, instance);
         if ( predictClass == testData.y[i] ) correctNumber++;
         results.add(predictClass);
      }

      System.out.println("correct classifications: " + correctNumber + " accuracy: "
         + NumberFormat.getPercentInstance().format(correctNumber / (float)testData.getInstanceNumber()));

      /* the original code */
      callSVM(new File(_targetDir, "training-scaled"), "svm_scale", "-l", "-1", "-u", "1", "-s", "scale-ranges", "trainingdata.txt");
      callSVM(new File(_targetDir, "test-scaled"), "svm_scale", "-r", "scale-ranges", "testdata.txt");
      callSVM(new File(_targetDir, "output"), "svm_train", "training-scaled");
      callSVM(new File(_targetDir, "output"), "svm_predict", "test-scaled", "training-scaled.model", "test-predictions");

      /* assert scaling */
      List<String> lines = FileUtils.readLines(new File(_targetDir, "training-scaled"));
      assertScaling(trainingData, lines);
      lines = FileUtils.readLines(new File(_targetDir, "test-scaled"));
      assertScaling(testData, lines);

      assertPredictions(results, testData);
   }

   @Test
   public void testUnscaled() throws Exception {
      TrainingData trainingData = readData(new File(_targetDir, "trainingdata.txt"));
      Parameters parameters = createDefaultSettingsProblem(trainingData);
      Model model = SVM.train(trainingData, parameters);

      int correctNumber = 0;
      TDoubleList results = new TDoubleArrayList();
      TrainingData testData = readData(new File(_targetDir, "testdata.txt"));
      for ( int i = 0, length = testData.x.length; i < length; i++ ) {
         Node[] instance = (Node[])testData.x[i];
         double predictClass = SVM.predictClass(model, instance);
         if ( predictClass == testData.y[i] ) correctNumber++;
         results.add(predictClass);
      }

      System.out.println("correct classifications: " + correctNumber + " accuracy: "
         + NumberFormat.getPercentInstance().format(correctNumber / (float)testData.getInstanceNumber()));

      callSVM(new File(_targetDir, "output"), "svm_train", "trainingdata.txt");
      callSVM(new File(_targetDir, "output"), "svm_predict", "testdata.txt", "trainingdata.txt.model", "test-predictions");

      assertPredictions(results, testData);
   }

   private void assertPredictions( TDoubleList results, TrainingData testData ) throws IOException {
      List<String> lines = FileUtils.readLines(new File(_targetDir, "test-predictions"));
      int differences = 0;
      for ( int i = 0, length = lines.size(); i < length; i++ ) {
         if ( Double.parseDouble(lines.get(i)) != results.get(i) ) differences++;
         //assertThat(Double.parseDouble(lines.get(i))).as("instance " + i + " is predicted differently").isEqualTo(results.get(i));
      }

      System.out.println("diff " + differences);

      boolean passed = differences / (float)testData.getInstanceNumber() < 0.01f;
      passed |= differences <= 1;
      assertThat(passed).isTrue();
   }

   private void assertScaling( TrainingData trainingData, List<String> lines ) {
      for ( int i = 0, length = lines.size(); i < length; i++ ) {
         String[] s = StringTool.split(lines.get(i), ' ');
         List<Node> nonzeronodes = new ArrayList<Node>();
         for ( svm_node node : trainingData.x[i] ) {
            if ( node.value != 0.0 ) nonzeronodes.add((Node)node);
         }
         assertThat(nonzeronodes.size() + 1).as("instance " + i + " has different node count").isEqualTo(s.length);
         for ( int j = 1, llength = s.length; j < llength; j++ ) {
            String[] ss = StringTool.split(s[j], ':');
            int index = Integer.parseInt(ss[0]);
            double value = Double.parseDouble(ss[1]);
            assertThat(nonzeronodes.get(j - 1).index).as("difference in training data, instance " + i + ", node-index " + j).isEqualTo(index);
            assertThat(Math.round(nonzeronodes.get(j - 1).value * 100000)).as("instance " + i + ", node-index " + j + " was differently scaled").isEqualTo(
               Math.round(value * 100000));
         }
      }
   }

   private void callSVM( File output, String... params ) throws Exception {

      ProcessBuilder builder = new ProcessBuilder("java", "-cp", "../../lib/libsvm.jar");
      builder.directory(_targetDir);
      builder.command().addAll(Arrays.asList(params));

      Process process = builder.start();
      builder.redirectErrorStream(true);

      BufferedInputStream processOut = new BufferedInputStream(process.getInputStream());
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output));
      org.apache.commons.io.IOUtils.copy(processOut, out);

      IOUtils.close(processOut, out);
      process.waitFor();
   }

   private Parameters createDefaultSettingsProblem( TrainingData trainingData ) {
      Parameters parameters = new Parameters(trainingData);
      parameters.C = 1;
      parameters.coef0 = 0;
      parameters.degree = 3;
      parameters.eps = 0.1;
      parameters.kernel_type = 2;
      parameters.nu = 0.5;
      parameters.shrinking = 1;
      parameters.svm_type = 0;
      parameters.probability = 0;
      return parameters;
   }

   private TrainingData readData( File in ) throws Exception {
      BufferedReader input = new BufferedReader(new FileReader(in));
      TDoubleList classLabels = new TDoubleArrayList();
      List<Node[]> vx = new ArrayList<Node[]>();
      int max_index = 0;

      while ( true ) {
         String line = input.readLine();
         if ( line == null ) break;

         StringTokenizer st = new StringTokenizer(line, " \t\n\r\f:");

         classLabels.add(Double.parseDouble(st.nextToken()));
         int m = st.countTokens() / 2;
         Node[] x = new Node[m];
         for ( int j = 0; j < m; j++ ) {
            int index = Integer.parseInt(st.nextToken());
            double value = Double.parseDouble(st.nextToken());
            x[j] = new Node(index, value);
         }
         if ( m > 0 ) max_index = Math.max(max_index, x[m - 1].index);
         vx.add(x);
      }

      return new TrainingData(vx.toArray(new Node[vx.size()][]), classLabels.toArray());
   }

}
