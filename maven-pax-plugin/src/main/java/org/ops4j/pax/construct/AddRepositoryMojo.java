package org.ops4j.pax.construct;

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

import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Adds a repository to the root pom.
 * 
 * @goal add-repository
 */
public final class AddRepositoryMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * The url of the repository.
     * 
     * @parameter expression="${repositoryId}"
     * @required
     */
    private String repositoryId;

    /**
     * The url of the repository.
     * 
     * @parameter expression="${repositoryURL}"
     * @required
     */
    private String repositoryURL;

    /**
     * Should we attempt to overwrite entries.
     * 
     * @parameter expression="${overwrite}" default-value="false"
     */
    private boolean overwrite;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        // execute only if a root project
        if( project.getParent() != null )
        {
            return;
        }

        try
        {
            Document pom = PomUtils.readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );
            Repository repository = new Repository();
            repository.setId( repositoryId );
            repository.setUrl( repositoryURL );
            PomUtils.addRepository( projectElem, repository, overwrite );

            PomUtils.writePom( project.getFile(), pom );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to add the requested repository", e );
        }
    }

}
