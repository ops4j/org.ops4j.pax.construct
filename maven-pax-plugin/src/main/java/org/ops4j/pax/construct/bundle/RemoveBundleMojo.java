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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Remove a bundle project and any references to it in the project tree, updating POMs as necessary
 * 
 * @goal remove-bundle
 */
public class RemoveBundleMojo extends AbstractMojo
{
    /**
     * The POM to be removed.
     */
    private static Pom m_bundlePom;

    /**
     * The current Maven project.
     * 
     * @parameter default-value="${project}"
     * @required
     */
    private MavenProject m_project;

    /**
     * The artifactId or symbolic-name of the bundle.
     * 
     * @parameter expression="${bundleName}"
     * @required
     */
    private String m_bundleName;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        // only need to search once
        if( null == m_bundlePom )
        {
            m_bundlePom = MoveBundleMojo.locateBundlePom( m_project.getBasedir(), m_bundleName );

            // protect against removing the wrong directory
            if( "pom".equals( m_bundlePom.getPackaging() ) )
            {
                throw new MojoExecutionException( "Ignoring multi-module project " + m_bundleName );
            }
        }

        // have we reached the actual bundle project yet?
        if( m_project.getId().equals( m_bundlePom.getId() ) )
        {
            removeBundleFiles();
        }
        else
        {
            removeBundleReferences();
        }
    }

    /**
     * Remove the entire bundle directory
     */
    void removeBundleFiles()
    {
        getLog().info( "Removing " + m_bundlePom.getId() );

        try
        {
            FileSet bundleFiles = new FileSet();

            File bundleFolder = m_bundlePom.getBasedir();
            bundleFiles.setDirectory( bundleFolder.getParent() );
            bundleFiles.addInclude( bundleFolder.getName() );

            new FileSetManager( getLog(), false ).delete( bundleFiles );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to remove the requested bundle", e );
        }
    }

    /**
     * Remove references to the bundle, such as its module entry or any dependencies
     * 
     * @throws MojoExecutionException
     */
    void removeBundleReferences()
        throws MojoExecutionException
    {
        boolean needsUpdate = false;

        Pom pom;
        try
        {
            pom = PomUtils.readPom( m_project.getFile() );
        }
        catch( IOException e )
        {
            getLog().warn( "Problem reading Maven POM: " + m_project.getFile(), e );
            return; // carry on
        }

        Dependency dependency = new Dependency();
        dependency.setGroupId( m_bundlePom.getGroupId() );
        dependency.setArtifactId( m_bundlePom.getArtifactId() );

        needsUpdate = needsUpdate || pom.removeDependency( dependency );
        needsUpdate = needsUpdate || pom.removeModule( m_bundlePom.getBasedir().getName() );

        if( needsUpdate )
        {
            getLog().info( "Removing " + m_bundlePom.getId() + " from " + pom.getId() );

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
