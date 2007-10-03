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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.ExistingElementException;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal wrap-jar
 */
public class OSGiWrapperArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * @component
     */
    private ArtifactFactory m_artifactFactory;

    /**
     * @component
     */
    private ArtifactResolver m_resolver;

    /**
     * @parameter alias="remoteRepositories" expression="${remoteRepositories}"
     *            default-value="${project.remoteArtifactRepositories}"
     */
    private List m_remoteRepos;

    /**
     * @parameter alias="localRepository" expression="${localRepository}"
     * @required
     */
    private ArtifactRepository m_localRepo;

    /**
     * @component
     */
    private MavenProjectBuilder m_projectBuilder;

    /**
     * @parameter alias="parentId" expression="${parentId}" default-value="wrapper-bundle-settings"
     */
    private String m_parentId;

    /**
     * @parameter alias="groupId" expression="${groupId}"
     * @required
     */
    private String m_groupId;

    /**
     * @parameter alias="artifactId" expression="${artifactId}"
     * @required
     */
    private String m_artifactId;

    /**
     * @parameter alias="version" expression="${version}"
     * @required
     */
    private String m_version;

    /**
     * @parameter alias="wrapTransitive" expression="${wrapTransitive}"
     */
    private boolean m_wrapTransitive;

    /**
     * @parameter alias="wrapOptional" expression="${wrapOptional}"
     */
    private boolean m_wrapOptional;

    /**
     * @parameter alias="embedTransitive" expression="${embedTransitive}"
     */
    private boolean m_embedTransitive;

    /**
     * @parameter alias="includeResource" expression="${includeResource}"
     */
    private String m_includeResource;

    /**
     * @parameter alias="importPackage" expression="${importPackage}"
     */
    private String m_importPackage;

    /**
     * @parameter alias="exportContents" expression="${exportContents}"
     */
    private String m_exportContents;

    /**
     * @parameter alias="requireBundle" expression="${requireBundle}"
     */
    private String m_requireBundle;

    /**
     * @parameter alias="dynamicImportPackage" expression="${dynamicImportPackage}"
     */
    private String m_dynamicImportPackage;

    /**
     * @parameter alias="testMetadata" expression="${testMetadata}" default-value="true"
     */
    private boolean m_testMetadata;

    /**
     * @parameter alias="addVersion" expression="${addVersion}"
     */
    private boolean m_addVersion;

    private List m_wrappingIds;

    private Set m_visitedIds;

    String getParentId()
    {
        return m_parentId;
    }

    void updateExtensionFields()
    {
        if( null == m_wrappingIds )
        {
            String rootId = m_groupId + ':' + m_artifactId + ':' + m_version;

            m_wrappingIds = new ArrayList();
            m_visitedIds = new HashSet();

            m_wrappingIds.add( rootId );
            m_visitedIds.add( rootId );
        }

        String id = (String) m_wrappingIds.remove( 0 );
        String[] fields = id.split( ":" );

        m_groupId = fields[0];
        m_artifactId = fields[1];
        m_version = fields[2];

        getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );

        String combinedName = getCompactName( m_groupId, m_artifactId );

        getArchetypeMojo().setField( "groupId", getInternalGroupId() );
        getArchetypeMojo().setField( "packageName", calculateGroupMarker( m_groupId, m_artifactId, combinedName ) );

        if( m_addVersion )
        {
            getArchetypeMojo().setField( "artifactId", combinedName + '-' + m_version );
            getArchetypeMojo().setField( "version", '+' + m_version );
        }
        else
        {
            getArchetypeMojo().setField( "artifactId", combinedName );
            getArchetypeMojo().setField( "version", '=' + m_version );
        }
    }

    void postProcess()
        throws MojoExecutionException
    {
        super.postProcess();

        if( m_wrapTransitive )
        {
            try
            {
                wrapTransitiveArtifacts();
                m_embedTransitive = false;
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Problem creating wrapper POM: " + getPomFile() );
            }
        }

        try
        {
            updateBndInstructions();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem updating Bnd instructions" );
        }
    }

    private void updateBndInstructions()
        throws IOException,
        MojoExecutionException
    {
        BndFile bndFile = BndFileUtils.readBndFile( getPomFile().getParentFile() );

        if( m_embedTransitive )
        {
            bndFile.setInstruction( "Embed-Transitive", "true", canOverwrite() );
        }
        if( m_includeResource != null )
        {
            bndFile.setInstruction( "Include-Resource", m_includeResource, canOverwrite() );
        }
        if( m_importPackage != null )
        {
            bndFile.setInstruction( "Import-Package", m_importPackage, canOverwrite() );
        }
        if( m_exportContents != null )
        {
            bndFile.setInstruction( "-exportcontents", m_exportContents, canOverwrite() );
        }
        if( m_requireBundle != null )
        {
            bndFile.setInstruction( "Require-Bundle", m_requireBundle, canOverwrite() );
        }
        if( m_dynamicImportPackage != null )
        {
            bndFile.setInstruction( "DynamicImport-Package", m_dynamicImportPackage, canOverwrite() );
        }

        bndFile.write();
    }

    boolean createMoreArtifacts()
    {
        return !m_wrappingIds.isEmpty();
    }

    void wrapTransitiveArtifacts()
        throws IOException,
        MojoExecutionException
    {
        Pom thisPom;

        thisPom = PomUtils.readPom( getPomFile() );

        List dependencyPoms = new ArrayList();
        dependencyPoms.add( m_artifactFactory.createProjectArtifact( m_groupId, m_artifactId, m_version ) );

        while( !dependencyPoms.isEmpty() )
        {
            Artifact pomArtifact = (Artifact) dependencyPoms.remove( 0 );

            Set artifacts;
            try
            {
                MavenProject p = m_projectBuilder.buildFromRepository( pomArtifact, m_remoteRepos, m_localRepo );
                artifacts = p.createArtifacts( m_artifactFactory, null, null );
            }
            catch( ProjectBuildingException e )
            {
                artifacts = Collections.EMPTY_SET;
            }
            catch( InvalidDependencyVersionException e )
            {
                artifacts = Collections.EMPTY_SET;
            }

            processDependencies( thisPom, dependencyPoms, artifacts );
        }

        thisPom.write();
    }

    private void processDependencies( Pom thisPom, List dependencyPoms, Set artifacts )
        throws ExistingElementException
    {
        for( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            String scope = artifact.getScope();

            if( !Artifact.SCOPE_SYSTEM.equals( scope ) && !Artifact.SCOPE_TEST.equals( scope )
                && ( m_wrapOptional || !artifact.isOptional() ) )
            {
                scheduleWrapping( thisPom, dependencyPoms, artifact );
            }
        }
    }

    private void scheduleWrapping( Pom thisPom, List dependencyPoms, Artifact artifact )
        throws ExistingElementException
    {
        String id = getCandidateId( artifact );

        if( m_visitedIds.add( id ) )
        {
            if( "pom".equals( artifact.getType() ) )
            {
                dependencyPoms.add( artifact );
            }
            else if( PomUtils.isBundleArtifact( artifact, m_resolver, m_remoteRepos, m_localRepo, m_testMetadata ) )
            {
                thisPom.addDependency( getBundleDependency( artifact ), true );
            }
            else
            {
                m_wrappingIds.add( id );

                thisPom.addDependency( getWrappedDependency( artifact ), true );
            }
        }
    }

    String getCandidateId( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + PomUtils.getMetaVersion( artifact );
    }

    Dependency getBundleDependency( Artifact artifact )
    {
        Dependency dependency = new Dependency();

        dependency.setGroupId( artifact.getGroupId() );
        dependency.setArtifactId( artifact.getArtifactId() );
        dependency.setVersion( PomUtils.getMetaVersion( artifact ) );
        dependency.setScope( Artifact.SCOPE_PROVIDED );

        return dependency;
    }

    Dependency getWrappedDependency( Artifact artifact )
    {
        Dependency dependency = new Dependency();

        dependency.setGroupId( getInternalGroupId() );
        String wrappedArtifactId = getCompactName( artifact.getGroupId(), artifact.getArtifactId() );
        String metaVersion = PomUtils.getMetaVersion( artifact );

        if( m_addVersion )
        {
            dependency.setArtifactId( wrappedArtifactId + '-' + metaVersion );
            dependency.setVersion( getProjectVersion() );
        }
        else
        {
            dependency.setArtifactId( wrappedArtifactId );
            dependency.setVersion( metaVersion + "-001" );
        }

        dependency.setScope( Artifact.SCOPE_PROVIDED );

        return dependency;
    }
}
