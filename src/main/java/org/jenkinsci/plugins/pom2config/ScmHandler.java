/*
 * Pom2ConfigScmUrl.java Jul 29, 2013
 * 
 * Copyright (c) 2013 1&1 Internet AG. All rights reserved.
 * 
 * $Id$
 */
package org.jenkinsci.plugins.pom2config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import hudson.model.AbstractProject;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;


public class ScmHandler {

    private static final Logger LOG = Logger.getLogger(ScmHandler.class.getName());
    
    private GitHandler gitHandler = null;

    /** The project. */
    private final transient AbstractProject<?, ?> project;
    
    ScmHandler (AbstractProject<?, ?> project) {
        this.project = project;
        if (isGitAvailable()) {
            gitHandler =  new GitHandler(project);
        }
    }

    private boolean isGitAvailable() {
        try {
            new GitSCM(null);
            return true;
        } catch (Throwable t) {
            return false;
        }    
    }

    /**
     * Finds the Git or SVN locations for a project.
     * @return List of found SCM paths
     */
    protected List<String> getSCMPaths() {
        final List<String> scmPaths = new ArrayList<String>();
        final SCM scm = project.getScm();
        
        if (scm instanceof SubversionSCM) {
            final SubversionSCM svn = (SubversionSCM) scm;
            for (ModuleLocation location : svn.getLocations()) {
                scmPaths.add(location.remote);
            }
        } else if (isGitAvailable() && scm instanceof GitSCM) {
            scmPaths.addAll(gitHandler.getGitPaths());
        }
        return scmPaths;
    }
    
    protected void replaceScmUrl(String oldScmUrl, String newScmUrl) throws IOException{
        final String[] scmParts = newScmUrl.split(":");
        if (!"scm".equals(scmParts[0].trim())){
            LOG.finest("No SCM address");
        } else if ("git".equals(scmParts[1]) && isGitAvailable()){
            gitHandler.replaceGitUrl(oldScmUrl, scmParts[2] + ":" + scmParts[3].trim());
        } else if ("svn".equals(scmParts[1])){
            replaceSvnUrl(oldScmUrl, scmParts[2] + ":" + scmParts[3].trim());
        }
    }

    private void replaceSvnUrl(String oldScmUrl, String newScmUrl) throws IOException{
        final SubversionSCM newSCM;
        final SCM scm = project.getScm();
        if (scm instanceof SubversionSCM) {
            final SubversionSCM svnSCM = (SubversionSCM) scm;
            final List<ModuleLocation> oldLocations = new ArrayList<ModuleLocation>(Arrays.asList(svnSCM.getProjectLocations(project)));

            for (ModuleLocation location : oldLocations) {
                if (location.remote.trim().equals(oldScmUrl)) {
                    oldLocations.remove(location);
                    break;
                }
            }
            
            final List<ModuleLocation> locationList = new ArrayList<ModuleLocation>();
            final ModuleLocation location = new ModuleLocation(newScmUrl, null);
            locationList.add(location);
            locationList.addAll(oldLocations);
            
            newSCM = new SubversionSCM(locationList, svnSCM.getWorkspaceUpdater(), svnSCM.getBrowser(),
                    svnSCM.getExcludedRegions(), svnSCM.getExcludedUsers(), svnSCM.getExcludedRevprop(),
                    svnSCM.getExcludedCommitMessages(), svnSCM.getIncludedRegions(), svnSCM.isIgnoreDirPropChanges(),
                    svnSCM.isFilterChangelog());
        } else {
            newSCM = new SubversionSCM(newScmUrl);
        }
        project.setScm(newSCM);
        project.save();
    }
}
