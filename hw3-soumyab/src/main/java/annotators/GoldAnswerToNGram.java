/**
 * This annotator takes Gold Answers as input and produces N-Grams that can be used for 
 * identifying answers using Token Overlap and N-Gram annotations
 */
package annotators;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.tcas.Annotation;

import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.NGram;
import edu.cmu.deiis.types.Token;

/**
 * @author Soumya Batra
 * 
 */
public class GoldAnswerToNGram extends JCasAnnotator_ImplBase {

  // Name of the current annotator
  private static final String annotator = "GoldAnswerToNGram";

  // Confidence value of 1.0 since these are Gold Answers
  private static final double confidence = 1.0;

  // Name of the current annotator
  private static final String elementType = "Token";

  // Break Iterator at every word
  private static final BreakIterator wordBreak = BreakIterator.getWordInstance(Locale.US);

  // CAS object
  JCas jcas;

  // Annotates Tokens
  static final Token tokenAnnotationMaker(JCas jcas, int start, int end) {
    Token newToken = new Token(jcas, start, end);
    newToken.setCasProcessorId(annotator);
    newToken.setConfidence(confidence);
    return newToken;
  }

  @Override
  // annotates NGrams of Gold Answers to include in the list of valid NGrams
  public void process(JCas document) throws AnalysisEngineProcessException {

    // Gets document in a CAS object
    jcas = document;
    // Gets document text in a String
    String input = jcas.getDocumentText();
    // Begin and End indices of an Annotation
    int begin, end;

    // Using output from previous annotator as input
    AnnotationIndex<Annotation> answerIndex = document.getAnnotationIndex(Answer.type);

    // Create an Iterator for Answers in the document
    Iterator<Annotation> answerIter = answerIndex.iterator();

    // Make NGram annotations for each Gold answer
    while (answerIter.hasNext()) {
      Answer ans = (Answer) answerIter.next();

      if (ans.getIsCorrect()) {
        begin = ans.getBegin();
        end = ans.getEnd();

        if (begin < end) {
          makeAnnotations(input.substring(begin, end), begin);
        }
      }
    }
  }

  // Makes all valid NGram annotations
  void makeAnnotations(String input, int begin) {

    ArrayList<Token> t = new ArrayList<Token>();

    BreakIterator b = wordBreak;
    char c;
    b.setText(input);

    // Collects all tokens in an ArrayList
    for (int end = b.next(), start = b.first(); end != BreakIterator.DONE; start = end, end = b
            .next()) {
      c = input.charAt(start);

      if (Character.isLetterOrDigit(c)) {
        Token newToken = tokenAnnotationMaker(jcas, begin + start, begin + end);

        t.add(newToken);

      }

    }

    Token[] toks = new Token[t.size()];
    toks = t.toArray(toks);
    // Makes 1,2 and 3 gram annotations from found tokens
    makeNGrams(toks, 3);

  }

  // Makes 1 gram, 2 gram and 3 gram annotations in a recursive fashion
  void makeNGrams(Token[] toks, int j) {
    int length = toks.length + 1;

    if (j > 1)
      makeNGrams(toks, j - 1);

    for (int i = 0; i < length - j; i++) {
      makeNGram(Arrays.copyOfRange(toks, i, i + j));

    }

  }

  // Creates NGrams from tokens and add the NGrams to indices
  void makeNGram(Token[] tok) {

    int length = tok.length;
    FSArray v = new FSArray(jcas, length);

    v.copyFromArray(tok, 0, 0, length);

    NGram ngram = new NGram(jcas, tok[0].getBegin(), tok[length - 1].getEnd());
    ngram.setElements(v);
    ngram.setElementType(elementType);
    ngram.setCasProcessorId(this.getClass().getName());
    ngram.setConfidence(confidence);

    ngram.addToIndexes();

  }

}
