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

import java.io.File;
import java.io.FileReader;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Removes a local bundle from the project.
 * 
 * @goal remove-bundle
 */
public final class RemoveBundleMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The local project name of the bundle to be removed.
     * 
     * @parameter expression="${name}"
     * @required
     */
    private String bundleName;

    /**
     * Records the Maven model for the removed bundle.
     */
    private static Model removedBundleModel;

    public void execute()
        throws MojoExecutionException
    {
        if( null == removedBundleModel )
        {
            // we must traverse from the top!
            if( project.getParent() != null )
            {
                throw new MojoExecutionException( "This command must be run from the project root" );
            }

            File bundlePomFile = PomUtils.findBundlePom( project.getBasedir(), bundleName );
            if( null == bundlePomFile || !bundlePomFile.exists() )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            try
            {
                // cache Maven information about the removed bundle
                FileReader input = new FileReader( bundlePomFile );
                MavenXpp3Reader modelReader = new MavenXpp3Reader();
                removedBundleModel = modelReader.read( input );
            }
            catch( Exception e )
            {
                throw new MojoExecutionException( "Unable to open sub-project " + bundleName, e );
            }

            if( !PomUtils.isBundleProject( removedBundleModel ) )
            {
                throw new MojoExecutionException( "Sub-project " + bundleName + " is not a bundle" );
            }

            try
            {
                FileSet bundleFiles = new FileSet();

                File bundlePomFolder = bundlePomFile.getParentFile();
                bundleFiles.setDirectory( bundlePomFolder.getParent() );
                bundleFiles.addInclude( bundlePomFolder.getName() );

                new FileSetManager( getLog(), true ).delete( bundleFiles );
            }
            catch( Exception e )
            {
                throw new MojoExecutionException( "Unable to remove the requested bundle", e );
            }
        }

        // ignore any removed bundle project(s)
        if( false == project.getFile().exists() )
        {
            return;
        }

        Document pom = PomUtils.readPom( project.getFile() );
        Element projectElem = pom.getElement( null, "project" );

        Dependency dependency = PomUtils.getBundleDependency( removedBundleModel );

        PomUtils.removeDependency( projectElem, dependency );
        PomUtils.removeModule( projectElem, new File( bundleName ).getName() );
        PomUtils.writePom( project.getFile(), pom );
    }

}
