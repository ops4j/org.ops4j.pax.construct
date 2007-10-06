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
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Move a bundle project to a new directory, updating and creating POMs as necessary
 * 
 * @goal move-bundle
 * @aggregator true
 */
public class MoveBundleMojo extends AbstractMojo
{
    /**
     * A directory in the same project tree.
     * 
     * @parameter alias="baseDirectory" expression="${baseDirectory}" default-value="${project.basedir}"
     */
    private File m_baseDirectory;

    /**
     * The new location for the bundle project.
     * 
     * @parameter alias="targetDirectory" expression="${targetDirectory}"
     * @required
     */
    private File m_targetDirectory;

    /**
     * The artifactId or symbolic-name of the bundle.
     * 
     * @parameter alias="bundleName" expression="${bundleName}"
     * @required
     */
    private String m_bundleName;

    /**
     * Locate the bundle project - try name first as a directory path, then an artifactId or symbolic-name
     * 
     * @param baseDir base directory in the same project tree
     * @param pathOrName either a path, or an artifactId or symbolic-name
     * @return the matching POM
     * @throws MojoExecutionException
     */
    public static Pom locateBundlePom( File baseDir, String pathOrName )
        throws MojoExecutionException
    {
        File path = new File( pathOrName );

        Pom bundlePom;
        try
        {
            bundlePom = PomUtils.readPom( path );
        }
        catch( IOException e )
        {
            try
            {
                bundlePom = DirUtils.findPom( baseDir, path.getName() );
            }
            catch( IOException e1 )
            {
                bundlePom = null;
            }
        }

        if( null == bundlePom )
        {
            throw new MojoExecutionException( "Cannot find bundle " + pathOrName );
        }

        return bundlePom;
    }

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        Pom bundlePom = locateBundlePom( m_baseDirectory, m_bundleName );

        Pom newModulesPom;
        try
        {
            // make sure we can reach the new location from the current project tree
            newModulesPom = DirUtils.createModuleTree( m_baseDirectory, m_targetDirectory );
        }
        catch( IOException e )
        {
            newModulesPom = null;
        }

        // sanity check
        if( null == newModulesPom )
        {
            throw new MojoExecutionException( "targetDirectory is outside of this project" );
        }

        File bundleDir = bundlePom.getBasedir();
        File modulesDir = bundleDir.getParentFile();

        final String moduleName = bundleDir.getName();

        File newModulesDir = newModulesPom.getBasedir();
        File newBundleDir = new File( newModulesDir, moduleName );

        getLog().info( "Moving " + bundlePom.getId() + " to " + newBundleDir );

        // MOVE BUNDLE!
        if( !bundleDir.renameTo( newBundleDir ) )
        {
            throw new MojoExecutionException( "Unable to move bundle " + m_bundleName + " to " + m_targetDirectory );
        }

        try
        {
            DirUtils.updateLogicalParent( newBundleDir, bundlePom.getParentId() );

            newModulesPom.addModule( moduleName, true );
            newModulesPom.write();

            Pom modulesPom = PomUtils.readPom( modulesDir );
            modulesPom.removeModule( moduleName );
            modulesPom.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem moving module from " + modulesDir + " to " + newModulesDir );
        }
    }
}
