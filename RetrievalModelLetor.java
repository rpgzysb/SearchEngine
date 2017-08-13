import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This is the Learning to Rank retrieval model.
 * It records all information needed to perform a learning to rank
 * information search.
 * */
public class RetrievalModelLetor extends RetrievalModel {
    /**
     * The number of features in this learning to rank model.
     * */
    static private final int featureNum = 18;
    /**
     * A file of training queries.
     * */
    private String trainingQueryFile;
    /**
     * A file of relevance judgments.
     * Column 1 is the query id. Column 2 is ignored. Column 3 is the document id.
     * Column 4 indicates the degree of relevance (0 - 2).
     * */
    private String trainingQrelsFile;
    /**
     * The file of feature vectors that your software will
     * write for the training queries.
     * */
    private String trainingFeatureVectorsFile;
    /**
     * Filepath to PageRank scores.
     * */
    private String pageRankFile;
    /**
     * A comma-separated list of features to disable for this assignment.
     * For example, "letor:featureDisable=6, 9, 12, 15" disables all Indri features.
     * If this parameter is missing, use all features.
     * */
    private boolean[] featureDisable;
    /**
     * A path to the svm_rank_learn executable.
     * */
    private String svmRankLearnPath;
    /**
     * A path to the svm_rank_classify executable.
     * */
    private String svmRankClassifyPath;
    /**
     * The value of the c parameter for SVMrank. 0.001 is a good default.
     * */
    private double svmRankParamC;
    /**
     * The file where svm_rank_learn will write the learned model.
     * */
    private String svmRankModelFile;
    /**
     * The file of feature vectors that your software
     * will write for the testing queries.
     * */
    private String testingFeatureVectorsFile;
    /**
     * The file of document scores that svm_rank_classify will write for the testing feature vectors.
     * */
    private String testingDocumentScores;
    /**
     * BM25 doc length.
     * */
    private double N;
    /**
     * The BM25 model to use for initial retrieval.
     * */
    private RetrievalModelBM25 BM25Model;
    /**
     * The Indri model to use for different scores.
     * */
    private RetrievalModelIndri indriModel;
    
    /**
     * The constructor for learning to rank model.
     * @param trainingQueryFile_ the file path to the training queries
     * @param trainingQrelsFile_ the file path to relevance judges
     * @param trainingFeatureVectorsFile_ the file path to be written for training feature vectors
     * @param pageRankFile_ the file path to page rank file
     * @param feaetureDisable_ list of features to be disabled
     * @param svmRankLearnPath_ the file path to svmRankLearn
     * @param svmRankClassifyPath_ the file path to svmRankClassify
     * @param svmRankParamC_ the parameter for svRankLearn
     * @param svmRankModelFile_ the file path to be written for svmRank model
     * @param testingFeatureVectorsFile_ the file path to write out testing query feature vectors
     * @param testingDocumentScores_ the file path to write out document scores
     * @param parameters the parameters provided
     * */
    public RetrievalModelLetor(String trainingQueryFile_, 
                                String trainingQrelsFile_, 
                                String trainingFeatureVectorsFile_,
                                String pageRankFile_, 
                                String featureDisable_,
                                String svmRankLearnPath_, 
                                String svmRankClassifyPath_, 
                                double svmRankParamC_, 
                                String svmRankModelFile_,
                                String testingFeatureVectorsFile_, 
                                String testingDocumentScores_, 
                                Map<String, String> parameters) throws Exception {
        
        trainingQueryFile = trainingQueryFile_;
        trainingQrelsFile = trainingQrelsFile_;
        trainingFeatureVectorsFile = trainingFeatureVectorsFile_;
        pageRankFile = pageRankFile_;
        // handle disabled features
        featureDisable = new boolean[featureNum];
        List<Integer> disablesList = convertDisableFeatures(featureDisable_);
        if (disablesList != null) {
            for (Integer idx : disablesList) {
                featureDisable[idx - 1] = true;
            }
        }
        
        svmRankLearnPath = svmRankLearnPath_;
        svmRankClassifyPath = svmRankClassifyPath_;
        svmRankParamC = svmRankParamC_;
        svmRankModelFile = svmRankModelFile_;
        testingFeatureVectorsFile = testingFeatureVectorsFile_;
        testingDocumentScores = testingDocumentScores_;
        
        BM25Model = new RetrievalModelBM25(Double.parseDouble(parameters.get("BM25:k_1")),
                                        Double.parseDouble(parameters.get("BM25:b")),
                                        Double.parseDouble(parameters.get("BM25:k_3")));
        
        indriModel = new RetrievalModelIndri(Integer.parseInt(parameters.get("Indri:mu")),
                                    Double.parseDouble(parameters.get("Indri:lambda")));
        
        N = Idx.getNumDocs();
    }
    
