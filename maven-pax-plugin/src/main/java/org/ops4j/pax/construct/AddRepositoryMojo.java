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

import static org.ops4j.pax.construct.PomUtils.NL;
import static org.ops4j.pax.construct.PomUtils.WS;
import static org.ops4j.pax.construct.PomUtils.readPom;
import static org.ops4j.pax.construct.PomUtils.writePom;

import java.io.IOException;

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
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        // execute only if a root project
        if ( project.getParent() != null )
        {
            return;
        }

        try
        {
            Document pom = readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );
            Element reposElem;

            try
            {
                reposElem = projectElem.getElement( null, "repositories" );
            }
            catch ( Exception e )
            {
                reposElem = projectElem.createElement( null, "repositories" );
                reposElem.addChild( Element.TEXT, NL + "  " );
                projectElem.addChild( Element.TEXT, "  " );
                projectElem.addChild( Element.ELEMENT, reposElem );
                projectElem.addChild( Element.TEXT, NL + NL );
            }

            for ( int i = 0; i < reposElem.getChildCount(); i++ )
            {
                Element childElem = reposElem.getElement( i );
                if ( childElem != null )
                {
                    Element idElem = childElem.getElement( null, "id" );
                    Element urlElem = childElem.getElement( null, "url" );

                    if ( repositoryId.equalsIgnoreCase( idElem.getChild( 0 ).toString() ) )
                    {
                        throw new IOException( "The project already has a repository with id " + repositoryId );
                    }
                    if ( repositoryURL.equalsIgnoreCase( urlElem.getChild( 0 ).toString() ) )
                    {
                        throw new IOException( "The project already has a repository with url " + repositoryURL );
                    }
                }
            }

            // add a new repository
            Element repoElem = reposElem.createElement( null, "repository" );
            reposElem.addChild( Element.TEXT, WS );
            reposElem.addChild( Element.ELEMENT, repoElem );
            reposElem.addChild( Element.TEXT, NL + WS );

            // add the id of the repository
            Element idElem = repoElem.createElement( null, "id" );
            idElem.addChild( Element.TEXT, repositoryId );
            repoElem.addChild( Element.TEXT, NL + WS + WS + WS );
            repoElem.addChild( Element.ELEMENT, idElem );

            // add the url of the repository
            Element urlElem = repoElem.createElement( null, "url" );
            urlElem.addChild( Element.TEXT, repositoryURL );
            repoElem.addChild( Element.TEXT, NL + WS + WS + WS );
            repoElem.addChild( Element.ELEMENT, urlElem );
            repoElem.addChild( Element.TEXT, NL + WS + WS );

            writePom( project.getFile(), pom );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to add the requested repository", e );
        }
    }

}
