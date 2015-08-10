package org.jenkinsci.plugins.p4workflow;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.jenkinsci.plugins.p4.credentials.P4PasswordImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

public class WorkflowTest {

	private final String credential = "id";
	private static String PWD = System.getProperty("user.dir") + "/";
	private static String P4BIN = "src/test/resources/r14.1/";
	private final static String P4ROOT = "tmp-p4root";
	private final static String P4PORT = "localhost:1999";
	private final static int HTTP_PORT = 1888;

	private final static P4Server p4d = new P4Server(PWD + P4BIN, P4ROOT,
			P4PORT);

	@Rule
	public JenkinsRule jenkins = new JenkinsRule();

	@BeforeClass
	public static void setupServer() throws Exception {
		File ckp = new File("src/test/resources/checkpoint.gz");
		assertTrue(ckp.exists());

		File depot = new File("src/test/resources/depot.tar.gz");
		assertTrue(depot.exists());

		p4d.clean();

		File root = new File(P4ROOT);
		assertTrue(root.exists());

		p4d.restore(ckp);
		p4d.upgrade();
		p4d.extract(depot);

		// start pseudo web server
		startHttpServer(HTTP_PORT);
	}

	@Before
	public void startServer() throws Exception {
		p4d.start();
	}

	@After
	public void stopServer() throws Exception {
		p4d.stop();
	}

	@AfterClass
	public static void cleanServer() throws Exception {
		p4d.clean();
	}

	private P4PasswordImpl createCredentials() throws IOException {
		P4PasswordImpl auth = new P4PasswordImpl(CredentialsScope.SYSTEM,
				credential, "desc", P4PORT, null, "jenkins", "0", "jenkins");
		SystemCredentialsProvider.getInstance().getCredentials().add(auth);
		SystemCredentialsProvider.getInstance().save();
		return auth;
	}

	@Test
	public void testCheckP4d() throws Exception {
		int ver = p4d.getVersion();
		assertTrue(ver >= 20121);
	}

	@Test
	public void testWorkflow() throws Exception {
		P4PasswordImpl auth = createCredentials();
		String id = auth.getId();

		WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class,
				"demo");
		job.setDefinition(new CpsFlowDefinition(
				"node {\n"
						+ "   p4sync credential: '"
						+ id
						+ "', template: 'test.ws'"
						+ "\n"
						+ "   p4tag rawLabelDesc: 'TestLabel', rawLabelName: 'jenkins-label'"
						+ "\n"
						+ "   publisher = [$class: 'SubmitImpl', description: 'Submitted by Jenkins', onlyOnSuccess: false, reopen: false]"
						+ "\n"
						+ "   buildWorkspace = [$class: 'TemplateWorkspaceImpl', charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, templateName: 'test.ws']"
						+ "\n" + "   p4publish credential: '" + id
						+ "', publish: publisher, workspace: buildWorkspace"
						+ " \n" + "}"));
		WorkflowRun run = jenkins.assertBuildStatusSuccess(job
				.scheduleBuild2(0));
		jenkins.assertLogContains("P4 Task: syncing files at change", run);
		jenkins.assertLogContains("P4 Task: tagging build.", run);
		jenkins.assertLogContains("P4 Task: reconcile files to changelist.",
				run);
	}

	private static void startHttpServer(int port) throws Exception {
		DummyServer server = new DummyServer(port);
		new Thread(server).start();
	}
}
