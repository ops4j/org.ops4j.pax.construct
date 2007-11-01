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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.archetype.MavenArchetypeMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.ops4j.pax.construct.util.BndUtils;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;
import org.ops4j.pax.construct.util.ReflectMojo;

/**
 * Extends <a href="http://maven.apache.org/plugins/maven-archetype-plugin/create-mojo.html">MavenArchetypeMojo</a> to
 * provide flexible custom archetypes for Pax-Construct projects.<br/>Inherited parameters can still be used, but
 * unfortunately don't appear in the generated docs.
 * 
 * @aggregator true
 * 
 * @requiresProject false
 */
public abstract class AbstractPaxArchetypeMojo extends MavenArchetypeMojo
{
    /**
     * Our local archetype group
     */
    protected static final String PAX_ARCHETYPE_GROUP_ID = "org.ops4j.pax.construct";

    /**
     * The archetype version to use, defaults to the plugin version.
     * 
     * @parameter expression="${archetypeVersion}" default-value="${plugin.version}"
     */
    private String archetypeVersion;

    /**
     * Comma-separated list of additional remote repository URLs.
     * 
     * @parameter expression="${remoteArchetypeRepositories}"
     */
    private String remoteArchetypeRepositories;

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
     */
    private MavenProject m_project;

    /**
     * Provide access to the private fields of the archetype mojo
     */
    private ReflectMojo m_archetypeMojo;

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
        return null != contents;
    }

    /**
     * @return the internal groupId for support artifacts belonging to the new project
     */
    protected final String getInternalGroupId()
    {
        if( null != m_modulesPom )
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
     * @return the version of the archetype
     */
    protected final String getArchetypeVersion()
    {
        return archetypeVersion;
    }

    /**
     * @return Access to the archetype mojo
     */
    protected final ReflectMojo getArchetypeMojo()
    {
        return m_archetypeMojo;
    }

    /**
     * @param pathExpression Ant-style path expression, can include wildcards
     */
    protected final void addTempFiles( String pathExpression )
    {
        m_tempFiles.addInclude( pathExpression );
    }

    /**
     * Standard Maven mojo entry-point
     */
    public final void execute()
        throws MojoExecutionException
    {
        // to support better templates
        VelocityBridge.setMojo( this );

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
            super.execute();
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
        String ops4jRepo;
        if( archetypeVersion.indexOf( "SNAPSHOT" ) >= 0 )
        {
            // OPS4J snapshot repository
            ops4jRepo = "http://repository.ops4j.org/mvn-snapshots";
        }
        else
        {
            // OPS4J standard repository
            ops4jRepo = "http://repository.ops4j.org/maven2";
        }

        // put OPS4J repository before others
        if( null == remoteArchetypeRepositories )
        {
            remoteArchetypeRepositories = ops4jRepo;
        }
        else
        {
            remoteArchetypeRepositories = ops4jRepo + ',' + remoteArchetypeRepositories;
        }

        /*
         * common shared settings
         */
        m_archetypeMojo = new ReflectMojo( this, MavenArchetypeMojo.class );
        m_archetypeMojo.setField( "archetypeGroupId", PAX_ARCHETYPE_GROUP_ID );
        m_archetypeMojo.setField( "archetypeVersion", archetypeVersion );
        m_archetypeMojo.setField( "remoteRepositories", remoteArchetypeRepositories );

        m_project = (MavenProject) m_archetypeMojo.getField( "project" );
        targetDirectory = DirUtils.resolveFile( targetDirectory, true );

        m_archetypeMojo.setField( "basedir", targetDirectory.getPath() );

        /*
         * these must be set by the various archetype sub-classes
         */
        // setField( "archetypeArtifactId", archetypeArtifactId );
        // setField( "groupId", groupId );
        // setField( "artifactId", artifactId );
        // setField( "version", version );
        // setField( "packageName", packageName );
    }

    /**
     * Fill-in any missing Maven POMs between the current project directory and the target location
     */
    private void createModuleTree()
    {
        if( attachPom )
        {
            try
            {
                // make sure we can reach the location of the new project from the current project
                m_modulesPom = DirUtils.createModuleTree( m_project.getBasedir(), targetDirectory );
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
     * Lay the foundations for the new project
     * 
     * @throws MojoExecutionException
     */
    private void prepareTarget()
        throws MojoExecutionException
    {
        String artifactId = (String) m_archetypeMojo.getField( "artifactId" );
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
            try
            {
                // exclude any already existing files, so we don't accidentally trash modified files
                m_tempFiles.setExcludes( FileUtils.getFileNames( pomDirectory, null, null, false ) );
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "I/O error while protecting existing files from deletion", e );
            }
        }
        else
        {
            pomDirectory.mkdirs();
        }

        if( null != m_modulesPom )
        {
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

            getArchetypeMojo().setField( "archetypeGroupId", fields[0] );
            getArchetypeMojo().setField( "archetypeArtifactId", fields[1] );
            getArchetypeMojo().setField( "archetypeVersion", fields[2] );

            super.execute();
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
        if( !hasCustomContent() || contents.trim().length() == 0 )
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
                scheduleArchetype( PAX_ARCHETYPE_GROUP_ID, fields[0], getArchetypeVersion() );
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
        m_customArchetypeIds.add( groupId + ':' + artifactId + ':' + version );
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
            new File( scanner.getBasedir(), discardedFiles[i] ).delete();
        }

        // remove any empty directories after the cleanup
        DirUtils.pruneEmptyFolders( scanner.getBasedir() );
    }
}