    /**
     * Helper function to get BM25 term score.
     * @param df the document frequency
     * @param tf the term frequency
     * @param doclen the document length
     * @param avgDocLength the average document length
     * @return the term score of BM25 model
     * */
    private double getBM25TermScore(double df, double tf, int doclen,
            double avgDocLength) {
        
        double k1 = BM25Model.getK1();
        double b = BM25Model.getB();
        
        double first = Math.max(0.0, Math.log((N - df + 0.5) / (df + 0.5)));
        double second = tf / (tf + k1 * ((1-b) + b * doclen / avgDocLength));
        return first * second;
    }
    
    /**
     * The main function to process the training process and rank test queries.
     * @param parameters the parameters provided
     * */
    public void trainThenProcessQuery(Map<String, String> parameters) throws Exception {
        // generate training data
        String qLine = null;
        Map<Integer, TreeMap<String, Integer>> queryRelDocs = sortOutQrelsFile(trainingQrelsFile);
        // get the query id and query itself
        int qid = -1;
        String query = null;
        BufferedReader input = new BufferedReader(new FileReader(trainingQueryFile));
       
        while ((qLine = input.readLine()) != null) {
            // use QryEval.tokenizeQuery to stop & stem the query
            int d = qLine.indexOf(':');
            if (d < 0) {
                throw new IllegalArgumentException
                ("Syntax error:  Missing ':' in query line.");
            }
            qid = Integer.parseInt(qLine.substring(0, d));
            query = qLine.substring(d + 1);
            String[] queryTokens = QryParser.tokenizeString(query);
            // for each document d in the relevance judgements for training query q {
            // read the PageRank feature from a file
            // fetch the term vector for d
            // calculate other features for <q, d>
            // }
            Map<String, Integer> relDocs = queryRelDocs.get(qid);
            Set<String> docs = relDocs.keySet();
            Map<Integer, Double> pageRankMap = new HashMap<Integer, Double>(docs.size());
            pageRankMap = processPageRankFile(docs, pageRankFile);
            // set up the maximum or minimum features statistics
            Double[] maxFeatures = new Double[featureNum];
            for (int i = 0; i < featureNum; ++i) { maxFeatures[i] = -Double.MAX_VALUE; }
            Double[] minFeatures = new Double[featureNum];
            for (int i = 0; i < featureNum; ++i) { minFeatures[i] = Double.MAX_VALUE; }
            // prepare for all document term vector
            // custom features only
            // disable unless
            TermVector[] allDocTermVectors = null;
            if (!featureDisable[16] || !featureDisable[17]) {
                allDocTermVectors = new TermVector[relDocs.size()];
                int cnt = 0;
                for (String externalid : relDocs.keySet()) {
                    if (Idx.hasInternalDocid(externalid)) {
                        int docid = Idx.getInternalDocid(externalid);
                        TermVector tv = new TermVector(docid, "body");
                        allDocTermVectors[cnt] = tv;
                    } else {
                        allDocTermVectors[cnt] = null;
                    }
                    cnt++;
                }
            }
            // create an empty feature vector
            Map<String, ArrayList<Double>> allFeatures = new TreeMap<String, ArrayList<Double>>();
            // iterate through all relevance judgements
            int docidx = 0;
            for (String externaldocid : relDocs.keySet()) {
                // see if we need to ignore
                if (Idx.hasInternalDocid(externaldocid)) {
                    int docid = Idx.getInternalDocid(externaldocid);
                    // process feature
                    ArrayList<Double> featureVector = 
                            processFeatureVector(docidx, allDocTermVectors, queryTokens, docid, pageRankMap);
                    // update statistics
                    for (int j = 0; j < featureNum; ++j) {
                        Double currFeature = featureVector.get(j);
                        if (currFeature != null) {
                            Double currmin = minFeatures[j];
                            Double currmax = maxFeatures[j];
                            if (currFeature > currmax) {
                                maxFeatures[j] = currFeature;
                            }
                            if (currFeature < currmin) {
                                minFeatures[j] = currFeature;
                            }
                        }
                    }
                    // put the feature into the global vector
                    allFeatures.put(externaldocid, featureVector);
                }
                docidx++;
            }
            // normalize the feature values for query q to [0..1]
            normalizeFeatureVector(maxFeatures, minFeatures, allFeatures);
            // write the feature vectors to file
            writeFeatureVectors(relDocs, qid, allFeatures, trainingFeatureVectorsFile);
        }
        
      
        
        // train
        // call svmrank to train a model
        Process trainProc = Runtime.getRuntime().exec(
                new String[]{svmRankLearnPath, "-c", String.valueOf(svmRankParamC), 
                        trainingFeatureVectorsFile, svmRankModelFile}
                );
        runSVMRank(trainProc);
        
         // generate testing data for top 100 documents in initial BM25 rankinig
        input = new BufferedReader(new FileReader(parameters.get("queryFilePath")));   
        int queryIdx = 0;
        while ((qLine = input.readLine()) != null) {
            // use QryEval.tokenizeQuery to stop & stem the query
            int d = qLine.indexOf(':');
            if (d < 0) {
                throw new IllegalArgumentException
                ("Syntax error:  Missing ':' in query line.");
            }
            qid = Integer.parseInt(qLine.substring(0, d));
            query = qLine.substring(d + 1);
            String[] queryTokens = QryParser.tokenizeString(query);
            // run BM25 to create an initial ranking (on body field)
            ScoreList initialBM25Rank = QryEval.processQuery(query, BM25Model);
            ScoreList result = new ScoreList();
            for (int i = 0; i < 100; ++i) {
                result.add(initialBM25Rank.getDocid(i), initialBM25Rank.getDocidScore(i));
            }
            initialBM25Rank = null;
            // prepare the page rank file
            Set<String> docs = new HashSet<String>(100);
            for (int i = 0; i < 100; ++i) {
                docs.add(Idx.getExternalDocid(result.getDocid(i)));
            }
            Map<Integer, Double> pageRankMap = new HashMap<Integer, Double>(docs.size());
            pageRankMap = processPageRankFile(docs, pageRankFile);
            // statistics for feature vectors
            Double[] maxFeatures = new Double[featureNum];
            for (int i = 0; i < featureNum; ++i) { maxFeatures[i] = -Double.MAX_VALUE; }
            Double[] minFeatures = new Double[featureNum];
            for (int i = 0; i < featureNum; ++i) { minFeatures[i] = Double.MAX_VALUE; }
            // for each document d in the top 100 of initial ranking {
            // create an empty feature vector
            // read the PageRank feature from a file
            // fetch the term vector for d
            // calculate other features for <q, d>
            // }
            // prepare for all document term vector
            TermVector[] allDocTermVectors = null;
            if (!featureDisable[16] || !featureDisable[17]) {
                allDocTermVectors = new TermVector[100];
                for (int i = 0; i < 100; ++i) {
                    TermVector tv = new TermVector(result.getDocid(i), "body");
                    allDocTermVectors[i] = tv;
                }
            }
            // begin to collect features
            Map<String, ArrayList<Double>> allFeatures = new TreeMap<String, ArrayList<Double>>();  
            for (int i = 0; i < 100; ++i) {
                int docid = result.getDocid(i);
                ArrayList<Double> featureVector = 
                        processFeatureVector(i, allDocTermVectors, queryTokens, docid, pageRankMap);
                // collect max & min features across all documents
                for (int j = 0; j < featureNum; ++j) {
                    Double currFeature = featureVector.get(j);
                    // update statistics
                    if (currFeature != null) {
                        Double currmin = minFeatures[j];
                        Double currmax = maxFeatures[j];
                        if (currFeature > currmax) {
                            maxFeatures[j] = currFeature;
                        }
                        if (currFeature < currmin) {
                            minFeatures[j] = currFeature;
                        }
                    }
                }
                // update feature into the global feature vector
                String externaldocid = Idx.getExternalDocid(docid);
                allFeatures.put(externaldocid, featureVector);
            }
            // normalize the feature values for query q to [0..1]
            normalizeFeatureVector(maxFeatures, minFeatures, allFeatures);
            // write the feature vectors to file
            writeFeatureVectors(null, qid, allFeatures, testingFeatureVectorsFile);

            // re-rank test data
            // call svmrank to produce scores for the test data
            Process classifyProc = Runtime.getRuntime().exec(
                    new String[]{svmRankClassifyPath, testingFeatureVectorsFile, 
                            svmRankModelFile, testingDocumentScores}
                    );
            runSVMRank(classifyProc);
            // read in the svmrank scores and re-rank the initial ranking based on the scores
            // output re-ranked result into trec-eval format
            // re-rank the initial ranking
            processSVMRankResult(100 * queryIdx, result, testingDocumentScores);
            result.sort();
            // write out results
            QryEval.printResults(null, parameters.get("trecEvalOutputPath"), qid + "", result);
            queryIdx++;
        }
    }

