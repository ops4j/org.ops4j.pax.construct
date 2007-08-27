package org.ops4j.pax.construct.bundle;

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
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Moves a local bundle to a new location in the project tree.
 * 
 * @goal move-bundle
 */
public final class MoveBundleMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The local project name of the bundle to be moved.
     * 
     * @parameter expression="${bundleName}"
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

    /**
     * Avoid repeated calls when there's more than one project in the reactor.
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

        MavenProject bundleProject = PomUtils.findModule( project, bundleName );
        if( null == bundleProject )
        {
            throw new MojoExecutionException( "Cannot find bundle " + bundleName );
        }

        Pom newModulesPom = PomUtils.createModuleTree( project.getBasedir(), targetDirectory );
        if( null == newModulesPom )
        {
            throw new MojoExecutionException( "targetDirectory is outside of this project" );
        }

        File bundleFolder = bundleProject.getBasedir();
        File modulesFolder = bundleFolder.getParentFile();
        final String moduleName = bundleFolder.getName();

        // MOVE BUNDLE!
        File newBundleFolder = new File( targetDirectory, moduleName );
        if( !bundleFolder.renameTo( newBundleFolder ) )
        {
            throw new MojoExecutionException( "Unable to move bundle " + bundleName + " to " + targetDirectory );
        }

        String relativePath = PomUtils.calculateRelativePath( targetDirectory, modulesFolder );
        String[] pathSegments = relativePath.split( "/" );

        int relativeOffset = 0;
        for( int i = 0; i < pathSegments.length; i++ )
        {
            relativeOffset += ("..".equals( pathSegments[i] )) ? 1 : -1;
        }

        if( relativeOffset != 0 )
        {
            Pom pom = PomUtils.readPom( new File( newBundleFolder, "pom.xml" ) );
            pom.adjustRelativePath( relativeOffset );
            pom.write();
        }

        Pom modulesPom = PomUtils.readPom( new File( modulesFolder, "pom.xml" ) );

        modulesPom.removeModule( moduleName );
        modulesPom.write();

        newModulesPom.addModule( moduleName, true );
        newModulesPom.write();
    }
}
