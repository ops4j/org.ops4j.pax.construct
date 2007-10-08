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
 * Create a new wrapper project inside an existing Pax-Construct OSGi project
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal wrap-jar
 */
public class OSGiWrapperArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * Component factory for Maven artifacts
     * 
     * @component
     */
    private ArtifactFactory m_artifactFactory;

    /**
     * Component for resolving Maven artifacts
     * 
     * @component
     */
    private ArtifactResolver m_resolver;

    /**
     * Component factory for Maven projects
     * 
     * @component
     */
    private MavenProjectBuilder m_projectBuilder;

    /**
     * List of remote Maven repositories for the containing project.
     * 
     * @parameter alias="remoteRepositories" expression="${remoteRepositories}"
     *            default-value="${project.remoteArtifactRepositories}"
     */
    private List m_remoteRepos;

    /**
     * The local Maven repository for the containing project.
     * 
     * @parameter alias="localRepository" expression="${localRepository}"
     * @required
     */
    private ArtifactRepository m_localRepo;

    /**
     * The logical parent of the new project (use artifactId or groupId:artifactId).
     * 
     * @parameter alias="parentId" expression="${parentId}" default-value="wrapper-bundle-settings"
     */
    private String m_parentId;

    /**
     * The groupId of the artifact to be wrapped.
     * 
     * @parameter alias="groupId" expression="${groupId}"
     * @required
     */
    private String m_groupId;

    /**
     * The artifactId of the artifact to be wrapped.
     * 
     * @parameter alias="artifactId" expression="${artifactId}"
     * @required
     */
    private String m_artifactId;

    /**
     * The version of the artifact to be wrapped.
     * 
     * @parameter alias="version" expression="${version}"
     * @required
     */
    private String m_version;

    /**
     * When true, create new wrapper projects for any dependencies.
     * 
     * @parameter alias="wrapTransitive" expression="${wrapTransitive}"
     */
    private boolean m_wrapTransitive;

    /**
     * When true, create new wrapper projects for optional dependencies.
     * 
     * @parameter alias="wrapOptional" expression="${wrapOptional}"
     */
    private boolean m_wrapOptional;

    /**
     * When true, embed any dependencies inside the wrapper bundle.
     * 
     * @parameter alias="embedTransitive" expression="${embedTransitive}"
     */
    private boolean m_embedTransitive;

    /**
     * The Include-Resource directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter alias="includeResource" expression="${includeResource}"
     */
    private String m_includeResource;

    /**
     * The Import-Package directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter alias="importPackage" expression="${importPackage}"
     */
    private String m_importPackage;

    /**
     * The -exportcontents directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter alias="exportContents" expression="${exportContents}"
     */
    private String m_exportContents;

    /**
     * The RequireBundle directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter alias="requireBundle" expression="${requireBundle}"
     */
    private String m_requireBundle;

    /**
     * The DynamicImport-Package directive, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter alias="dynamicImportPackage" expression="${dynamicImportPackage}"
     */
    private String m_dynamicImportPackage;

    /**
     * When true, check dependency artifacts for OSGi metadata before wrapping them.
     * 
     * @parameter alias="testMetadata" expression="${testMetadata}" default-value="true"
     */
    private boolean m_testMetadata;

    /**
     * When true, add the wrapped artifact version to the project name.
     * 
     * @parameter alias="addVersion" expression="${addVersion}"
     */
    private boolean m_addVersion;

    /**
     * A list of artifacts (groupId:artifactId:version) to be wrapped
     */
    private List m_wrappingIds;

    /**
     * A list of artifacts (groupId:artifactId:version) that have already been processed.
     */
    private Set m_visitedIds;

    /**
     * {@inheritDoc}
     */
    String getParentId()
    {
        return m_parentId;
    }

    /**
     * {@inheritDoc}
     */
    void updateExtensionFields()
    {
        if( null == m_wrappingIds )
        {
            // only need to set these fields once
            getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );
            getArchetypeMojo().setField( "groupId", getInternalGroupId() );

            // bootstrap with the initial wrapper artifact
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

        /*
         * This is a little trick to get the archetype mojo to create the wrapper project with a compound name - based
         * on the groupId and artifactId - while still allowing the archetype files to use the groupId and artifactId.
         * 
         * See the velocity macros in: maven-archetype-osgi-wrapper/src/main/resources/archetype-resources/pom.xml
         */
        String compoundWrapperId = getCompoundId( m_groupId, m_artifactId );
        String compoundMarker = getCompoundMarker( m_groupId, m_artifactId, compoundWrapperId );

        /*
         * Provide support for wrapping different versions of an artifact: the versioning can either be done in the POM
         * using a project version similar to "2.1-001" where 001 is the local edition of the POM, or by appending the
         * version to the directory name - in which case the POM can simply follow the project version.
         * 
         * example 1, addVersion is false : asm/pom.xml <-- POM version 3.0-001
         * 
         * example 2, addVersion is true : asm-3.0/pom.xml <-- POM version 1.0-SNAPSHOT
         * 
         */
        if( m_addVersion )
        {
            getArchetypeMojo().setField( "artifactId", compoundWrapperId + '-' + m_version );
            getArchetypeMojo().setField( "version", '+' + m_version );
        }
        else
        {
            getArchetypeMojo().setField( "artifactId", compoundWrapperId );
            getArchetypeMojo().setField( "version", '!' + m_version );
        }

        getArchetypeMojo().setField( "packageName", compoundMarker );
    }

    /**
     * {@inheritDoc}
     */
    void postProcess()
        throws MojoExecutionException
    {
        // remove files, etc.
        super.postProcess();

        if( m_wrapTransitive )
        {
            try
            {
                // look for more to wrap
                wrapDirectDependencies();
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Problem updating Wrapper POM: " + getPomFile() );
            }

            // no need to embed now
            m_embedTransitive = false;
        }

        try
        {
            // apply custom instructions
            updateBndInstructions();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem updating Bnd instructions" );
        }
    }

    /**
     * Updates the default BND instructions with custom settings
     * 
     * @throws IOException
     * @throws MojoExecutionException
     */
    void updateBndInstructions()
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

    /**
     * {@inheritDoc}
     */
    boolean createMoreArtifacts()
    {
        return !m_wrappingIds.isEmpty();
    }

    /**
     * Attempt to wrap direct dependencies of the wrapped artifact - in turn they will call this method, and so on...
     * 
     * @throws IOException
     */
    void wrapDirectDependencies()
        throws IOException
    {
        /*
         * Use a local list to capture dependencies that are type POM, ie. collections of dependencies. These POM
         * artifacts don't require wrapping, so we must store and process them locally in the following loop...
         */
        List dependencyPoms = new ArrayList();

        // use the wrapped artifact's POM to kick things off
        dependencyPoms.add( m_artifactFactory.createProjectArtifact( m_groupId, m_artifactId, m_version ) );

        while( !dependencyPoms.isEmpty() )
        {
            Artifact pomArtifact = (Artifact) dependencyPoms.remove( 0 );

            try
            {
                // Standard Maven code to get direct dependencies for a given POM
                MavenProject p = m_projectBuilder.buildFromRepository( pomArtifact, m_remoteRepos, m_localRepo );
                Set artifacts = p.createArtifacts( m_artifactFactory, null, null );

                // look for new artifacts to wrap
                dependencyPoms.addAll( processDependencies( artifacts ) );
            }
            catch( ProjectBuildingException e )
            {
                getLog().warn( e );
            }
            catch( InvalidDependencyVersionException e )
            {
                getLog().warn( e );
            }
        }
    }

    /**
     * Look for more artifacts that need to be wrapped, ignoring those already wrapped or containing OSGi metadata
     * 
     * @param artifacts list of potential artifacts to be wrapped
     * @return list of POM artifacts discovered while processing
     * @throws IOException
     */
    List processDependencies( Set artifacts )
        throws IOException
    {
        // open current wrapper pom to add dependencies
        Pom thisPom = PomUtils.readPom( getPomFile() );

        List newDependencyPoms = new ArrayList();
        for( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            String candidateId = getCandidateId( artifact );

            if( isValidWrapperDependency( artifact ) && m_visitedIds.add( candidateId ) )
            {
                // process POM artifacts in parent loop...
                if( "pom".equals( artifact.getType() ) )
                {
                    newDependencyPoms.add( artifact );
                }
                // copy dependency to current wrapper pom (not all require wrapping)
                else if( addWrapperDependency( thisPom, artifact ) )
                {
                    m_wrappingIds.add( candidateId );
                }
            }
        }

        thisPom.write();

        return newDependencyPoms;
    }

    /**
     * @param artifact wrapper dependency
     * @return groupId:artifactId:metaVersion
     */
    String getCandidateId( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + PomUtils.getMetaVersion( artifact );
    }

    /**
     * @param artifact wrapper dependency
     * @return true if this artifact needs to be wrapped, otherwise false
     */
    boolean isValidWrapperDependency( Artifact artifact )
    {
        String scope = artifact.getScope();

        if( Artifact.SCOPE_SYSTEM.equals( scope ) || Artifact.SCOPE_TEST.equals( scope ) )
        {
            return false;
        }
        else if( !m_wrapOptional && artifact.isOptional() )
        {
            return false;
        }

        return true;
    }

    /**
     * Adds a dependency to the wrapped artifact, or the original if it doesn't require wrapping
     * 
     * @param pom wrapped Maven project
     * @param artifact wrapper dependency
     * @return true if the dependency should be wrapped, otherwise false
     */
    boolean addWrapperDependency( Pom pom, Artifact artifact )
    {
        try
        {
            if( PomUtils.isBundleArtifact( artifact, m_resolver, m_remoteRepos, m_localRepo, m_testMetadata ) )
            {
                pom.addDependency( getBundleDependency( artifact ), true );
                return false;
            }
            else
            {
                pom.addDependency( getWrappedDependency( artifact ), true );
                return true;
            }
        }
        catch( ExistingElementException e )
        {
            // this should never happen
            throw new RuntimeException( e );
        }
    }

    /**
     * @param artifact wrapper dependency
     * @return a provided dependency to the bundle
     */
    Dependency getBundleDependency( Artifact artifact )
    {
        Dependency dependency = new Dependency();

        dependency.setGroupId( artifact.getGroupId() );
        dependency.setArtifactId( artifact.getArtifactId() );
        dependency.setVersion( PomUtils.getMetaVersion( artifact ) );
        dependency.setScope( Artifact.SCOPE_PROVIDED );

        return dependency;
    }

    /**
     * @param artifact wrapper dependency
     * @return a provided dependency to the (future) wrapped artifact
     */
    Dependency getWrappedDependency( Artifact artifact )
    {
        Dependency dependency = new Dependency();

        dependency.setGroupId( getInternalGroupId() );
        String compoundWrapperId = getCompoundId( artifact.getGroupId(), artifact.getArtifactId() );
        String metaVersion = PomUtils.getMetaVersion( artifact );

        if( m_addVersion )
        {
            dependency.setArtifactId( compoundWrapperId + '-' + metaVersion );
            dependency.setVersion( getProjectVersion() );
        }
        else
        {
            dependency.setArtifactId( compoundWrapperId );
            dependency.setVersion( metaVersion + "-001" );
        }

        dependency.setScope( Artifact.SCOPE_PROVIDED );

        return dependency;
    }
}
