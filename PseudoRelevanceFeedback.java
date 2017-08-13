import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This is the class for providing pseudo relevance feedback query expansion.
 * It records the feedback parameters provided by the parameter file.
 * */
public class PseudoRelevanceFeedback {

    /**
     * Feedback parameters provided.
     * */
    private int fbDocs;
    private int fbTerms;
    private double fbMu;
    private double fbOrigWeight;
    
    /**
     * Constructor for feedback service.
     * @param fbDocs_ the provided fbDocs
     * @param fbTerms_ the provided fbTerms
     * @param fbMu_ the provided fbMu
     * @param fbOrigWeight_ the provided fbOrigWeight
     * */
    public PseudoRelevanceFeedback(int fbDocs_, int fbTerms_,
            double fbMu_, double fbOrigWeight_) {
        fbDocs = fbDocs_;
        fbTerms = fbTerms_;
        fbMu = fbMu_;
        fbOrigWeight = fbOrigWeight_;
    }
    
    /**
     * The private nested class for a learned query.
     * It records the term and score for a query.
     * */
    private class LearnedTerm implements Comparable<LearnedTerm> {
        /**
         * The term itself.
         * */
        private String term;
        /**
         * The score after learning.
         * */
        private double score;
        
        /**
         * The constructor for learned query.
         * @param term_ the term
         * @param score_ the learned score
         * */
        public LearnedTerm(String term_, double score_) {
            term = term_;
            score = score_;
        }

        /**
         * Getter for the score.
         * @return the score
         * */
        public double getScore() {
            return score;
        }
        
        /**
         * Getter for the term.
         * @return the term
         * */
        public String getTerm() {
            return term;
        }
        
        /**
         * Implementation of the comparable interface.
         * @param o the other learned query
         * @return 1 if greater than the other, -1 if less than, 0 if equal
         * */
        @Override
        public int compareTo(LearnedTerm o) {
            if (score > o.getScore()) { return 1; }
            else if (score < o.getScore()) { return -1; }
            else { return 0; }
        }
    }
        
    
    /**
     *  getScore for the Indri retrieval model.
     *  @param termCtf the term ctf
     *  @param termC the length of tokens over a collection
     *  @param term the term 
     *  @param tv the forward list
     *  @param docid the document id
     *  @return the feedback indri score
     *  @throws IOException Error accessing the Lucene index
     */
    private double getTermScoreIndri(double termCtf, double termC, String term, TermVector tv, int docid) throws IOException {
        int termIndex = tv.indexOfStem(term);
        double tf = (termIndex > -1) ? tv.stemFreq(termIndex) : 0.0;
        double docLength = Idx.getFieldLength("body", docid);
        double indriScore = 0.0;
        double pMLE = (fbMu == 0.0) ? 0.0 : (termCtf / termC);
        if (tf == 0.0 && fbMu == 0.0) { return 0.0; }
        else if (fbMu == 0.0) {
            indriScore = tf / docLength;
        } else {
            indriScore = (tf + fbMu * pMLE) / (fbMu + docLength);
        }
        return indriScore;
    }
    
    /**
     * Get the score for a document for each original query.
     * @param r the original query score list
     * @param docIndex the corresponding index in score list
     * @return the original score
     * */
    private double getOriginalScore(ScoreList r, int docIndex) {
        return r.getDocidScore(docIndex);
    }
    
    /**
     * See if a term is valid for expansion.
     * @return true if valid, false otherwise
     * */
    private boolean isValidTerm(String term) {
        return !(term == null || term.isEmpty() || term.contains(".") || term.contains(","));
    }
    