    /**
     * Calculate the average term frequencies of query terms appear in a document.
     * @param stemTokens the query terms
     * @param doc the document
     * @return the average term frequencies
     * */
    private static double averageTf(String[] stemTokens, TermVector doc) {
        
        int sum = 0;
        // count how many terms appear in a document
        int cnt = 0;
        
        for (int i = 0; i < stemTokens.length; ++i) {
            int idx = doc.indexOfStem(stemTokens[i]);
            if (idx != -1) {
                cnt++;
                sum += doc.stemFreq(idx);
            }
        }
        // if there is no query terms appear in a document, return 0
        if (cnt == 0) {return 0.0;}
        // otherwise return average
        return (double)sum / cnt;
    }
    
    /**
     * Calculate the term frequency deviation.
     * @param stemTokens the query terms
     * @param doc the document
     * @return the term frequency deviation
     * */
    private static Double termFreqDeviation(String[] stemTokens, TermVector doc) {
     
        // calculate the average term frequency
        double avgTf = averageTf(stemTokens, doc);
       
        // if it is zero, means no query terms appear in a document
        // then this feature does not apply
        if (avgTf == 0.0) {
            return null;
        }
        // we have at least one query term in a document
        double sum = 0.0;
        for (int i = 0; i < stemTokens.length; ++i) {
            int idx = doc.indexOfStem(stemTokens[i]);
            int tf = 0;
            if (idx != -1) {
                tf = doc.stemFreq(idx);
            }
            double diff = avgTf - tf;
            // sum over the square distance for deviation
            sum += diff * diff;
        }

        return Math.sqrt(sum);
    }
    
