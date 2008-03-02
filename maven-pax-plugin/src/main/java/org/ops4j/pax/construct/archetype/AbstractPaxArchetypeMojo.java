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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.archetype.Archetype;
import org.apache.maven.archetype.ArchetypeDescriptorException;
import org.apache.maven.archetype.ArchetypeNotFoundException;
import org.apache.maven.archetype.ArchetypeTemplateProcessingException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.ops4j.pax.construct.util.BndUtils;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Based on <a href="http://maven.apache.org/plugins/maven-archetype-plugin/create-mojo.html">MavenArchetypeMojo</a>,
 * this abstract mojo adds support for additional archetype properties, needed to provide multi-module archetypes.
 * 
 * @aggregator true
 * 
 * @requiresProject false
 */
public abstract class AbstractPaxArchetypeMojo extends AbstractMojo
{
    /**
     * Our local archetype group
     */
    public static final String PAX_CONSTRUCT_GROUP_ID = "org.ops4j.pax.construct";

    /**
     * Component factory for Maven archetypes.
     * 
     * @component
     */
    private Archetype m_archetypeFactory;

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
     * The local Maven repository for the containing project.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository m_localRepo;

    /**
     * List of remote Maven repositories for the containing project.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List m_remoteRepos;

    /**
     * Other remote repositories available for discovering dependencies and extensions.
     * 
     * @parameter expression="${remoteRepositories}"
     */
    private String remoteRepositories;

    /**
     * The version of the currently executing plugin.
     * 
     * @parameter default-value="${plugin.version}"
     * @required
     * @readonly
     */
    private String pluginVersion;

    /**
     * The archetype version to use, defaults to a version compatible with the current plugin.
     * 
     * @parameter expression="${archetypeVersion}"
     */
    private String archetypeVersion;

    /**
     * Target directory where the project should be created.
     * 
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File targetDirectory;

    /**
     * Comma-separated list of additional archetypes to merge with the current one (use artifactId for Pax-Construct
     * archetypes and groupId:artifactId:version for external artifacts).
     * 
     * @parameter expression="${contents}"
     */
    private String contents;

    /**
     * When true, avoid duplicate elements when combining group and artifact ids.
     * 
     * @parameter expression="${compactIds}" default-value="true"
     */
    private boolean compactIds;

    /**
     * When true, create the necessary POMs to attach it to the current project.
     * 
     * @parameter expression="${attachPom}" default-value="true"
     */
    private boolean attachPom;

    /**
     * When true, replace existing files with ones from the new project.
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    /**
     * The current Maven project (will be Maven super-POM if no existing project)
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject m_project;

    /**
     * The new project's POM file
     */
    private File m_pomFile;

    /**
     * Excludes any existing files, includes any discarded files
     */
    private FileSet m_tempFiles;

    /**
     * Additional archetypes that supply customized content
     */
    private List m_customArchetypeIds;

    /**
     * Maven POM representing the project that contains the new module
     */
    private Pom m_modulesPom;

    /**
     * Working copy of current Maven POM
     */
    private Pom m_pom;

    /**
     * Working copy of current Bnd instructions
     */
    private Bnd m_bnd;

    /**
     * Working set of archetype properties
     */
    private Properties m_archetypeProperties;

    /**
     * @return component factory for Maven artifacts
     */
    protected final ArtifactFactory getFactory()
    {
        return m_factory;
    }

    /**
     * @return component for resolving Maven artifacts
     */
    protected final ArtifactResolver getResolver()
    {
        return m_resolver;
    }

    /**
     * @return component for resolving Maven metadata
     */
    protected final ArtifactMetadataSource getSource()
    {
        return m_source;
    }

    /**
     * @return local Maven repository
     */
    protected final ArtifactRepository getLocalRepo()
    {
        return m_localRepo;
    }

    /**
     * @return true if existing files can be overwritten, otherwise false
     */
    protected final boolean canOverwrite()
    {
        return overwrite;
    }

    /**
     * @return true if the user has selected one or more custom archetypes
     */
    protected final boolean hasCustomContent()
    {
        return PomUtils.isNotEmpty( contents );
    }

