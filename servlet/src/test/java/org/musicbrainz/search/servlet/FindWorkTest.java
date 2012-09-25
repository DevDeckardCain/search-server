package org.musicbrainz.search.servlet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.NumericUtils;
import org.junit.Before;
import org.junit.Test;
import org.musicbrainz.mmd2.Artist;
import org.musicbrainz.mmd2.AttributeList;
import org.musicbrainz.mmd2.DefDirection;
import org.musicbrainz.mmd2.ObjectFactory;
import org.musicbrainz.mmd2.Relation;
import org.musicbrainz.mmd2.RelationList;
import org.musicbrainz.search.LuceneVersion;
import org.musicbrainz.search.MbDocument;
import org.musicbrainz.search.index.DatabaseIndex;
import org.musicbrainz.search.index.MMDSerializer;
import org.musicbrainz.search.index.MetaIndexField;
import org.musicbrainz.search.index.WorkIndexField;

/**
 * Assumes an index has been built stored and in the data folder, I've picked a fairly obscure bside so hopefully
 * will not get added to another release
 */
public class FindWorkTest {

  private SearchServer ss;
  private SearchServer sd;

  @Before
  public void setUp() throws Exception {
    RAMDirectory ramDir = new RAMDirectory();
    ObjectFactory of = new ObjectFactory();

    Analyzer analyzer = DatabaseIndex.getAnalyzer(WorkIndexField.class);
    IndexWriterConfig  writerConfig = new IndexWriterConfig(LuceneVersion.LUCENE_VERSION,analyzer);
    IndexWriter writer = new IndexWriter(ramDir, writerConfig);
    {
      MbDocument doc = new MbDocument();
      doc.addField(WorkIndexField.WORK_ID, "4ff89cf0-86af-11de-90ed-001fc6f176ff");
      doc.addField(WorkIndexField.WORK, "Symphony No. 5");
      doc.addField(WorkIndexField.ISWC, "T-101779304-1");
      doc.addField(WorkIndexField.ISWC, "B-101779304-1");
      doc.addField(WorkIndexField.ARTIST_ID, "1f9df192-a621-4f54-8850-2c5373b7eac9");
      doc.addField(WorkIndexField.ARTIST, "Пётр Ильич Чайковский");
      doc.addField(WorkIndexField.COMMENT, "demo");
      doc.addField(WorkIndexField.LYRICS_LANG, "eng");
      doc.addField(WorkIndexField.TYPE, "Opera");
      doc.addField(WorkIndexField.ALIAS, "Symp5");
      doc.addField(WorkIndexField.TAG, "classical");
      doc.addField(WorkIndexField.TAGCOUNT, "10");


      RelationList rl = of.createRelationList();
      rl.setTargetType("artist");
      {
        Relation relation = of.createRelation();
        AttributeList al  = of.createAttributeList();
        Artist artist = of.createArtist();
        artist.setId("1f9df192-a621-4f54-8850-2c5373b7eac9");
        artist.setName("Пётр Ильич Чайковский");
        artist.setSortName("Пётр Ильич Чайковский");
        relation.setArtist(artist);
        relation.setType("composer");
        relation.setDirection(DefDirection.BACKWARD);
        al.getAttribute().add("additional");
        relation.setAttributeList(al);
        rl.getRelation().add(relation);
      }
      {
        Relation relation = of.createRelation();
        AttributeList al  = of.createAttributeList();
        Artist artist = of.createArtist();
        artist.setId("abcdefgh-a621-4f54-8850-2c5373b7eac9");
        artist.setName("frank");
        artist.setSortName("turner");
        relation.setArtist(artist);
        relation.setType("writer");
        relation.setDirection(DefDirection.BACKWARD);
        rl.getRelation().add(relation);
      }
      doc.addField(WorkIndexField.ARTIST_RELATION, MMDSerializer.serialize(rl));
      writer.addDocument(doc.getLuceneDocument());
    }

    {
      MbDocument doc = new MbDocument();
      doc.addField(MetaIndexField.META, MetaIndexField.META_VALUE);
      doc.addField(MetaIndexField.LAST_UPDATED, NumericUtils.longToPrefixCoded(new Date().getTime()));
      writer.addDocument(doc.getLuceneDocument());
    }

    writer.close();
    SearcherManager searcherManager = new SearcherManager(ramDir, new MusicBrainzSearcherFactory(ResourceType.WORK));
    ss = new WorkSearch(searcherManager);
    sd = new WorkDismaxSearch(searcherManager);
  }

