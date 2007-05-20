package org.ops4j.pax.construct;

/*
 * Copyright 2007 Stuart McCulloch
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

import static org.ops4j.pax.construct.PomUtils.readPom;
import static org.ops4j.pax.construct.PomUtils.writePom;

import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Removes a bundle from the project.
 * 
 * @goal remove-bundle
 */
public final class RemoveBundleMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * The name of the new bundle.
     * 
     * @parameter expression="${name}"
     * @required
     */
    private String bundleName;

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
            boolean foundBundle = false;
            Document pom = readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );
            Element modulesElem;

            try
            {
                modulesElem = projectElem.getElement( null, "modules" );
            }
            catch ( Exception e )
            {
                throw new IOException( "Parent project has no <modules> element" );
            }

            for ( int i = 0; i < modulesElem.getChildCount(); i++ )
            {
                Element childElem = modulesElem.getElement( i );
                if ( childElem != null )
                {
                    if ( bundleName.equalsIgnoreCase( childElem.getChild( 0 ).toString() ) )
                    {
                        // assume no duplicates
                        modulesElem.removeChild( i );
                        foundBundle = true;
                        break;
                    }
                }
            }

            if ( foundBundle )
            {
                FileSet bundleFiles = new FileSet();
                bundleFiles.setDirectory( project.getBasedir().getPath() );
                bundleFiles.addInclude( bundleName );

                new FileSetManager( getLog(), true ).delete( bundleFiles );

                writePom( project.getFile(), pom );
            }
            else
            {
                throw new IOException( "The project doesn't have a bundle named " + bundleName );
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to remove the requested bundle", e );
        }
    }

}
