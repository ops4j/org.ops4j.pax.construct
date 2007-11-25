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
import java.util.Iterator;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomIterator;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Move a bundle project to a new directory, updating and creating POMs as necessary
 * 
 * <code><pre>
 *   mvn pax:move-bundle -DbundleName=... -DtargetDirectory=...
 * </pre></code>
 * 
 * @goal move-bundle
 * @aggregator true
 * 
 * @requiresProject false
 */
public class MoveBundleMojo extends AbstractMojo
{
    /**
     * A directory in the same project tree.
     * 
     * @parameter expression="${baseDirectory}" default-value="${project.basedir}"
     */
    private File baseDirectory;

    /**
     * The new location for the bundle project.
     * 
     * @parameter expression="${targetDirectory}"
     * @required
     */
    private File targetDirectory;

    /**
     * The artifactId or symbolic-name of the bundle.
     * 
     * @parameter expression="${bundleName}"
     * @required
     */
    private String bundleName;

    /**
     * When true, repair the groupId and any references to the moved bundle.
     * 
     * @parameter expression="${repair}" default-value="true"
     */
    private boolean repair;

    /**
     * Locate the bundle project - try name first as a directory path, then an artifactId or symbolic-name
     * 
     * @param baseDir base directory in the same project tree
     * @param pathOrName either a path, or an artifactId or symbolic-name
     * @return the matching POM
     * @throws MojoExecutionException
     */
    protected static Pom locateBundlePom( File baseDir, String pathOrName )
        throws MojoExecutionException
    {
        Pom bundlePom = null;

        if( null != pathOrName )
        {
            File path = new File( pathOrName );
            try
            {
                bundlePom = PomUtils.readPom( path );
            }
            catch( IOException e )
            {
                bundlePom = DirUtils.findPom( baseDir, path.getName() );
            }
        }

        if( null == bundlePom || !bundlePom.isBundleProject() )
        {
            throw new MojoExecutionException( "Cannot find bundle " + pathOrName );
        }

        return bundlePom;
    }

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        Pom oldBundlePom = locateBundlePom( baseDirectory, bundleName );

        File oldBundleDir = oldBundlePom.getBasedir();

        // the main work - move files and update modules
        Pom newModulesPom = moveBundleFiles( oldBundlePom );
        transferBundleOwnership( oldBundleDir, newModulesPom );

        if( repair )
        {
            // construct a groupId from the new containing POM, eliminating duplicate segments where possible
            String newGroupId = PomUtils.getCompoundId( newModulesPom.getGroupId(), newModulesPom.getArtifactId() );

            // need to open the recently moved POM, can't use the old one!
            Pom newBundlePom = newModulesPom.getModulePom( oldBundleDir.getName() );
            if( null != newBundlePom )
            {
                changeBundleGroup( newBundlePom, newGroupId );
            }
        }
    }

    /**
     * Move a bundle directory to a new directory, creating Maven POMs as necessary to keep it connected to the project
     * 
     * @param bundlePom current Maven POM for the bundle
     * @return modules POM directly above the new bundle directory
     * @throws MojoExecutionException
     */
    private Pom moveBundleFiles( Pom bundlePom )
        throws MojoExecutionException
    {
        Pom newModulesPom;
        try
        {
            // make sure we can reach the new location from the current project tree
            newModulesPom = DirUtils.createModuleTree( baseDirectory, targetDirectory );
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
        else if( !"pom".equals( newModulesPom.getPackaging() ) )
        {
            throw new MojoExecutionException( "targetDirectory is not a modules directory" );
        }

        File oldBundleDir = bundlePom.getBasedir();
        File newBundleDir = new File( newModulesPom.getBasedir(), oldBundleDir.getName() );

        getLog().info( "Moving " + oldBundleDir + " to " + newBundleDir );

        /*
         * MOVE DIRECTORY CONTENTS
         */
        if( !oldBundleDir.renameTo( newBundleDir ) )
        {
            try
            {
                // fallback to copy and delete on Windows...
                FileUtils.copyDirectoryStructure( oldBundleDir, newBundleDir );
                FileUtils.deleteDirectory( oldBundleDir );
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Cannot move bundle " + bundleName + " to " + targetDirectory );
            }
        }

        String parentId = bundlePom.getParentId();
        if( null != parentId )
        {
            try
            {
                // update the relative path, as it's probably changed
                DirUtils.updateLogicalParent( newBundleDir, parentId );
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to update logical parent " + parentId );
            }
        }

        return newModulesPom;
    }

    /**
     * Transfer the bundle's module from the old modules POM to the new one
     * 
     * @param oldBundleDir previous location of the bundle
     * @param newModulesPom modules POM directly above the new bundle directory
     */
    private void transferBundleOwnership( File oldBundleDir, Pom newModulesPom )
    {
        String moduleName = oldBundleDir.getName();

        try
        {
            // add first, in case of problems later
            newModulesPom.addModule( moduleName, true );
            newModulesPom.write();

            // open POM above the old directory, and remove the bundle module
            Pom oldModulesPom = PomUtils.readPom( oldBundleDir.getParentFile() );
            oldModulesPom.removeModule( moduleName );
            oldModulesPom.write();
        }
        catch( IOException e )
        {
            getLog().warn( "Problem transferring bundle ownership" );
        }
    }

    /**
     * Update the bundle POM and any references to the bundle in the complete Maven project tree
     * 
     * @param bundlePom bundle POM from the new directory
     * @param newGroupId groupId based on the new location
     */
    private void changeBundleGroup( Pom bundlePom, String newGroupId )
    {
        try
        {
            // update bundle first, in case of failure
            String oldGroupId = bundlePom.getGroupId();
            bundlePom.setGroupId( newGroupId );
            bundlePom.write();

            for( Iterator i = new PomIterator( bundlePom.getBasedir() ); i.hasNext(); )
            {
                Pom pom = (Pom) i.next();
                if( !pom.equals( bundlePom ) )
                {
                    updateBundleReferences( pom, oldGroupId, bundlePom.getGroupId(), bundlePom.getArtifactId() );
                }
            }
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to update bundle groupId to " + newGroupId );
        }
    }

    /**
     * Update any references (ie. dependencies, dependencyManagement) to use the new bundle group id
     * 
     * @param pom a Maven POM in the project tree
     * @param oldGroupId old bundle group id
     * @param newGroupId new bundle group id
     * @param artifactId bundle artifact id
     */
    private void updateBundleReferences( Pom pom, String oldGroupId, String newGroupId, String artifactId )
    {
        Dependency dependency = new Dependency();
        dependency.setGroupId( oldGroupId );
        dependency.setArtifactId( artifactId );

        if( pom.updateDependencyGroup( dependency, newGroupId ) )
        {
            getLog().info( "Updating " + newGroupId + ':' + artifactId + " in " + pom );

            try
            {
                pom.write();
            }
            catch( IOException e )
            {
                getLog().warn( "Problem writing Maven POM: " + pom.getFile() );
            }
        }
    }
}
