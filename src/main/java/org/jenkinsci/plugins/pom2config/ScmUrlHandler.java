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
import java.util.List;
import java.util.logging.Logger;

import hudson.model.AbstractProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class ScmUrlHandler {

    private static final Logger LOG = Logger.getLogger(ScmUrlHandler.class.getName());

    /** The project. */
    private final transient AbstractProject<?, ?> project;
    
    ScmUrlHandler (AbstractProject<?, ?> project) {
        this.project = project;
    }

    /**
     * Finds the Git or SVN locations for a project.
     * @param item a job
     * @return Array of found SCM paths
     */
    protected String getSCMPaths() {
        final StringBuilder scmPaths = new StringBuilder();
        final SCM scm = project.getScm();
        
        if (scm instanceof SubversionSCM) {
            final SubversionSCM svn = (SubversionSCM) scm;
            for (ModuleLocation location : svn.getLocations()) {
                scmPaths.append(location.remote);
                LOG.fine(location.remote + " added");
            }
        } else if (scm instanceof GitSCM) {
            final GitSCM git = (GitSCM) scm;
            
            for (RemoteConfig repo : git.getRepositories()) {
                for (URIish urIish : repo.getURIs()) {
                    scmPaths.append(urIish.toString());
                    LOG.fine(urIish.toString() + " added");
                }
            }
        }
        return scmPaths.toString();
    }

    
    protected void replaceScmUrl(String newScmUrl) throws IOException{
        final String[] scmParts = newScmUrl.split(":");
        
        if (!"scm".equals(scmParts[0].trim())){
            LOG.finest("No SCM address");
        } else if ("git".equals(scmParts[1])){
            replaceGitUrl(scmParts[2] + ":" + scmParts[3].trim());
        } else if ("svn".equals(scmParts[1])){
            replaceSvnUrl(scmParts[2] + ":" + scmParts[3].trim());
        }
    }

    private void replaceGitUrl(String newScmUrl) throws IOException{
        final GitSCM newSCM;
        final SCM scm = project.getScm();
        if (scm instanceof GitSCM) {
            final GitSCM gitSCM = (GitSCM) scm;
            final List<UserRemoteConfig> remoteConfigList = new ArrayList<UserRemoteConfig>();
            remoteConfigList.add(new UserRemoteConfig(newScmUrl, null, null));

            newSCM = new GitSCM(gitSCM.getScmName(), 
                                remoteConfigList, 
                                gitSCM.getBranches(), 
                                gitSCM.getUserMergeOptions(),
                                gitSCM.getDoGenerate(), 
                                gitSCM.getSubmoduleCfg(), 
                                gitSCM.getClean(), 
                                gitSCM.getWipeOutWorkspace(),
                                gitSCM.getBuildChooser(), 
                                gitSCM.getBrowser(), 
                                gitSCM.getGitTool(), 
                                gitSCM.getAuthorOrCommitter(), 
                                gitSCM.getRelativeTargetDir(),
                                gitSCM.getReference(),
                                gitSCM.getExcludedRegions(), 
                                gitSCM.getExcludedUsers(), 
                                gitSCM.getLocalBranch(), 
                                gitSCM.getDisableSubmodules(),
                                gitSCM.getRecursiveSubmodules(), 
                                gitSCM.getPruneBranches(), 
                                gitSCM.getRemotePoll(),
                                gitSCM.getGitConfigName(), 
                                gitSCM.getGitConfigEmail(), 
                                gitSCM.getSkipTag(),
                                gitSCM.getIncludedRegions(),
                                gitSCM.isIgnoreNotifyCommit(),
                                gitSCM.getUseShallowClone());
        } else {
            newSCM = new GitSCM(newScmUrl);
        }
        project.setScm(newSCM);
        project.save();
    }

    private void replaceSvnUrl(String newScmUrl) throws IOException{
        final SubversionSCM newSCM;
        final SCM scm = project.getScm();
        if (scm instanceof SubversionSCM) {
            final SubversionSCM svnSCM = (SubversionSCM) scm;
            final List<ModuleLocation> locationList = new ArrayList<ModuleLocation>();
            
            final ModuleLocation location = new ModuleLocation(newScmUrl, null);
            locationList.add(location);
            
            newSCM = new SubversionSCM(locationList,
                    svnSCM.getWorkspaceUpdater(),
                    svnSCM.getBrowser(),
                    svnSCM.getExcludedRegions(),
                    svnSCM.getExcludedUsers(),
                    svnSCM.getExcludedRevprop(),
                    svnSCM.getExcludedCommitMessages(),
                    svnSCM.getIncludedRegions(),
                    svnSCM.isIgnoreDirPropChanges(),
                    svnSCM.isFilterChangelog());
        } else {
            newSCM = new SubversionSCM(newScmUrl);
        }
        project.setScm(newSCM);
        project.save();
    }
}