  @Test
  public void testFindWorkById() throws Exception {
    Results res = ss.searchLucene("wid:\"4ff89cf0-86af-11de-90ed-001fc6f176ff\"", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByName() throws Exception {
    Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByLyricsLang() throws Exception {
    Results res = ss.searchLucene("lang:eng", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByDismax1() throws Exception {
    Results res = sd.searchLucene("Symphony No. 5", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByDismax2() throws Exception {
    Results res = sd.searchLucene("Symphony", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByComment() throws Exception {
    Results res = ss.searchLucene("comment:demo", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("demo", doc.get(WorkIndexField.COMMENT));
  }

  @Test
  public void testFindWorkByArtist() throws Exception {
    Results res = ss.searchLucene("artist:\"Пётр Ильич Чайковский\"", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }


  @Test
  public void testFindWorkByISWC() throws Exception {
    Results res = ss.searchLucene("iswc:\"T-101779304-1\"", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByType() throws Exception {
    Results res = ss.searchLucene("type:\"opera\"", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByAlias() throws Exception {
    Results res = ss.searchLucene("alias:symp5", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByTag() throws Exception {
    Results res = ss.searchLucene("tag:classical", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
  public void testFindWorkByDefaultUsingName() throws Exception {
    Results res = ss.searchLucene("\"Symphony No. 5\"", 0, 10);
    assertEquals(1, res.totalHits);
    Result result = res.results.get(0);
    MbDocument doc = result.doc;
    assertEquals("4ff89cf0-86af-11de-90ed-001fc6f176ff", doc.get(WorkIndexField.WORK_ID));
    assertEquals("Symphony No. 5", doc.get(WorkIndexField.WORK));
  }

  @Test
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
  @Test
  public void testOutputAsXml() throws Exception {

    Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 1);
    ResultsWriter writer = ss.getMmd2Writer();
    StringWriter sw = new StringWriter();
    PrintWriter pr = new PrintWriter(sw);
    writer.write(pr, res,SearchServerServlet.RESPONSE_XML, true);
    pr.close();
    String output = sw.toString();
    System.out.println("Xml is" + output);
    assertTrue(output.contains("count=\"1\""));
    assertTrue(output.contains("offset=\"0\""));
    assertTrue(output.contains("id=\"4ff89cf0-86af-11de-90ed-001fc6f176ff\""));
    assertTrue(output.contains("<title>Symphony No. 5</title>"));
    assertTrue(output.contains("<name>Пётр Ильич Чайковский</name>"));
    assertTrue(output.contains("<disambiguation>demo</disambiguation>"));
    assertTrue(output.contains("<sort-name>Пётр Ильич Чайковский</sort-name>"));
    assertTrue(output.contains("<relation type=\"composer\""));
    assertTrue(output.contains("<iswc>T-101779304-1</iswc>"));
    assertTrue(output.contains("<iswc>B-101779304-1</iswc>"));
    assertTrue(output.contains("<language>eng</language>"));
    assertTrue(output.contains("<relation-list target-type=\"artist\">"));
    assertTrue(output.contains("<direction>backward</direction>"));
    assertTrue(output.contains("<attribute-list>"));
    assertTrue(output.contains("<attribute>additional</attribute>"));
    assertTrue(output.contains("type=\"Opera\""));
    assertTrue(output.contains("<alias>Symp5</alias>"));
    assertTrue(output.contains("<tag-list>"));
    assertTrue(output.contains("<tag count=\"10\">"));
    assertTrue(output.contains("<name>classical</name>"));

  }

  /**
   * Tests
   *
   * @throws Exception
   */
  @Test
  public void testOutputAsJson() throws Exception {

    Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 1);
    ResultsWriter writer = ss.getMmd2Writer();
    StringWriter sw = new StringWriter();
    PrintWriter pr = new PrintWriter(sw);
    writer.write(pr, res, SearchServerServlet.RESPONSE_JSON);
    pr.close();

    String output = sw.toString();
    System.out.println("Json is" + output);

    assertTrue(output.contains("\"count\":1"));
    assertTrue(output.contains("\"offset\":0"));
    assertTrue(output.contains("\"work\":[{\"id\":\"4ff89cf0-86af-11de-90ed-001fc6f176ff\""));
    assertTrue(output.contains("\"type\":\"Opera\""));
    assertTrue(output.contains("\"score\":\"100\""));
    assertTrue(output.contains("\"title\":\"Symphony No. 5\""));
    assertTrue(output.contains("\"language\":\"eng\""));
    assertTrue(output.contains("\"iswc-list\":{\"iswc\":[\"T-101779304-1\",\"B-101779304-1\"]}"));
    assertTrue(output.contains("\"disambiguation\":\"demo\""));
    assertTrue(output.contains("\"alias-list\":{\"alias\":[\"Symp5\"]}"));
    assertTrue(output.contains("\"relation-list\":[{\"target-type\":\"artist\""));
    assertTrue(output.contains("\"relation\":[{\"type\":\"composer\",\"direction\":\"backward\",\"attribute-list\":{\"attribute\":[\"additional\"]}"));
    assertTrue(output.contains("\"artist\":{\"id\":\"1f9df192-a621-4f54-8850-2c5373b7eac9\""));
    assertTrue(output.contains("\"name\":\"Пётр Ильич Чайковский\""));
    assertTrue(output.contains("\"sort-name\":\"Пётр Ильич Чайковский\""));
    assertTrue(output.contains("\"tag-list\":{\"tag\""));
    assertTrue(output.contains("\"name\":\"classical\""));
    assertTrue(output.contains("\"count\":10"));
    assertTrue(output.contains(""));
  }

  /**
   * Tests
   *
   * @throws Exception
   */
  @Test
  public void testOutputAsJsonNew() throws Exception {

    Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 1);
    ResultsWriter writer = ss.getMmd2Writer();
    StringWriter sw = new StringWriter();
    PrintWriter pr = new PrintWriter(sw);
    writer.write(pr, res, SearchServerServlet.RESPONSE_JSON_NEW);
    pr.close();

    String output = sw.toString();
    System.out.println("Json New is" + output);

    assertTrue(output.contains("\"count\":1"));
    assertTrue(output.contains("\"offset\":0"));
    assertTrue(output.contains("\"work\":[{\"id\":\"4ff89cf0-86af-11de-90ed-001fc6f176ff\""));
    assertTrue(output.contains("\"type\":\"Opera\""));
    assertTrue(output.contains("\"score\":\"100\""));
    assertTrue(output.contains("\"title\":\"Symphony No. 5\""));
    assertTrue(output.contains("\"language\":\"eng\""));
    assertTrue(output.contains("iswcs\":[\"T-101779304-1\",\"B-101779304-1\"]"));
    assertTrue(output.contains("\"disambiguation\":\"demo\""));
    assertTrue(output.contains("\"aliases\":[\"Symp5\"]"));
    assertTrue(output.contains("\"relations\":[{"));
    assertTrue(output.contains("\"artist\":{\"id\":\"1f9df192-a621-4f54-8850-2c5373b7eac9\""));
    assertTrue(output.contains("\"name\":\"Пётр Ильич Чайковский\""));
    assertTrue(output.contains("\"sort-name\":\"Пётр Ильич Чайковский\""));
    assertTrue(output.contains("\"tags\":[{\"count\":10,\"name\":\"classical\"}"));
    assertTrue(output.contains("\"count\":10"));
    assertTrue(output.contains(""));
  }

  /**
   * Tests
   *
   * @throws Exception
   */
  @Test
  public void testOutputAsJsonNewPretty() throws Exception {

    Results res = ss.searchLucene("work:\"Symphony No. 5\"", 0, 1);
    ResultsWriter writer = ss.getMmd2Writer();
    StringWriter sw = new StringWriter();
    PrintWriter pr = new PrintWriter(sw);
    writer.write(pr, res, SearchServerServlet.RESPONSE_JSON_NEW, true);
    pr.close();

    String output = sw.toString();
    System.out.println("Json New Pretty is" + output);

    assertTrue(output.contains("\"count\" : 1"));
    assertTrue(output.contains(""));
  }
}