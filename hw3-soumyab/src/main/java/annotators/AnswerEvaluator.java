/**
 * Ranks answers according to their scores, calculates precision and outputs results to Console
 */
package annotators;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;

import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.AnswerScore;
import edu.cmu.deiis.types.Question;

/**
 * @author Soumya Batra
 * 
 */
public class AnswerEvaluator extends JCasAnnotator_ImplBase {

  // Comparator class that compares scores of two AnswerScore objects
  class CompareScore implements Comparator<AnswerScore> {
    public int compare(AnswerScore a1, AnswerScore a2) {
      // Returns -1 if score of first object is greater than that of second object
      if (a1.getScore() > a2.getScore())
        return -1;
      else
        return 1;
    }
  }

  // Comparator object
  Comparator<AnswerScore> scoreComparator = new CompareScore();

  // Priority Queue to hold sorted AnswerScore objects
  PriorityQueue<AnswerScore> answers = new PriorityQueue<AnswerScore>(10, scoreComparator);

  // We define global precision and total number of test cases to determine average precision
  double precision = 0.0;

  int tot = 0;

  /* Main processing takes place here */
  @Override
  public void process(JCas document) throws AnalysisEngineProcessException {

    // Using output from previous annotators as input
    AnnotationIndex<Annotation> questionIndex = document.getAnnotationIndex(Question.type);
    AnnotationIndex<Annotation> answerScoreIndex = document.getAnnotationIndex(AnswerScore.type);

    // Create an Iterators for Questions and Answer Scores in the document
    Iterator<Annotation> questionIter = questionIndex.iterator();
    Iterator<Annotation> answerScoreIter = answerScoreIndex.iterator();

    // Question, Answer and AnswerScore objects
    Question q = (Question) questionIter.next();
    Answer a = null;
    AnswerScore as = null;

    // Local variables
    double prec = 0.0;
    char symbol = '+';
    int i = 0, totCorrect = 0, predCorrect = 0;

    // Get document text as a String
    String input = document.getDocumentText();

    // Iterating over all answers and assigning each to Priority Queue
    while (answerScoreIter.hasNext()) {
      as = (AnswerScore) answerScoreIter.next();
      answers.add(as);
      if (as.getAnswer().getIsCorrect())
        // Total number of correct answers
        totCorrect++;
    }

    // Setting Answer object as null so as to reuse it
    as = null;

    // Displaying required output to Console
    System.out.println("Question:" + " " + input.substring(q.getBegin(), q.getEnd()));
    while ((as = answers.poll()) != null) {
      i++;
      a = as.getAnswer();
      if (a.getIsCorrect()) {
        symbol = '+';
        if (i <= totCorrect)
          predCorrect++;
      } else
        symbol = '-';
      System.out.println(symbol + " " + as.getScore() + " "
              + input.substring(a.getBegin(), a.getEnd()));

    }

    // Displaying precision information
    prec = (double) predCorrect / totCorrect;
    precision += prec;
    tot++;
    System.out.printf("Precision at %d: %.2f", totCorrect , prec);
    System.out.println();
   
    

  }

  public void destroy() {

    // Displaying average precision for all documents
    System.out.println("Average Precision: " + precision / tot);
  }

}
