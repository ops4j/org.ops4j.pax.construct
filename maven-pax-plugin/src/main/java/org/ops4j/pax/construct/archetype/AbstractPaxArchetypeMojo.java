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
     * Target directory where the archetype should be created.
     * 
     * @parameter alias="targetDirectory" expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File m_targetDirectory;

    /**
     * When true, avoid duplicate elements when combining group and artifact ids.
     * 
     * @parameter alias="compactNames" expression="${compactNames}" default-value="true"
     */
    private boolean m_compactNames;

    /**
     * When true, add the new archetype as a module in the parent directory's POM.
     * 
     * @parameter alias="attachPom" expression="${attachPom}" default-value="true"
     */
    private boolean m_attachPom;

    /**
     * When true, replace existing files with ones from the new archetype.
     * 
     * @parameter alias="overwrite" expression="${overwrite}"
     */
    private boolean m_overwrite;

    private MavenProject m_project;

    private ReflectMojo m_archetypeMojo;

    private File m_pomFile;

    private FileSet m_tempFiles;

    boolean canOverwrite()
    {
        return m_overwrite;
    }

    String getInternalGroupId()
    {
        return getCompactName( m_project.getGroupId(), m_project.getArtifactId() );
    }

    String getProjectVersion()
    {
        return m_project.getVersion();
    }

    ReflectMojo getArchetypeMojo()
    {
        return m_archetypeMojo;
    }

    File getPomFile()
    {
        return m_pomFile;
    }

    void addTempFiles( String pathExpression )
    {
        m_tempFiles.addInclude( pathExpression );
    }

    public final void execute()
        throws MojoExecutionException
    {
        updateFields();

        do
        {
            updateExtensionFields();

            prepareTarget();
            super.execute();
            postProcess();

        } while( createMoreArtifacts() );
    }

    boolean createMoreArtifacts()
    {
        return false;
    }

    final void updateFields()
    {
        m_archetypeMojo = new ReflectMojo( this, MavenArchetypeMojo.class );

        m_archetypeMojo.setField( "archetypeGroupId", PAX_ARCHETYPE_GROUP_ID );
        m_archetypeMojo.setField( "archetypeVersion", m_archetypeVersion );
        m_archetypeMojo.setField( "remoteRepositories", m_remoteRepositories );

        m_project = (MavenProject) m_archetypeMojo.getField( "project" );
        m_targetDirectory = DirUtils.resolveFile( m_targetDirectory, true );

        m_archetypeMojo.setField( "basedir", m_targetDirectory.getPath() );

        // these must be set by the various archetype sub-classes
        // setField( "archetypeArtifactId", archetypeArtifactId );
        // setField( "groupId", groupId );
        // setField( "artifactId", artifactId );
        // setField( "version", version );
        // setField( "packageName", packageName );
    }

    abstract void updateExtensionFields();

    abstract String getParentId();

    void prepareTarget()
        throws MojoExecutionException
    {
        String artifactId = (String) m_archetypeMojo.getField( "artifactId" );
        File pomDirectory = new File( m_targetDirectory, artifactId );

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
                Pom modulesPom = DirUtils.createModuleTree( m_project.getBasedir(), m_targetDirectory );
                if( null != modulesPom )
                {
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

    void postProcess()
        throws MojoExecutionException
    {
        try
        {
            if( !m_tempFiles.getIncludes().isEmpty() )
            {
                new FileSetManager( getLog(), false ).delete( m_tempFiles );
            }
            DirUtils.pruneEmptyFolders( new File( m_tempFiles.getDirectory() ) );
        }
        catch( IOException e )
        {
            getLog().warn( "I/O error while cleaning temporary files", e );
        }

        try
        {
            Pom parentPom = DirUtils.findPom( m_targetDirectory, getParentId() );
            if( null != parentPom )
            {
                Pom thisPom = PomUtils.readPom( m_pomFile );

                String relativePath = null;
                String[] pivot = DirUtils.calculateRelativePath( thisPom.getBasedir(), parentPom.getBasedir() );
                if( null != pivot )
                {
                    relativePath = pivot[0] + pivot[2];
                }

                thisPom.setParent( parentPom, relativePath, m_overwrite );
                thisPom.write();
            }
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to set parent POM: " + getParentId(), e );
        }
    }

    final String calculateGroupMarker( String groupId, String artifactId, String compoundName )
    {
        if( artifactId.equals( compoundName ) )
        {
            return '=' + groupId;
        }
        else if( groupId.equals( compoundName ) )
        {
            return '~' + artifactId;
        }

        return '+' + groupId;
    }

    final String getCompactName( String groupId, String artifactId )
    {
        if( m_compactNames )
        {
            return PomUtils.getCompoundName( groupId, artifactId );
        }

        return groupId + '.' + artifactId;
    }
}