    /**
     * @param bundleGroupId customized bundle groupId (may be null)
     * @return the internal groupId for support artifacts belonging to the new project
     */
    protected final String getInternalGroupId( String bundleGroupId )
    {
        if( PomUtils.isNotEmpty( bundleGroupId ) )
        {
            return bundleGroupId;
        }
        else if( null != m_modulesPom )
        {
            return getCompoundId( m_modulesPom.getGroupId(), m_modulesPom.getArtifactId() );
        }
        else
        {
            // standalone group
            return "examples";
        }
    }

    /**
     * @return the version of the plugin
     */
    protected final String getPluginVersion()
    {
        return pluginVersion;
    }

    /**
     * @param pathExpression Ant-style path expression, can include wildcards
     */
    protected final void addTempFiles( String pathExpression )
    {
        m_tempFiles.addInclude( pathExpression );
    }

    /**
     * {@inheritDoc}
     */
    public final void execute()
        throws MojoExecutionException
    {
        updateFields();
        createModuleTree();

        /*
         * support repeated creation of projects
         */
        do
        {
            scheduleCustomArchetypes();
            updateExtensionFields();

            prepareTarget();
            generateArchetype();
            cacheSettings();

            runCustomArchetypes();

            postProcess();
            cleanUp();

        } while( createMoreArtifacts() );
    }

    /**
     * Set common fields in the archetype mojo
     */
    private void updateFields()
    {
        m_archetypeProperties = new Properties();

        /*
         * common shared settings
         */
        targetDirectory = DirUtils.resolveFile( targetDirectory, true );
        setArchetypeProperty( "basedir", targetDirectory.getPath() );

        /*
         * these must be set by the various archetype sub-classes
         */
        // setMainArchetype( archetypeArtifactId );
        //
        // setArchetypeProperty( "groupId", groupId );
        // setArchetypeProperty( "artifactId", artifactId );
        // setArchetypeProperty( "version", version );
        //
        // setArchetypeProperty( "packageName", packageName );
    }

    /**
     * Fill-in any missing Maven POMs between the current project directory and the target location
     * 
     * @throws MojoExecutionException
     */
    private void createModuleTree()
        throws MojoExecutionException
    {
        if( attachPom )
        {
            try
            {
                // make sure we can reach the location of the new project from the current project
                m_modulesPom = DirUtils.createModuleTree( m_project.getBasedir(), targetDirectory );
                if( null != m_modulesPom && !"pom".equals( m_modulesPom.getPackaging() ) )
                {
                    throw new MojoExecutionException( "Containing project does not have packaging type 'pom'" );
                }
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to create module tree" );
            }
        }
    }

    /**
     * Set the remaining fields in the archetype mojo
     * 
     * @throws MojoExecutionException
     */
    protected abstract void updateExtensionFields()
        throws MojoExecutionException;

    /**
     * @return The logical parent of the new project (use artifactId or groupId:artifactId)
     */
    protected abstract String getParentId();

    /**
     * Gives sub-classes the chance to cache the original files before custom archetypes run
     * 
     * @param baseDir project base directory
     */
    protected void cacheOriginalFiles( File baseDir )
    {
        // for sub-classes to override if they need to
    }

    /**
     * Sub-class specific post-processing, which runs *after* custom archetypes are added
     * 
     * @param pom working copy of Maven POM
     * @param bnd working copy of Bnd instructions
     * @throws MojoExecutionException
     */
    protected void postProcess( Pom pom, Bnd bnd )
        throws MojoExecutionException
    {
        // for sub-classes to override if they need to
    }

    /**
     * @return true to continue creating more projects, otherwise false
     */
    protected boolean createMoreArtifacts()
    {
        return false;
    }

    /**
     * @return acceptable version range of Pax-Construct archetypes
     */
    private VersionRange getArchetypeVersionRange()
    {
        ArtifactVersion version = new DefaultArtifactVersion( pluginVersion );

        int major = version.getMajorVersion();
        int minor = version.getMinorVersion();

        try
        {
            // limit version range to the same major.minor release as the current plugin
            String spec = "[" + major + '.' + minor + ',' + major + '.' + ( 1 + minor ) + ')';
            return VersionRange.createFromVersionSpec( spec );
        }
        catch( InvalidVersionSpecificationException e )
        {
            return null;
        }
    }