    /**
     * Helper function determine whether a term vector is empty.
     * @param tv the term vector
     * @return true if empty, false otherwise
     * */
    private static boolean emptyTermVector(TermVector tv) {
        return tv.positionsLength() == 0 || tv.stemsLength() == 0;
    }
    
    /**
     * Calculate the vagueness feature score.
     * @param stemTokens the query terms
     * @param allDocs all of document vectors
     * @param idx the current document index in allDocs
     * @return the vagueness score
     * */
    private static Double vaguenessScore(String[] stemTokens, 
            TermVector[] allDocs, int idx) {
        // get the current document
        TermVector doc = allDocs[idx];
        // if it does not exist, this feature does not apply
        if (stemTokens == null || emptyTermVector(doc)) { return null; }
        // return the deviation
        return termFreqDeviation(stemTokens, doc);
    }


    /**
     * The average English word length.
     * */
    private static final double averageEnglishLength = 5.1;
    /**
     * Calculate the average length deviation.
     * @param doc the document
     * @return the deviation
     * */
    private static Double averageLengthNorm(TermVector doc) {
        
        if (emptyTermVector(doc)) { return null; }
        
        double score = 0.0;
        // iterate through the terms
        for (int i = 1; i < doc.stemsLength(); ++i) {
            double difference = doc.stemString(i).length() - averageEnglishLength;
            // square to see the deviation
            score += difference * difference;
        }
        return Math.sqrt(score);
    }
    
