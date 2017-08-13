/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.2.
 */
import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
        { "body", "title", "url", "inlink" };


    //  --------------- Methods ---------------------------------------

    /**
     *  @param args The only argument is the parameter file name.
     *  @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start ();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException (USAGE);
        }

        Map<String, String> parameters = readParameterFile (args[0]);

        //  Open the index and initialize the retrieval model.

        Idx.open (parameters.get ("indexPath"));
        RetrievalModel model = (parameters.containsKey("retrievalAlgorithm")) ? 
                initializeRetrievalModel(parameters) : null;

        
                
        if (model != null && model instanceof RetrievalModelLetor) {
            // machine learning training model
            ((RetrievalModelLetor)model).trainThenProcessQuery(parameters);
            
        } else {
            // normal ranking model
            //  Perform experiments.
            processQueryFile(parameters, model);
        }
        //  Clean up.

        timer.stop ();
        System.out.println ("Time:  " + timer);
    }

    /**
     *  Allocate the retrieval model and initialize it using parameters
     *  from the parameter file.
     *  @return The initialized retrieval model
     * @throws Exception 
     */
    public static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
            throws Exception {

        RetrievalModel model = null;
        String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelString.equals("bm25")) {
            double k1 = Double.parseDouble(parameters.get("BM25:k_1"));
            double b = Double.parseDouble(parameters.get("BM25:b"));
            double k3 = Double.parseDouble(parameters.get("BM25:k_3"));
            model = new RetrievalModelBM25(k1, b, k3);
        } else if (modelString.equals("indri")) {
            int mu = Integer.parseInt(parameters.get("Indri:mu"));
            double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
            model = new RetrievalModelIndri(mu, lambda);
        } else if (modelString.equals("letor")) {
            // get all parameters
            String trainingQueryFile = parameters.get("letor:trainingQueryFile");
            String trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
            String trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
            String pageRankFile = parameters.get("letor:pageRankFile");
            String featureDisable = parameters.get("letor:featureDisable");
            String svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
            String svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
            double svmRankParamC = Double.parseDouble(parameters.get("letor:svmRankParamC"));
            String svmRankModelFile = parameters.get("letor:svmRankModelFile");
            String testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
            String testingDocumentScores = parameters.get("letor:testingDocumentScores");
            // initialize the model
            model = new RetrievalModelLetor(trainingQueryFile, 
                                            trainingQrelsFile,
                                            trainingFeatureVectorsFile,
                                            pageRankFile,
                                            featureDisable,
                                            svmRankLearnPath,
                                            svmRankClassifyPath,
                                            svmRankParamC,
                                            svmRankModelFile,
                                            testingFeatureVectorsFile,
                                            testingDocumentScores,
                                            parameters);     
        } else {
            throw new IllegalArgumentException
            ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     * 
     * @param gc
     *          If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     * @param qString A string that contains a query.
     * @param model The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName ();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery (qString);

        // Show the query that is evaluated

        System.out.println("    --> " + q);

        if (q != null) {
            ScoreList r = new ScoreList ();

            if (q.args.size () > 0) {		// Ignore empty queries

                q.initialize (model);
                while (q.docIteratorHasMatch (model)) {
                    int docid = q.docIteratorGetMatch ();
                    double score = ((QrySop) q).getScore (model);
                    r.add (docid, score);
                    q.docIteratorAdvancePast (docid);
                }
            }
            r.sort();
            return r;
        } else
            return null;
    }

    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @param model
     * @throws Exception 
     */
    static void processQueryFile(Map<String, String> parameters,
            RetrievalModel model)
                    throws Exception {

        String outFilePath = parameters.get("trecEvalOutputPath");
        String queryFilePath = parameters.get("queryFilePath");


        BufferedReader input = null;

        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.

            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                    ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;
                // see if feedback service is specified
                boolean hasFeedback = 
                        parameters.containsKey("fb") && parameters.get("fb").equals("true");
                // see if we need diversity
                boolean hasDiversity = 
                        parameters.containsKey("diversity") && parameters.get("diversity").equals("true");
                
                
                if (!hasDiversity) {
                    // if no diversity
                    // use the query to retrieve documents
                    r = processQuery(query, model);
                } else {
                    // has diversity
                    boolean hasInitialRankingFile = parameters.containsKey("diversity:initialRankingFile");
                    // get all parameters
                    int maxInputRankingsLength 
                        = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
                    int maxResultRankingLength
                        = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
                    QueryDiversification.DiverseAlgorithm da 
                        = parameters.get("diversity:algorithm").equals("PM2") ? 
                            (QueryDiversification.DiverseAlgorithm.PM2)
                            : (QueryDiversification.DiverseAlgorithm.xQuAD);
                    double lambda = Double.parseDouble(parameters.get("diversity:lambda"));
                    String intentsFile = parameters.get("diversity:intentsFile");
                    
                    QueryDiversification qd = new QueryDiversification(maxInputRankingsLength, 
                            maxResultRankingLength, 
                            da,
                            lambda);
                    // the number of queries
                    int nqid = Integer.parseInt(qid);
                    // get all intents
                    List<String> allqItents
                        = QueryDiversification.processAllIntents(intentsFile, nqid);
                    // update the intents
                    qd.setqIntent(allqItents);
                   // get all ranking scores
                    List<Map<Integer, Double>> allRankingFile = null;
                    if (hasInitialRankingFile) {
                        // read relevance-based document rankings for query q 
                        // from the the diversity:initialRankingFile file;
                        // read relevance-based document rankings for 
                        // query intents q.i from the diversity:initialRankingFile file;
                        String initialRankingFile = parameters.get("diversity:initialRankingFile");
                        
                        allRankingFile
                            = QueryDiversification.processInitialRankingFile(initialRankingFile, nqid);
                    } else {
                        // read query q from the query file
                        // use query q to retrieve documents;
                        // for each of query q's intents
                        // read intent qi from the diversity:intentsFile file;
                        // use query qi to retrieve documents;
                        allRankingFile
                            = QueryDiversification.processRankingWithQuery(query, allqItents, model);
                    }
                    // use the diversity:algorithm to produce a diversified ranking;
                    r = qd.runDiversification(allRankingFile);
                    r.sort();
                }
                
                
                
                //String experiment = "-Exp2-" + qid;
                String experiment = null;
                if (!hasDiversity) {
                    if (!hasFeedback) {
                        // if no feedback
                        // produce query immediately
                        r = processQuery(query, model);
                    } else {
                        // see if has provided feedback ranking file
                        boolean hasFbInitialRank = parameters.containsKey("fbInitialRankingFile");
    
                        if (hasFbInitialRank) {
                            // read a document ranking in trec-eval input format
                            r = readFeedbackRankFile(qid, parameters.get("fbInitialRankingFile"));
                        } else {
                            // produce query for feedback
                            r = processQuery(query, model);
                        }
                        // get parameters
                        int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
                        int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
                        double fbMu = Double.parseDouble(parameters.get("fbMu"));
                        double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
                        String expansionQueryFile = parameters.get("fbExpansionQueryFile");
                        
                        // the pseudo relevance feedback service
                        PseudoRelevanceFeedback prf = new PseudoRelevanceFeedback(fbDocs, fbTerms, fbMu, fbOrigWeight);
    
                        // use the Indri query expansion algorithm to produce an expanded query
                        String expandQuery = prf.produceExpandQuery(r);
                        // write the expanded query to a file specified by the parameter
                        prf.printExpandQueryOut(experiment, expansionQueryFile, Integer.parseInt(qid), expandQuery);
                        // create a combined query
                        String defaultOp = model.defaultQrySopName ();
                        query = defaultOp + "(" + query + ")";
                        String combinedQuery = prf.produceCombinedQuery(query, expandQuery);
                        // use the combined query to retrieve documents
                        r = processQuery(combinedQuery, model);
                    }
                }
                
                // write the retrieval results to a file in trec_eval input format
                if (r != null) {
                    printResults(experiment, outFilePath, qid, r);
                    System.out.println();
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
        }
    }

    /**
     * Print the query results.
     * 
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * 
     * QueryID Q0 DocID Rank Score RunID
     * 
     * @param queryName
     *          Original query.
     * @param result
     *          A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static String QUERY_RESULT_FORMAT = "%d Q0 %s %d %.18f HW4\n";
    static String DUMMY_RESULT = "10 Q0 dummy 1 0 HW4\n";
    static void printResults(String append, String outFilePath, String queryName, ScoreList result) throws IOException { 
        
        if (append != null) {
            String[] filenames = outFilePath.split("\\.");
            outFilePath = filenames[0] + append + "." + filenames[1];
        }
        
        PrintWriter writer = new PrintWriter(new FileOutputStream(new File(outFilePath), true));
        if (result.size() < 1) {
            writer.print(DUMMY_RESULT);
        } else {
            for (int i = 0; i < Math.min(result.size(), 100); i++) {
                writer.format(QUERY_RESULT_FORMAT, Integer.parseInt(queryName), Idx.getExternalDocid(result.getDocid(i)), i + 1, result.getDocidScore(i));
            }
        }
        writer.close();
    }

    /**
     *  Read the specified parameter file, and confirm that the required
     *  parameters are present.  The parameters are returned in a
     *  HashMap.  The caller (or its minions) are responsible for processing
     *  them.
     *  @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile (String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();

        File parameterFile = new File (parameterFileName);

        if (! parameterFile.canRead ()) {
            throw new IllegalArgumentException
            ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split ("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (! (parameters.containsKey ("indexPath") &&
                parameters.containsKey ("queryFilePath") &&
                parameters.containsKey ("trecEvalOutputPath"))) {
            throw new IllegalArgumentException
            ("Required parameters were missing from the parameter file.");
        }

        return parameters;
    }

    /**
     *  Read the specified feedback ranking file.
     *  @param feedbackFileName the file name
     *  @return the score list
     */
    private static ScoreList readFeedbackRankFile(String qid, String feedbackFileName)
            throws Exception {
        
        ScoreList sl = new ScoreList();

        File feedbackFile = new File (feedbackFileName);

        if (!feedbackFile.canRead()) {
            throw new IllegalArgumentException
            ("Can't read " + feedbackFileName);
        }

        Scanner scan = new Scanner(feedbackFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split (" ");
            // get the docid and score
            if (qid.equals(pair[0])) {
                int internalId = Idx.getInternalDocid(pair[2]);
                double score = Double.parseDouble(pair[4]);
                sl.add(internalId, score);
            }
        } while (scan.hasNext());

        scan.close();

        return sl;
    }

}
