package org.ops4j.pax.construct.project;

/*
 * Copyright 2007 Alin Dreghiciu, Stuart McCulloch
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

import java.io.File;
import java.io.IOException;

import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Add a Maven repository element to a project
 * 
 * @goal add-repository
 * @aggregator true
 */
public class AddRepositoryMojo extends AbstractMojo
{
    /**
     * The repository identifier.
     * 
     * @parameter expression="${repositoryId}"
     * @required
     */
    private String repositoryId;

    /**
     * The repository URL.
     * 
     * @parameter expression="${repositoryURL}"
     * @required
     */
    private String repositoryURL;

    /**
     * The directory containing the POM to be updated.
     * 
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File targetDirectory;

    /**
     * When true, overwrite matching entries in the POM.
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    /**
     * When true, enable snapshots from this repository.
     * 
     * @parameter expression="${snapshots}"
     */
    private boolean snapshots;

    /**
     * When true, enable releases from this repository.
     * 
     * @parameter expression="${releases}" default-value="true"
     */
    private boolean releases;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        Pom pom;

        try
        {
            pom = PomUtils.readPom( targetDirectory );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem reading Maven POM: " + targetDirectory );
        }

        Repository repository = new Repository();
        repository.setId( repositoryId );
        repository.setUrl( repositoryURL );

        getLog().info( "Adding repository " + repositoryURL + " to " + pom );

        pom.addRepository( repository, snapshots, releases, overwrite );

        try
        {
            pom.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem writing Maven POM: " + pom.getFile() );
        }
    }
}