    /**
     * Give the feature BM25 score.
     * @param queryTokens the terms appear in a query
     * @param field the field
     * @param docid the document id
     * @return the score if it has this feature, null otherwise
     * */
    private Double featureBM25(String[] queryTokens, String field, int docid) 
            throws IOException {
        
        int docLength = Idx.getFieldLength(field, docid);
        double avgDocLength = Idx.getSumOfFieldLengths(field) / (double)Idx.getDocCount(field);
        // add for BM25
        double score = 0.0;
        TermVector tv = new TermVector(docid, field);
        // see if this feature applies
        if (tv.positionsLength() != 0 && tv.stemsLength() != 0) {
            for (int i = 0; i < queryTokens.length; ++i) {
                String qryStem = queryTokens[i];
                int idx = tv.indexOfStem(qryStem);
                if (idx != -1) {
                    double df = (double)tv.stemDf(idx);
                    double tf = (double)tv.stemFreq(idx);
                    score += getBM25TermScore(df, tf, docLength, avgDocLength);
                }
            }
            return score;
        }
        // does not apply, return null
        else {
            return null;
        }
    }
   
    /**
     * Give the term score for indri model.
     * @param mu the indri mu
     * @param lambda the indri lambda
     * @param tf the term frequency
     * @param docLength the document length
     * @param pMLE the pMLE
     * @return the term score
     * */
    public double getIndriTermScore(double mu, double lambda, 
            double tf, double docLength, 
            double pMLE) throws IOException {
       
        double leftSmooth = (1.0 - lambda) * (tf + mu * pMLE) / (mu + docLength);
        double rightSmooth = lambda * pMLE;
        
        return leftSmooth + rightSmooth;
        
    }
    
    /**
     * Give the feature Indri score.
     * @param queryTokens the terms appear in a query
     * @param field the field
     * @param docid the document id
     * @return the score if it has this feature, null otherwise
     * */
    private Double featureIndri(String[] queryTokens, String field, int docid) 
            throws Exception {
        double mu = (double)indriModel.getMu();
        double lambda = indriModel.getLambda();
        double docLength = Idx.getFieldLength(field, docid);
        double termC = (double)Idx.getSumOfFieldLengths(field);
        // multiplication for indri
        double score = 1.0;
        TermVector tv = new TermVector(docid, field);
        // if the feature applies
        if (tv.positionsLength() != 0 && tv.stemsLength() != 0) {
            // see if no query term matches
            boolean notMatchAnyTerm = true;
            for (int i = 0; i < queryTokens.length; ++i) {
                int idx = tv.indexOfStem(queryTokens[i]);
                if (idx != -1) { notMatchAnyTerm = false; }
            }
            if (notMatchAnyTerm) {return 0.0;}
            // at least one query term matches
            for (int i = 0; i < queryTokens.length; ++i) {
                String qryStem = queryTokens[i];
                int idx = tv.indexOfStem(qryStem);
                double ctf = Idx.getTotalTermFreq(field, qryStem);
                double pMLE = ctf / termC;
                if (idx != -1) {
                    double tf = (double)tv.stemFreq(idx);
                    score *= getIndriTermScore(mu, lambda, tf, docLength, pMLE);
                }
                // default score
                else {
                    score *= getIndriTermScore(mu, lambda, 0.0, docLength, pMLE);
                }
            }
            return Math.pow(score, 1.0 / ((double)queryTokens.length));
        }
        // it does not have this feature
        else {
            return null;
        }
    }
    
