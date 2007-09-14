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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @goal import-bundle
 * @aggregator true
 */
public class ImportBundleMojo extends AbstractMojo
{
    /**
     * @component
     */
    ArtifactFactory artifactFactory;

    /**
     * @component
     */
    ArtifactResolver artifactResolver;

    /**
     * @parameter expression="${remoteRepositories}" default-value="${project.remoteArtifactRepositories}"
     */
    List remoteRepositories;

    /**
     * @parameter expression="${localRepository}"
     * @required
     */
    ArtifactRepository localRepository;

    /**
     * @component
     */
    MavenProjectBuilder projectBuilder;

    /**
     * @parameter expression="${groupId}"
     * @required
     */
    String groupId;

    /**
     * @parameter expression="${artifactId}"
     * @required
     */
    String artifactId;

    /**
     * @parameter expression="${version}"
     * @required
     */
    String version;

    /**
     * @parameter expression="${provisionId}" default-value="provision"
     */
    String provisionId;

    /**
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    File targetDirectory;

    /**
     * @parameter expression="${excludeTransitive}"
     */
    boolean excludeTransitive;

    /**
     * @parameter expression="${deploy}" default-value="true"
     */
    boolean deploy;

    /**
     * @parameter expression="${overwrite}" default-value="true"
     */
    boolean overwrite;

    Pom m_provisionPom;
    Pom m_targetPom;

    List m_candidateIds;
    Set m_visitedIds;

    public void execute()
        throws MojoExecutionException
    {
        m_provisionPom = DirUtils.findPom( targetDirectory, provisionId );
        m_targetPom = PomUtils.readPom( targetDirectory );

        String rootId = groupId + ':' + artifactId + ':' + version;

        m_candidateIds = new ArrayList();
        m_visitedIds = new HashSet();

        m_candidateIds.add( rootId );
        m_visitedIds.add( rootId );

        while( !m_candidateIds.isEmpty() )
        {
            String id = (String) m_candidateIds.remove( 0 );
            String[] fields = id.split( ":" );

            Artifact pom = artifactFactory.createProjectArtifact( fields[0], fields[1], fields[2] );

            try
            {
                MavenProject project = projectBuilder.buildFromRepository( pom, remoteRepositories, localRepository );
                if( !"pom".equals( project.getPackaging() ) )
                {
                    if( PomUtils.isBundleProject( project ) || hasBundleMetadata( project ) )
                    {
                        importBundle( project );
                    }
                    else
                    {
                        continue;
                    }

                    if( excludeTransitive )
                    {
                        return;
                    }
                }

                Set artifacts = project.createArtifacts( artifactFactory, Artifact.SCOPE_PROVIDED, null );
                for( Iterator i = artifacts.iterator(); i.hasNext(); )
                {
                    Artifact artifact = (Artifact) i.next();
                    id = getCandidateId( artifact );
                    String scope = artifact.getScope();

                    boolean inScope = Artifact.SCOPE_PROVIDED.equals( scope );

                    if( m_visitedIds.add( id ) && inScope && !artifact.isOptional() )
                    {
                        m_candidateIds.add( id );
                    }
                }
            }
            catch( Exception e )
            {
                getLog().warn( e.getMessage() );
            }
        }
    }

    String getCandidateId( Artifact artifact )
    {
        String symbolicVersion;

        try
        {
            // use symbolic version if available (ie. 1.0.0-SNAPSHOT)
            symbolicVersion = artifact.getSelectedVersion().toString();
        }
        catch( Exception e )
        {
            symbolicVersion = artifact.getVersion();
        }

        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + symbolicVersion;
    }

    boolean hasBundleMetadata( MavenProject project )
    {
        try
        {
            Artifact bundleArtifact = project.getArtifact();
            if( bundleArtifact.getFile() == null || !bundleArtifact.getFile().exists() )
            {
                artifactResolver.resolve( bundleArtifact, remoteRepositories, localRepository );
            }

            JarFile jarFile = new JarFile( bundleArtifact.getFile() );
            Manifest manifest = jarFile.getManifest();

            Attributes mainAttributes = manifest.getMainAttributes();
            return null != mainAttributes.getValue( "Bundle-ManifestVersion" );
        }
        catch( Exception e )
        {
            getLog().warn( e.getMessage() );
            return false;
        }
    }

    void importBundle( MavenProject project )
    {
        Dependency dependency = new Dependency();

        dependency.setGroupId( project.getGroupId() );
        dependency.setArtifactId( project.getArtifactId() );
        dependency.setVersion( project.getVersion() );
        dependency.setScope( Artifact.SCOPE_PROVIDED );

        if( deploy )
        {
            // non-optional, must be deployed
            dependency.setOptional( false );
        }
        else
        {
            // optional (ie. framework package)
            dependency.setOptional( true );
        }

        boolean localProject = m_targetPom != null && m_targetPom.getGroupId().equals( dependency.getGroupId() );

        if( m_provisionPom != null && !localProject )
        {
            getLog().info( "Importing " + project.getName() + " to " + m_provisionPom.getId() );

            m_provisionPom.addDependency( dependency, overwrite );
            m_provisionPom.write();
        }

        if( m_targetPom != null && m_targetPom.isBundleProject() )
        {
            getLog().info( "Adding " + project.getName() + " as a dependency to " + m_targetPom.getId() );

            m_targetPom.addDependency( dependency, overwrite );
            m_targetPom.write();
        }
    }
}