    /**
     * Produce fbDocs number of learned query and recorded in data structure.
     * @param r the original score list
     * @return the list of learned query
     * */
    public PriorityQueue<LearnedTerm> produceExpandTerms(ScoreList r) throws IOException {
        // use heap to keep a certain number and order 
        PriorityQueue<LearnedTerm> expandTerms = new PriorityQueue<LearnedTerm>(fbTerms);
        // document numbers
        int documentToInspect = Math.min(r.size(), fbDocs);
        // hash the document id and its index in score list
        Map<Integer, Integer> docidToIndex = new HashMap<Integer, Integer>(documentToInspect);
        for (int i = 0; i < documentToInspect; ++i) {
            docidToIndex.put(r.getDocid(i), i);
        }
        // get all document ids
        Set<Integer> docidSet = docidToIndex.keySet();
        // create forward list
        List<TermVector> docTermVector = new ArrayList<TermVector>(documentToInspect);
        for (Integer docid : docidSet) {
            docTermVector.add(new TermVector(docid, "body"));
        }
        // cache the ctf for terms
        Map<String, Double> termCtf = new TreeMap<String, Double>();
        // collect all terms
        Set<String> potentialTerms = new TreeSet<String>();
        for (TermVector tv : docTermVector) {
            // begin iterating the forward list
            int len = tv.stemsLength();
            for (int currIndex = 1; currIndex < len; ++currIndex) {
                // get the term
                String currTerm = tv.stemString(currIndex);
                // see if valid
                if (isValidTerm(currTerm)) {
                    potentialTerms.add(currTerm);
                    // collect its ctf
                    if (!termCtf.containsKey(currTerm)) {
                        termCtf.put(currTerm, new Double(tv.totalStemFreq(currIndex)));
                    }
                }
            }
        }
        // cache term length
        double termC = Idx.getSumOfFieldLengths("body");
        // begin to calculate score
        for (String term : potentialTerms) {
            double ctf = termCtf.get(term);
            double expandWeight = Math.log(termC / ctf);
            double score = 0.0;
            // sum over all documents
            for (TermVector tv : docTermVector) {
                int docid = tv.docId;
                score += 
                        getTermScoreIndri(ctf, termC, term, tv, docid) * 
                        getOriginalScore(r, docidToIndex.get(docid)) * 
                        expandWeight;
                
            }
            // add into the expansion terms list
            expandTerms.add(new LearnedTerm(term, score));
            // maintain the heap size
            if (expandTerms.size() > fbTerms) {
                expandTerms.poll();
            }
        }
        
        return expandTerms;
    }
    
    /**
     * Convert the data structure of learned queries into a string.
     * @param r the score list
     * @return the converted string
     * */
    public String produceExpandQuery(ScoreList r) throws IOException {
        // get the heap
        PriorityQueue<LearnedTerm> expandTerms = produceExpandTerms(r);
        // specify output format
        DecimalFormat decimalFormat = new DecimalFormat("0.0000");
        
        String accumulate = "";
        for (LearnedTerm lt : expandTerms) {
            String curr = decimalFormat.format(lt.getScore()) + " " + lt.getTerm();
            if (accumulate == "") { accumulate = curr; }
            else {
                accumulate += " " + curr;
            }
        }
        return "#wand (" + accumulate + ")";
    }
    
    /**
     * The query result format for output.
     * */
    static String QUERY_RESULT_FORMAT = "%d: %s\n";
    /**
     * To print out the expanded queries.
     * @param expansionQueryFile the file name to output
     * @param expandQuery the expanded query
     * */
    public void printExpandQueryOut(String append, String expansionQueryFile, int qid, String expandQuery) 
            throws FileNotFoundException {
        
        if (append != null) {
            String[] filenames = expansionQueryFile.split("\\.");
            expansionQueryFile = filenames[0] + append + "." + filenames[1];
        }
        
        PrintWriter writer = new PrintWriter(new FileOutputStream(new File(expansionQueryFile), true));
        // write out
        writer.format(QUERY_RESULT_FORMAT, qid, expandQuery);
        
        writer.close();
    }
    
    /**
     * To create a combined query based on the original query and expanded query.
     * @param originalQuery the original query
     * @param expandQuery the expanded query
     * @return the combined query
     * */
    public String produceCombinedQuery(String originalQuery, String expandQuery) {
        // #wand (w original (1-w) expand)
        return "#wand (" + fbOrigWeight + " " + originalQuery + " " + 
                        (1.0 - fbOrigWeight) + " " + expandQuery + 
                      ")";
    }
    
    /**
     * Getter for fbDocs.
     * @return fbDocs
     * */
    public int getfbDocs() {
        return fbDocs;
    }
    
    /**
     * Getter for fbTerms.
     * @return fbTerms
     * */
    public int getfbTerms() {
        return fbTerms;
    }
    
    /**
     * Getter for fbMu.
     * @return fbMu
     * */
    public double getfbMu() {
        return fbMu;
    }
    
    /**
     * Getter for fbOrigWeight.
     * @return fbOrigWeight
     * */
    public double getfbOrigWeight() {
        return fbOrigWeight;
    }
    
}