package org.jenkinsci.plugins.pom2config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import net.sf.json.JSONObject;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import hudson.Functions;
import hudson.XmlFile;
import hudson.model.AbstractItem;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.tasks.Publisher;
import hudson.util.DescribableList;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.emailext.ExtendedEmailPublisher;
import hudson.plugins.emailext.ExtendedEmailPublisherDescriptor;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;


/**
 * @author Kathi Stutz
 */
public class Pom2ConfigProjectAction implements Action {

    private static final Logger LOG = Logger.getLogger(Pom2ConfigProjectAction.class.getName());

    /** The project. */
    private final transient AbstractProject<?, ?> project;
    
    private final String descLabel = "Project Description";
    private final String emailLabel = "Developer Email Addresses";
    private final String scmLabel = "SCM URLs";
    private String message = "";
    
    private List<DataSet> configDetails = new ArrayList<DataSet>();
    private DataSet descriptions = null;
    private DataSet emailAddresses = null;
    private DataSet scmUrls = null;

    /**
     * @param project
     *            for which configurations should be returned.
     */
    public Pom2ConfigProjectAction(AbstractProject<?, ?> project) {
        super();
        this.project = project;
    }

/*    public DataSet getDescriptions() {
        return descriptions;
    }

    public DataSet getEmailAddresses() {
        return emailAddresses;
    }

    public DataSet getScmUrls() {
        return scmUrls;
    }
*/

    public List<DataSet> getConfigDetails() {
        return configDetails;
    }

    public final void doGetPom(StaplerRequest req, StaplerResponse rsp) 
    throws IOException, ServletException, SAXException, XPathExpressionException {
        
        configDetails.clear();
        String pomAsString = "";
        
        final JSONObject formData = req.getSubmittedForm().getJSONObject("fromWhere");
        LOG.finest(formData.toString());
        
        if ("useExisting".equals(formData.getString("value"))) {

            //checkout pom from repo
/*            String[] scmPath = null;
            final SCM scm = ((AbstractProject<?, ?>) project).getScm();
            if (scm instanceof SubversionSCM) {
                final SubversionSCM svn = (SubversionSCM) scm;
                final ModuleLocation[] locs = svn.getLocations();
                scmPath = new String[locs.length];
                for (int i = 0; i < locs.length; i++) {
                    final ModuleLocation moduleLocation = locs[i];
                    scmPath[i] = moduleLocation.remote;
                    LOG.fine(scmPath[i] + " added");
                }
            } else if (scm instanceof GitSCM) {
                final GitSCM git = (GitSCM) scm;
                final List<RemoteConfig> repoList = git.getRepositories();

                scmPath = new String[repoList.size()];
                for (int i = 0; i < repoList.size(); i++) {
                    final List<URIish> uris = repoList.get(i).getURIs();
                    for (final Iterator iterator = uris.iterator(); iterator.hasNext();) {
                        final URIish urIish = (URIish) iterator.next();
                        scmPath[i] = urIish.toString();
                        LOG.fine(scmPath[i] + " added");
                    }
                }
            }
*/
            
            //subversionSCM.getBrowser().getFileLink(SubversionChangeLogSet.Path path)

            //wie kommt man an svnUrl ran?
            //project.getScm().getType()
            //project.getScm().getDescriptor().getConfigFile()?
            
            //find existing
        } else if ("fromUrl".equals(formData.getString("value"))){
            final URL pomURL = new URL(formData.getString("location"));
            BufferedReader in = new BufferedReader(
                new InputStreamReader(pomURL.openStream()));

            StringBuilder builder = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                builder.append(inputLine);
            }
            in.close();
            
            pomAsString = builder.toString();
            
            
        } else {
            final FileItem fileItem = req.getFileItem(formData.getString("file"));
            pomAsString = fileItem.getString();
        }
        
        //pom-String parsen
        String xml = pomAsString;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        try {
            db = dbf.newDocumentBuilder();
            InputSource is = new InputSource();
            is.setCharacterStream(new StringReader(xml));
            try {
                Document doc = db.parse(is);
                
                descriptions = new DataSet(descLabel, project.getDescription(), retrieveDetailsFromPom(doc, "//description/text()"));
                emailAddresses = new DataSet(emailLabel, getProjectRecipients(), retrieveDetailsFromPom(doc, "//developers/developer/email/text()"));
                scmUrls = new DataSet(scmLabel, getSCMPaths(project), retrieveDetailsFromPom(doc, "//scm/connection/text()"));
                
                configDetails.add(descriptions);
                configDetails.add(emailAddresses);
                configDetails.add(scmUrls);
                
//                configDetails.add(new DataSet(descLabel, project.getDescription(), retrieveDetailsFromPom(doc, "//description/text()")));
//                configDetails.add(new DataSet(emailLabel, getProjectRecipients(), retrieveDetailsFromPom(doc, "//developers/developer/email/text()")));
//                configDetails.add(new DataSet(scmLabel, getSCMPaths(project), retrieveDetailsFromPom(doc, "//scm/connection/text()")));
                
            } catch (SAXException e) {
                LOG.finest(e.toString());
                // handle SAXException
            } catch (IOException e) {
                LOG.finest(e.toString());
                // handle IOException
            }
        } catch (ParserConfigurationException e1) {
            LOG.finest(e1.toString());
            // handle ParserConfigurationException
        }

