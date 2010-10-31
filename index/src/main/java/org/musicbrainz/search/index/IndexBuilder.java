/* Copyright (c) 2009 Lukas Lalinsky
 * Copyright (c) 2009 Aurelien Mino
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the MusicBrainz project nor the names of the
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.musicbrainz.search.index;

import org.apache.commons.lang.time.StopWatch;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.FSDirectory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

public class IndexBuilder
{


    // Lucene parameters
    private static final int MAX_BUFFERED_DOCS = 10000;
    private static final int MERGE_FACTOR = 3000;

	// PostgreSQL schema that holds MB data
	protected static final String DB_SCHEMA = "musicbrainz";


    public static void main(String[] args) throws SQLException, IOException
    {

        IndexBuilderOptions options = new IndexBuilderOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("Couldn't parse command line parameters");
            parser.printUsage(System.out);
            System.exit(1);
        }

        // On request, print command line usage
        if (options.isHelp()) {
            parser.printUsage(System.out);
            System.exit(1);
        }
        
        if (options.isTest()) { System.out.println("Running in test mode."); }

        // At least one index should have been selected 
        ArrayList<String> selectedIndexes = options.selectedIndexes();
        if (selectedIndexes.size() == 0 
              || (selectedIndexes.size() == 1 && selectedIndexes.contains(""))) { 
            System.out.println("No indexes selected. Exiting.");
            System.exit(1);
        }

        Connection mainDbConn = null;
        Connection rawDbConn = null; 
        
        // Check that FreeDB is not the only index requested for build
        if (options.selectedIndexes().size() > 1 || !options.buildIndex("freedb")) {

            // Try loading PostgreSql driver
            try {
                Class.forName("org.postgresql.Driver");
            }
            catch (ClassNotFoundException e) {
                System.err.println("Couldn't load org.postgresql.Driver");
                System.exit(1);
            }

            // Connect to main database
            String url = "jdbc:postgresql://" + options.getMainDatabaseHost() + "/" + options.getMainDatabaseName();
            Properties props = new Properties();
            props.setProperty("user", options.getMainDatabaseUser());
            props.setProperty("password", options.getMainDatabasePassword());
            mainDbConn = DriverManager.getConnection(url, props);
            prepareDbConnection(mainDbConn);

            // Connect to raw database
            url = "jdbc:postgresql://" + options.getRawDatabaseHost() + "/" + options.getRawDatabaseName();
            props = new Properties();
            props.setProperty("user", options.getRawDatabaseUser());
            props.setProperty("password", options.getRawDatabasePassword());
            rawDbConn = DriverManager.getConnection(url, props);
            prepareDbConnection(rawDbConn);
        }
    
        StopWatch clock = new StopWatch();


        // MusicBrainz data indexing
        DatabaseIndex[] indexes = {
                new ArtistIndex(mainDbConn),
                new ReleaseIndex(mainDbConn),
                new ReleaseGroupIndex(mainDbConn),
                new RecordingIndex(mainDbConn),
                new LabelIndex(mainDbConn),
                new WorkIndex(mainDbConn),
                new AnnotationIndex(mainDbConn),
                new TagIndex(mainDbConn),
                new CDStubIndex(rawDbConn), //Note different db
        };

        for (DatabaseIndex index : indexes) {

            // Check if this index should be built
            if (!options.buildIndex(index.getName())) {
                System.out.println("Skipping index: " + index.getName());
                continue;
            }

            clock.start();
            buildDatabaseIndex(index, options);
            clock.stop();
            System.out.println("  Finished in " + Float.toString(clock.getTime()/1000) + " seconds");
            clock.reset();
        }

        // FreeDB data indexing
        if(options.buildIndex("freedb")) {

            File dumpFile = new File(options.getFreeDBDump());
            //If they have set freedbdump file 
            if (options.getFreeDBDump() != null && options.getFreeDBDump().length()!=0)  {
                if( dumpFile.isFile()) {
                    clock.start();
                    buildFreeDBIndex(dumpFile, options);
                    clock.stop();
                    System.out.println("  Finished in " + Float.toString(clock.getTime()/1000) + " seconds");
                } else {
                    System.out.println("  Can't build FreeDB index: invalid file "+options.getFreeDBDump());
                }
            }
        }
    }

    /**
     * Build an index from database
     * 
     * @param options
     * @throws IOException 
     * @throws SQLException 
     */
    private static void buildDatabaseIndex(DatabaseIndex index, IndexBuilderOptions options) throws IOException, SQLException
    {
        IndexWriter indexWriter;
        String path = options.getIndexesDir() + index.getFilename();
        System.out.println("Started Building index: " + path + " at "+new Date());

        /* All addDocuments request are put on a queue to allow another query to be made to database without waiting
         * for all added documents to be analysed, queue is serviced by available processer no of threads.
         * If the max query outperforms the lucene analysis then analysis will switch to main thread because
         * the pool queue size cannot be larger than the max number of documents returned from one query.
         * Will get best results on multicpu systems accessing database on another system.
         */
        indexWriter = new ThreadedIndexWriter(FSDirectory.open(new File(path)),
                index.getAnalyzer(),
                true,
                Runtime.getRuntime().availableProcessors(),
                options.getDatabaseChunkSize(),
                IndexWriter.MaxFieldLength.LIMITED);

        indexWriter.setMaxBufferedDocs(MAX_BUFFERED_DOCS);
        indexWriter.setMergeFactor(MERGE_FACTOR);

        index.init(indexWriter);
        index.writeMetaInformation(indexWriter);
        int maxId = index.getMaxId();
        if (options.isTest() && options.getTestIndexSize() < maxId)
            maxId = options.getTestIndexSize();
        int j = 0;
        while (j < maxId) {
            System.out.print("  Indexing " + j + "..." + (j + options.getDatabaseChunkSize()) + " / " + maxId + " (" + (100*j/maxId) + "%)\r");
            index.indexData(indexWriter, j, j + options.getDatabaseChunkSize() - 1);
            j += options.getDatabaseChunkSize();
        }

        index.destroy();
        System.out.println("\n  Started Optimizing at "+new Date());
        indexWriter.optimize();
        indexWriter.close();

        //For debugging to check sql is not creating too few/many rows
        if(true) {
            int dbRows = index.getNoOfRows(maxId);
            IndexReader reader = IndexReader.open(FSDirectory.open(new File(path)),true);
            System.out.println("  Indexed "+dbRows+" rows, creating "+(reader.maxDoc() - 1)+" lucene documents");
            reader.close();
        }
        System.out.println("\n  Completed Optimizing at "+new Date());

    }
    
    /**
     * Build a FreeDB index from a FreeDB dump
     * 
     * @param dumpFile FreeDB dump file
     * @param options
     * @throws IOException 
     */
    private static void buildFreeDBIndex(File dumpFile, IndexBuilderOptions options) throws IOException
    {
        FreeDBIndex index = new FreeDBIndex();
        index.setDumpFile(dumpFile);

        IndexWriter indexWriter;
        String path = options.getIndexesDir() + index.getFilename();
        System.out.println("Building index: " + path);
        indexWriter = new IndexWriter(FSDirectory.open(new File(path)), index.getAnalyzer() , true, IndexWriter.MaxFieldLength.LIMITED);
        indexWriter.setMaxBufferedDocs(MAX_BUFFERED_DOCS);
        indexWriter.setMergeFactor(MERGE_FACTOR);

        index.writeMetaInformation(indexWriter);
        index.indexData(indexWriter);

        System.out.println("  Optimizing");
        indexWriter.optimize();
        indexWriter.close();
    }
   
    /**
     * Prepare a database connection, and set its default Postgres schema
     * 
     * @param connection
     * @throws SQLException 
     */
    private static void prepareDbConnection(Connection connection) throws SQLException
    {
		Statement st = connection.createStatement();
        //Forces Query Analyser to take advantage of indexes when they exist, this works round the problem with the
        //explain sometimes deciding to do full table scans when building recording index causing query to run unacceptably slow.
        st.executeUpdate("SET enable_seqscan = off");
		st.executeUpdate("SET search_path TO '" + DB_SCHEMA + "'");
    }
    
}

