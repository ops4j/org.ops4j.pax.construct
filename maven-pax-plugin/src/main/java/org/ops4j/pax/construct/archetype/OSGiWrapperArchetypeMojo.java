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
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Create a new wrapper project inside an existing Pax-Construct OSGi project
 * 
 * <code><pre>
 *   mvn pax:wrap-jar [-DgroupId=...] -DartifactId=... [-Dversion=...]
 * </pre></code>
 * 
 * or create a standalone version which doesn't require an existing project
 * 
 * <code><pre>
 *   cd some-empty-folder
 *   mvn org.ops4j:maven-pax-plugin:wrap-jar ...etc...
 * </pre></code>
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal wrap-jar
 */
public class OSGiWrapperArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * Support POM versioning by using an additional version field
     */
    private static final String INITIAL_POM_VERSION = "001-SNAPSHOT";

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
     * Component factory for Maven projects
     * 
     * @component
     */
    private MavenProjectBuilder m_projectBuilder;

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
     * The logical parent of the new project (use artifactId or groupId:artifactId).
     * 
     * @parameter expression="${parentId}" default-value="wrapper-bundle-settings"
     */
    private String parentId;

    /**
     * The groupId for the bundle (generated from project details if empty).
     * 
     * @parameter expression="${bundleGroupId}"
     */
    private String bundleGroupId;

    /**
     * The symbolic-name for the bundle (generated from wrapped artifact if empty).
     * 
     * @parameter expression="${bundleName}"
     */
    private String bundleName;

    /**
     * The version for the bundle (generated from wrapped artifact if empty).
     * 
     * @parameter expression="${bundleVersion}"
     */
    private String bundleVersion;

    /**
     * The groupId of the artifact to be wrapped.
     * 
     * @parameter expression="${groupId}"
     */
    private String groupId;

    /**
     * The artifactId of the artifact to be wrapped.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the artifact to be wrapped.
     * 
     * @parameter expression="${version}"
     */
    private String version;

    /**
     * Comma-separated list of artifacts (use groupId:artifactId) to exclude from wrapping.
     * 
     * @parameter expression="${exclusions}"
     */
    private String exclusions;

    /**
     * When true, create new wrapper projects for any dependencies.
     * 
     * @parameter expression="${wrapTransitive}"
     */
    private boolean wrapTransitive;

    /**
     * When true, create new wrapper projects for optional dependencies.
     * 
     * @parameter expression="${wrapOptional}"
     */
    private boolean wrapOptional;

    /**
     * When true, embed any dependencies inside the wrapper bundle.
     * 
     * @parameter expression="${embedTransitive}"
     */
    private boolean embedTransitive;

    /**
     * The Include-Resource directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter expression="${includeResource}"
     */
    private String includeResource;

    /**
     * The Import-Package directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter expression="${importPackage}"
     */
    private String importPackage;

    /**
     * The -exportcontents directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter expression="${exportContents}"
     */
    private String exportContents;

    /**
     * The RequireBundle directive for this bundle, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter expression="${requireBundle}"
     */
    private String requireBundle;

    /**
     * The DynamicImport-Package directive, see <a href="http://aqute.biz/Code/Bnd#directives">Bnd docs</a>.
     * 
     * @parameter expression="${dynamicImport}"
     */
    private String dynamicImport;

    /**
     * When true, check dependency artifacts for OSGi metadata before wrapping them.
     * 
     * @parameter expression="${testMetadata}" default-value="true"
     */
    private boolean testMetadata;

    /**
     * When true, add the wrapped artifact version to the project name.
     * 
     * @parameter expression="${addVersion}"
     */
    private boolean addVersion;

    /**
     * A list of artifacts (groupId:artifactId:version) to be wrapped
     */
    private List m_candidateIds;

    /**
     * A list of artifacts (groupId:artifactId) that have been explicitly excluded.
     */
    private Set m_excludedIds;

    /**
     * A list of artifacts (groupId:artifactId:version) that have already been processed.
     */
    private Set m_wrappedIds;

    /**
     * {@inheritDoc}
     */
    protected String getParentId()
    {
        return parentId;
    }

    /**
     * {@inheritDoc}
     */
    protected void updateExtensionFields()
        throws MojoExecutionException
    {
        populateMissingFields();

        if( null == m_candidateIds )
        {
            // only need to set these fields once
            getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );
            getArchetypeMojo().setField( "groupId", getInternalGroupId( bundleGroupId ) );

            // bootstrap with the initial wrapper artifact
            String rootId = groupId + ':' + artifactId + ':' + version;

            m_candidateIds = new ArrayList();
            m_excludedIds = new HashSet();
            m_wrappedIds = new HashSet();

            excludeCandidates( exclusions );

            // kickstart the wrapping
            m_candidateIds.add( rootId );
            m_wrappedIds.add( rootId );
        }

        String id = (String) m_candidateIds.remove( 0 );
        String[] fields = id.split( ":" );

        groupId = fields[0];
        artifactId = fields[1];
        version = fields[2];

        String compoundWrapperId = getBundleSymbolicName();
        if( addVersion )
        {
            compoundWrapperId += '-' + version;
        }

        // common groupId shared between all wrappers in same session
        getArchetypeMojo().setField( "artifactId", compoundWrapperId );
        getArchetypeMojo().setField( "packageName", artifactId );
        getArchetypeMojo().setField( "version", version );
    }

    /**
     * Provide Velocity template with customized Bundle-SymbolicName
     * 
     * @return bundle symbolic name
     */
    public String getBundleSymbolicName()
    {
        if( null != bundleName && bundleName.trim().length() > 0 )
        {
            return bundleName;
        }
        else
        {
            return getCompoundId( groupId, artifactId );
        }
    }

    /**
     * Provide Velocity template with customized bundle version
     * 
     * @return bundle version
     */
    public String getBundleVersion()
    {
        if( null != bundleVersion && bundleVersion.trim().length() > 0 )
        {
            return bundleVersion;
        }
        else if( addVersion )
        {
            return INITIAL_POM_VERSION;
        }
        else
        {
            return version + '-' + INITIAL_POM_VERSION;
        }
    }

    /**
     * Provide Velocity template with wrappee's groupId
     * 
     * @return wrapped group id
     */
    public String getWrappedGroupId()
    {
        return groupId;
    }

    /**
     * Provide Velocity template with wrappee's artifactId
     * 
     * @return wrapped artifact id
     */
    public String getWrappedArtifactId()
    {
        return artifactId;
    }

    /**
     * Populate missing fields with information from the Maven repository
     * 
     * @throws MojoExecutionException
     */
    private void populateMissingFields()
        throws MojoExecutionException
    {
        if( null == groupId || groupId.length() == 0 )
        {
            // this is a common assumption
            groupId = artifactId;
        }

        if( PomUtils.needReleaseVersion( version ) )
        {
            Artifact artifact = m_factory.createBuildArtifact( groupId, artifactId, "RELEASE", "jar" );
            version = PomUtils.getReleaseVersion( artifact, m_source, m_remoteRepos, m_localRepo );
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void postProcess( Pom pom, Bnd bnd )
        throws MojoExecutionException
    {
        if( null == pom.getParentId() )
        {
            OSGiBundleArchetypeMojo.makeStandalone( pom, "wrappers", getArchetypeVersion() );
        }

        updatePomDependencies( pom );
        updateBndInstructions( bnd );

        // poms no longer needed
        addTempFiles( "poms/" );
    }

    /**
     * Add dependencies to the Maven project according to wrapper settings
     * 
     * @param pom Maven project model
     */
    private void updatePomDependencies( Pom pom )
    {
        if( wrapTransitive )
        {
            // also handles exclusions
            wrapDirectDependencies( pom );
            embedTransitive = false;
        }
        else
        {
            excludeDependencies( pom );
        }
    }

    /**
     * Updates the default Bnd instructions with custom settings
     * 
     * @param bnd Bnd instructions
     * @throws MojoExecutionException
     */
    private void updateBndInstructions( Bnd bnd )
        throws MojoExecutionException
    {
        if( embedTransitive )
        {
            bnd.setInstruction( "Embed-Transitive", "true", canOverwrite() );
        }
        if( includeResource != null )
        {
            bnd.setInstruction( "Include-Resource", includeResource, canOverwrite() );
        }
        if( importPackage != null )
        {
            bnd.setInstruction( "Import-Package", importPackage, canOverwrite() );
        }
        if( exportContents != null )
        {
            bnd.setInstruction( "-exportcontents", exportContents, canOverwrite() );
        }
        if( requireBundle != null )
        {
            bnd.setInstruction( "Require-Bundle", requireBundle, canOverwrite() );
        }
        if( dynamicImport != null )
        {
            bnd.setInstruction( "DynamicImport-Package", dynamicImport, canOverwrite() );
        }
    }

    /**
     * {@inheritDoc}
     */
    protected boolean createMoreArtifacts()
    {
        return !m_candidateIds.isEmpty();
    }

    /**
     * Attempt to wrap direct dependencies of the wrapped artifact - in turn they will call this method, and so on...
     * 
     * @param pom Maven project model
     */
    private void wrapDirectDependencies( Pom pom )
    {
        /*
         * Use a local list to capture dependencies that are type POM, ie. collections of dependencies. These POM
         * artifacts don't require wrapping, so we must store and process them locally in the following loop...
         */
        List dependencyPoms = new ArrayList();

        // use the wrapped artifact's POM to kick things off
        dependencyPoms.add( m_factory.createProjectArtifact( groupId, artifactId, version ) );

        while( !dependencyPoms.isEmpty() )
        {
            Artifact pomArtifact = (Artifact) dependencyPoms.remove( 0 );

            try
            {
                // Standard Maven code to get direct dependencies for a given POM
                MavenProject p = m_projectBuilder.buildFromRepository( pomArtifact, m_remoteRepos, m_localRepo );
                Set artifacts = p.createArtifacts( m_factory, null, null );

                // look for new artifacts to wrap
                dependencyPoms.addAll( processDependencies( pom, artifacts ) );
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
     * @param pom Maven project model
     * @param artifacts list of potential artifacts to be wrapped
     * @return list of POM artifacts discovered while processing
     */
    private List processDependencies( Pom pom, Set artifacts )
    {
        List newDependencyPoms = new ArrayList();
        for( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            String candidateId = getCandidateId( artifact );

            if( isValidWrapperDependency( artifact ) )
            {
                // process POM artifacts in parent loop...
                if( "pom".equals( artifact.getType() ) )
                {
                    newDependencyPoms.add( artifact );
                }
                // copy dependency to current wrapper pom (not all require wrapping)
                else if( addWrapperDependency( pom, artifact ) )
                {
                    m_candidateIds.add( candidateId );
                    m_wrappedIds.add( candidateId );
                }
            }
        }

        return newDependencyPoms;
    }

    /**
     * Get the first version of the given artifact to be wrapped during this cycle
     * 
     * @param candidateId potential new candidate
     * @return first version wrapped, or null if it has not been wrapped
     */
    private String getWrappedVersion( String candidateId )
    {
        // ignore version field while searching...
        int versionIndex = candidateId.lastIndexOf( ':' );
        String prefix = candidateId.substring( 0, 1 + versionIndex );

        for( Iterator i = m_wrappedIds.iterator(); i.hasNext(); )
        {
            String wrappedId = (String) i.next();
            if( wrappedId.startsWith( prefix ) )
            {
                // return version field from the id
                return wrappedId.substring( prefix.length() );
            }
        }

        return null;
    }

    /**
     * @param artifact wrapper dependency
     * @return groupId:artifactId:metaVersion
     */
    private static String getCandidateId( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + PomUtils.getMetaVersion( artifact );
    }

    /**
     * @param artifact wrapper dependency
     * @return true if this artifact needs to be wrapped, otherwise false
     */
    private boolean isValidWrapperDependency( Artifact artifact )
    {
        String scope = artifact.getScope();

        if( Artifact.SCOPE_SYSTEM.equals( scope ) || Artifact.SCOPE_TEST.equals( scope ) )
        {
            getLog().info( "Skipping dependency " + artifact );
            return false;
        }
        else if( !wrapOptional && artifact.isOptional() )
        {
            getLog().info( "Skipping optional dependency " + artifact );
            return false;
        }

        return true;
    }

    /**
     * Adds a dependency to the wrapped artifact, or the original if it doesn't require wrapping
     * 
     * @param pom Maven project model
     * @param artifact wrapper dependency
     * @return true if the dependency should be wrapped, otherwise false
     */
    private boolean addWrapperDependency( Pom pom, Artifact artifact )
    {
        if( m_excludedIds.contains( artifact.getGroupId() + ':' + artifact.getArtifactId() ) )
        {
            // exclude this dependency from current POM rather than wrapping it here
            pom.addExclusion( artifact.getGroupId(), artifact.getArtifactId(), true );
            return false;
        }
        else if( PomUtils.isBundleArtifact( artifact, m_resolver, m_remoteRepos, m_localRepo, testMetadata ) )
        {
            pom.addDependency( getBundleDependency( artifact ), true );
            return false;
        }
        else
        {
            String existingVersion = getWrappedVersion( getCandidateId( artifact ) );
            if( null != existingVersion )
            {
                /*
                 * We've already wrapped this artifact but with a different version: we can't easily update any of the
                 * earlier wrapper POMs so for now we'll force the other transitive wrappers to use the first version
                 */
                artifact.setVersion( existingVersion );
            }

            pom.addDependency( getWrappedDependency( artifact ), true );
            return ( null == existingVersion );
        }
    }

    /**
     * @param artifact wrapper dependency
     * @return a provided dependency to the bundle
     */
    private Dependency getBundleDependency( Artifact artifact )
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
    private Dependency getWrappedDependency( Artifact artifact )
    {
        Dependency dependency = new Dependency();

        dependency.setGroupId( getInternalGroupId( bundleGroupId ) );
        String compoundWrapperId = getCompoundId( artifact.getGroupId(), artifact.getArtifactId() );
        String metaVersion = PomUtils.getMetaVersion( artifact );

        if( addVersion )
        {
            dependency.setArtifactId( compoundWrapperId + '-' + metaVersion );
            dependency.setVersion( INITIAL_POM_VERSION );
        }
        else
        {
            dependency.setArtifactId( compoundWrapperId );
            dependency.setVersion( metaVersion + '-' + INITIAL_POM_VERSION );
        }

        dependency.setScope( Artifact.SCOPE_PROVIDED );

        return dependency;
    }

    /**
     * Explicitly exclude artifacts from the wrapping process
     * 
     * @param artifacts comma-separated list of artifacts to exclude from wrapping
     */
    private void excludeCandidates( String artifacts )
    {
        if( null == artifacts || artifacts.length() == 0 )
        {
            return;
        }

        String[] exclusionIds = artifacts.split( "," );
        for( int i = 0; i < exclusionIds.length; i++ )
        {
            String id = exclusionIds[i].trim();
            String[] fields = id.split( ":" );
            if( fields.length > 1 )
            {
                // handle groupId:artifactId:other:stuff
                m_excludedIds.add( fields[0] + ':' + fields[1] );
            }
            else
            {
                // assume groupId same as artifactId
                m_excludedIds.add( id + ':' + id );
            }
        }
    }

    /**
     * Add explicit dependency exclusion list to the main POM dependency element
     * 
     * @param pom Maven project model
     */
    private void excludeDependencies( Pom pom )
    {
        for( Iterator i = m_excludedIds.iterator(); i.hasNext(); )
        {
            String[] fields = ( (String) i.next() ).split( ":" );
            pom.addExclusion( fields[0], fields[1], true );
        }
    }
}
