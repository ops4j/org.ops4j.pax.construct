package org.ops4j.pax.construct;

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
import java.lang.reflect.Field;
import java.util.List;

import org.apache.maven.archetype.Archetype;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.archetype.MavenArchetypeMojo;
import org.apache.maven.project.MavenProject;

/**
 * Foundation for all OSGi project goals that use archetypes.
 */
public abstract class AbstractArchetypeMojo extends MavenArchetypeMojo
{
    private final static String archetypeGroupId = "org.ops4j.pax.construct";

    /**
     * @parameter expression="${archetypeVersion}" default-value="0.1.3-SNAPSHOT"
     */
    private String archetypeVersion;

    /**
     * @parameter expression="${compactNames}" default-value="true"
     */
    private boolean compactNames;

    /**
     * @component
     */
    protected Archetype archetype;

    /**
     * @component
     */
    protected ArtifactRepositoryFactory artifactRepositoryFactory;

    /**
     * @component role="org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout" roleHint="default"
     */
    protected ArtifactRepositoryLayout defaultArtifactRepositoryLayout;

    /**
     * @parameter expression="${localRepository}"
     * @required
     */
    protected ArtifactRepository localRepository;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     */
    protected List pomRemoteRepositories;

    /**
     * @parameter expression="${remoteRepositories}" default-value="http://repository.ops4j.org/maven2"
     */
    protected String remoteRepositories;

    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    /**
     * The containing OSGi project's base directory
     * 
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    protected File targetDirectory;

    protected final void setField( String name, Object value )
    {
        try
        {
            // Attempt to bypass normal private field protection
            Field f = MavenArchetypeMojo.class.getDeclaredField( name );
            f.setAccessible( true );
            f.set( this, value );
        }
        catch ( Exception e )
        {
            System.out.println( "Cannot set " + name + " to " + value + " exception=" + e );
        }
    }

    protected final String getGroupMarker( String groupId, String artifactId )
    {
        if ( compactNames && artifactId.startsWith( groupId ) )
        {
            return "-" + groupId;
        }

        return "+" + groupId;
    }

    protected final String getCompoundName( String groupId, String artifactId )
    {
        if ( compactNames && artifactId.startsWith( groupId ) )
        {
            return artifactId;
        }

        return groupId + "." + artifactId;
    }

    public void execute()
        throws MojoExecutionException
    {
        if ( checkEnvironment() == false )
        {
            return;
        }

        final String userDir = System.getProperty( "user.dir" );

        if ( targetDirectory != null )
        {
            System.setProperty( "user.dir", targetDirectory.getPath() );
        }

        updateFields();
        super.execute();
        postProcess();

        if ( targetDirectory != null )
        {
            System.setProperty( "user.dir", userDir );
        }
    }

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        return false;
    }

    private final void updateFields()
        throws MojoExecutionException
    {
        setField( "archetype", archetype );
        setField( "artifactRepositoryFactory", artifactRepositoryFactory );
        setField( "defaultArtifactRepositoryLayout", defaultArtifactRepositoryLayout );
        setField( "localRepository", localRepository );
        setField( "archetypeGroupId", archetypeGroupId );
        setField( "archetypeVersion", archetypeVersion );
        setField( "pomRemoteRepositories", pomRemoteRepositories );
        setField( "remoteRepositories", remoteRepositories );
        setField( "project", project );

        // to be set by the various OSGi archetype sub-classes
        // setField( "archetypeArtifactId", archetypeArtifactId );
        // setField( "groupId", groupId );
        // setField( "artifactId", artifactId );
        // setField( "version", version );
        // setField( "packageName", packageName );

        updateExtensionFields();
    }

    protected abstract void updateExtensionFields()
        throws MojoExecutionException;

    protected void postProcess()
        throws MojoExecutionException
    {
    }
}