    /**
     * Attempts to find the latest released (or snapshot) archetype that's compatible with this plugin
     * 
     * @param groupId archetype group id
     * @param artifactId archetype artifact id
     * @return compatible archetype version
     */
    private String getArchetypeVersion( String groupId, String artifactId )
    {
        // special case when plugin is a snapshot - use the local archetype snapshot, if available
        Artifact artifact = m_factory.createBuildArtifact( groupId, artifactId, pluginVersion, "jar" );
        if( artifact.isSnapshot() && PomUtils.getFile( artifact, m_resolver, m_localRepo ) )
        {
            return pluginVersion;
        }

        // limit archetypes to same major and minor version
        VersionRange range = getArchetypeVersionRange();

        try
        {
            getLog().info( "Selecting latest archetype release within version range " + range );
            return PomUtils.getReleaseVersion( artifact, m_source, Collections.EMPTY_LIST, m_localRepo, range );
        }
        catch( MojoExecutionException e )
        {
            return pluginVersion;
        }
    }

    /**
     * Fill in archetype details for the selected Pax-Construct archetype
     * 
     * @param archetypeArtifactId selected OSGi archetype
     */
    protected final void setMainArchetype( String archetypeArtifactId )
    {
        if( PomUtils.isEmpty( archetypeVersion ) )
        {
            archetypeVersion = getArchetypeVersion( PAX_CONSTRUCT_GROUP_ID, archetypeArtifactId );
        }

        setArchetypeProperty( "archetypeGroupId", PAX_CONSTRUCT_GROUP_ID );
        setArchetypeProperty( "archetypeArtifactId", archetypeArtifactId );
        setArchetypeProperty( "archetypeVersion", archetypeVersion );
    }

    /**
     * Lay the foundations for the new project
     * 
     * @throws MojoExecutionException
     */
    private void prepareTarget()
        throws MojoExecutionException
    {
        String artifactId = getArchetypeProperty( "artifactId" );
        File pomDirectory = new File( targetDirectory, artifactId );

        // support overwriting of existing projects
        m_pomFile = new File( pomDirectory, "pom.xml" );
        if( canOverwrite() && m_pomFile.exists() )
        {
            m_pomFile.delete();
        }

        // reset trashcan
        m_tempFiles = new FileSet();
        m_tempFiles.setDirectory( pomDirectory.getAbsolutePath() );

        if( pomDirectory.exists() )
        {
            preserveExistingFiles( pomDirectory );
        }
        else
        {
            pomDirectory.mkdirs();
        }

        if( null != m_modulesPom )
        {
            setArchetypeProperty( "isMultiModuleProject", "true" );

            try
            {
                // attach new project to its physical parent
                m_modulesPom.addModule( pomDirectory.getName(), canOverwrite() );
                m_modulesPom.write();
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to attach POM to existing project" );
            }
        }
    }

    /**
     * @param baseDir project base directory
     * @throws MojoExecutionException
     */
    private void preserveExistingFiles( File baseDir )
        throws MojoExecutionException
    {
        try
        {
            // exclude existing files, so we don't accidentally trash them later
            List excludes = FileUtils.getFileNames( baseDir, null, null, false );
            for( Iterator i = excludes.iterator(); i.hasNext(); )
            {
                getLog().debug( "Preserving " + i.next() );
            }
            m_tempFiles.setExcludes( excludes );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error while protecting existing files from deletion", e );
        }
    }

    /**
     * Cache the original generated files before any custom archetypes run
     * 
     * @throws MojoExecutionException
     */
    private void cacheSettings()
        throws MojoExecutionException
    {
        try
        {
            if( null != m_modulesPom )
            {
                // before caching, search for logical parent in project tree
                DirUtils.updateLogicalParent( m_pomFile, getParentId() );
            }

            m_pom = PomUtils.readPom( m_pomFile );
            if( hasCustomContent() )
            {
                // allow further customization
                m_pom.getFile().delete();
            }
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error reading generated Maven POM " + m_pomFile, e );
        }

        try
        {
            m_bnd = BndUtils.readBnd( m_pom.getBasedir() );
            if( hasCustomContent() )
            {
                // allow further customization
                m_bnd.getFile().delete();
            }
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error reading generated Bnd instructions", e );
        }

        if( hasCustomContent() )
        {
            // no need to cache if not customizing
            cacheOriginalFiles( m_pom.getBasedir() );
        }
    }

