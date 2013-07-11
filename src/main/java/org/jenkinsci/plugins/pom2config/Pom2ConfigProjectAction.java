package org.jenkinsci.plugins.pom2config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
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
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.scm.SubversionSCM;
import hudson.scm.SubversionSCM.ModuleLocation;
import hudson.tasks.Publisher;

import hudson.plugins.git.GitSCM;
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
    
    private DataSet descriptions = new DataSet("Project Description");
    private DataSet emailAddresses = new DataSet("Developer Email Addresses");
    private DataSet scmUrls = new DataSet("SCM URLs");

    /**
     * @param project
     *            for which configurations should be returned.
     */
    public Pom2ConfigProjectAction(AbstractProject<?, ?> project) {
        super();
        this.project = project;
    }

    public DataSet getDescriptions() {
        return descriptions;
    }

    public DataSet getEmailAddresses() {
        return emailAddresses;
    }

    public DataSet getScmUrls() {
        return scmUrls;
    }


    public final void doGetPom(StaplerRequest req, StaplerResponse rsp) 
    throws IOException, ServletException, SAXException, XPathExpressionException {
                
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
                
                descriptions.addOldEntry(project.getDescription());
                for (String detailFromPom : retrieveDetailsFromPom(doc, "//description/text()")) {
                    descriptions.addNewEntry(detailFromPom);
                }

                emailAddresses.addOldEntry(getProjectRecipients());
                
                for (String detailFromPom : retrieveDetailsFromPom(doc, "//developers/developer/email/text()")) {
                    emailAddresses.addNewEntry(detailFromPom);
                }
                
                for (String detailFromPom : getSCMPath(project)) {
                    scmUrls.addOldEntry(detailFromPom);
                }
                for (String detailFromPom : retrieveDetailsFromPom(doc, "//scm/connection/text()")) {
                    scmUrls.addNewEntry(detailFromPom);
                }

                
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
    
    private List<String> retrieveDetailsFromPom(Document doc, String path) {
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
    
    /**
     * Finds the Git or SVN locations for a project.
     * @param item a job
     * @return Array of found SCM paths
     */
    private List<String> getSCMPath(Item item) {
        final List<String> scmPath = new ArrayList<String>();
        final SCM scm = ((AbstractProject<?, ?>) item).getScm();

        if (scm instanceof SubversionSCM) {
            final SubversionSCM svn = (SubversionSCM) scm;
            for (ModuleLocation location : svn.getLocations()) {
                scmPath.add(location.remote);
                LOG.fine(location.remote + " added");
            }
        } else if (scm instanceof GitSCM) {
            final GitSCM git = (GitSCM) scm;
            
            for (RemoteConfig repo : git.getRepositories()) {
                for (URIish urIish : repo.getURIs()) {
                    scmPath.add(urIish.toString());
                    LOG.fine(urIish.toString() + " added");
                }
            }
        }
        return scmPath;
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

    
        
    public final void doSetDetails(StaplerRequest req, StaplerResponse rsp) {
        
        //gewünschte ersetzen
        //und dann? wie Rückmeldung?
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
