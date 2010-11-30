package org.ops4j.pax.construct.lifecycle;

/*
 * Copyright 2009,2010 Mike Smoot
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.List;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.apache.maven.plugin.MojoExecutionException;


/**
 * This mojo is an extension of the Provision mojo that instead of 
 * provisioning, simply collects all of the bundles, configuration 
 * files, and scripts needed to provision the application and puts 
 * them in a single directory. The intent is for the directory to 
 * serve as input into a Maven assembly descriptor so that an 
 * application may be properly packaged and deployed.  The default
 * directory created is target/pax-runner-dir, where the name
 * "pax-runner-dir" may be controlled with the "outputDirectory"
 * parameter.
 *
 * @goal directory
 * @aggregator true
 * 
 * @requiresProject false
 */
public class DirectoryMojo extends ProvisionMojo
{
	/**
	 * The name of the directory that will contain all of the files
	 * collected for provisioning the application.
	 *
	 * @parameter default-value="pax-runner-dir"
	 */
	private String outputDirectory;

	private String runnerDirName; 
	private File runnerDir; 

    public void execute()
		throws MojoExecutionException
	{
		runnerDirName = System.getProperty("project.build.directory","target") + "/" + outputDirectory;
		runnerDir = new File(runnerDirName);
		super.execute();
	}

	protected List getDeployCommands() 
	{
		List l = super.getDeployCommands();
		l.add("--executor=script");
		l.add("--workingDirectory=" + runnerDirName);
		return l;
	}
}
