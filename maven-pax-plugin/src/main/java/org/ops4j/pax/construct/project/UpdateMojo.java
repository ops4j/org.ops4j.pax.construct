package org.ops4j.pax.construct.project;

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
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.ops4j.pax.construct.archetype.AbstractPaxArchetypeMojo;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Updates a Pax-Construct project or (when run in the scripts directory) the installed scripts to the latest version
 * 
 * <code><pre>
 *   mvn pax:update [-Dversion=...]
 * </pre></code>
 * 
 * @goal update
 * @aggregator true
 * 
 * @requiresProject false
 */
public class UpdateMojo extends AbstractMojo
{
    /**
     * Component factory for Maven artifacts
     * 
     * @component
     */
    private ArtifactFactory m_factory;

    /**
     * Component for resolving Maven artifacts
     * 
     * @component
     */
    private ArtifactResolver m_resolver;

    /**
     * Component for resolving Maven metadata
     * 
     * @component
     */
    private ArtifactMetadataSource m_source;

    /**
     * Component factory for Maven repositories.
     * 
     * @component
     */
    private ArtifactRepositoryFactory m_repoFactory;

    /**
     * @component roleHint="default"
     */
    private ArtifactRepositoryLayout m_defaultLayout;

    /**
     * List of remote Maven repositories for the containing project.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List m_remoteRepos;

    /**
     * The local Maven repository for the containing project.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository m_localRepo;

    /**
     * The directory containing the POM to be updated.
     * 
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File targetDirectory;

    /**
     * The version of Pax-Construct to update to.
     * 
     * @parameter expression="${version}"
     */
    private String version;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        String groupId = AbstractPaxArchetypeMojo.PAX_CONSTRUCT_GROUP_ID;

        addPaxRepositories();

        // find latest release if no explicit version is given
        Artifact scripts = m_factory.createBuildArtifact( groupId, "scripts", version, "zip" );
        if( PomUtils.needReleaseVersion( version ) )
        {
            version = PomUtils.getReleaseVersion( scripts, m_source, m_remoteRepos, m_localRepo );
            scripts.selectVersion( version );
        }

        if( new File( targetDirectory, "pax-bootstrap-pom.xml" ).exists() )
        {
            updatePaxConstructScripts( scripts );
        }
        else if( new File( targetDirectory, "pom.xml" ).exists() )
        {
            updatePaxConstructProject();
        }
        else
        {
            getLog().warn( "pax-update should be run from the scripts directory, or from a Pax-Construct project" );
        }
    }

    /**
     * Add OPS4J standard and snapshot repositories to the remote repository list
     */
    private void addPaxRepositories()
    {
        m_remoteRepos.clear();

        ArtifactRepositoryPolicy enabled = new ArtifactRepositoryPolicy( true,
            ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_FAIL );
        ArtifactRepositoryPolicy disabled = new ArtifactRepositoryPolicy( false, null, null );

        m_remoteRepos.add( m_repoFactory.createArtifactRepository( AbstractPaxArchetypeMojo.OPS4J_STANDARD_REPO_ID,
            AbstractPaxArchetypeMojo.OPS4J_STANDARD_REPO_URL, m_defaultLayout, disabled, enabled ) );
        m_remoteRepos.add( m_repoFactory.createArtifactRepository( AbstractPaxArchetypeMojo.OPS4J_SNAPSHOT_REPO_ID,
            AbstractPaxArchetypeMojo.OPS4J_SNAPSHOT_REPO_URL, m_defaultLayout, enabled, disabled ) );
    }

    /**
     * Update each script in turn from the zipfile stored in the repository
     * 
     * @param scripts maven artifact pointing to the scripts
     * @throws MojoExecutionException
     */
    private void updatePaxConstructScripts( Artifact scripts )
        throws MojoExecutionException
    {
        try
        {
            // download to local repository if not already there
            m_resolver.resolve( scripts, m_remoteRepos, m_localRepo );
            getLog().info( "Updating scripts to version " + version );

            ZipFile zip = new ZipFile( scripts.getFile() );
            for( Enumeration i = zip.entries(); i.hasMoreElements(); )
            {
                ZipEntry entry = (ZipEntry) i.nextElement();

                // only need shallow copying (no nested folders)
                String name = new File( entry.getName() ).getName();
                String path = new File( targetDirectory, name ).getPath();

                if( !entry.isDirectory() )
                {
                    // overwrite using data stored in the zip
                    InputStream in = zip.getInputStream( entry );
                    String data = IOUtil.toString( in );
                    FileUtils.fileWrite( path, data );

                    getLog().info( " => " + path );
                }
            }
        }
        catch( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Unable to resolve scripts attachment", e );
        }
        catch( ArtifactNotFoundException e )
        {
            throw new MojoExecutionException( "Unable to find scripts attachment", e );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem updating local scripts", e );
        }
    }

    /**
     * Update existing Pax-Construct project to use the latest release of the plugin
     * 
     * @throws MojoExecutionException
     */
    private void updatePaxConstructProject()
        throws MojoExecutionException
    {
        try
        {
            // only need to update in one place...
            Pom pom = PomUtils.readPom( targetDirectory );
            boolean foundPlugin = pom.updatePluginVersion( "org.ops4j", "maven-pax-plugin", version );
            if( foundPlugin )
            {
                getLog().info( "Updating Pax-Construct project to version " + version );

                pom.write();

                getLog().info( " => " + pom.getFile() );
            }
            else
            {
                getLog().warn( "Unable to find reference to org.ops4j:maven-pax-plugin" );
                getLog().warn( "you may need to convert this project using 'pax-clone'" );
            }
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem reading Maven POM: " + targetDirectory );
        }
    }
}
