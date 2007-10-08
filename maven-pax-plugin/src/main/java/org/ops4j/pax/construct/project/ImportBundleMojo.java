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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactStatus;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.ExcludeSystemBundlesFilter;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Import an OSGi bundle as a project dependency and mark it for deployment
 * 
 * @goal import-bundle
 * @aggregator true
 */
public class ImportBundleMojo extends AbstractMojo
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
     * The groupId of the bundle to be imported.
     * 
     * @parameter alias="groupId" expression="${groupId}"
     * @required
     */
    private String m_groupId;

    /**
     * The artifactId of the bundle to be imported.
     * 
     * @parameter alias="artifactId" expression="${artifactId}"
     * @required
     */
    private String m_artifactId;

    /**
     * The version of the bundle to be imported.
     * 
     * @parameter alias="version" expression="${version}"
     * @required
     */
    private String m_version;

    /**
     * Reference to the project's provision POM (use artifactId or groupId:artifactId).
     * 
     * @parameter alias="provisionId" expression="${provisionId}" default-value="provision"
     */
    private String m_provisionId;

    /**
     * Target directory where the bundle should be imported.
     * 
     * @parameter alias="targetDirectory" expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File m_targetDirectory;

    /**
     * When true, also try to import any provided dependencies belonging to this bundle.
     * 
     * @parameter alias="importTransitive" expression="${importTransitive}"
     */
    private boolean m_importTransitive;

    /**
     * When true, also consider compile and runtime dependencies as potential bundles.
     * 
     * @parameter alias="widenScope" expression="${widenScope}"
     */
    private boolean m_widenScope;

    /**
     * When true, check dependency artifacts for OSGi metadata before wrapping them.
     * 
     * @parameter alias="testMetadata" expression="${testMetadata}" default-value="true"
     */
    private boolean m_testMetadata;

    /**
     * When false, mark the imported bundle as optional so it won't be provisioned.
     * 
     * @parameter alias="deploy" expression="${deploy}" default-value="true"
     */
    private boolean m_deploy;

    /**
     * When true, overwrite existing entries with the new imports.
     * 
     * @parameter alias="overwrite" expression="${overwrite}" default-value="true"
     */
    private boolean m_overwrite;

    /**
     * The local provisioning POM, where imported non-local bundles are recorded.
     */
    private Pom m_provisionPom;

    /**
     * The bundle POM in the target directory.
     */
    private Pom m_localBundlePom;

    /**
     * A list of potential artifacts (groupId:artifactId:version) to be imported
     */
    private List m_candidateIds;

    /**
     * A list of artifacts (groupId:artifactId:version) that have already been processed.
     */
    private Set m_visitedIds;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        // Find host POMs which will receive the imported dependencies
        m_provisionPom = DirUtils.findPom( m_targetDirectory, m_provisionId );
        m_localBundlePom = readBundlePom( m_targetDirectory );

        String rootId = m_groupId + ':' + m_artifactId + ':' + m_version;

        m_candidateIds = new ArrayList();
        m_visitedIds = new HashSet();

        // kickstart the import
        m_candidateIds.add( rootId );
        m_visitedIds.add( rootId );

        while( !m_candidateIds.isEmpty() )
        {
            String id = (String) m_candidateIds.remove( 0 );
            String[] fields = id.split( ":" );

            MavenProject project = buildMavenProject( fields[0], fields[1], fields[2] );
            if( null != project )
            {
                if( "pom".equals( project.getPackaging() ) )
                {
                    // support 'dependency' POMs
                    processDependencies( project );
                }
                else if( PomUtils.isBundleProject( project, m_resolver, m_remoteRepos, m_localRepo, m_testMetadata ) )
                {
                    importBundle( project );

                    // stop at first bundle
                    if( !m_importTransitive )
                    {
                        return;
                    }

                    processDependencies( project );
                }
                else
                {
                    getLog().info( "Ignoring non-bundle dependency " + project.getId() );
                }
            }
        }
    }

    /**
     * @param here a Maven POM, or a directory containing a file named 'pom.xml'
     * @return the POM, null if the POM is not a bundle project or doesn't exist
     */
    static Pom readBundlePom( File here )
    {
        try
        {
            Pom bundlePom = PomUtils.readPom( here );
            if( null != bundlePom && bundlePom.isBundleProject() )
            {
                return bundlePom;
            }
            else
            {
                return null;
            }
        }
        catch( IOException e )
        {
            return null;
        }
    }

    /**
     * Resolve the Maven project for the given artifact, handling when a POM cannot be found in the repository
     * 
     * @param groupId project group id
     * @param artifactId project artifact id
     * @param version project version
     * @return resolved Maven project
     */
    MavenProject buildMavenProject( String groupId, String artifactId, String version )
    {
        Artifact pomArtifact = m_artifactFactory.createProjectArtifact( groupId, artifactId, version );
        MavenProject project;
        try
        {
            project = m_projectBuilder.buildFromRepository( pomArtifact, m_remoteRepos, m_localRepo );
        }
        catch( ProjectBuildingException e )
        {
            getLog().warn( "Problem resolving project " + pomArtifact.getId() );
            return null;
        }

        /*
         * look to see if this is a local project (if so then set the POM location)
         */
        Pom localPom = DirUtils.findPom( m_targetDirectory, groupId + ':' + artifactId );
        if( localPom != null )
        {
            project.setFile( localPom.getFile() );
        }

        /*
         * Repair stubs (ie. when a POM couldn't be found in the various repositories)
         */
        DistributionManagement dm = project.getDistributionManagement();
        if( dm != null && ArtifactStatus.GENERATED.toString().equals( dm.getStatus() ) )
        {
            if( localPom != null )
            {
                // local project, use values from the local POM
                project.setPackaging( localPom.getPackaging() );
                project.setName( localPom.getId() );
            }
            else
            {
                // remote project - assume it creates a jarfile (so we can test later for OSGi metadata)
                Artifact jar = m_artifactFactory.createBuildArtifact( groupId, artifactId, version, "jar" );
                project.setArtifact( jar );

                project.setPackaging( "jar" );
                project.setName( jar.getId() );
            }
        }

        return project;
    }

    /**
     * Search direct dependencies for more import candidates
     * 
     * @param project the Maven project being imported
     */
    void processDependencies( MavenProject project )
    {
        try
        {
            /*
             * exclude common OSGi system bundles, as they don't need to be imported or provisioned
             */
            Set artifacts = project.createArtifacts( m_artifactFactory, null, new ExcludeSystemBundlesFilter() );
            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                String id = getCandidateId( artifact );
                String scope = artifact.getScope();

                scope = adjustDependencyScope( scope );

                // assume non-optional, provided dependencies are bundles meant to be imported and provisioned
                if( m_visitedIds.add( id ) && !artifact.isOptional() && Artifact.SCOPE_PROVIDED.equals( scope ) )
                {
                    m_candidateIds.add( id );
                }
                else
                {
                    getLog().info( "Skipping dependency " + artifact );
                }
            }
        }
        catch( InvalidDependencyVersionException e )
        {
            getLog().warn( "Problem resolving dependencies for " + project.getId() );
        }
    }

    /**
     * Support widening of scopes to treat compile and runtime dependencies as provided dependencies
     * 
     * @param scope original dependency scope
     * @return potentially widened scope
     */
    String adjustDependencyScope( String scope )
    {
        if( m_widenScope && !Artifact.SCOPE_SYSTEM.equals( scope ) && !Artifact.SCOPE_TEST.equals( scope ) )
        {
            return Artifact.SCOPE_PROVIDED;
        }

        return scope;
    }

    /**
     * @param artifact candidate artifact
     * @return simple unique id
     */
    static String getCandidateId( Artifact artifact )
    {
        return artifact.getGroupId() + ':' + artifact.getArtifactId() + ':' + PomUtils.getMetaVersion( artifact );
    }

    /**
     * Add bundle as a dependency to the provisioning POM and the local bundle POM, as appropriate
     * 
     * @param project bundle project
     * @throws MojoExecutionException
     */
    void importBundle( MavenProject project )
        throws MojoExecutionException
    {
        Dependency dependency = new Dependency();

        // use provided scope for OSGi bundles
        dependency.setGroupId( project.getGroupId() );
        dependency.setArtifactId( project.getArtifactId() );
        dependency.setVersion( project.getVersion() );
        dependency.setScope( Artifact.SCOPE_PROVIDED );
        dependency.setType( project.getPackaging() );
        dependency.setOptional( !m_deploy );

        // only add non-local bundles to the provisioning POM
        if( m_provisionPom != null && project.getFile() == null )
        {
            getLog().info( "Importing " + project.getName() + " to " + m_provisionPom );
            m_provisionPom.addDependency( dependency, m_overwrite );
        }

        if( m_localBundlePom != null )
        {
            getLog().info( "Adding " + project.getName() + " as dependency to " + m_localBundlePom );
            m_localBundlePom.addDependency( dependency, m_overwrite );
        }
    }
}
