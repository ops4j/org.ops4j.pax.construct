package org.ops4j.pax.construct.lifecycle;

/*
 * Copyright 2009 Mike Smoot
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
 * An extension of the Provision mojo that instead of provisioning,  
 * collects all of the files needed to provision and zips them into 
 * a single file suitable for distribution.
 *
 * @goal package
 * @aggregator true
 * 
 * @requiresProject false
 */
public class PackageMojo extends ProvisionMojo
{

	private File runnerDir = new File("runner");

    public void execute()
		throws MojoExecutionException
	{
		super.execute();

		try {
			ZipOutputStream zipStream = new ZipOutputStream(new FileOutputStream("runner.zip"));
			addFiles( runnerDir, zipStream );	
			zipStream.close();
		} catch (IOException ioe) {
			throw new MojoExecutionException("Couldn't create zip file",ioe);
		}
	}

	private void addFiles( final File work, final ZipOutputStream zipStream ) 
		throws IOException
	{
        File[] files = work.listFiles();
        int cut = runnerDir.getAbsolutePath().length() + 1;
        for (int i = 0; i < files.length; i++) {
			File f = files[i];
            if (f.isDirectory()) {
                addFiles(f, zipStream);
            } else if (!f.isHidden()) {
                String fileName = f.getAbsolutePath().substring(cut);
                zipStream.putNextEntry(new ZipEntry(fileName));
                InputStream input = new FileInputStream(f);
		        byte[] buffer = new byte[1024];
        		int b;
        		while ((b = input.read(buffer)) != -1) 
            		zipStream.write(buffer, 0, b);
                input.close();
            }
        }
	}


	protected List getDeployCommands() 
	{
		List l = super.getDeployCommands();
		l.add("--executor=script");
		return l;
	}
}