    /**
     * Process the SVMRank score of the initial ranking.
     * @param r the initial ranking
     * @param testingDocumentScores the file path to SVMRank score
     * */
    private void processSVMRankResult(int begin, ScoreList r, String testingDocumentScores) 
            throws FileNotFoundException {
        // open the file
        File svmRankResult = new File(testingDocumentScores);
        if (!svmRankResult.canRead()) {
            throw new IllegalArgumentException
            ("Can't read " + testingDocumentScores);
        }

        Scanner scan = new Scanner(svmRankResult);
        String line = null;
        int idx = 0;
        int cnt = 0;
        do {
            line = scan.nextLine();
            // update the new score
            if (cnt >= begin) {
                r.setDocidScore(idx, Double.parseDouble(line));
                idx++;
            }
            cnt++;
        } while (scan.hasNext() && idx < 100);
        scan.close();
    }
    
    // the feature vector output format
    static String FEATURE_VECTOR_FORMAT = "%d qid:%d %s # %s\n";
    /**
     * Write the feature vectors to a file.
     * @param relDocs the relevance judgments
     * @param qid the query id
     * @param allfv all features
     * @param outFilePath the output file path
     * */
    private void writeFeatureVectors(Map<String, Integer> relDocs, int qid,  
            Map<String, ArrayList<Double>> allfv, String outFilePath) throws FileNotFoundException {
        
        PrintWriter writer = new PrintWriter(new FileOutputStream(new File(outFilePath), true));
        for (String externalid : allfv.keySet()) {
            ArrayList<Double> fv = allfv.get(externalid);
            String featureString = "";
            for (int i = 0; i < fv.size(); ++i) {
                Double currFeature = fv.get(i);
                // see if the current feature applies
                if (currFeature != null) {
                    featureString += (i + 1) + ":" + fv.get(i) + " ";
                }
            }
            // get relevance judgements
            int relevance = (relDocs == null) ? 0 : relDocs.get(externalid);
            writer.format(FEATURE_VECTOR_FORMAT, relevance, qid, featureString, externalid);
        }
        
        writer.close();
    }
    
    /**
     * Helper function to process the relevance judgments.
     * @param filePath the file path to relevance judgments
     * @return the map of query id as key, and <docid, rels> pair map as value
     * */
    private Map<Integer, TreeMap<String, Integer>> sortOutQrelsFile(String filePath) 
            throws FileNotFoundException {
        
        Map<Integer, TreeMap<String, Integer>> queryRelDocs = 
                new TreeMap<Integer, TreeMap<String, Integer>>();
        
        File queryRelFile = new File(filePath);
        if (!queryRelFile.canRead()) {
            throw new IllegalArgumentException
            ("Can't read " + filePath);
        }

        Scanner scan = new Scanner(queryRelFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split (" ");
            int qid = Integer.parseInt(pair[0]);
            String externalid = pair[2];
            int relevance = Integer.parseInt(pair[3]);
            // each query id we have <docid, rels> pair
            TreeMap<String, Integer> currMap = 
                    (queryRelDocs.containsKey(qid) ? queryRelDocs.get(qid) : new TreeMap<String, Integer>());
            currMap.put(externalid, relevance);
            queryRelDocs.put(qid, currMap);
        } while (scan.hasNext());

        scan.close();
        return queryRelDocs;
    }
    