    /**
     * Apply selected custom archetypes to the directory, which may add to the original archetype content
     * 
     * @throws MojoExecutionException
     */
    private void runCustomArchetypes()
        throws MojoExecutionException
    {
        for( Iterator i = m_customArchetypeIds.iterator(); i.hasNext(); )
        {
            String[] fields = ( (String) i.next() ).split( ":" );

            setArchetypeProperty( "archetypeGroupId", fields[0] );
            setArchetypeProperty( "archetypeArtifactId", fields[1] );
            setArchetypeProperty( "archetypeVersion", fields[2] );

            generateArchetype();
        }
    }

    /**
     * Perform any necessary post-processing and write Maven POM and optional Bnd instructions back to disk
     * 
     * @throws MojoExecutionException
     */
    private void postProcess()
        throws MojoExecutionException
    {
        // sub-class processing
        postProcess( m_pom, m_bnd );

        try
        {
            /*
             * merge customized files with the original Pax-Construct generated templates
             */
            saveProjectModel( m_pom );
            saveBndInstructions( m_bnd );
        }
        catch( IOException e )
        {
            getLog().error( "Unable to save customized settings" );
        }
    }

    /**
     * @param pom Maven project to merge with the latest file copy
     * @throws IOException
     */
    protected final void saveProjectModel( Pom pom )
        throws IOException
    {
        if( hasCustomContent() && pom.getFile().exists() )
        {
            Pom customPom = PomUtils.readPom( pom.getBasedir() );
            pom.overlayDetails( customPom );
        }
        pom.write();
    }

    /**
     * @param bnd Bnd instructions to merge with the latest file copy
     * @throws IOException
     */
    protected final void saveBndInstructions( Bnd bnd )
        throws IOException
    {
        if( hasCustomContent() && bnd.getFile().exists() )
        {
            Bnd customBnd = BndUtils.readBnd( bnd.getBasedir() );
            bnd.overlayInstructions( customBnd );
        }
        bnd.write();
    }

    /**
     * Combine the groupId and artifactId, eliminating duplicate elements if compactNames is true
     * 
     * @param groupId project group id
     * @param artifactId project artifact id
     * @return the combined group and artifact sequence
     */
    protected final String getCompoundId( String groupId, String artifactId )
    {
        if( compactIds )
        {
            return PomUtils.getCompoundId( groupId, artifactId );
        }

        return groupId + '.' + artifactId;
    }

    /**
     * Add custom Maven archetypes, to be used after the main archetype has finished
     */
    private void scheduleCustomArchetypes()
    {
        m_customArchetypeIds = new ArrayList();

        // use default content
        if( !hasCustomContent() )
        {
            return;
        }

        String[] ids = contents.split( "," );
        for( int i = 0; i < ids.length; i++ )
        {
            String id = ids[i].trim();

            // handle groupId:artifactId:other:stuff
            String[] fields = id.split( ":" );
            if( fields.length > 2 )
            {
                // fully-qualified external archetype
                scheduleArchetype( fields[0], fields[1], fields[2] );
            }
            else if( fields.length > 1 )
            {
                // semi-qualified external archetype (assume groupId same as artifactId)
                scheduleArchetype( fields[0], fields[0], fields[1] );
            }
            else
            {
                // internal Pax-Construct archetype (assume same version as archetype template)
                scheduleArchetype( PAX_CONSTRUCT_GROUP_ID, fields[0], null );
            }
        }
    }

