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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.archetype.MavenArchetypeMojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
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
 */
public abstract class AbstractPaxArchetypeMojo extends MavenArchetypeMojo
{
    /**
     * Our local archetype group
     */
    private static final String PAX_ARCHETYPE_GROUP_ID = "org.ops4j.pax.construct";

    /**
     * The archetype version to use, defaults to the plugin version.
     * 
     * @parameter alias="archetypeVersion" expression="${archetypeVersion}" default-value="${plugin.version}"
     */
    private String m_archetypeVersion;

    /**
     * Comma separated list of additional remote repository URLs.
     * 
     * @parameter alias="remoteRepositories" expression="${remoteRepositories}"
     *            default-value="http://repository.ops4j.org/maven2"
     */
    private String m_remoteRepositories;

    /**
     * Target directory where the project should be created.
     * 
     * @parameter alias="targetDirectory" expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File m_targetDirectory;

    /**
     * When true, avoid duplicate elements when combining group and artifact ids.
     * 
     * @parameter alias="compactIds" expression="${compactIds}" default-value="true"
     */
    private boolean m_compactIds;

    /**
     * When true, add the new project as a module in the parent directory's POM.
     * 
     * @parameter alias="attachPom" expression="${attachPom}" default-value="true"
     */
    private boolean m_attachPom;

    /**
     * When true, replace existing files with ones from the new project.
     * 
     * @parameter alias="overwrite" expression="${overwrite}"
     */
    private boolean m_overwrite;

    /**
     * The current Maven project (may be null)
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
     * Temporary files that should be removed at the end
     */
    private FileSet m_tempFiles;

    /**
     * @return true if existing files can be overwritten, otherwise false
     */
    boolean canOverwrite()
    {
        return m_overwrite;
    }

    /**
     * @return the internal groupId for support artifacts belonging to the new project
     */
    String getInternalGroupId()
    {
        return getCompoundId( m_project.getGroupId(), m_project.getArtifactId() );
    }

    /**
     * @return the version of the new project
     */
    String getProjectVersion()
    {
        return m_project.getVersion();
    }

    /**
     * @return Access to the archetype mojo
     */
    ReflectMojo getArchetypeMojo()
    {
        return m_archetypeMojo;
    }

    /**
     * @return The new project's POM file
     */
    File getPomFile()
    {
        return m_pomFile;
    }

    /**
     * @param pathExpression Ant-style path expression, can include wildcards
     */
    void addTempFiles( String pathExpression )
    {
        m_tempFiles.addInclude( pathExpression );
    }

    /**
     * Standard Maven mojo entry-point
     */
    public final void execute()
        throws MojoExecutionException
    {
        updateFields();

        /*
         * support repeated creation of projects
         */
        do
        {
            updateExtensionFields();

            prepareTarget();
            super.execute();
            postProcess();

        } while( createMoreArtifacts() );
    }

    /**
     * @return true to continue creating more projects, otherwise false
     */
    boolean createMoreArtifacts()
    {
        return false;
    }

    /**
     * Set common fields in the archetype mojo
     */
    final void updateFields()
    {
        m_archetypeMojo = new ReflectMojo( this, MavenArchetypeMojo.class );

        /*
         * common shared settings
         */

        m_archetypeMojo.setField( "archetypeGroupId", PAX_ARCHETYPE_GROUP_ID );
        m_archetypeMojo.setField( "archetypeVersion", m_archetypeVersion );
        m_archetypeMojo.setField( "remoteRepositories", m_remoteRepositories );

        m_project = (MavenProject) m_archetypeMojo.getField( "project" );
        m_targetDirectory = DirUtils.resolveFile( m_targetDirectory, true );

        m_archetypeMojo.setField( "basedir", m_targetDirectory.getPath() );

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
     * Set the remaining fields in the archetype mojo
     */
    abstract void updateExtensionFields();

    /**
     * @return The logical parent of the new project (use artifactId or groupId:artifactId)
     */
    abstract String getParentId();

    /**
     * Lay the foundations for the new project
     * 
     * @throws MojoExecutionException
     */
    void prepareTarget()
        throws MojoExecutionException
    {
        String artifactId = (String) m_archetypeMojo.getField( "artifactId" );
        File pomDirectory = new File( m_targetDirectory, artifactId );

        // support overwriting of existing projects
        m_pomFile = new File( pomDirectory, "pom.xml" );
        if( m_overwrite && m_pomFile.exists() )
        {
            m_pomFile.delete();
        }

        m_tempFiles = new FileSet();
        m_tempFiles.setDirectory( pomDirectory.getAbsolutePath() );

        if( m_attachPom )
        {
            try
            {
                // make sure we can reach the location of the new project from the current project
                Pom modulesPom = DirUtils.createModuleTree( m_project.getBasedir(), m_targetDirectory );
                if( null != modulesPom )
                {
                    // attach new project to its physical parent
                    pomDirectory.mkdirs();
                    modulesPom.addModule( artifactId, m_overwrite );
                    modulesPom.write();
                }
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to attach POM to project" );
            }
        }
    }

    /**
     * Make any necessary adjustments to the generated files and clean-up temporary files
     * 
     * @throws MojoExecutionException
     */
    void postProcess()
        throws MojoExecutionException
    {
        try
        {
            if( !m_tempFiles.getIncludes().isEmpty() )
            {
                new FileSetManager( getLog(), false ).delete( m_tempFiles );
            }

            // remove any left-over empty directories after the cleanup
            DirUtils.pruneEmptyFolders( new File( m_tempFiles.getDirectory() ) );
        }
        catch( IOException e )
        {
            getLog().warn( "I/O error while cleaning temporary files", e );
        }

        try
        {
            // attempt to find the logical parent of the new project (may be null)
            DirUtils.updateLogicalParent( m_pomFile, getParentId() );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to set parent POM: " + getParentId(), e );
        }
    }

    /**
     * Combine the groupId and artifactId, eliminating duplicate elements if compactNames is true
     * 
     * @param groupId project group id
     * @param artifactId project artifact id
     * @return the combined group and artifact sequence
     */
    final String getCompoundId( String groupId, String artifactId )
    {
        if( m_compactIds )
        {
            return PomUtils.getCompoundId( groupId, artifactId );
        }

        return groupId + '.' + artifactId;
    }

    /**
     * Provide a marker that can be used with the compoundId to get back the group and artifact
     * 
     * @param groupId project group id
     * @param artifactId project artifact id
     * @param compoundId compound id created from the group and artifact
     * @return marker string for the compoundId
     */
    final String getCompoundMarker( String groupId, String artifactId, String compoundId )
    {
        if( artifactId.equals( compoundId ) )
        {
            // groupId prefix
            return '<' + groupId;
        }
        else if( groupId.equals( compoundId ) )
        {
            // artifactId suffix
            return '>' + artifactId;
        }

        // simple append
        return '+' + groupId;
    }
}
