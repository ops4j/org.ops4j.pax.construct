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
 * @goal add-repository
 * @aggregator true
 */
public class AddRepositoryMojo extends AbstractMojo
{
    /**
     * @parameter expression="${repositoryId}"
     * @required
     */
    String repositoryId;

    /**
     * @parameter expression="${repositoryURL}"
     * @required
     */
    String repositoryURL;

    /**
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    File targetDirectory;

    /**
     * @parameter expression="${overwrite}"
     */
    boolean overwrite;

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

        getLog().info( "Adding repository " + repositoryURL + " to " + pom.getId() );

        pom.addRepository( repository, overwrite );

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
