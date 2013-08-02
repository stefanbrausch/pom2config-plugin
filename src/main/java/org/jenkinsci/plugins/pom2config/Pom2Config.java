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

import hudson.FilePath;
import hudson.Functions;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.plugins.emailext.EmailType;


/**
 * @author Kathi Stutz
 */
public class Pom2Config implements Action {

    private static final Logger LOG = Logger.getLogger(Pom2Config.class.getName());

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
    
    private EmailExtHandler emailExt = null;
    private ScmHandler scmHandler;
    private List<String> scmUrlList = new ArrayList<String>();
    
    /**
     * @param project
     */
    public Pom2Config(AbstractProject<?, ?> project) {
        super();
        this.project = project;
        if (emailExtAvailable()) {
            emailExt = new EmailExtHandler(project);
        }
        scmHandler = new ScmHandler(project);
    }

    public List<DataSet> getConfigDetails() {
        return configDetails;
    }
    
    public String getScmUrlLabel() {
        return scmLabel;
    }
    
    private boolean emailExtAvailable() {
        try {
            new EmailType();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isPomInWorkspace() {
        FilePath workspace = project.getSomeWorkspace();
        if (project.getLastBuild() != null && project.getLastBuild().getWorkspace() != null) {
            workspace = project.getLastBuild().getWorkspace();
        }
        
        if (project instanceof MavenModuleSet) {
            LOG.finest("RootPom: " + ((MavenModuleSet) project).getRootPOM(null));
        }

        if (workspace != null) {
            FilePath pomPath = workspace.child("pom.xml");
            try {
                if (pomPath.exists()){
                    return true;
                }
            } catch (IOException ex) {
                return false;
            } catch (InterruptedException ex) {
                return false;
            }
        }
        return false;
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
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        final DocumentBuilder db = dbf.newDocumentBuilder();
        final InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xml));
        final Document doc = db.parse(is);

        descriptions = new DataSet(descLabel, project.getDescription(), retrieveDetailsFromPom(doc, "//description/text()"));
        configDetails.add(descriptions);
        
        if (emailExt != null) {
            emailAddresses = new DataSet(emailLabel, emailExt.getProjectRecipients(), retrieveDetailsFromPom(doc,
                    "//developers/developer/email/text()"));
            configDetails.add(emailAddresses);
        }
        
        scmUrlList = scmHandler.getSCMPaths();
        if (scmUrlList.size() > 0) {
            scmUrls = new DataSet(scmLabel, scmHandler.getSCMPaths().get(0), retrieveDetailsFromPom(doc, "//scm/connection/text()"));
        } else {
            scmUrls = new DataSet(scmLabel, "", retrieveDetailsFromPom(doc, "//scm/connection/text()"));
        }
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

    
    public List<String> getScmUrlList() {
        return scmUrlList;
    }
    
    public final void doSetDetails(StaplerRequest req, StaplerResponse rsp) throws IOException, URISyntaxException{
//        final String newDescription = req.getParameter(descLabel);
//        final String newAddresses = req.getParameter(emailLabel);
//        final String newScm = req.getParameter(scmLabel);
        
//        LOG.finest("descLabel: " + newDescription);
//        LOG.finest("emailLabel: " + newAddresses);
//        LOG.finest("scmLabel: " + newScm);
        
        JSONObject formData = null;
        
        try {
            formData = req.getSubmittedForm();
            LOG.finest(formData.toString(2));
        } catch (ServletException e) {
            // TODO Auto-generated catch block
        }
        
        if (formData.containsKey(descLabel) 
                && !formData.getJSONObject(descLabel).getString("pomEntry").trim().isEmpty()) {
            try {
                project.setDescription(formData.getJSONObject(descLabel).getString("pomEntry").trim());
            } catch (IOException ex) {
                LOG.finest("Unable to change project description." + ex.getMessage());
                messages.add("Description not replaced");
            }
            messages.add("Description replaced");
        } else {
            messages.add("Description not replaced");
        }

        if (formData.containsKey(emailLabel)) {
            final String newEmail = formData.getJSONObject(emailLabel).getString("pomEntry").trim();
            if (!newEmail.isEmpty() && emailExt != null) {
                emailExt.replaceEmailAddresses(newEmail);
                messages.add("Email Addresses replaced");
            }
        } else {
            messages.add("Email Addresses not replaced");
        }
        
        //scm-url ersetzen
        if (formData.containsKey(scmLabel)) {
            final String newScm = formData.getJSONObject(scmLabel).getString("pomEntry").trim();
            final String oldScm = formData.getJSONObject(scmLabel).getString("configEntry").trim();
            if (!newScm.isEmpty()) {
                scmHandler.replaceScmUrl(oldScm, newScm);
                messages.add("SCM Url replaced");
            }
        } else {
            messages.add("SCM Url not replaced");
        }
        
//        getMessages();
        
        //weiterleiten auf Seite, wo angezeigt, was ersetzt wurde/Fehlermeldungen ausgegeben?
        rsp.sendRedirect("showOutcome");
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