        //keine pom -> Fehlermeldung
        
        
        rsp.sendRedirect("chooseDetails");
    }
    

    private String retrieveDetailsFromPom(Document doc, String path) {
        final XPath xpath = XPathFactory.newInstance().newXPath();
        final StringBuilder builder = new StringBuilder();
        NodeList nodes;
        try {
            XPathExpression expr = xpath.compile(path);
            nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                builder.append(nodes.item(i).getNodeValue());
                builder.append(" ");
            }
        } catch (XPathExpressionException ex) {}
        return builder.toString();
        
    }

    
    
/*    private List<String> retrieveDetailsFromPom(Document doc, String path) {
        final List<String> list = new ArrayList<String>();
        final XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList nodes;
        try {
            XPathExpression expr = xpath.compile(path);
            nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                LOG.finest(nodes.item(i).getNodeValue());
                list.add(nodes.item(i).getNodeValue());
            }
        } catch (XPathExpressionException ex) {}
        return list;
        
    }
*/    
    /**
     * Finds the Git or SVN locations for a project.
     * @param item a job
     * @return Array of found SCM paths
     */
    private String getSCMPaths(Item item) {
        final StringBuilder scmPaths = new StringBuilder();
        final SCM scm = ((AbstractProject<?, ?>) item).getScm();

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
    
    private String getProjectRecipients() throws IOException {
        
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

    
        
    public final void doSetDetails(StaplerRequest req, StaplerResponse rsp) throws IOException, URISyntaxException{
        final String newDescription = req.getParameter(descLabel);
        final String newAddresses = req.getParameter(emailLabel);
        final String newScm = req.getParameter(scmLabel);
        final StringBuilder sb = new StringBuilder();
        
        LOG.finest(req.getParameterMap().toString());
        
        if (req.hasParameter("replace_" + descLabel)) {
            try {
                project.setDescription(newDescription);
            } catch (IOException ex) {
                LOG.finest("Unable to change project description." + ex.getMessage());
            }
            sb.append("Description replaced");
        } else {
            sb.append("Description not replaced");
        }
        
        //email-ext-Adressen ersetzen (wie?)
        if (req.hasParameter("replace_" + emailLabel)) {
            replaceEmailAddresses(newAddresses);
            sb.append("Email Addresses replaced");
        } else {
            sb.append("Email Addresses not replaced");
        }
        
        //scm-url ersetzen
        if (req.hasParameter("replace_" + scmLabel) && newScm != null && !newScm.isEmpty()) {
            replaceScmUrl(newScm);
            sb.append("SCM URL replaced");
        } else {
            sb.append("SCM URL not replaced");
        }
        
        
        //weiterleiten auf Seite, wo angezeigt, was ersetzt wurde/Fehlermeldungen ausgegeben?
        message = sb.toString();
        LOG.finest(message);
        rsp.sendRedirect("showOutcome");
    }
    
    private void replaceScmUrl(String newScmUrl) throws IOException{
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
            
            //was ist local? wie verarbeitet man mehrere Locs?
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

    private void replaceEmailAddresses(String newAddresses) throws IOException{
        String addresses = newAddresses.trim().replace(" ", ",");
        ExtendedEmailPublisher publisher = project.getPublishersList().get(ExtendedEmailPublisher.class);
        publisher.recipientList = addresses;
        project.save();
    }
    
    
    public String getMessage() {
        LOG.finest("HALLO! " + message);
        return message;
    }

    
    /**
     * {@inheritDoc}
     */
    public String getDisplayName() {
        return "Pom2Config";
    }

    /**
     * {@inheritDoc}
     */
    public String getIconFileName() {
        return Functions.getResourcePath() + "/plugin/pom2config/icons/monkey-32x32.png";
    }

    /**
     * {@inheritDoc}
     */
    public String getUrlName() {
        return "pom2config";
    }
}
