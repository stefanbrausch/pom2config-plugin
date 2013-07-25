package org.jenkinsci.plugins.pom2config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
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
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import hudson.FilePath;
import hudson.Functions;
import hudson.XmlFile;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Action;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.scm.SubversionRepositoryBrowser;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.scm.subversion.WorkspaceUpdater;
import hudson.triggers.SCMTrigger;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.emailext.ExtendedEmailPublisher;

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
    private List<String> messages = new ArrayList<String>();
    
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

    public final void doGetPom(StaplerRequest req, StaplerResponse rsp) throws IOException {

        final String notRetrieved = "Unable to retrieve pom file.";
        final String notParsed = "Unable to parse pom file.";
        final Writer writer = rsp.getCompressedWriter(req);

        String pomAsString = "";
        
        try {
            pomAsString = retrievePom(req.getSubmittedForm().getJSONObject("fromWhere"));
        } catch (IOException ioe) {
            writeErrorMessage(notRetrieved, writer);
        } catch (InterruptedException ie) {
            writeErrorMessage(notRetrieved, writer);
        } catch (ServletException se) {
            writeErrorMessage(notRetrieved, writer);
        }
        
        if (!pomAsString.isEmpty()) {
            try {
                parsePom(pomAsString);
            } catch (ParserConfigurationException e) {
                writeErrorMessage(notParsed, writer);
            } catch (SAXException e) {
                writeErrorMessage(notParsed, writer);
            } catch (IOException e) {
                writeErrorMessage(notParsed, writer);
            }
            rsp.sendRedirect("chooseDetails");
        } else {
            writeErrorMessage(notRetrieved, writer);
        }
        
    }
    
    private void writeErrorMessage(String message, Writer writer) throws IOException {
        try {
            writer.append(message + "\n");
        } finally {
            writer.close();
        }
    }
    
    private String retrievePom(JSONObject formData) throws ServletException, IOException, InterruptedException {
        String pomAsString = "";
        final String fromWhere = formData.getString("value");
        
        if ("useExisting".equals(fromWhere)) {
            
            project.getTrigger(SCMTrigger.class).run();
            //woher weiß ich, wann er durch ist?
            
            FilePath workspace = project.getSomeWorkspace();
            if (project.getLastBuild() != null && project.getLastBuild().getWorkspace() != null) {
                workspace = project.getLastBuild().getWorkspace();
            }
            
            if (workspace != null) {
                FilePath pomPath = workspace.child("pom.xml");
                if (pomPath.exists()){
                    pomAsString = pomPath.readToString();
                }
            }
        } else if ("fromUrl".equals(fromWhere)){
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
            final FileItem fileItem = Stapler.getCurrentRequest().getFileItem(formData.getString("file"));
            pomAsString = fileItem.getString();
        }
        
        return pomAsString;
    }
    
    
    private void parsePom(String xml) throws ParserConfigurationException, SAXException, IOException {
        configDetails.clear();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = null;
        db = dbf.newDocumentBuilder();
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xml));

        Document doc = db.parse(is);

        descriptions = new DataSet(descLabel, project.getDescription(), retrieveDetailsFromPom(doc, "//description/text()"));
        emailAddresses = new DataSet(emailLabel, getProjectRecipients(), retrieveDetailsFromPom(doc,
                "//developers/developer/email/text()"));
        scmUrls = new DataSet(scmLabel, getSCMPaths(project), retrieveDetailsFromPom(doc, "//scm/connection/text()"));

        configDetails.add(descriptions);
        configDetails.add(emailAddresses);
        configDetails.add(scmUrls);
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
        
        LOG.finest("descLabel: " + newDescription);
        LOG.finest("emailLabel: " + newAddresses);
        LOG.finest("scmLabel: " + newScm);

        LOG.finest("repl_descLabel: " + req.getParameter("replace_" + descLabel));
        LOG.finest("repl_emailLabel: " + req.getParameter("replace_" + emailLabel));
        LOG.finest("repl_scmLabel: " + req.getParameter("replace_" + scmLabel));
        
        if (req.hasParameter("replace_" + descLabel) && !descLabel.trim().isEmpty()) {
            try {
                project.setDescription(newDescription);
            } catch (IOException ex) {
                LOG.finest("Unable to change project description." + ex.getMessage());
                messages.add("Description not replaced");
            }
            messages.add("Description replaced");
        } else {
            messages.add("Description not replaced");
        }
        
        if (req.hasParameter("replace_" + emailLabel) && !emailLabel.trim().isEmpty()) {
            replaceEmailAddresses(newAddresses);
            messages.add("Email Addresses replaced");
        } else {
            messages.add("Email Addresses not replaced");
        }
        
        //scm-url ersetzen
        if (req.hasParameter("replace_" + scmLabel) && !newScm.trim().isEmpty()) {
            replaceScmUrl(newScm);
            messages.add("SCM URL replaced");
        } else {
            messages.add("SCM URL not replaced");
        }
        
//        getMessages();
        
        //weiterleiten auf Seite, wo angezeigt, was ersetzt wurde/Fehlermeldungen ausgegeben?
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
    
    
    public List<String> getMessages() {
        for (String s : messages) {
            LOG.finest("MMMMMMMMMM: " + s);
        }
        return messages;
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
