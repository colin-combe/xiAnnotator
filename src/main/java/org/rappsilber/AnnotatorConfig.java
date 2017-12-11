/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.rappsilber;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import rappsilber.db.ConnectionPool;

/**
 *
 * @author lfischer
 */
public class AnnotatorConfig extends rappsilber.config.DBRunConfig {

    public AnnotatorConfig(ConnectionPool cp) throws SQLException {
        super(cp);
        try {
            this.evaluateConfigLine("custom:FRAGMENTTREE:default");
        } catch (ParseException ex) {
            Logger.getLogger(AnnotatorConfig.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public AnnotatorConfig(Connection c) throws SQLException {
        super(c);
        try {
            this.evaluateConfigLine("custom:FRAGMENTTREE:default");
        } catch (ParseException ex) {
            Logger.getLogger(AnnotatorConfig.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void addCustomConfig(String conf) throws ParseException {
        this.evaluateConfigLine(conf);
    }
    
}