class IndexBuilderOptions {

    private static final int MAX_TEST_ID = 50000;
    private static final int IDS_PER_CHUNK = 20000;

    // Main database connection parameters

    @Option(name="--db-host", aliases = { "-h" }, usage="The database server to connect to. (default: localhost)")
    private String mainDatabaseHost = "localhost";
    public String getMainDatabaseHost() { return mainDatabaseHost; }

    @Option(name="--db-name", aliases = { "-d" }, usage="The name of the database server to connect to. (default: musicbrainz_db)")
    private String mainDatabaseName = "musicbrainz_db";        
    public String getMainDatabaseName() { return mainDatabaseName; }

    @Option(name="--db-user", aliases = { "-u" }, usage="The username to connect with. (default: musicbrainz_user)")
    private String mainDatabaseUser = "musicbrainz_user";
    public String getMainDatabaseUser() { return mainDatabaseUser; }

    @Option(name="--db-password", aliases = { "-p" }, usage="The password for the db user. (default: -blank-)")
    private String mainDatabasePassword = "";
    public String getMainDatabasePassword() { return mainDatabasePassword; }

    // Raw database connection parameters

    @Option(name="--raw-db-host", aliases = { "-o" }, usage="The raw database server to connect to. (default: localhost)")
    private String rawDatabaseHost = "";
    public String getRawDatabaseHost() { return rawDatabaseHost.isEmpty() ? getMainDatabaseHost() : rawDatabaseHost; }

