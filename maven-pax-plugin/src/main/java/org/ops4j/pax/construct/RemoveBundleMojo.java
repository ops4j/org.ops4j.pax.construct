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
            Document pom = readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );
            PomUtils.removeModule( projectElem, bundleName );

            FileSet bundleFiles = new FileSet();
            bundleFiles.setDirectory( project.getBasedir().getPath() );
            bundleFiles.addInclude( bundleName );

            new FileSetManager( getLog(), true ).delete( bundleFiles );

            writePom( project.getFile(), pom );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to remove the requested bundle", e );
        }
    }

}
