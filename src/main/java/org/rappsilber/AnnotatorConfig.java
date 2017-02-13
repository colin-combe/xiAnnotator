/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rappsilber;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import rappsilber.db.ConnectionPool;

/**
 *
 * @author lfischer
 */
public class AnnotatorConfig extends rappsilber.config.DBRunConfig {

    public AnnotatorConfig(ConnectionPool cp) throws SQLException {
        super(cp);
    }

    public AnnotatorConfig(Connection c) throws SQLException {
        super(c);
    }
    
    public void addCustomConfig(String conf) throws ParseException {
        this.evaluateConfigLine(conf);
    }
    
}
