package org.musicbrainz.search.servlet;

import junit.framework.TestCase;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;
import org.musicbrainz.mmd2.Artist;
import org.musicbrainz.mmd2.ArtistCredit;
import org.musicbrainz.mmd2.NameCredit;
import org.musicbrainz.mmd2.ObjectFactory;
import org.musicbrainz.search.MbDocument;
import org.musicbrainz.search.analysis.PerFieldEntityAnalyzer;
import org.musicbrainz.search.index.MMDSerializer;
import org.musicbrainz.search.index.ReleaseGroupIndexField;
import org.musicbrainz.search.index.WorkIndexField;
import org.musicbrainz.search.servlet.mmd2.WorkWriter;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Assumes an index has been built stored and in the data folder, I've picked a fairly obscure bside so hopefully
 * will not get added to another release
 */
public class FindWorkTest extends TestCase {

    private SearchServer ss;


    public FindWorkTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        RAMDirectory ramDir = new RAMDirectory();
        ObjectFactory of = new ObjectFactory();

        PerFieldAnalyzerWrapper analyzer = new PerFieldEntityAnalyzer(WorkIndexField.class);
        IndexWriter writer = new IndexWriter(ramDir, analyzer, true, IndexWriter.MaxFieldLength.LIMITED);
        {
            MbDocument doc = new MbDocument();
            doc.addField(WorkIndexField.WORK_ID, "4ff89cf0-86af-11de-90ed-001fc6f176ff");
            doc.addField(WorkIndexField.WORK, "Symphony No. 5");
            doc.addField(WorkIndexField.ISWC, "T-101779304-1");
            doc.addField(WorkIndexField.ARTIST_ID, "1f9df192-a621-4f54-8850-2c5373b7eac9");
            doc.addField(WorkIndexField.ARTIST, "Пётр Ильич Чайковский");
            doc.addField(WorkIndexField.ARTIST_NAME, "Пётр Ильич Чайковский");
            doc.addField(WorkIndexField.ARTIST_NAME, "Tchaikovsky");             //Eng Alias

            doc.addField(WorkIndexField.ARTIST_NAMECREDIT, "Пётр Ильич Чайковский");
            doc.addField(WorkIndexField.TYPE, "Opera");
            doc.addField(WorkIndexField.ALIAS, "Symp5");
            doc.addField(WorkIndexField.TAG, "classical");
            doc.addField(WorkIndexField.TAGCOUNT, "10");


            ArtistCredit ac = of.createArtistCredit();
            NameCredit nc = of.createNameCredit();
            Artist artist = of.createArtist();
            artist.setId("1f9df192-a621-4f54-8850-2c5373b7eac9");
            artist.setName("Пётр Ильич Чайковский");
            artist.setSortName("Пётр Ильич Чайковский");
            nc.setArtist(artist);
            ac.getNameCredit().add(nc);
            doc.addField(ReleaseGroupIndexField.ARTIST_CREDIT, MMDSerializer.serialize(ac));

            writer.addDocument(doc.getLuceneDocument());
        }
        writer.close();
        ss = new WorkSearch(new IndexSearcher(ramDir, true));
    }

    public void testFindWorkById() throws Exception {
        Results res = ss.searchLucene("wid:\"4ff89cf0-86af-11de-90ed-001fc6f176ff\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }

    public void testFindWorkByName() throws Exception {
        Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }

    public void testFindWorkByArtist() throws Exception {
            Results res = ss.searchLucene("artist:\"Пётр Ильич Чайковский\"", 0, 10);
            assertEquals(1, res.totalHits);
            Result result = res.results.get(0);
            MbDocument doc = result.doc;
            assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
            assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
        }


    public void testFindWorkByArtistName() throws Exception {
        Results res = ss.searchLucene("artistname:\"Пётр Ильич Чайковский\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }

    public void testFindWorkByArtistNameEnglishAlias() throws Exception {
        Results res = ss.searchLucene("artistname:\"Tchaikovsky\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }


    public void testFindWorkByISWC() throws Exception {
        Results res = ss.searchLucene("iswc:\"T-101779304-1\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }

    public void testFindWorkByType() throws Exception {
        Results res = ss.searchLucene("type:\"opera\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }

    public void testFindWorkByAlias() throws Exception {
        Results res = ss.searchLucene("alias:symp5", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }

    public void testFindWorkByTag() throws Exception {
        Results res = ss.searchLucene("tag:classical", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }


    public void testFindWorkByDefaultUsingName() throws Exception {
        Results res = ss.searchLucene("\"Symphony No. 5\"", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }


    public void testFindWorkByDefaultUsingAlias() throws Exception {
        Results res = ss.searchLucene("symp5", 0, 10);
        assertEquals(1, res.totalHits);
        Result result = res.results.get(0);
        MbDocument doc = result.doc;
        assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
        assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
    }


    /**
     * Tests
     *
     * @throws Exception
     */
    public void testOutputAsXml() throws Exception {

        Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 1);
        ResultsWriter writer = new WorkWriter();
        StringWriter sw = new StringWriter();
        PrintWriter pr = new PrintWriter(sw);
        writer.write(pr, res);
        pr.close();
        String output = sw.toString();
        System.out.println("Xml is" + output);
        assertTrue(output.contains("count=\"1\""));
        assertTrue(output.contains("offset=\"0\""));
        assertTrue(output.contains("id=\"4ff89cf0-86af-11de-90ed-001fc6f176ff\""));
        assertTrue(output.contains("<title>Symphony No. 5</title>"));
        assertTrue(output.contains("<name>Пётр Ильич Чайковский</name>"));
        assertTrue(output.contains("<sort-name>Пётр Ильич Чайковский</sort-name>"));
        assertTrue(output.contains("<iswc>T-101779304-1</iswc>"));
        assertTrue(output.contains("type=\"Opera\""));
        assertTrue(output.contains("<alias-list><alias>Symp5</alias></alias-list>"));
        assertTrue(output.contains("<tag-list><tag count=\"10\"><name>classical</name></tag></tag-list>"));


    }
}