    @Option(name="--raw-db-name", aliases = { "-a" }, usage="The name of the raw database server to connect to. (default: musicbrainz_db_raw)")
    private String rawDatabaseName = "musicbrainz_db_raw";     
    public String getRawDatabaseName() { return rawDatabaseName; }

    @Option(name="--raw-db-user", aliases = { "-s" }, usage="The username for the raw database to connect with. (default: musicbrainz_user)")
    private String rawDatabaseUser = "musicbrainz_user";
    public String getRawDatabaseUser() { return rawDatabaseUser; }

    @Option(name="--raw-db-password", aliases = { "-w" }, usage="The password of the db user of the raw database. (default: -blank-)")
    private String rawDatabasePassword = "";
    public String getRawDatabasePassword() { return rawDatabasePassword; }

    // Indexes directory
    @Option(name="--indexes-dir", usage="The directory . (default: ./data/)")
    private String indexesDir = "." + System.getProperty("file.separator") + "data" + System.getProperty("file.separator");
    public String getIndexesDir() {
        if (indexesDir.endsWith(System.getProperty("file.separator"))) return indexesDir; 
        else return indexesDir + System.getProperty("file.separator");
    }

    // FreeDB dump file
    @Option(name="--freedb-dump", usage="The FreeDB dump file to index.")
    private String freeDBDump = "";
    public String getFreeDBDump() { return freeDBDump; }

    // Selection of indexes to build
    @Option(name="--indexes", usage="A comma-separated list of indexes to build (artist,releasegroup,release,recording,label,work,tag,annotation,cdstub,freedb)")
    private String indexes = "artist,label,release,recording,releasegroup,work,tag,annotation,cdstub,freedb";
    public ArrayList<String> selectedIndexes() { return new ArrayList<String>(Arrays.asList(indexes.split(","))); }
    public boolean buildIndex(String indexName) { return selectedIndexes().contains(indexName); }

    // Test mode
    @Option(name="--test", aliases = { "-t" }, usage="Test the index builder by creating small text indexes.")
    private boolean test = false;
    public boolean isTest() { return test; }

    @Option(name="--help", usage="Print this usage information.")
    private boolean help = false;
    public boolean isHelp() { return help; }

    @Option(name="--testindexsize", aliases = { "-b" }, usage="The number of rows to index when using the test option. (default: -10000)")
    private int testIndexSize = MAX_TEST_ID;
    public int getTestIndexSize() { return testIndexSize; }

    @Option(name="--chunksize", aliases = { "-c" }, usage="Chunk Size, The number of rows to return in each SQL query. (default: -10000)")
    private int databaseChunkSize = IDS_PER_CHUNK;
    public int getDatabaseChunkSize() { return databaseChunkSize; }

}
