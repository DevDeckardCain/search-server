/* Copyright (c) 2009 Lukas Lalinsky
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

package org.musicbrainz.search.servlet;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.NumericUtils;
import org.musicbrainz.search.MbDocument;
import org.musicbrainz.search.index.MetaIndexField;
import org.musicbrainz.search.servlet.mmd1.Mmd1XmlWriter;
import org.musicbrainz.search.servlet.mmd2.ResultsWriter;

public abstract class SearchServer implements Callable<Results> {

  protected String query;
  protected int offset;
  protected int limit;

  protected Analyzer analyzer;
  protected ResultsWriter resultsWriter;
  protected Mmd1XmlWriter mmd1Writer;
  protected List<String> defaultFields;
  protected SearcherManager searcherManager;
  protected Date serverLastUpdatedDate;
  protected SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm zz", Locale.US);
  protected AtomicInteger searchCount = new AtomicInteger();

  protected SearchServer() {
  }

  /**
   * Set the last updated date by getting the value from the index, then for efficiency convert to a format suitable for
   * use in output html
   * 
   * @throws IOException
   */
  protected void setLastServerUpdatedDate() throws IOException {

    if (searcherManager == null) {
      return;
    }

    // Is not a disaster if missing so just log and carry on
    IndexSearcher searcher = searcherManager.acquire();
    try {
      Term term = new Term(MetaIndexField.META.getName(), MetaIndexField.META_VALUE);
      TermQuery query = new TermQuery(term);
      TopDocs hits = searcher.search(query, 10);

      if (hits.scoreDocs.length == 0) {
        System.out.println("No matches in the index for the meta document.");
        return;
      } else if (hits.scoreDocs.length > 1) {
        System.out.println("More than one meta document was found in the index.");
        return;
      }

      int docId = hits.scoreDocs[0].doc;
      MbDocument doc = new MbDocument(searcher.doc(docId));
      serverLastUpdatedDate = new Date(NumericUtils.prefixCodedToLong(doc.get(MetaIndexField.LAST_UPDATED)));
      dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    } catch (Exception e) {
      System.out.println(e);
    } finally {
      searcherManager.release(searcher);
    }
  }

  public Date getServerLastUpdatedDate() {
    return serverLastUpdatedDate;
  }

  public void reloadIndex() throws CorruptIndexException, IOException {
    if (searcherManager != null) {
      // Try to refresh
      searcherManager.maybeRefresh();
      // Update last update date
      this.setLastServerUpdatedDate();
      resultsWriter.setLastServerUpdatedDate(this.getServerLastUpdatedDate());
    }
  }

  public void close() throws IOException {
  }

  public org.musicbrainz.search.servlet.mmd2.ResultsWriter getMmd2Writer() {
    return resultsWriter;
  }

  public Mmd1XmlWriter getMmd1Writer() {
    return mmd1Writer;
  }

  public List<String> getSearchFields() {
    return defaultFields;
  }

  public org.musicbrainz.search.servlet.ResultsWriter getWriter(String version) {
    if (SearchServerServlet.WS_VERSION_1.equals(version)) {
      return getMmd1Writer();
    } else {
      return getMmd2Writer();
    }
  }

  /**
   * Use this for All Searches run on an Executor
   * 
   * @return
   * @throws IOException
   * @throws ParseException
   */
  @Override
  public Results call() throws IOException, ParseException {
    return search(query, offset, limit);
  }

  /**
   * Process query from Mbserver before sending to lucene searcher, returning between results from offset upto limit
   * 
   * @param query
   * @param offset
   * @param limit
   * @return
   * @throws IOException
   * @throws ParseException
   */
  public Results search(String query, int offset, int limit) throws IOException, ParseException {

    return searchLucene(query, offset, limit);
  }

  /**
   * Parse and search lucene query, returning between results from offset up to limit
   * 
   * @param query
   * @param offset
   * @param limit
   * @return
   * @throws IOException
   * @throws ParseException if the query was invalid
   */
  public Results searchLucene(String query, int offset, int limit) throws IOException, ParseException {

    IndexSearcher searcher = searcherManager.acquire();
    try {
      TopDocs topdocs = searcher.search(parseQuery(query), offset + limit);
      searchCount.incrementAndGet();
      return processResults(searcher, topdocs, offset);
    } finally {
      searcherManager.release(searcher);
    }
  }

  /**
   * Parse the query
   * 
   * @param query
   * @return
   * @throws ParseException
   */
  protected Query parseQuery(String query) throws ParseException {
    QueryParser parser = getParser();
    return parser.parse(query);
  }

  /**
   * @return count of searches done on this index since servlet started
   */
  public String getCount() {
    return searchCount.toString();
  }

  /**
   * Get Query Parser for parsing queries for this resourceType , QueryParser is not thread safe so always get a new
   * instance;
   * 
   * @return
   */
  protected abstract QueryParser getParser();

  /**
   * Process results of search
   * 
   * @param searcher
   * @param topDocs
   * @param offset
   * @return
   * @throws IOException
   */
  private Results processResults(IndexSearcher searcher, TopDocs topDocs, int offset) throws IOException {
    Results results = new Results();
    results.offset = offset;
    results.totalHits = topDocs.totalHits;
    ScoreDoc docs[] = topDocs.scoreDocs;
    float maxScore = topDocs.getMaxScore();
    for (int i = offset; i < docs.length; i++) {
      Result result = new Result();
      result.score = docs[i].score / maxScore;
      result.doc = new MbDocument(searcher.doc(docs[i].doc));
      results.results.add(result);
    }
    return results;
  }

  /**
   * Explain the results This method is for debugging and to allow end users to understand why their query is not
   * returning the results they expected so they can refine their query
   * 
   * @param query
   * @param offset
   * @param limit
   * @return
   * @throws IOException
   * @throws ParseException
   */
  public String explain(String query, int offset, int limit) throws IOException, ParseException {
    StringBuffer sb = new StringBuffer("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">\n");
    sb.append("<html lang=\"en\">\n<head>\n");
    sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n");
    sb.append("</head>\n<body>");
    IndexSearcher searcher = searcherManager.acquire();
    try {
      Query parsedQuery = parseQuery(query);
      TopDocs topdocs = searcher.search(parsedQuery, offset + limit);
      ScoreDoc docs[] = topdocs.scoreDocs;
      float maxScore = topdocs.getMaxScore();
      sb.append("<p>Query:" + parsedQuery.toString() + "</p>\n");
      for (int i = 0; i < docs.length; i++) {
        explainAndDisplayResult(i, sb, searcher, parsedQuery, docs[i], maxScore);
      }
      searchCount.incrementAndGet();
    } finally {
      searcherManager.release(searcher);
    }
    sb.append("</body>\n</html>");
    return sb.toString();
  }

  /**
   * Output the Explain for the document
   * 
   * @param sb
   * @param searcher
   * @param query
   * @param scoreDoc
   * @throws IOException
   * @throws ParseException
   */
  protected void explainAndDisplayResult(int i, StringBuffer sb, IndexSearcher searcher, Query query,
      ScoreDoc scoreDoc, float maxScore) throws IOException, ParseException {
    sb.append("<p>" + i + ":Score:" + (scoreDoc.score / maxScore) * 100 + "</p>\n");
    sb.append(printExplainHeader(searcher.doc(scoreDoc.doc)));
    sb.append(searcher.explain(query, scoreDoc.doc).toHtml());

  }

  /**
   * Print details about the matching document, override to give resource type specific information
   * 
   * @param doc
   * @return
   * @throws IOException
   * @throws ParseException
   */
  protected abstract String printExplainHeader(Document doc) throws IOException, ParseException;

  public SearcherManager getSearcherManager() {
    return searcherManager;
  }

}
