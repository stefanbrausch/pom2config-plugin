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

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class GitHandler {

    private static final Logger LOG = Logger.getLogger(GitHandler.class.getName());

    /** The project. */
    private final transient AbstractProject<?, ?> project;
    
    GitHandler (AbstractProject<?, ?> project) {
        this.project = project;
    }

    /**
     * Finds the Git or SVN locations for a project.
     * @return List of found SCM paths
     */
    protected List<String> getGitPaths() {
        final List<String> gitPaths = new ArrayList<String>();
        final GitSCM git = (GitSCM) project.getScm();

        for (RemoteConfig repo : git.getRepositories()) {
            for (URIish uriIsh : repo.getURIs()) {
                gitPaths.add(uriIsh.toString());
            }
        }
        return gitPaths;
    }
    
    protected void replaceGitUrl(String oldScmUrl, String newScmUrl) throws IOException{
        final GitSCM newSCM;
        final SCM scm = project.getScm();
        if (scm instanceof GitSCM) {
            final GitSCM gitSCM = (GitSCM) scm;
            final List<UserRemoteConfig> oldRemoteConfigs = new ArrayList<UserRemoteConfig>(gitSCM.getUserRemoteConfigs());
            for (UserRemoteConfig config : oldRemoteConfigs) {
                if (config.getUrl().trim().equals(oldScmUrl)) {
                    oldRemoteConfigs.remove(config);
                    break;
                }
            }
            
            final List<UserRemoteConfig> newRemoteConfigList = new ArrayList<UserRemoteConfig>();
            newRemoteConfigList.add(new UserRemoteConfig(newScmUrl, null, null));
            newRemoteConfigList.addAll(oldRemoteConfigs);

            newSCM = new GitSCM(gitSCM.getScmName(), newRemoteConfigList, gitSCM.getBranches(), gitSCM.getUserMergeOptions(),
                    gitSCM.getDoGenerate(), gitSCM.getSubmoduleCfg(), gitSCM.getClean(), gitSCM.getWipeOutWorkspace(),
                    gitSCM.getBuildChooser(), gitSCM.getBrowser(), gitSCM.getGitTool(), gitSCM.getAuthorOrCommitter(),
                    gitSCM.getRelativeTargetDir(), gitSCM.getReference(), gitSCM.getExcludedRegions(), gitSCM.getExcludedUsers(),
                    gitSCM.getLocalBranch(), gitSCM.getDisableSubmodules(), gitSCM.getRecursiveSubmodules(),
                    gitSCM.getPruneBranches(), gitSCM.getRemotePoll(), gitSCM.getGitConfigName(), gitSCM.getGitConfigEmail(),
                    gitSCM.getSkipTag(), gitSCM.getIncludedRegions(), gitSCM.isIgnoreNotifyCommit(), gitSCM.getUseShallowClone());
        } else {
            newSCM = new GitSCM(newScmUrl);
        }
        project.setScm(newSCM);
        project.save();
    }
}
