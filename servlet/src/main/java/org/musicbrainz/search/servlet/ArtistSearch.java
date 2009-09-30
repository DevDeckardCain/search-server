package org.musicbrainz.search.servlet;

import org.apache.lucene.search.IndexSearcher;
import org.musicbrainz.search.index.ArtistIndexField;
import org.musicbrainz.search.index.ReleaseAnalyzer;
import org.musicbrainz.search.index.ArtistAnalyzer;

import java.util.ArrayList;


public class ArtistSearch extends SearchServer {

    private ArtistSearch() throws Exception {
        xmlWriter = new ArtistXmlWriter();
        htmlWriter = new ArtistHtmlWriter();
        queryMangler = new ArtistMangler();
        defaultFields = new ArrayList<String>();
        defaultFields.add(ArtistIndexField.ARTIST.getName());
        defaultFields.add(ArtistIndexField.ALIAS.getName());
        defaultFields.add(ArtistIndexField.SORTNAME.getName());
        analyzer = new ArtistAnalyzer();
    }


    public ArtistSearch(String indexDir) throws Exception {

        this();
        indexSearcher = createIndexSearcherFromFileIndex(indexDir,"artist_index");
        this.setLastServerUpdatedDate();
    }


    public ArtistSearch(IndexSearcher searcher) throws Exception {

        this();
        indexSearcher = searcher;
    }

}