    /**
     * Add a custom archetype to the list of archetypes to merge in once the main archetype has been applied
     * 
     * @param groupId archetype group id
     * @param artifactId archetype atifact id
     * @param version archetype version
     */
    protected final void scheduleArchetype( String groupId, String artifactId, String version )
    {
        if( PomUtils.isEmpty( version ) )
        {
            m_customArchetypeIds.add( groupId + ':' + artifactId + ':' + archetypeVersion );
        }
        else
        {
            m_customArchetypeIds.add( groupId + ':' + artifactId + ':' + version );
        }
    }

    /**
     * @return set of filenames that will be left at the end of this archetype cycle
     */
    protected final Set getFinalFilenames()
    {
        Set finalFiles = new HashSet();

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( m_tempFiles.getDirectory() );
        scanner.setFollowSymlinks( false );

        scanner.addDefaultExcludes();
        scanner.setExcludes( m_tempFiles.getExcludesArray() );
        scanner.setIncludes( m_tempFiles.getIncludesArray() );

        scanner.scan();

        finalFiles.addAll( Arrays.asList( scanner.getNotIncludedFiles() ) );
        finalFiles.addAll( Arrays.asList( scanner.getExcludedFiles() ) );

        return finalFiles;
    }

    /**
     * Clean up any temporary or unnecessary files, including empty directories
     */
    private void cleanUp()
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( m_tempFiles.getDirectory() );
        scanner.setFollowSymlinks( false );

        scanner.addDefaultExcludes();
        scanner.setExcludes( m_tempFiles.getExcludesArray() );
        scanner.setIncludes( m_tempFiles.getIncludesArray() );

        scanner.scan();

        String[] discardedFiles = scanner.getIncludedFiles();
        for( int i = 0; i < discardedFiles.length; i++ )
        {
            String filename = discardedFiles[i];
            getLog().debug( "Discarding " + filename );
            new File( scanner.getBasedir(), filename ).delete();
        }

        // remove any empty directories after the cleanup
        DirUtils.pruneEmptyFolders( scanner.getBasedir() );
    }

    /**
     * @param name property name
     * @param value property value
     */
    protected final void setArchetypeProperty( String name, String value )
    {
        m_archetypeProperties.setProperty( name, value );
        if( "packageName".equals( name ) )
        {
            m_archetypeProperties.setProperty( "package", value );
        }
    }

    /**
     * @param name property name
     * @return property value
     */
    protected final String getArchetypeProperty( String name )
    {
        return m_archetypeProperties.getProperty( name );
    }

    /**
     * Generate Pax-Construct archetype (derived from classic archetype plugin)
     * 
     * @throws MojoExecutionException
     */
    private void generateArchetype()
        throws MojoExecutionException
    {
        List archetypeRemoteRepositories = new ArrayList();

        if( remoteRepositories != null )
        {
            getLog().info( "We are using command line specified remote repositories: " + remoteRepositories );

            String[] s = remoteRepositories.split( "," );
            for( int i = 0; i < s.length; i++ )
            {
                archetypeRemoteRepositories.add( createRemoteRepository( "id" + i, s[i] ) );
            }
        }
        else
        {
            archetypeRemoteRepositories.addAll( m_remoteRepos );
        }

        try
        {
            m_archetypeFactory.createArchetype( getArchetypeProperty( "archetypeGroupId" ),
                getArchetypeProperty( "archetypeArtifactId" ), archetypeVersion, m_localRepo,
                archetypeRemoteRepositories, m_archetypeProperties );
        }
        catch( ArchetypeNotFoundException e )
        {
            throw new MojoExecutionException( "Error creating from archetype", e );
        }
        catch( ArchetypeDescriptorException e )
        {
            throw new MojoExecutionException( "Error creating from archetype", e );
        }
        catch( ArchetypeTemplateProcessingException e )
        {
            throw new MojoExecutionException( "Error creating from archetype", e );
        }
    }

    /**
     * @param id repository id
     * @param url repository url
     * @return repository instance
     */
    private ArtifactRepository createRemoteRepository( String id, String url )
    {
        ArtifactRepositoryPolicy snapshots = new ArtifactRepositoryPolicy( true,
            ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, null );
        ArtifactRepositoryPolicy releases = new ArtifactRepositoryPolicy( true,
            ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, null );

        return m_repoFactory.createArtifactRepository( id, url, m_defaultLayout, snapshots, releases );
    }
}
