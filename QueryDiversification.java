import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class QueryDiversification {
    
    /**
     * The Algorithm to be chosen.
     * */
    public static enum DiverseAlgorithm {
        PM2, xQuAD
    };
    
    /**
     * Data structure pair for <docid, score>
     * */
    private class DiversifyScore {
        private int docid;
        private double score;
        
        public DiversifyScore(int docid_, double score_) {
            docid = docid_;
            score = score_;
        }
    }
    /**
     * Acceptable values are integers > 0. This value determines
     * the maximum number of documents in the relevance ranking and the
     * intent rankings that your software should use for diversification
     * You software should ignore documents below this ranking
     * */
    private static int maxInputRankingsLength;
    /**
     * Acceptable values are integers > 0. This value determines the number
     * of documents in the diversified ranking that your software will
     * produce.
     * */
    private static int maxResultRankingLength;
    /**
     * Acceptable values are "PM2" and "xQuAD". This value
     * controls the diversification algorithm used by
     * your software.
     * */
    private DiverseAlgorithm algorithm;
    /**
     * The path to the query intents file.
     * */
    private List<String> qIntents;
    /**
     * The query intent number.
     * */
    private static int qIntentNum;
    /**
     * Acceptable values are in the range [0.0, 1.0]
     * */
    private double lambda;
    
    /**
     * Constructor.
     * @throws Exception 
     * */
    public QueryDiversification(int maxInputRankingsLength_, 
            int maxResultRankingLength_, 
            DiverseAlgorithm algorithm_,
            double lambda_) throws Exception {
        
        maxInputRankingsLength = maxInputRankingsLength_;
        maxResultRankingLength = maxResultRankingLength_;
        algorithm = algorithm_;
        lambda = lambda_;
        
    }
    
    /**
     * Change the array of intents.
     * @param qIntents_ the array of intents
     * */
    public void setqIntent(List<String> qIntents_) {
        qIntents = qIntents_;
        qIntentNum = qIntents.size();
    }
    
    /**
     * Get rid of the documents in intents results that are not in initial rankings.
     * @param qScores the document scores for all queries
     * */
    private static void truncateDocuments(List<Map<Integer, Double>> qScores) {
        // initial ranking
        Set<Integer> initialDocs = qScores.get(0).keySet();
        // all other intents
        for (int i = 1; i < qScores.size(); ++i) {
            Map<Integer, Double> currIntent = qScores.get(i);
            // collect all documents to be removed
            Set<Integer> toRemove = new TreeSet<Integer>();
            for (Integer docid : currIntent.keySet()) {
                if (!initialDocs.contains(docid)) {
                    toRemove.add(docid);
                }
            }
            // remove documents
            for (Integer docid : toRemove) {
                currIntent.remove(docid);
            }
        }
    }
    
    /**
     * Find the number that we need to scale for all queries.
     * @param qScores the documents scores
     * @return the scale if we need to scale, -1 otherwise
     * */
    private double scaleStandard(List<Map<Integer, Double>> qScores) {
        // see if we need to scale the documents 
        boolean needScale = false;
        
        double maxsum = 0.0;
        for (int i = 0; i < qScores.size(); ++i) {
            double subsum = 0.0;
            // the current score list
            Map<Integer, Double> currMap = qScores.get(i);
            // find the sum of all documents
            for (Integer docid : currMap.keySet()) {
                double currScore = currMap.get(docid);
                if (currScore > 1.0) { needScale = true; }
                subsum += currScore;
            }
            if (maxsum < subsum) {
                maxsum = subsum;
            }
        }
        // return the scale accordingly
        if (needScale) {
            return maxsum;
        } else {
            return -1.0;
        }
    }
    
    /**
     * To scale the documents now.
     * @param qScores all document scores
     * */
    private void scaleDocScores(List<Map<Integer, Double>> qScores) {
        // we first truncate all documents
        truncateDocuments(qScores);
        // then we get the scale across all queries
        double base = scaleStandard(qScores);
        // if we need to scale, we scale
        if (base != -1.0) {
            for (int i = 0; i < qScores.size(); ++i) {
                Map<Integer, Double> currMap = qScores.get(i);
                for (Integer docid : currMap.keySet()) {
                    // all divide the scale
                    currMap.put(docid, currMap.get(docid) / base);
                }
            }
        }
    }
    
    /**
     * To run the diversification algorithm.
     * @param qScores the score lists
     * @return the final score list
     * */
    public ScoreList runDiversification(List<Map<Integer, Double>> qScores) throws Exception {
        scaleDocScores(qScores);
        // the initial ranking
        Map<Integer, Double> initialRanking = qScores.get(0);
        // dispatch
        if (algorithm.equals(DiverseAlgorithm.xQuAD)) {
            return xQuadDiversification(initialRanking, qScores);
        } else if (algorithm.equals(DiverseAlgorithm.PM2)) {
            return PM2Diversification(initialRanking, qScores);
        } else {
            return null;
        }
    }
    
    /**
     * Get the document scores if we are using query models.
     * @param queryOriginal the original query
     * @param allqIntents all intents
     * @param model the retrieval model we use
     * @return the document score
     * */
    public static List<Map<Integer, Double>> 
        processRankingWithQuery(String queryOriginal, List<String> allqIntents, RetrievalModel model) 
                throws Exception {
        // we have intents + original query
        List<Map<Integer, Double>> allRankings = new ArrayList<>(allqIntents.size() + 1);
        // the result
        ScoreList r = QryEval.processQuery(queryOriginal, model);
        // we select the less number of documents
        int size = Math.min(maxInputRankingsLength, r.size());
        // first get the initial ranking
        Map<Integer, Double> initialMap = new HashMap<>(size);
        for (int j = 0; j < size; ++j) {
            initialMap.put(r.getDocid(j), r.getDocidScore(j));
        }
        allRankings.add(initialMap);
        // then we proceed all other intents
        for (int i = 0; i < allqIntents.size(); ++i) {
            String query = allqIntents.get(i);
            Map<Integer, Double> intentRanking = new HashMap<>(size);
            // use the model to get score list
            ScoreList rr = QryEval.processQuery(query, model);
            for (int j = 0; j < size; ++j) {
                intentRanking.put(rr.getDocid(j), rr.getDocidScore(j));
            }
            allRankings.add(intentRanking);
        }
        return allRankings;
    }
    
    /**
     * Get all query intents from the input file.
     * @param intentsFile_ the filepath for query intents
     * @param currqid the current query id
     * @param the query list
     * */
    public static List<String> processAllIntents(String intentsFile_, int currqid) throws Exception {
        
        List<String> allqIntents = new ArrayList<String>();
        
        FileInputStream rankFile = new FileInputStream(intentsFile_);
        BufferedReader br = new BufferedReader(new InputStreamReader(rankFile));
        
        String line = null;
        while ((line = br.readLine()) != null) {
             String[] pair = line.split(":");
             String query = pair[1];
             String[] qidWIntent = pair[0].split("\\.");
             // see if it is the query we are looking for
             int qid = Integer.parseInt(qidWIntent[0]);
             if (qid == currqid) {
                 allqIntents.add(query);
             }
        }
        
        br.close();
        return allqIntents;
    }
    
    /**
     * Process the ranking from file.
     * @param initialRankingFile_ the file path
     * @param currqid the current query id
     * @return the document score
     * */
    public static List<Map<Integer, Double>> 
        processInitialRankingFile(String initialRankingFile_, int currqid) throws Exception {
        
        List<Map<Integer, Double>> initialRankingFile = 
                new ArrayList<Map<Integer, Double>>();
        
        FileInputStream rankFile = new FileInputStream(initialRankingFile_);
        BufferedReader br = new BufferedReader(new InputStreamReader(rankFile));
        // first store as score list
        List<ScoreList> initialScoresFromFile = new ArrayList<>();
        
        String line = null;
        while ((line = br.readLine()) != null) {
             String[] pair = line.split(" ");
             String query = pair[0];
             String externalid = pair[2];
             if (Idx.hasInternalDocid(externalid)) {
                 int docid = Idx.getInternalDocid(externalid);
                 double score = Double.parseDouble(pair[4]);
                 int qid = 0;
                 int intent = 0;
                 if (query.contains(".")) {
                     // different intents
                     String[] parts = query.split("\\.");
                     qid = Integer.parseInt(parts[0]);
                     intent = Integer.parseInt(parts[1]);
                 } else {
                     // original query
                     qid = Integer.parseInt(query);
                 }
                 if (qid == currqid) {
                     // this array contains all <docid, score> pairs for
                     // a particular intent
                     if (initialScoresFromFile.size() < (intent + 1)) {
                         // not yet initialize
                         ScoreList r = new ScoreList();
                         r.add(docid, score);
                         initialScoresFromFile.add(r);  
                     } else {
                         // already there, append more <docid, score> pair
                         ScoreList r = initialScoresFromFile.get(intent);
                         r.add(docid, score);
                     }
                 }
             }
        }
        
        // then we change the score list into the document score map
        int size = Math.min(initialScoresFromFile.get(0).size(), maxInputRankingsLength);
        for (int i = 0; i < initialScoresFromFile.size(); ++i) {
            Map<Integer, Double> currMap = new HashMap<>(size);
            ScoreList r = initialScoresFromFile.get(i);
            for (int j = 0; j < size; ++j) {
                currMap.put(r.getDocid(j), r.getDocidScore(j));
            }
            initialRankingFile.add(currMap);
        }
        
        br.close();
        return initialRankingFile;
    }
    
    /**
     * Find the next document in xQuad algorithm.
     * @param diversifySofar the diversify documents so far
     * @param qScores the document scores
     * @param initialRanking the initial ranking
     * @return the document and score to be selected next.
     * */
    private DiversifyScore findNextDocumentxQuad(ScoreList diversifySofar, 
            List<Map<Integer, Double>> qScores, 
            Map<Integer, Double> initialRanking) {
        
        double intentWeight = 1.0 / qIntentNum;
        // the default score and docid
        DiversifyScore ds = new DiversifyScore(-1, -Double.MAX_VALUE);        
        for (Integer docid : initialRanking.keySet()) {
            // the relevance score
            double relevance = (1.0 - lambda) * initialRanking.get(docid);
            // calculate the diversity score
            double diversity = 0.0;
            // iterate thorugh all intents
            int intentNumber = 1;
            while (intentNumber <= qIntentNum) {
                Map<Integer, Double> currScoreMap = qScores.get(intentNumber);
                Double docscore = currScoreMap.get(docid);
                double score = (docscore == null) ? 0.0 : docscore;
                for (int i = 0; i < diversifySofar.size(); ++i) {
                    int diversifyDocid = diversifySofar.getDocid(i);
                    Double diverdocscore = currScoreMap.get(diversifyDocid);
                    score *= 1.0 - ((diverdocscore == null) ? 0.0 : diverdocscore);
                }
                diversity += score;
                intentNumber++;
            }
            // scale
            diversity *= lambda * intentWeight;
            // find maximum
            if (ds.score < (relevance + diversity)) {
                ds.docid = docid;
                ds.score = relevance + diversity;
            }
        }
        
        return ds;
    }
    
    /**
     * The xQuad algorithm.
     * @param initialRanking the initial ranking
     * @param qScores the document score
     * @return the final score list
     * */
    private ScoreList xQuadDiversification(Map<Integer, Double> initialRanking,
            List<Map<Integer, Double>> qScores) {
        // the result
        ScoreList result = new ScoreList();
        // follow the algorithm
        while (result.size() < maxResultRankingLength) {
            DiversifyScore currDs = findNextDocumentxQuad(result, qScores, initialRanking);
            initialRanking.remove(currDs.docid);
            result.add(currDs.docid, currDs.score);
        }
        
        return result;
    }
    
    
    /**
     * Find the next document to add in PM2 algorithm.
     * @param diversitySofar the documents we added so far
     * @param qScores the document list
     * @param initialRanking the initial ranking
     * @param qt the priority
     * @param i_ the intent selected
     * @return the <docid, score> to be returned for next time
     * */
    private DiversifyScore findNextDocumentPM2(ScoreList diversifySofar, 
            List<Map<Integer, Double>> qScores, 
            Map<Integer, Double> initialRanking, 
            double qt[], 
            int i_) {
        // the default score
        DiversifyScore ds = new DiversifyScore(-1, -Double.MAX_VALUE);
        for (Integer docid : initialRanking.keySet()) {
            Double coverQScore = (qScores.get(i_ + 1).get(docid));
            // see how well it covers the query 
            double coverQ = coverQScore == null ? 
                    0.0 : (lambda * qt[i_] * coverQScore);
            // see how well it covers other intents
            double coverOther = 0.0;
            for (int i = 0; i < qIntentNum; ++i) {
                if (i != i_) {
                    Double docscore = qScores.get(i + 1).get(docid);
                    coverOther += (docscore == null) ?
                            0.0 : qt[i] * docscore;
                }
            }
            // scale
            double score = coverQ + (1.0 - lambda) * coverOther;
            // find maximum
            if (ds.score < score) {
                ds.docid = docid;
                ds.score = score;
            }
        }
        return ds;
    }
    
    /**
     * Find the next intent number.
     * @param qt the priority
     * */
    private int findNextIntent(double qt[]) {
        int idx = -1;
        double score = 0.0;
        for (int i = 0; i < qIntentNum; ++i) {
            double rank = qt[i];
            if (score < rank) {
                idx = i;
                score = rank;
            }
        }
        return idx;
    }
    
    /**
     * Update the priority.
     * @param docid the current document
     * @param intent the current intent
     * @param qScores the document scores
     * @return the score to be added
     * */
    private double updateCoverage(int docid, int intent, List<Map<Integer, Double>> qScores) {
        
        Double currScore = qScores.get(intent + 1).get(docid);
        // if we do not have the current document just return 0.0
        if (currScore == null) { return 0.0; }
        // we have the document return the percentage
        double sum = 0.0;
        for (int i = 0; i < qIntentNum; ++i) {
            Double docscore = qScores.get(i + 1).get(docid);
            sum += (docscore == null) ? 0.0 : docscore;
        }
        
        return currScore / sum;
    }
    
    /**
     * The PM2 algorithm.
     * @param initialRanking the initial ranking
     * @param qScores the document score
     * @return the final score list
     * */
    private ScoreList PM2Diversification(Map<Integer, Double> initialRanking,
            List<Map<Integer, Double>> qScores) throws Exception {
        
        double desiredRank = (maxResultRankingLength + 0.0) / qIntentNum;
        
        ScoreList result = new ScoreList();
        double s[] = new double[qIntentNum];
        // the priority
        double qt[] = new double[qIntentNum];
        // iterate until we have enough documents
        while (result.size() < maxResultRankingLength) {
            // update the priority
            for (int i = 0; i < qIntentNum; ++i) {
                qt[i] = desiredRank / (2.0 * s[i] + 1.0);
            }
            // get next intent
            int nextIntent = findNextIntent(qt);
            DiversifyScore ds = findNextDocumentPM2(result, qScores, initialRanking, qt, nextIntent);
            // update result
            result.add(ds.docid, ds.score);
            initialRanking.remove(ds.docid);
            
            for (int i = 0; i < qIntentNum; ++i) {
                s[i] += updateCoverage(ds.docid, i, qScores);
            }
        }
        
        return result;
    }
}