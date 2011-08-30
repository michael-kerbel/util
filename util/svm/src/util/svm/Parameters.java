package util.svm;

import libsvm.svm_parameter;


/** A wrapper for {@link svm_parameter} providing a nicer name and sensible default parameters. <p/>
 * 
 * doc from libsvm:
 * <pre>
    struct svm_parameter
    {
        int svm_type;
        int kernel_type;
        int degree; // for poly 
        double gamma;   // for poly/rbf/sigmoid 
        double coef0;   // for poly/sigmoid 

        // these are for training only 
        double cache_size; // in MB 
        double eps; // stopping criteria 
        double C;   // for C_SVC, EPSILON_SVR, and NU_SVR 
        int nr_weight;      // for C_SVC 
        int *weight_label;  // for C_SVC 
        double* weight;     // for C_SVC 
        double nu;  // for NU_SVC, ONE_CLASS, and NU_SVR 
        double p;   // for EPSILON_SVR 
        int shrinking;  // use the shrinking heuristics 
        int probability; // do probability estimates 
    };
   </pre>
    
    <code>svm_type</code> can be one of <code>C_SVC, NU_SVC, ONE_CLASS, EPSILON_SVR, NU_SVR</code>:

   <ul>
    <li><code>C_SVC</code>:      C-SVM classification</li>
    <li><code>NU_SVC</code>:     nu-SVM classification</li>
    <li><code>ONE_CLASS</code>:      one-class-SVM</li>
    <li><code>EPSILON_SVR</code>:    epsilon-SVM regression</li>
    <li><code>NU_SVR</code>:     nu-SVM regression</li>
   </ul>

    <code>kernel_type</code> can be one of <code>LINEAR, POLY, RBF, SIGMOID</code>:

   <ul>
    <li><code>LINEAR</code>: u'*v</li>
    <li><code>POLY</code>:   (gamma*u'*v + coef0)^degree</li>
    <li><code>RBF</code>:    exp(-gamma*|u-v|^2)</li>
    <li><code>SIGMOID</code>:    tanh(gamma*u'*v + coef0)</li>
    <li><code>PRECOMPUTED</code>: kernel values in training_set_file</li>
   </ul>

    <code>cache_size</code> is the size of the kernel cache, specified in megabytes.
    <code>C</code> is the cost of constraints violation. 
    <code>eps</code> is the stopping criterion. (we usually use 0.00001 in nu-SVC,
    0.001 in others). <code>nu</code> is the parameter in nu-SVM, nu-SVR, and
    one-class-SVM. <code>p</code> is the epsilon in epsilon-insensitive loss function
    of epsilon-SVM regression. <code>shrinking = 1</code> means shrinking is conducted;
    = 0 otherwise. <code>probability = 1</code> means model with probability
    information is obtained; = 0 otherwise.<p/>

    <code>nr_weight, weight_label</code>, and <code>weight</code> are used to change the penalty
    for some classes (If the weight for a class is not changed, it is
    set to 1). This is useful for training classifier using unbalanced
    input data or with asymmetric misclassification cost.<p/>

    <code>nr_weight</code> is the number of elements in the array <code>weight_label</code> and
    <code>weight</code>. Each <code>weight[i]</code> corresponds to <code>weight_label[i]</code>, meaning that
    the penalty of class <code>weight_label[i]</code> is scaled by a factor of <code>weight[i]</code>.<p/>
    
    If you do not want to change penalty for any of the classes,
    just set <code>nr_weight</code> to <code>0</code>.<p/>

    <b>NOTE</b> To avoid wrong parameters, svm_check_parameter() should be
    called before svm_train().
 */
public class Parameters extends svm_parameter {

   public static Parameters createDefaultLinear( TrainingData trainingData ) {
      return new Parameters(trainingData);
   }

   public Parameters( TrainingData trainingData ) {
      svm_type = C_SVC;
      kernel_type = LINEAR;
      degree = 3;
      coef0 = 0;
      nu = 0.5;
      cache_size = 100;
      gamma = 1.0 / trainingData.getFeatureNumber();

      C = 100f;

      eps = 1e-3;
      p = 0.1;
      shrinking = 1;
      probability = 1;
   }
}
