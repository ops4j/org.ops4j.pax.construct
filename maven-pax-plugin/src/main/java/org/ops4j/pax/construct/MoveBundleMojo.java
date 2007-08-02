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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;

/**
 * Moves a bundle to a new location in the project.
 * 
 * @goal move-bundle
 */
public final class MoveBundleMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * The name of the bundle to move.
     * 
     * @parameter expression="${name}"
     * @required
     */
    private String bundleName;

    /**
     * The new location for the bundle.
     * 
     * @parameter expression="${targetDirectory}"
     * @required
     */
    private File targetDirectory;

    // fudge one-shot behaviour...
    private static boolean ignore = false;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        if( ignore )
        {
            return;
        }
        ignore = true;

        if( project.getParent() != null )
        {
            throw new MojoExecutionException( "This command must be run from the project root" );
        }

        try
        {
            File oldBundlePomFile = PomUtils.findBundlePom( project.getBasedir(), bundleName );

            if( null == oldBundlePomFile || !oldBundlePomFile.exists() )
            {
                throw new MojoExecutionException( "Cannot find bundle " + bundleName );
            }

            File newParentFile = PomUtils.createModuleTree( project.getBasedir(), targetDirectory );

            if( null == newParentFile )
            {
                throw new MojoExecutionException( "targetDirectory is outside of this project" );
            }

            File oldBundlePomFolder = oldBundlePomFile.getParentFile();
            File oldParentPomFolder = oldBundlePomFolder.getParentFile();
            final String moduleName = oldBundlePomFolder.getName();

            File newBundlePomFolder = new File( targetDirectory, moduleName );
            File newBundlePomFile = new File( newBundlePomFolder, "pom.xml" );

            if( false == oldBundlePomFolder.renameTo( newBundlePomFolder ) )
            {
                throw new MojoExecutionException( "Unable to move bundle " + bundleName + " to " + targetDirectory );
            }

            String relativePath = PomUtils.calculateRelativePath( targetDirectory, oldParentPomFolder );
            String[] pathSegments = relativePath.split( "/" );

            int relativeOffset = 0;
            for( int i = 0; i < pathSegments.length; i++ )
            {
                relativeOffset += ("..".equals( pathSegments[i] )) ? 1 : -1;
            }

            if( relativeOffset != 0 )
            {
                Document bundlePom = PomUtils.readPom( newBundlePomFile );
                PomUtils.adjustRelativePath( bundlePom.getElement( null, "project" ), relativeOffset );
                PomUtils.writePom( newBundlePomFile, bundlePom );
            }

            File oldParentFile = new File( oldParentPomFolder, "pom.xml" );

            Document oldParentPom = PomUtils.readPom( oldParentFile );
            PomUtils.removeModule( oldParentPom.getElement( null, "project" ), moduleName );
            PomUtils.writePom( oldParentFile, oldParentPom );

            Document newParentPom = PomUtils.readPom( newParentFile );
            PomUtils.addModule( newParentPom.getElement( null, "project" ), moduleName, true );
            PomUtils.writePom( newParentFile, newParentPom );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to move the requested bundle", e );
        }
    }
}
