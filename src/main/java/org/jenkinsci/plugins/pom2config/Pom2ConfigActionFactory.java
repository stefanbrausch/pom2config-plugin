package org.jenkinsci.plugins.pom2config;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Extends project actions for all jobs.
 *
 * @author Kathi Stutz
 */
@Extension
public class Pom2ConfigActionFactory extends TransientProjectActionFactory {
    /** Our logger. */
    private static final Logger LOG = Logger.getLogger(Pom2ConfigActionFactory.class.getName());
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends Action> createFor(@SuppressWarnings("rawtypes") AbstractProject target) {
        final ArrayList<Action> actions = new ArrayList<Action>();
        final Pom2ConfigProjectAction newAction = new Pom2ConfigProjectAction(target);
        actions.add(newAction);
        return actions;
    }
}
