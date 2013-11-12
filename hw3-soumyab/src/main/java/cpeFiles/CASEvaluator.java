/**
 * Evaluates and ranks the answers and writes to file and console
 */
package cpeFiles;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.cleartk.ne.type.NamedEntity;
import org.cleartk.ne.type.NamedEntityMention;
import org.xml.sax.SAXException;

import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.AnswerScore;
import edu.cmu.deiis.types.Question;

/**
 * A simple CAS consumer that writes the CAS to XMI format.
 * <p>
 * This CAS Consumer takes one parameter:
 * <ul>
 * <li><code>OutputDirectory</code> - path to directory into which output files will be written</li>
 * </ul>
 */
public class CASEvaluator extends CasConsumer_ImplBase {

  // Comparator class that compares scores of two AnswerScore objects
  class CompareScore implements Comparator<AnswerScore> {
    public int compare(AnswerScore a1, AnswerScore a2) {
      // Returns -1 if score of first object is greater than that of second object
      if (a1.getScore() > a2.getScore())
        return -1;
      else if (a1.getScore() == a2.getScore()) {
        if (a1.getAnswer().getIsCorrect())
          return -1;
        else
          return 1;
      } else
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

  /**
   * Name of configuration parameter that must be set to the path of a directory into which the
   * output files will be written.
   */
  public static final String PARAM_OUTPUTDIR = "OutputDirectory";

  private File mOutputDir;

  private int mDocNum;

  public void initialize() throws ResourceInitializationException {
    mDocNum = 0;
    mOutputDir = new File((String) getConfigParameterValue(PARAM_OUTPUTDIR));
    if (!mOutputDir.exists()) {
      mOutputDir.mkdirs();
    }
  }

  /**
   * Processes the CAS which was populated by the TextAnalysisEngines. <br>
   * In this case, the CAS is converted to XMI and written into the output file .
   * 
   * @param aCAS
   *          a CAS which has been populated by the TAEs
   * 
   * @throws ResourceProcessException
   *           if there is an error in processing the Resource
   * 
   * @see org.apache.uima.collection.base_cpm.CasObjectProcessor#processCas(org.apache.uima.cas.CAS)
   */
  public void processCas(CAS aCAS) throws ResourceProcessException {
    String modelFileName = null;

    JCas jcas;
    try {
      jcas = aCAS.getJCas();
    } catch (CASException e) {
      throw new ResourceProcessException(e);
    }

    // retrieve the filename of the input file from the CAS
    FSIterator<Annotation> it = jcas.getAnnotationIndex(SourceDocumentInformation.type).iterator();

    File outFile = null;
    if (it.hasNext()) {
      SourceDocumentInformation fileLoc = (SourceDocumentInformation) it.next();
      File inFile;
      try {
        inFile = new File(new URL(fileLoc.getUri()).getPath());
        String outFileName = inFile.getName();
        if (fileLoc.getOffsetInSource() > 0) {
          outFileName += ("_" + fileLoc.getOffsetInSource());
        }
        if (!(outFileName.substring(outFileName.length() - 4, outFileName.length()).equals(".txt")))
          outFileName += ".txt";
        outFile = new File(mOutputDir, outFileName);
        modelFileName = mOutputDir.getAbsolutePath() + "\\" + inFile.getName() + ".ecore";
      } catch (MalformedURLException e1) {
        // invalid URL, use default processing below
      }
    }
    if (outFile == null) {
      outFile = new File(mOutputDir, "doc" + mDocNum++ + ".txt");
    }
    // serialize XCAS and write to output file
    try {
      rankAndWrite(jcas, outFile, modelFileName);
    } catch (IOException e) {
      throw new ResourceProcessException(e);
    } catch (SAXException e) {
      throw new ResourceProcessException(e);
    }
  }

  /**
   * Rank answers and write to file
   */
  private void rankAndWrite(JCas jcas, File name, String modelFileName) throws IOException,
          SAXException {

    // Array containing named entities of Question object
    ArrayList<NamedEntityMention> ners = new ArrayList<NamedEntityMention>();

    // Getting named entities from the remote Stanford CoreNLP service
    FSIterator<Annotation> neIter = jcas.getAnnotationIndex(NamedEntityMention.type).iterator();

    // Using output from previous annotators as input
    FSIterator<Annotation> questionIter = (FSIterator<Annotation>) jcas.getAnnotationIndex(
            Question.type).iterator();
    FSIterator<Annotation> answerScoreIter = jcas.getAnnotationIndex(AnswerScore.type).iterator();

    // Question, Answer,AnswerScore and NER objects
    Question q = (Question) questionIter.next();
    Answer a = null;
    AnswerScore as = null;
     NamedEntityMention ner = null;

    // Local variables
    double prec = 0.0;
    int totCorrect = 0, predCorrect = 0;
    char symbol = '+';
    int i = 0;
    int size = 0;

    // Get document text as a String
    String input = jcas.getDocumentText();

    // Get all named entities from question in an arraylist
    while (((ner = (NamedEntityMention) neIter.next()).getBegin()) <= q.getEnd()) {
      if (!(ner.getCoveredText().contains(" "))) {
        ners.add(ner);
      }
    }
    size = ners.size();

    PrintWriter outf = null;
    try {

      try {
        outf = new PrintWriter(name, "UTF-8");
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }

      // Iterating over all answers and assigning each to Priority Queue
      while (answerScoreIter.hasNext()) {

        double sc = 0.0;
        as = (AnswerScore) answerScoreIter.next();

        // Get the named entities of an answer
        ArrayList<NamedEntityMention> anem = new ArrayList<NamedEntityMention>();
        NamedEntityMention nans = null;

        while ((neIter.hasNext())
                && (((ner = (NamedEntityMention) neIter.next()).getBegin() <= as.getAnswer()
                        .getEnd()))) {
          if (!(ner.getCoveredText().contains(" "))) {
            anem.add(ner);
          }
        }

        for (int j = 0; j < size; j++) {
          for (NamedEntityMention n : anem) {
            if (n.getCoveredText().equals(ners.get(j).getCoveredText())) {
              nans = n;
              break;
            }
          }
          for (int k = j + 1; k < size; k++) {
            int diff = ners.get(k).getBegin() - ners.get(j).getBegin();
            int d = 0;
            if (nans == null)
              break;
            else {
              for (NamedEntityMention n : anem) {
                if (n.getCoveredText().equals(ners.get(k).getCoveredText())) {
                  d = n.getBegin() - nans.getBegin();
                  break;
                }
              }

            }

            // Get the difference between the distances between named entities from Question and
            // Answers. Also, check the order in which they appear
            sc += Math.signum(d) * (double) (diff - Math.abs(d));
          }
        }

        sc = (sc != 0) ? sc : sc + 1;
        sc = 1 / sc;

        as.setScore(as.getScore() + sc);
        answers.add(as);
        if (as.getAnswer().getIsCorrect())
          // Total number of correct answers
          totCorrect++;
      }

      // Setting Answer object as null so as to reuse it
      as = null;

      // Writing required output to file
      outf.println("Question:" + " " + input.substring(q.getBegin(), q.getEnd()));
      while ((as = answers.poll()) != null) {
        i++;
        a = as.getAnswer();
        if (a.getIsCorrect()) {
          symbol = '+';
          if (i <= totCorrect)
            predCorrect++;
        } else
          symbol = '-';

        outf.printf("%c %.2f %s", symbol, as.getScore(), input.substring(a.getBegin(), a.getEnd()));
      }

      // Displaying precision information
      prec = (double) predCorrect / totCorrect;
      precision += prec;
      tot++;
      outf.printf("Precision at %d: %.2f", totCorrect, prec);
      outf.println();

    } finally {
      if (outf != null) {
        outf.close();
      }
    }
  }

  public void destroy() {

    // Displaying average precision for all documents
    System.out.printf("Average Precision: %.2f", (precision / tot));
  }

}
