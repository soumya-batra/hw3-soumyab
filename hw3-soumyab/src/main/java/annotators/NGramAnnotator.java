/**
 * Takes Question/Answer as input and returns 1,2 and 3 gram annotations as well as score
 * the Answers according to Token and N Gram overlap
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

import edu.cmu.deiis.types.AnswerScore;
import edu.cmu.deiis.types.NGram;
import edu.cmu.deiis.types.Question;
import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.Token;

/**
 * @author Soumya Batra
 * 
 */
public class NGramAnnotator extends JCasAnnotator_ImplBase {

  // Name of the current annotator
  private static final String annotator = "NGramAnnotator";

  // Confidence value of 1.0 since we will always separate tokens and NGrams successfully
  private static final double confidence = 1.0;

  // Element Type for NGrams = Token
  private static final String elementType = "Token";

  // Break Iterator at every word
  private static final BreakIterator wordBreak = BreakIterator.getWordInstance(Locale.US);

  // CAS object
  JCas jcas;

  // Determines sentence type (Q = Question, A = Answer)
  char type;

  // String that holds document text
  String input;

  // Score for an Answer
  double score = 0.0;

  // Flag to check whether an answer was correct
  boolean isAnsCorrect = false;

  // List of all NGrams
  ArrayList<NGram> ngrams = new ArrayList<NGram>();

  // Annotates Tokens
  static final Token tokenAnnotationMaker(JCas jcas, int start, int end) {
    Token newToken = new Token(jcas, start, end);
    newToken.setCasProcessorId(annotator);
    newToken.setConfidence(confidence);
    return newToken;
  }

  @Override
  public void process(JCas document) throws AnalysisEngineProcessException {

    // Assigning variables
    jcas = document;
    input = jcas.getDocumentText();
    int begin, end;

    // Using output from previous annotators as input
    AnnotationIndex<Annotation> questionIndex = document.getAnnotationIndex(Question.type);
    AnnotationIndex<Annotation> answerIndex = document.getAnnotationIndex(Answer.type);
    AnnotationIndex<Annotation> ngramIndex = document.getAnnotationIndex(NGram.type);

    // Add list of all NGrams found from Gold Answer pipeline to ArrayList containing
    // NGrams to be searched in the Answers
    Iterator<Annotation> ngramIter = ngramIndex.iterator();
    while (ngramIter.hasNext()) {
      ngrams.add((NGram) ngramIter.next());
    }

    // Get Iterator for single Question in the document
    Iterator<Annotation> questionIter = questionIndex.iterator();

    if (questionIter.hasNext()) {

      Question ques = (Question) questionIter.next();
      begin = ques.getBegin();
      end = ques.getEnd();
      type = 'Q';

      // Get all NGram annotations
      makeAnnotations(input.substring(begin, end), begin);

      // Create an Iterator for Answers in the document
      Iterator<Annotation> answerIter = answerIndex.iterator();

      // Match the NGram Annotators found for each answer
      while (answerIter.hasNext()) {

        score = 0.0;
        Answer ans = (Answer) answerIter.next();
        begin = ans.getBegin();
        end = ans.getEnd();
        type = 'A';

        if (begin < end) {
          // Determines whether an Answer is correct
          isAnsCorrect = ans.getIsCorrect();

          // Makes 1,2,3 gram annotations and obtains a score for the answer based on matching
          // NGrams in the NGrams search ArrayList
          makeAnnotations(input.substring(begin, end), begin);

          // Normalizing the score
          score = score / 6;

          // Creating AnswerScore object based on obtained information
          AnswerScore ansScore = new AnswerScore(jcas, begin, end);
          ansScore.setAnswer(ans);
          ansScore.setScore(Math.round(score * 100) / 100.0d);
          ansScore.setCasProcessorId(annotator);
          ansScore.setConfidence(confidence);
          ansScore.addToIndexes();
        }

      }
    }

  }

  // *************************************************************
  // * Helper Methods *
  // *************************************************************

  // Makes all valid NGram annotations
  void makeAnnotations(String input, int begin) {

    ArrayList<Token> t = new ArrayList<Token>();

    BreakIterator b = wordBreak;
    char c;
    b.setText(input);

    // Puts all tokens in an array
    for (int end = b.next(), start = b.first(); end != BreakIterator.DONE; start = end, end = b
            .next()) {
      c = input.charAt(start);

      if (Character.isLetterOrDigit(c)) {
        Token newToken = tokenAnnotationMaker(jcas, begin + start, begin + end);
        newToken.addToIndexes();

        t.add(newToken);

      }

    }

    Token[] toks = new Token[t.size()];
    toks = t.toArray(toks);

    // Makes 1,2 and 3 gram annotation of tokens obtained above
    makeNGrams(toks, 3);

  }

  // Makes 1 gram, 2 gram and 3 gram annotations in a recursive fashion
  void makeNGrams(Token[] toks, int j) {
    int length = toks.length + 1;
    double cntQNGram = 0, cntNGram = 0;

    if (j > 1)
      makeNGrams(toks, j - 1);

    // If an NGram exists in the NGram search ArrayList, increase the count of cntQNGram
    for (int i = 0; i < length - j; i++) {
      boolean isQNGram = makeNGram(Arrays.copyOfRange(toks, i, i + j));
      if (type == 'A') {
        cntNGram++;
        if (isQNGram)
          cntQNGram++;
      }
    }

    // Calculating non-normalized score. We give more weight to higher degree NGrams
    if (cntNGram != 0)
      score += j * (cntQNGram / cntNGram);
    else
      score += 0;

  }

  // Adds all tokens of a Question to the NGram ArrayList. For answers, if they match Question's
  // tokens, it adds the NGrams to indices
  boolean makeNGram(Token[] tok) {

    int length = tok.length;
    boolean flag = true;
    NGram ngram = null;

    if ((type == 'Q') || (!isAnsCorrect))
      ngram = setNGram(tok);

    if (type == 'Q')
      ngrams.add(ngram);

    // Check whether the NGram exists in the NGram search array
    else {
      for (NGram qngram : ngrams) {
        FSArray v1 = qngram.getElements();
        if ((v1 != null) && (v1.size() == length)) {
          flag = true;
          for (int i = 0; i < length; i++) {
            Token t1 = (Token) v1.get(i);
            if (!(input.substring(t1.getBegin(), t1.getEnd()).equalsIgnoreCase(input.substring(
                    tok[i].getBegin(), tok[i].getEnd())))) {
              flag = false;
              break;
            }
          }
          if (flag == true)
            return flag;

        }
      }

    }
    return flag;

  }

  // Sets NGram indices
  NGram setNGram(Token[] tok) {
    int length = tok.length;
    FSArray v = new FSArray(jcas, length);

    v.copyFromArray(tok, 0, 0, length);

    NGram ngram = new NGram(jcas, tok[0].getBegin(), tok[length - 1].getEnd());
    ngram.setElements(v);
    ngram.setElementType(elementType);
    ngram.setCasProcessorId(annotator);
    ngram.setConfidence(confidence);

    ngram.addToIndexes();
    return ngram;
  }
}
