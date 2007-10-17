package org.ops4j.pax.construct.snapshot;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.ops4j.pax.construct.util.DirUtils;

/**
 * Snapshot a current project and produce a script to mimic its structure using Pax-Construct
 * 
 * <code><pre>
 *   mvn pax:snapshot
 * </pre></code>
 * 
 * @goal snapshot
 * @aggregator true
 */
public class SnapshotMojo extends AbstractMojo
{
    /**
     * Initiating base directory.
     * 
     * @parameter expression="${project.basedir}"
     * @required
     * @readonly
     */
    private File m_basedir;

    /**
     * When true, add missing parents for all POMs except the top-most one.
     * 
     * @parameter expression="${oneRoot}"
     */
    private boolean oneRoot;

    /**
     * The current Maven reactor.
     * 
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    private List m_reactorProjects;

    /**
     * Infrastructure POMs that cannot be directly translated
     */
    private List m_skippedProjects;

    /**
     * The generated Pax script to recreate the project
     */
    private PaxScriptBuilder m_buildScript;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        m_skippedProjects = new ArrayList();
        m_buildScript = new DefaultPaxScriptBuilder();

        for( Iterator i = m_reactorProjects.iterator(); i.hasNext(); )
        {
            MavenProject project = (MavenProject) i.next();
            MavenProject parent = project.getParent();
            String packaging = project.getPackaging();

            if( "bundle".equals( packaging ) )
            {
                handleBundle( project );
            }
            else if( !"pom".equals( packaging ) )
            {
                // TODO: copy this project unchanged
            }
            else if( !"poms".equals( project.getBasedir().getName() )
                && ( parent == null || !m_skippedProjects.contains( parent.getId() ) ) )
            {
                handleModule( project );
            }
            else
            {
                m_skippedProjects.add( project.getId() );
            }
        }

        System.err.println( m_buildScript.toString() );
    }

    void handleModule( MavenProject project )
    {
        if( project.isExecutionRoot() || ( null == project.getParent() && !oneRoot ) )
        {
            PaxOptionBuilder command = m_buildScript.at( 0 ).command( "pax-create-project" );
            command.option( 'g', project.getGroupId() );
            command.option( 'a', project.getArtifactId() );
            command.option( 'v', project.getVersion() );

            setTargetDirectory( command, project );
        }
    }

    Dependency findWrappee( MavenProject project )
    {
        Properties properties = project.getProperties();
        if( null == properties )
        {
            return null;
        }

        Dependency wrappee = new Dependency();

        wrappee.setGroupId( properties.getProperty( "jar.groupId" ) );
        wrappee.setArtifactId( properties.getProperty( "jar.artifactId" ) );
        wrappee.setVersion( properties.getProperty( "jar.version" ) );

        if( wrappee.getArtifactId() == null )
        {
            wrappee.setGroupId( properties.getProperty( "wrapped.groupId" ) );
            wrappee.setArtifactId( properties.getProperty( "wrapped.artifactId" ) );
            wrappee.setVersion( properties.getProperty( "wrapped.version" ) );
        }

        if( wrappee.getArtifactId() != null )
        {
            return wrappee;
        }

        return null;
    }

    String findBundleNamespace( MavenProject project )
    {
        String namespace = null;

        Properties properties = project.getProperties();
        if( null != properties )
        {
            namespace = properties.getProperty( "bundle.package" );
            if( null == namespace )
            {
                namespace = properties.getProperty( "bundle.namespace" );
            }
        }

        if( null == namespace )
        {
            // FIXME: find best package
        }

        return namespace;
    }

    void handleBundle( MavenProject project )
    {
        PaxOptionBuilder command;

        Dependency wrappee = findWrappee( project );
        if( wrappee != null )
        {
            command = handleWrapper( project, wrappee );
        }
        else
        {
            String namespace = findBundleNamespace( project );
            if( namespace != null )
            {
                command = m_buildScript.command( "pax-create-bundle" );
                command.option( 'p', namespace );
                command.option( 'n', project.getArtifactId() );
                command.option( 'v', project.getVersion() );

                // TODO: clone bundle source code and BND settings
            }
            else
            {
                m_buildScript.comment( "Skipped " + project.getId() );
                return;
            }
        }

        setTargetDirectory( command, project );
    }

    PaxOptionBuilder handleWrapper( MavenProject project, Dependency wrappee )
    {
        PaxOptionBuilder command = m_buildScript.command( "pax-wrap-jar" );

        command.option( 'g', wrappee.getGroupId() );
        command.option( 'a', wrappee.getArtifactId() );
        command.option( 'v', wrappee.getVersion() );

        // TODO: clone BND settings

        if( project.getArtifactId().endsWith( wrappee.getVersion() ) )
        {
            command.maven().flag( "addVersion" );
        }

        return command;
    }

    void setTargetDirectory( PaxOptionBuilder command, MavenProject project )
    {
        File targetDir = project.getBasedir().getParentFile();
        String[] pivot = DirUtils.calculateRelativePath( m_basedir.getParentFile(), targetDir );

        if( pivot != null && pivot[2].length() > 0 )
        {
            command.maven().option( "targetDirectory", pivot[2] );
        }
    }
}
