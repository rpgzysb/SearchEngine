/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;


/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
    
    
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean(r);
    } else if (r instanceof RetrievalModelBM25) {
        return this.getScoreBM25(r);
    } else if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  
  /**
   *  getScore for the RankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean(RetrievalModel r) throws IOException {
      QryIop q = (QryIop)(this.getArg(0));
      if (q.docIteratorHasMatch(r)) {
          // using tf for ranked boolean
          return q.docIteratorGetMatchPosting().tf;
      } else {
          return 0.0;
      }
  }
  
  /**
   * Calculate the RSJ weight.
   * @param r the retrieval model
   * @return the RSI weight score   
   * */
  private double calcDf(RetrievalModel r) throws IOException {
      long N = Idx.getNumDocs();
      QryIop q = (QryIop)(this.getArg(0));
      int df = q.getDf();
      return Math.max(0.0, Math.log(((double)N - df + 0.5)/(df + 0.5)));  
  }
  
  /**
   * Calculate the tf weight.
   * @param r the retrieval model
   * @return the tf weight score 
   * */
  private double calcAvgDoclen(RetrievalModel r) throws IOException {
      double k1 = ((RetrievalModelBM25)r).getK1();
      double b = ((RetrievalModelBM25)r).getB();
      QryIop q = (QryIop)(this.getArg(0));
      String qfield = q.getField();
      int tf = q.docIteratorGetMatchPosting().tf;
      int docLength = Idx.getFieldLength(qfield, q.docIteratorGetMatch());
      double avgDocLength = Idx.getSumOfFieldLengths(qfield) / (double)Idx.getDocCount(qfield);
      return tf / (tf + k1 * ((1.0 - b) + b * (docLength / avgDocLength)));
  }
  

  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreBM25(RetrievalModel r) throws IOException {
      return calcDf(r) * calcAvgDoclen(r);
  }
  
  /**
   * Calculate the MLE weight.
   * @param r the retrieval model
   * @return the MLE weight score 
   * */
  private double getMLE() throws IOException {
      QryIop q = (QryIop)(this.getArg(0));
      double ctf = (double)q.invertedList.ctf;
      double termC = (double)Idx.getSumOfFieldLengths(q.getField());
      return ctf / termC;
  }
  
  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri(RetrievalModel r) throws IOException {
      if (!this.docIteratorHasMatch(r)) {
          return 0.0;
      } else {
          double mu = (double)((RetrievalModelIndri)r).getMu();
          double lambda = ((RetrievalModelIndri)r).getLambda();
          QryIop q = (QryIop)(this.getArg(0));
          double tf = (double)q.docIteratorGetMatchPosting().tf;
          double docLength = Idx.getFieldLength(q.getField(), q.docIteratorGetMatch());
          double pMLE = getMLE();
          double leftSmooth = (1.0 - lambda) * (tf + mu * pMLE) / (mu + docLength);
          double rightSmooth = lambda * pMLE;
          
          return leftSmooth + rightSmooth;
      }
  }
  
  /**
   * The default score function to deal with non matching query terms.
   * @param r the retrieval model
   * @param docid the document id
   * @return the default score
   * */
  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
      double mu = (double)((RetrievalModelIndri)r).getMu();
      double lambda = ((RetrievalModelIndri)r).getLambda();
      QryIop q = (QryIop)(this.getArg(0));
      double docLength = Idx.getFieldLength(q.getField(), (int)docid);
      double pMLE = getMLE();
      double leftSmooth = (1.0 - lambda) * (mu * pMLE) / (mu + docLength);
      double rightSmooth = lambda * pMLE;
      
      return leftSmooth + rightSmooth;
  }
  
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
