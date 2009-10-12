/*
 * MusicBrainz Search Server
 * Copyright (C) 2009  Lukas Lalinsky

 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package org.musicbrainz.search.index;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.document.Document;

import java.sql.*;

public class LabelIndex extends DatabaseIndex {

    private Pattern stripLabelCodeOfLeadingZeroes;

    public LabelIndex(Connection dbConnection) {
        super(dbConnection);
        stripLabelCodeOfLeadingZeroes = Pattern.compile("^0+");
    }

    @Override
    public String getName() {
        return "label";
    }
    
    @Override
    public void init() throws SQLException {
        addPreparedStatement("ALIASES",
                "SELECT label_alias.label as label, n.name as alias " +
                "FROM label_alias " +
                " JOIN label_name n ON (label_alias.name = n.id) " +
                "WHERE label BETWEEN ? AND ?"
        );
        
        addPreparedStatement("LABELS",
                "SELECT label.id, gid, n0.name as name, n1.name as sortname, " +
                "       lower(label_type.name) as type, begindate_year, begindate_month, begindate_day, " +
                "       enddate_year, enddate_month, enddate_day, " +
                "       comment, labelcode, lower(isocode) as country " +
                "FROM label " +
                " JOIN label_name n0 ON label.name = n0.id " +
                " JOIN label_name n1 ON label.sortname = n1.id " +
                " LEFT JOIN label_type ON label.type = label_type.id " +
                " LEFT JOIN country ON label.country = country.id " +
                "WHERE label.id BETWEEN ? AND ?"
        );
    }
    
    @Override
    public int getMaxId() throws SQLException {
        Statement st = getDbConnection().createStatement();
        ResultSet rs = st.executeQuery("SELECT MAX(id) FROM label");
        rs.next();
        return rs.getInt(1);
    }

    @Override
    public void indexData(IndexWriter indexWriter, int min, int max) throws SQLException, IOException {
    	// Get labels aliases
        Map<Integer, List<String>> aliases = new HashMap<Integer, List<String>>();
        PreparedStatement st = getPreparedStatement("ALIASES");
        st.setInt(1, min);
        st.setInt(2, max);
        ResultSet rs = st.executeQuery();
        while (rs.next()) {
            int labelId = rs.getInt("label");

            List<String> list;
            if (!aliases.containsKey(labelId)) {
                list = new LinkedList<String>();
                aliases.put(labelId, list);
            } else {
                list = aliases.get(labelId);
            }
            list.add(rs.getString("alias"));
        }
        
        // Get labels
        st = getPreparedStatement("LABELS");
        st.setInt(1, min);
        st.setInt(2, max);
        rs = st.executeQuery();
        
        while (rs.next()) {
            indexWriter.addDocument(documentFromResultSet(rs, aliases));
        }
    }

    protected Document documentFromResultSet(ResultSet rs, Map<Integer, List<String>> aliases) throws SQLException {

        Document doc = new Document();
        int labelId = rs.getInt("id");
        addFieldToDocument(doc, LabelIndexField.ENTITY_TYPE, this.getName());
        addFieldToDocument(doc, LabelIndexField.ENTITY_GID, rs.getString("gid"));
        addFieldToDocument(doc, LabelIndexField.LABEL, rs.getString("name"));
        addFieldToDocument(doc, LabelIndexField.SORTNAME, rs.getString("sortname"));
        addNonEmptyFieldToDocument(doc, LabelIndexField.TYPE, rs.getString("type"));
        addNonEmptyFieldToDocument(doc, LabelIndexField.COMMENT, rs.getString("comment"));
        addNonEmptyFieldToDocument(doc, LabelIndexField.COUNTRY, rs.getString("country"));
        
        addNonEmptyFieldToDocument(doc, LabelIndexField.BEGIN, 
        	Utils.formatDate(rs.getInt("begindate_year"), rs.getInt("begindate_month"), rs.getInt("begindate_day")));

        addNonEmptyFieldToDocument(doc, LabelIndexField.END, 
            Utils.formatDate(rs.getInt("enddate_year"), rs.getInt("enddate_month"), rs.getInt("enddate_day")));
        
        String labelcode = rs.getString("labelcode");
        if (labelcode != null && !labelcode.isEmpty()) {
            Matcher m = stripLabelCodeOfLeadingZeroes.matcher(labelcode);
            addFieldToDocument(doc, LabelIndexField.CODE, m.replaceFirst(""));
        }

        if (aliases.containsKey(labelId)) {
            for (String alias : aliases.get(labelId)) {
                addFieldToDocument(doc, LabelIndexField.ALIAS, alias);
            }
        }
        return doc;
    }

}
