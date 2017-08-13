/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
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
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
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
  private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
      // using the maximum operators
      double max = 0.0;
      if (!this.docIteratorHasMatchCache()) {
          return max;
      }
      int id = this.docIteratorGetMatch();
      for (Qry q : args) {
          // match the right document at a time
          if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
              double curr = ((QrySop)q).getScore(r);
              if (max < curr) { max = curr; }
          }
      }
      
      return max;
  }

  
  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
   public double getScoreBM25(RetrievalModel r) throws IOException {
       return 0.0;
   }
  
  
  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
   public double getScoreIndri(RetrievalModel r) throws IOException {
       double geometricMean = 1.0;
       int id = this.docIteratorGetMatch();
       for (Qry q : args) {
           if (q.docIteratorHasMatch(r) && (q.docIteratorGetMatch() == id)) {
               geometricMean *= 1.0 - ((QrySop)q).getScore(r);
           }
           else {
               geometricMean *= 1.0 - ((QrySop)q).getDefaultScore(r, id);
           }
       }
       return 1.0 - geometricMean;
   }
   
   /**
    * The default score function to deal with non matching query terms.
    * @param r the retrieval model
    * @param docid the document id
    * @return the default score
    * */
   public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
       double geometricMean = 1.0;
       for (Qry q : args) {
           geometricMean *= 1.0 - ((QrySop)q).getDefaultScore(r, docid);
       }
       return 1.0 - geometricMean;
   }
   
}
