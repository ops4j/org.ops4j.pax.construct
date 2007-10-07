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

        // go round entire project removing references / bundle files
        for( Iterator i = new PomIterator( m_baseDirectory ); i.hasNext(); )
        {
            Pom pom = (Pom) i.next();

            if( pom.equals( bundlePom ) )
            {
                removeBundleFiles( pom );
            }
            else
            {
                removeBundleReferences( pom, bundlePom );
            }
        }
    }

    /**
     * Remove the entire bundle directory
     */
    void removeBundleFiles( Pom pom )
    {
        getLog().info( "Removing " + pom );
        File bundleFolder = pom.getBasedir();

        try
        {
            FileSet bundleFiles = new FileSet();

            bundleFiles.setDirectory( bundleFolder.getParent() );
            bundleFiles.addInclude( bundleFolder.getName() );

            new FileSetManager( getLog(), false ).delete( bundleFiles );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to remove directory " + bundleFolder, e );
        }
    }

    /**
     * Remove references to the bundle, such as its module entry or any dependencies
     * 
     * @throws MojoExecutionException
     */
    void removeBundleReferences( Pom pom, Pom bundlePom )
        throws MojoExecutionException
    {
        boolean needsUpdate = false;

        Dependency dependency = new Dependency();
        dependency.setGroupId( bundlePom.getGroupId() );
        dependency.setArtifactId( bundlePom.getArtifactId() );

        File bundleFolder = pom.getBasedir();

        needsUpdate = needsUpdate || pom.removeDependency( dependency );
        needsUpdate = needsUpdate || pom.removeModule( bundleFolder.getName() );

        if( needsUpdate )
        {
            getLog().info( "Removing " + bundlePom + " from " + pom );

            try
            {
                pom.write();
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Problem writing Maven POM: " + pom.getFile() );
            }
        }
    }
}
