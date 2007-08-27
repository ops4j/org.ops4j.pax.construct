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

import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Adds a Maven repository to the current project.
 * 
 * @goal add-repository
 */
public final class AddRepositoryMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The id of the repository.
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
     * Should we attempt to overwrite entries?
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    /**
     * Only update the first pom in the reactor.
     */
    private static boolean ignore = false;

    public void execute()
        throws MojoExecutionException
    {
        if( ignore )
        {
            return;
        }
        ignore = true;

        Pom pom = PomUtils.readPom( project.getFile() );

        Repository repository = new Repository();
        repository.setId( repositoryId );
        repository.setUrl( repositoryURL );

        pom.addRepository( repository, overwrite );

        pom.write();
    }
}
