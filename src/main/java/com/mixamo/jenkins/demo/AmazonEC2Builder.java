package com.mixamo.jenkins.demo;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

public class AmazonEC2Builder extends Builder {

	private String aws_region;
	private String aws_base_ami;
	private String aws_instance_type;
	private String aws_key_name;
	private String aws_sec_group;
	
	Reservation ec2Boot(String userKey, String userSec, PrintStream logger) {
		
		// Connect to AWS
		AWSCredentials credentials = new BasicAWSCredentials(userKey, userSec);
		AmazonEC2Client amazonEC2Client = new AmazonEC2Client(credentials);
		amazonEC2Client.setEndpoint(aws_region);
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		
		// Describe Launch Request
		runInstancesRequest.withImageId(aws_base_ami)
				.withInstanceType(aws_instance_type)
				.withMinCount(1)
				.withMaxCount(1).withKeyName(aws_key_name)
				.withSecurityGroups(aws_sec_group);
		
		// Submit Request
		RunInstancesResult runInstancesResult = amazonEC2Client.runInstances(runInstancesRequest);
		
		// The easiest thing to do here is just *wait* for the instance to boot
		// otherwise you won't receive the IP Address
		
		logger.println("Waiting 60 Sec. for Instance to Boot");
		try {
			Thread.sleep(60000);
		} catch (InterruptedException e) {
			// oops
		}
		
		// Boilerplate to re-query AWS
		String id = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
		DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(id);
		DescribeInstancesResult dir = amazonEC2Client.describeInstances(describeInstancesRequest);
		List<Reservation> r = dir.getReservations();
		
		return r.get(0);
		
	}
	
	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public AmazonEC2Builder(String baseami, String itype, String region, String sec, String key) {
		aws_base_ami = baseami;
		aws_instance_type = itype;
		aws_key_name = key;
		aws_sec_group = sec;
		aws_region = region;
	}
	
	// Called automagically by config.jelly
	public String getItype() {
		return aws_instance_type;
	}
	public String getBaseami() {
		return aws_base_ami;
	}
	public String getKey() {
		return aws_key_name;
	}
	public String getSec() {
		return aws_sec_group;
	}
	public String getRegion() {
		return aws_region;
	}
	
	// This does the actual work during the build
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {

		// This is where you 'build' the project.
		PrintStream logger = listener.getLogger();
		
		String userKey = getDescriptor().getUserKey();
		String userSec = getDescriptor().getUserSec();
		
		Reservation reservation = ec2Boot(userKey,userSec,logger);
		
		String address = "";
		for( Instance i : reservation.getInstances() ){
			listener.getLogger().println("InstanceId: " + i.getInstanceId() );
			listener.getLogger().println("Public DNS: " + i.getPublicDnsName() );
			address = i.getPublicDnsName();
		}
		
		// Write EC2 Instance Address to AWSInfo File 
		
		FilePath info = new FilePath(build.getWorkspace(), "AWSInfo");
		
		try {
			info.write(address, "utf-8");
			listener.getLogger().println("Wrote Info to AWSInfo");
		} catch (IOException e) {
			return false; 
		} catch (InterruptedException e) {
			return false;
		}
		
		return true;
	}
	
	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link AmazonEC2Builder}. Used as a singleton. The class
	 * is marked as public so that it can be accessed from views.
	 * 
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information, simply store it in a
		 * field and call save().
		 * 
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */

		private String aws_userKey;
		private String aws_userSec;

		// Validate userKey Field
		public FormValidation doCheckUserKey(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set the key");
			return FormValidation.ok();
		}
		
		// Validate userSec Field
		public FormValidation doCheckUserSec(@QueryParameter String value)
			throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set the secret");
			return FormValidation.ok();
		}

		@SuppressWarnings("rawtypes")
		public boolean isApplicable( Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Launch EC2 Instance";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {

			// To persist global configuration information,
			// set that to properties and call save().
			aws_userKey = formData.getString("userKey");
			aws_userSec = formData.getString("userSec");

			// ^Can also use req.bindJSON(this, formData);
			// (easier when there are many fields; need set* methods for this,
			// like setUseFrench)
			save();

			return super.configure(req, formData);
		}

		/**
		 * This method returns true if the global configuration says we should
		 * speak French.
		 * 
		 * The method name is bit awkward because global.jelly calls this method
		 * to determine the initial state of the checkbox by the naming
		 * convention.
		 */
		public String getUserKey() {
			return aws_userKey;
		}

		public String getUserSec() {
			return aws_userSec;
		}
	}
}
