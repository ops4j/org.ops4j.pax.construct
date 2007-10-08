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
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.ops4j.pax.construct.util.PomIterator;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Remove a bundle project and any references to it in the project tree, updating POMs as necessary
 * 
 * @goal remove-bundle
 * @aggregator true
 */
public class RemoveBundleMojo extends AbstractMojo
{
    /**
     * A directory in the same project tree.
     * 
     * @parameter alias="baseDirectory" expression="${baseDirectory}" default-value="${project.basedir}"
     */
    private File m_baseDirectory;

    /**
     * The artifactId or symbolic-name of the bundle.
     * 
     * @parameter alias="bundleName" expression="${bundleName}"
     * @required
     */
    private String m_bundleName;

    /**
     * When true, repair any references to the removed bundle.
     * 
     * @parameter alias="repair" expression="${repair}" default-value="true"
     */
    private boolean m_repair;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        Pom bundlePom = MoveBundleMojo.locateBundlePom( m_baseDirectory, m_bundleName );

        // protect against removing the wrong directory
        if( "pom".equals( bundlePom.getPackaging() ) )
        {
            throw new MojoExecutionException( "Ignoring multi-module project " + m_bundleName );
        }

        if( m_repair )
        {
            for( Iterator i = new PomIterator( m_baseDirectory ); i.hasNext(); )
            {
                Pom pom = (Pom) i.next();
                if( !pom.equals( bundlePom ) )
                {
                    removeBundleReferences( pom, bundlePom );
                }
            }
        }

        // now do the actual removal work
        dropBundleOwnership( bundlePom );
        removeBundleFiles( bundlePom );
    }

    /**
     * Remove the bundle's module from the POM directly above it
     * 
     * @param bundlePom the Maven POM for the bundle
     */
    void dropBundleOwnership( Pom bundlePom )
    {
        String moduleName = bundlePom.getBasedir().getName();

        try
        {
            Pom modulesPom = bundlePom.getContainingPom();
            modulesPom.removeModule( moduleName );
            modulesPom.write();
        }
        catch( IOException e )
        {
            getLog().warn( "Module " + moduleName + " not found in containing POM" );
        }
    }

    /**
     * Remove all files belonging to the bundle
     * 
     * @param bundlePom the Maven POM for the bundle
     */
    void removeBundleFiles( Pom bundlePom )
    {
        getLog().info( "Removing " + bundlePom );
        File bundleDir = bundlePom.getBasedir();

        try
        {
            FileSet bundleFiles = new FileSet();

            bundleFiles.setDirectory( bundleDir.getParent() );
            bundleFiles.addInclude( bundleDir.getName() );

            new FileSetManager( getLog(), false ).delete( bundleFiles );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to remove directory " + bundleDir, e );
        }
    }

    /**
     * Remove any references (ie. dependencies, dependencyManagement) to the bundle artifact
     * 
     * @param pom a Maven POM in the project tree
     * @param bundlePom the Maven POM for the bundle
     */
    void removeBundleReferences( Pom pom, Pom bundlePom )
    {
        Dependency dependency = new Dependency();
        dependency.setGroupId( bundlePom.getGroupId() );
        dependency.setArtifactId( bundlePom.getArtifactId() );

        if( pom.removeDependency( dependency ) )
        {
            getLog().info( "Removing " + bundlePom + " from " + pom );

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
