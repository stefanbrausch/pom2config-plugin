/*
 * DataSet.java Jul 4, 2013
 * 
 * Copyright (c) 2013 1&1 Internet AG. All rights reserved.
 * 
 * $Id$
 */
package org.jenkinsci.plugins.pom2config;

public class DataSet {
    private final String name;
    private final String oldEntry;
    private final String newEntry;
   
    DataSet(String name, String oldEntry, String newEntry) {
        this.name = name;
        this.oldEntry = oldEntry;
        this.newEntry = newEntry;
    }
    
    public String getName() {
        return name;
    }
    
    public String getOldEntry() {
        return oldEntry;
    }
    
    public String getNewEntry() {
        return newEntry;
    }
}