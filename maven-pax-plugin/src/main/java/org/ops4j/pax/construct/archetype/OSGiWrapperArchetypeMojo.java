package org.ops4j.pax.construct.archetype;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;

/**
 * @goal archetype:create=wrap-jar
 */
public class OSGiWrapperArchetypeMojo extends AbstractPaxArchetypeMojo
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
    List remoteRepos;

    /**
     * @parameter expression="${localRepository}"
     * @required
     */
    ArtifactRepository localRepo;

    /**
     * @component
     */
    MavenProjectBuilder projectBuilder;

    /**
     * @parameter expression="${parentId}" default-value="wrapper-bundle-settings"
     */
    String parentId;

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
     * @parameter expression="${wrapTransitive}"
     */
    boolean wrapTransitive;

    /**
     * @parameter expression="${wrapOptional}"
     */
    boolean wrapOptional;

    /**
     * @parameter expression="${embedTransitive}"
     */
    boolean embedTransitive;

    /**
     * @parameter expression="${includeResource}"
     */
    String includeResource;

    /**
     * @parameter expression="${importPackage}"
     */
    String importPackage;

    /**
     * @parameter expression="${exportContents}"
     */
    String exportContents;

    /**
     * @parameter expression="${requireBundle}"
     */
    String requireBundle;

    /**
     * @parameter expression="${dynamicImportPackage}"
     */
    String dynamicImportPackage;

    /**
     * @parameter expression="${addVersion}"
     */
    boolean addVersion;

    List m_candidateIds;
    Set m_visitedIds;

    void updateExtensionFields()
    {
        if( null == m_candidateIds )
        {
            String rootId = groupId + ':' + artifactId + ':' + version;

            m_candidateIds = new ArrayList();
            m_visitedIds = new HashSet();

            m_candidateIds.add( rootId );
            m_visitedIds.add( rootId );
        }

        if( wrapTransitive )
        {
            scheduleTransitiveArtifacts();
            embedTransitive = false;
        }

        String id = (String) m_candidateIds.remove( 0 );
        String[] fields = id.split( ":" );

        groupId = fields[0];
        artifactId = fields[1];
        version = fields[2];

        m_mojo.setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );
        String compoundWrapperName = getCompactName( groupId, artifactId );

        if( addVersion )
        {
            compoundWrapperName += '-' + version;
        }

        m_mojo.setField( "groupId", getCompactName( project.getGroupId(), project.getArtifactId() ) );
        m_mojo.setField( "artifactId", compoundWrapperName );
        m_mojo.setField( "version", (addVersion ? '+' : ' ') + version );

        m_mojo.setField( "packageName", calculateGroupMarker( groupId, artifactId ) );
    }

    void postProcess()
        throws MojoExecutionException
    {
        super.postProcess();

        BndFile bndFile = BndFileUtils.readBndFile( m_pomFile.getParentFile() );

        if( embedTransitive )
            bndFile.setInstruction( "Embed-Transitive", "true", overwrite );

        if( includeResource != null )
            bndFile.setInstruction( "Include-Resource", includeResource, overwrite );

        if( importPackage != null )
            bndFile.setInstruction( "Import-Package", importPackage, overwrite );

        if( exportContents != null )
            bndFile.setInstruction( "-exportcontents", exportContents, overwrite );

        if( requireBundle != null )
            bndFile.setInstruction( "Require-Bundle", requireBundle, overwrite );

        if( dynamicImportPackage != null )
            bndFile.setInstruction( "DynamicImport-Package", dynamicImportPackage, overwrite );

        bndFile.write();
    }

    String getParentId()
    {
        return parentId;
    }

    boolean createMoreArtifacts()
    {
        return !m_candidateIds.isEmpty();
    }

    void scheduleTransitiveArtifacts()
    {
        Artifact pom = artifactFactory.createProjectArtifact( groupId, artifactId, version );

        try
        {
            MavenProject project = projectBuilder.buildFromRepository( pom, remoteRepos, localRepo );

            Set artifacts = project.createArtifacts( artifactFactory, null, null );
            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                String id = getCandidateId( artifact );
                String scope = artifact.getScope();

                boolean inScope = true;
                if( scope != null )
                {
                    inScope = Artifact.SCOPE_COMPILE.equals( scope ) || Artifact.SCOPE_RUNTIME.equals( scope );
                }

                if( m_visitedIds.add( id ) && inScope && (wrapOptional || !artifact.isOptional()) )
                {
                    if( !"pom".equals( project.getPackaging() ) )
                    {
                        m_candidateIds.add( id );
                    }
                }
            }
        }
        catch( Exception e )
        {
            getLog().warn( e.getMessage() );
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
}