    /**
     * Invoke the SVMRank functionality within JAVA.
     * @param cmdProc the process abstraction
     * */
    private void runSVMRank(Process cmdProc) throws Exception {
        // runs svm_rank learn from within Java to train the model
        // execPath is the location of the svm_rank learn utility
        // FEAT_GEN.c is the value of the letor:c parameter
        
        // The stdout/stderr consuming code must be included
        // it prevents the OS from running out of output buffer space and staling
        
        // consume stdout and print it out for deugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream())
                );
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            //System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream())
                );
        while ((line = stderrReader.readLine()) != null) {
            //System.out.println(line);
        }
        
        // get the return value from the executable. 0 means success, 
        // non-zero indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }
    
    /**
     * Normalize the feature vectors.
     * @param maxFeatures the maximum across all features.
     * @param minFeatures the minimum across all features.
     * @param allFeatures all of the features
     * */
    private void normalizeFeatureVector(Double[] maxFeatures, Double[] minFeatures,
            Map<String, ArrayList<Double>> allFeatures) {
        
        // for each feature document
        for (String exdoc : allFeatures.keySet()) {
            ArrayList<Double> featureV = allFeatures.get(exdoc);
            // normalize all features
            for (int i = 0; i < featureNum; ++i) {
                Double currElt = featureV.get(i);
                Double maxElt = maxFeatures[i];
                Double minElt = minFeatures[i];
                
                Double range = maxElt - minElt;
                // if range is 0 or feature does not apply
                if (currElt == null || range == 0.0) { featureV.set(i, 0.0); }
                else {
                    featureV.set(i, (currElt - minElt) / range);
                }
            }
        }
    }
    
    /**
     * Helper to get Url depth.
     * @param rawUrl the url
     * @return the depth
     * */
    private Double getUrlDepth(String rawUrl) {
        // does not apply
        if (rawUrl == null) {
            return null;
        }
        
        int cnt = 0;
        for (int i = 0; i < rawUrl.length(); ++i) {
            if (rawUrl.charAt(i) == '/') { cnt++; }
        }
        // minus out the double slash
        cnt -= 2;
        return (double)cnt;
    }
    
    /**
     * Helper function to get wiki score.
     * @param rawUrl the url
     * @return 1 if has wiki, 0 no, null does not apply
     * */
    private Double getWikiScore(String rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        
        return rawUrl.contains("wikipedia.org") ? 1.0 : 0.0;
    }
    
    /**
     * Add feature based on the disabled vector.
     * @param isDisable see if this feature is disabled.
     * @param score the score to be returned
     * @return null if is disabled, score otherwise
     * */
    private static Double addFeature(boolean isDisable, Double score) {
        if (isDisable) { return null; }
        else { return score; }
    }
    
    /**
     * To process the feature vectors.
     * @param queryStems the terms within a query
     * @param docid the document id
     * @return the feature vector
     * */
    private ArrayList<Double> processFeatureVector(int idx, TermVector[] allDocTermVectors, 
            String[] queryStems, int docid, 
            Map<Integer, Double> pageRankMap) 
            throws Exception {
        // empty feature vector
        ArrayList<Double> featureVector = new ArrayList<Double>(featureNum);
        String rawUrl = Idx.getAttribute("rawUrl", docid);
        
        // f1: Spam score for d (read from index).
        featureVector.add(addFeature(featureDisable[0], 
                        Double.parseDouble((Idx.getAttribute("score", docid)))));
        
        // f2: Url depth for d(number of '/' in the rawUrl field).
        featureVector.add(addFeature(featureDisable[1],
                        getUrlDepth(rawUrl)));
        
        // f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
        featureVector.add(addFeature(featureDisable[2], 
                        getWikiScore(rawUrl)));
        
        // f4: PageRank score for d (read from file).
        Double toInsert = pageRankMap.get(docid);
        featureVector.add(addFeature(featureDisable[3], 
                        toInsert));
        
        // f5: BM25 score for <q, dbody>.
        featureVector.add(addFeature(featureDisable[4], 
                    featureBM25(queryStems, "body", docid)));
        
        // f6: Indri score for <q, dbody>.
        featureVector.add(addFeature(featureDisable[5], 
                    featureIndri(queryStems, "body", docid)));
        
        // f7: Term overlap score for <q, dbody>.
        featureVector.add(addFeature(featureDisable[6], 
                    calculateOverlap(queryStems, "body", docid)));
        
        // f8: BM25 score for <q, dtitle>.
        featureVector.add(addFeature(featureDisable[7], 
                    featureBM25(queryStems, "title", docid)));
        
        // f9: Indri score for <q, dtitle>.
        featureVector.add(addFeature(featureDisable[8], 
                    featureIndri(queryStems, "title", docid)));
        
        // f10: Term overlap score for <q, dtitle>.
        featureVector.add(addFeature(featureDisable[9], 
                    calculateOverlap(queryStems, "title", docid)));
        
        // f11: BM25 score for <q, durl>.
        featureVector.add(addFeature(featureDisable[10], 
                    featureBM25(queryStems, "url", docid)));
        
        // f12: Indri score for <q, durl>.
        featureVector.add(addFeature(featureDisable[11], 
                    featureIndri(queryStems, "url", docid)));
        
        // f13: Term overlap score for <q, durl>.
        featureVector.add(addFeature(featureDisable[12], 
                    calculateOverlap(queryStems, "url", docid)));
        
        // f14: BM25 score for <q, dinlink>.
        featureVector.add(addFeature(featureDisable[13], 
                    featureBM25(queryStems, "inlink", docid)));
        
        // f15: Indri score for <q, dinlink>.
        featureVector.add(addFeature(featureDisable[14], 
                    featureIndri(queryStems, "inlink", docid)));
        
        // f16: Term overlap score for <q, dinlink>.
        featureVector.add(addFeature(featureDisable[15], 
                    calculateOverlap(queryStems, "inlink", docid)));
        
        // f17: norm of average length deviation
        if (allDocTermVectors == null) {
            featureVector.add(null);
        } else {
            featureVector.add(addFeature(featureDisable[16], 
                    averageLengthNorm(allDocTermVectors[idx])));
        }
        // f18: query vagueness
        if (allDocTermVectors == null) {
            featureVector.add(null);
        } else {
            featureVector.add(addFeature(featureDisable[17],
                    vaguenessScore(queryStems, allDocTermVectors, idx)));
        }
        
        return featureVector;
    }
    
    /**
     * Helper function to calculate overlap.
     * @param queryTokens all query terms
     * @param field the field
     * @param docid the document id
     * @return the overlap score.
     * */
    private Double calculateOverlap(String[] queryTokens, String field, int docid) 
            throws IOException {
        
        int cnt = 0;
        TermVector tv = new TermVector(docid, field);
        if (tv.positionsLength() != 0 && tv.stemsLength() != 0) {
            for (int i = 0; i < queryTokens.length; ++i) {
                String qryStem = queryTokens[i];
                int idx = tv.indexOfStem(qryStem);
                if (idx != -1) { cnt++; }
            }
            return (double)cnt / (double)queryTokens.length;
        } 
        // does not apply
        else {
            return null;
        }
    }
    
    /**
     * Process the page rank file and store internally.
     * @param pageRankFile_ the path to page rank file
     * @return the <docid, pagerank> pair map
     * */
    private Map<Integer, Double> processPageRankFile(Set<String> docs, 
            String PageRankFile_) throws Exception {
        
        Map<Integer, Double> corpusPageRank = new HashMap<Integer, Double>(docs.size());
        
        FileInputStream pageRank = new FileInputStream(PageRankFile_);
        BufferedReader br = new BufferedReader(new InputStreamReader(pageRank));
        
        String line = null;
        while ((line = br.readLine()) != null) {
             String[] pair = line.split("\t");
             String externalid = pair[0];
             if (docs.contains(externalid) && Idx.hasInternalDocid(externalid)) {
                 float prscore = Float.parseFloat(pair[1]);
                 corpusPageRank.put(Idx.getInternalDocid(pair[0]), (double)prscore);
             }
        }
        return corpusPageRank;
    }
    
    /**
     * Process the disable features from input string.
     * @param featureDisable_ the input disable features string
     * @return a list of disabled features
     * */
    private List<Integer> convertDisableFeatures(String featureDisable_) {
        if (featureDisable_ == null) {
            return null;
        }
        
        List<Integer> disabledFeatures = new ArrayList<Integer>();
        String[] dFeatures = featureDisable_.split(",");
        for (int i = 0; i < dFeatures.length; ++i) {
            disabledFeatures.add(Integer.parseInt(dFeatures[i]));
        }
        return disabledFeatures;
    }
    
    /**
     * dummy inheritance function.
     * */
    @Override
    public String defaultQrySopName() {
        return null;
    }
    
    
    
}