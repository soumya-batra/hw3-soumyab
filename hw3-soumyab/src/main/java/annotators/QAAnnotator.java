/**
 * Annotator that annotates Sentences into Question and Answer
 */
package annotators;

import java.text.BreakIterator;
import java.util.Locale;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import edu.cmu.deiis.types.Annotation;
import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.Question;

/**
 * @author Soumya Batra
 * 
 */

/**
 * @author Soumya Batra
 *
 */
public class QAAnnotator extends JCasAnnotator_ImplBase {

  // A string beginning with Q or q denote that it is a Question
  private static final String question = "Qq";

  // Name of the current annotator
  private final static String annotator = "QAAnnotator";

  // Confidence value of 1.0 since the Questions and Answers will always be annotated as required
  private static final double confidence = 1.0;

  // A common class for annotating (inherited by classes that annotate questions and answers)
  static abstract class Maker {
    abstract Annotation newAnnotation(JCas jcas, int start, int end);
  }

  // *********************************************
  // * Implementing Annotation classes *
  // *********************************************
  static final Maker questionAnnotationMaker = new Maker() {
    Annotation newAnnotation(JCas jcas, int start, int end) {
      Question newQuestion = new Question(jcas, start, end);
      newQuestion.setCasProcessorId(QAAnnotator.annotator);
      newQuestion.setConfidence(confidence);
      return newQuestion;
    }
  };

  static final Maker answerAnnotationMaker = new Maker() {
    Annotation newAnnotation(JCas jcas, int start, int end) {
      Answer newAnswer = new Answer(jcas, start, end);
      newAnswer.setCasProcessorId(QAAnnotator.annotator);
      newAnswer.setConfidence(confidence);
      return newAnswer;
    }
  };

  // ****************************************
  // * Static variable holding break iterator
  // ****************************************
  static final BreakIterator sentenceBreak = BreakIterator.getSentenceInstance(Locale.US);

  // Class variables
  JCas jcas;

  String input;

  // *************************************************************
  // * process *
  // *************************************************************

  @Override
  // The processing of dividing the sentences into Question and Answer takes place here
  public void process(JCas document) throws AnalysisEngineProcessException {

    jcas = document;

    // Get document text in a string
    input = jcas.getDocumentText();

    // Create Annotations
    makeAnnotations();
  }

  // *************************************************************
  // * Helper Methods *
  // *************************************************************
  void makeAnnotations() {
    BreakIterator b = sentenceBreak;
    char c;
    b.setText(input);

    // Adds Questions and Answers to CAS
    for (int end = b.next(), start = b.first(); end != BreakIterator.DONE; start = end, end = b
            .next()) {
      c = input.charAt(start);

      if ((question.indexOf(c)) != -1)
        questionAnnotationMaker.newAnnotation(jcas, start + 2, end).addToIndexes();
      else {
        Answer ans = (Answer) answerAnnotationMaker.newAnnotation(jcas, start + 4, end);
        ans.setIsCorrect(input.charAt(start + 2) == '1');
        ans.addToIndexes();
      }

    }

  }

}
