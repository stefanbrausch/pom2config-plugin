/*
 * DataSet.java Jul 4, 2013
 * 
 * Copyright (c) 2013 1&1 Internet AG. All rights reserved.
 * 
 * $Id$
 */
package org.jenkinsci.plugins.pom2config;

import java.util.ArrayList;
import java.util.List;

public class DataSet {
    private final String name;
    private final List<String> oldEntries = new ArrayList<String>();
    private final List<String> newEntries = new ArrayList<String>();
   
    DataSet(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    protected void addOldEntry(String s) {
        oldEntries.add(s);
    }
    
    protected void addNewEntry(String s) {
        newEntries.add(s);
    }

    public List<String> getOldEntries() {
        return oldEntries;
    }
    
    public List<String> getNewEntries() {
        return newEntries;
    }
}