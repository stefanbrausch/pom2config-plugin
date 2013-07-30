package org.jenkinsci.plugins.pom2config;


import hudson.model.FreeStyleProject;

import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import com.gargoylesoftware.htmlunit.WebAssert;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

/**
 * @author kstutz
 */
public class Pom2ConfigTest extends HudsonTestCase {

    @LocalData
    public void testReplacingDescription() throws Exception {
        final String newDescription = "Brave new description";
        final FreeStyleProject project = (FreeStyleProject) hudson.getItem("TestJob");
        project.scheduleBuild2(0);

        final WebClient webClient =  new WebClient();

        final HtmlPage indexPage = webClient.goTo(project.getUrl() + "pom2config");
        final HtmlElement radioButton = indexPage.getElementsByName("fromWhere").get(0);
//        radioButton.click();
        final HtmlPage chooseDetailsPage = submit(radioButton.getEnclosingForm(), "Submit");
        
        System.out.println(chooseDetailsPage.asText());
        
        WebAssert.assertTextPresent(chooseDetailsPage, "Old description");
        WebAssert.assertTextPresent(chooseDetailsPage, newDescription);
                
        submit(chooseDetailsPage.getFormByName("setDetails"), "Submit");
        
        assertTrue(newDescription.equals(project.getDescription()));
    }
    
}
