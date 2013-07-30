/*
 * Pom2ConfigEmailExt.java Jul 29, 2013
 * 
 * Copyright (c) 2013 1&1 Internet AG. All rights reserved.
 * 
 * $Id$
 */
package org.jenkinsci.plugins.pom2config;

import hudson.model.AbstractProject;
import hudson.plugins.emailext.ExtendedEmailPublisher;

import java.io.IOException;
import java.util.logging.Logger;

public class EmailExtHandler {
    
    private static final Logger LOG = Logger.getLogger(EmailExtHandler.class.getName());

    /** The project. */
    private final transient AbstractProject<?, ?> project;
    
    
    EmailExtHandler (AbstractProject<?, ?> project) {
        this.project = project;
    }
    
    protected String getProjectRecipients() throws IOException {
        String recipients = "";
            try {
                ExtendedEmailPublisher publisher = project.getPublishersList().get(
                        ExtendedEmailPublisher.class);
                recipients = publisher.recipientList;
            } catch (NullPointerException e) {
                recipients = "No email recipients set.";
            }
        return recipients;
    }

    protected void replaceEmailAddresses(String newAddresses) throws IOException{
        String addresses = newAddresses.trim().replace(" ", ",");
        ExtendedEmailPublisher publisher = project.getPublishersList().get(ExtendedEmailPublisher.class);
        publisher.recipientList = addresses;
        project.save();
    }
}
