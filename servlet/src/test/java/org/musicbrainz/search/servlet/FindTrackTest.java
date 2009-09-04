package org.musicbrainz.search.servlet;
import junit.framework.TestCase;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumberTools;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;
import org.apache.velocity.app.Velocity;
import org.musicbrainz.search.analysis.StandardUnaccentAnalyzer;
import org.musicbrainz.search.index.*;
import org.musicbrainz.search.servlet.MbDocument;
import org.musicbrainz.search.servlet.Result;
import org.musicbrainz.search.servlet.Results;
import org.musicbrainz.search.servlet.ResultsWriter;
import org.musicbrainz.search.servlet.SearchServer;
import org.musicbrainz.search.servlet.SearchServerServlet;
import org.musicbrainz.search.servlet.TrackSearch;
import org.musicbrainz.search.servlet.TrackXmlWriter;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Assumes an index has been built stored and in the data folder, I've picked a fairly obscure bside so hopefully
 * will not get added to another release
 */
public class FindTrackTest extends TestCase {


    private SearchServer ss;


    public FindTrackTest(String testName) {
        super(testName);
    }


    @Override
    protected void setUp() throws Exception {
        SearchServerServlet.setUpVelocity();
        Velocity.init();
        RAMDirectory ramDir = new RAMDirectory();
        IndexWriter writer = new IndexWriter(ramDir, new StandardUnaccentAnalyzer(), true, IndexWriter.MaxFieldLength.LIMITED);

        Document doc = new Document();
        Index.addFieldToDocument(doc, TrackIndexField.TRACK_ID, "7ca7782b-a602-448b-b108-bb881a7be2d6");
        Index.addFieldToDocument(doc, TrackIndexField.TRACK, "Gravitational Lenz");
        Index.addFieldToDocument(doc, TrackIndexField.RELEASE_ID, "1d9e8ed6-3893-4d3b-aa7d-6cd79609e386");
        Index.addFieldToDocument(doc, TrackIndexField.RELEASE, "Our Glorious 5 Year Plan");
        Index.addFieldToDocument(doc, TrackIndexField.ARTIST_ID, "4302e264-1cf0-4d1f-aca7-2a6f89e34b36");
        Index.addFieldToDocument(doc, TrackIndexField.ARTIST, "Farming Incident");
        Index.addFieldToDocument(doc, TrackIndexField.ARTIST_COMMENT, "Leeds band");

        Index.addFieldToDocument(doc, TrackIndexField.DURATION, NumberTools.longToString(234000));
        Index.addFieldToDocument(doc, TrackIndexField.QUANTIZED_DURATION, NumberTools.longToString(234000 / 2000));
        Index.addFieldToDocument(doc, TrackIndexField.NUM_TRACKS, String.valueOf(10));
        Index.addFieldToDocument(doc, TrackIndexField.TRACKNUM, NumberTools.longToString(5));
        Index.addFieldToDocument(doc, TrackIndexField.RELEASE_TYPE, ReleaseType.ALBUM.getName());
        writer.addDocument(doc);
        writer.close();
        ss = new TrackSearch(new IndexSearcher(ramDir,true));
    }

    public void testFindTrack() throws Exception {
        Results res = ss.searchLucene("track:\"Gravitational Lenz\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackById() throws Exception {
        Results res = ss.searchLucene("trid:\"7ca7782b-a602-448b-b108-bb881a7be2d6\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByReleaseId() throws Exception {
        Results res = ss.searchLucene("reid:\"1d9e8ed6-3893-4d3b-aa7d-6cd79609e386\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByArtistId() throws Exception {
        Results res = ss.searchLucene("arid:\"4302e264-1cf0-4d1f-aca7-2a6f89e34b36\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByReleaseType() throws Exception {
        Results res = ss.searchLucene("type:\"album\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByNumberOfTracksOnRelease() throws Exception {
        Results res = ss.searchLucene("tracks:10", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByDuration() throws Exception {
        Results res = ss.searchLucene("dur:000000000050k0", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByQdur() throws Exception {
        Results res = ss.searchLucene("qdur:00000000000039", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    public void testFindTrackByTrackNumber() throws Exception {
        Results res = ss.searchLucene("tnum:00000000000005", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }


    public void testFindTrackByDefault() throws Exception {
        Results res = ss.searchLucene("\"Gravitational Lenz\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("7ca7782b-a602-448b-b108-bb881a7be2d6", doc.get(TrackIndexField.TRACK_ID));
        assertEquals("Gravitational Lenz", doc.get(TrackIndexField.TRACK));
        assertEquals("4302e264-1cf0-4d1f-aca7-2a6f89e34b36", doc.get(TrackIndexField.ARTIST_ID));
        assertEquals("Farming Incident", doc.get(TrackIndexField.ARTIST));
        assertEquals("1d9e8ed6-3893-4d3b-aa7d-6cd79609e386", doc.get(TrackIndexField.RELEASE_ID));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("00000000000005", doc.get(TrackIndexField.TRACKNUM));
        assertEquals(5, NumberTools.stringToLong(doc.get(TrackIndexField.TRACKNUM)));
        assertEquals("Our Glorious 5 Year Plan", doc.get(TrackIndexField.RELEASE));
        assertEquals("000000000050k0", doc.get(TrackIndexField.DURATION));
        assertEquals(234000, NumberTools.stringToLong(doc.get(TrackIndexField.DURATION)));
    }

    /**
     * Results should match http://musicbrainz.org/ws/1/track/?type=xml&query=%22Gravitational%20Lenz%22
     *
     * @throws Exception
     */
    public void testOutputAsXml() throws Exception {

        Results res = ss.searchLucene("track:\"Gravitational Lenz\"", 0, 10);
        ResultsWriter writer = new TrackXmlWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res);
        pr.close();
        String output = sw.toString();
        //System.out.println("Xml is" + output);
        assertTrue(output.contains("count=\"1\""));
        assertTrue(output.contains("offset=\"0\""));
        assertTrue(output.contains("<track id=\"7ca7782b-a602-448b-b108-bb881a7be2d6\""));
        assertTrue(output.contains("<title>Gravitational Lenz</title>"));
        assertTrue(output.contains("<duration>234000</duration>"));
        assertTrue(output.contains("<artist id=\"4302e264-1cf0-4d1f-aca7-2a6f89e34b36\""));
        assertTrue(output.contains("<name>Farming Incident</name>"));
        assertTrue(output.contains("release type=\"Album\" id=\"1d9e8ed6-3893-4d3b-aa7d-6cd79609e386\""));
        assertTrue(output.contains("<title>Our Glorious 5 Year Plan</title>"));
        assertTrue(output.contains("offset=\"4\""));
        assertTrue(output.contains("count=\"10\""));


    }
}