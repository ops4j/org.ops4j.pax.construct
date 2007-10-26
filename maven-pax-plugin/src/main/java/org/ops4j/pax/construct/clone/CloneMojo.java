package org.ops4j.pax.construct.clone;

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
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;

/**
 * Clones an existing project and produces a script to mimic its structure using Pax-Construct
 * 
 * <code><pre>
 *   mvn pax:clone [-DunifyRoot]
 * </pre></code>
 * 
 * @goal clone
 * @aggregator true
 */
public class CloneMojo extends AbstractMojo
{
    /**
     * Component factory for Maven artifacts
     * 
     * @component
     */
    private ArtifactFactory m_factory;

    /**
     * Jar archiver
     * 
     * @component role-hint="jar"
     */
    private Archiver m_archiver;

    /**
     * Component for installing Maven artifacts
     * 
     * @component
     */
    private ArtifactInstaller m_installer;

    /**
     * The local Maven repository for the containing project.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository m_localRepo;

    /**
     * Initiating groupId.
     * 
     * @parameter expression="${project.groupId}"
     * @required
     * @readonly
     */
    private String m_rootGroupId;

    /**
     * Initiating artifactId.
     * 
     * @parameter expression="${project.artifactId}"
     * @required
     * @readonly
     */
    private String m_rootArtifactId;

    /**
     * Initiating base directory.
     * 
     * @parameter expression="${project.basedir}"
     * @required
     * @readonly
     */
    private File m_basedir;

    /**
     * Temporary directory, where scripts and templates will be saved.
     * 
     * @parameter expression="${project.build.directory}"
     * @required
     * @readonly
     */
    private File m_tempdir;

    /**
     * When true, ensures all projects are connected to the top-most project.
     * 
     * @parameter expression="${unifyRoot}"
     */
    private boolean unifyRoot;

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
    private PaxScript m_buildScript;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        m_skippedProjects = new ArrayList();
        m_buildScript = new PaxScriptImpl();

        for( Iterator i = m_reactorProjects.iterator(); i.hasNext(); )
        {
            MavenProject project = (MavenProject) i.next();
            String packaging = project.getPackaging();

            if( "bundle".equals( packaging ) )
            {
                handleBundle( project );
            }
            else if( !"pom".equals( packaging ) )
            {
                // non-OSGi project?
                cloneDirectory( project );
            }
            else
            {
                handleModule( project );
            }
        }

        String scriptName = "pax-clone-" + PomUtils.getCompoundId( m_rootGroupId, m_rootArtifactId );
        File winScript = new File( m_tempdir, scriptName + ".bat" );
        File nixScript = new File( m_tempdir, scriptName );

        try
        {
            getLog().info( "Saving UNIX shell script " + nixScript );
            m_buildScript.write( nixScript );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write " + nixScript );
        }

        try
        {
            getLog().info( "Saving Windows batch file " + winScript );
            m_buildScript.write( winScript );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write " + winScript );
        }
    }

    /**
     * Analyze POM project and determine if any pax-create-project or pax-import-bundle calls are needed
     * 
     * @param project Maven POM project
     */
    void handleModule( MavenProject project )
    {
        PaxCommandBuilder command = null;

        Dependency importee = findImportee( project );
        if( importee != null )
        {
            command = handleImportee( project, importee );
        }
        else if( isMajorProject( project ) )
        {
            command = m_buildScript.call( PaxScript.CREATE_PROJECT );
            command.option( 'g', project.getGroupId() );
            command.option( 'a', project.getArtifactId() );
            command.option( 'v', project.getVersion() );
        }
        else
        {
            m_skippedProjects.add( project.getId() );
            return;
        }

        setTargetDirectory( command, project.getBasedir().getParentFile() );
    }

    /**
     * Analyze the position of this project in the tree, as not all projects need their own distinct set of "poms"
     * 
     * @param project Maven POM project
     * @return true if this project requires a pax-create-project call
     */
    boolean isMajorProject( MavenProject project )
    {
        if( project.isExecutionRoot() )
        {
            // top-most project
            return true;
        }
        else if( unifyRoot )
        {
            // there can be only one!
            return false;
        }
        else
        {
            // disconnected project, or project with its own set of "poms" where settings can be customized
            return ( null == project.getParent() || new File( project.getBasedir(), "poms" ).isDirectory() );
        }
    }

    /**
     * Analyze bundle project to see if it actually just wraps another artifact
     * 
     * @param project Maven bundle project
     * @return wrapped artifact, null if it isn't a wrapper project
     */
    Dependency findWrappee( MavenProject project )
    {
        Properties properties = project.getProperties();
        Dependency wrappee = new Dependency();

        // original Pax-Construct
        wrappee.setGroupId( properties.getProperty( "jar.groupId" ) );
        wrappee.setArtifactId( properties.getProperty( "jar.artifactId" ) );
        wrappee.setVersion( properties.getProperty( "jar.version" ) );

        if( wrappee.getArtifactId() == null )
        {
            // Pax-Construct v2
            wrappee.setGroupId( properties.getProperty( "wrapped.groupId" ) );
            wrappee.setArtifactId( properties.getProperty( "wrapped.artifactId" ) );
            wrappee.setVersion( properties.getProperty( "wrapped.version" ) );
        }

        if( wrappee.getArtifactId() == null )
        {
            // has someone customized their wrapper?
            return findCustomizedWrapper( project );
        }
        else
        {
            return wrappee;
        }
    }

    /**
     * Analyze project structure to try to deduce if this really is a wrapper
     * 
     * @param project Maven bundle project
     * @return wrapped artifact, null if it isn't a wrapper project
     */
    Dependency findCustomizedWrapper( MavenProject project )
    {
        // check in case it's really a compiled bundle
        if( findBundleNamespace( project ) != null )
        {
            return null;
        }

        // assume first dependency is the wrapped artifact
        List dependencies = project.getDependencies();
        if( dependencies.size() > 0 )
        {
            return (Dependency) dependencies.get( 0 );
        }

        return null;
    }

    /**
     * Analyze POM project to see if it actually just imports an existing bundle
     * 
     * @param project Maven POM project
     * @return imported bundle, null if it isn't a import project
     */
    Dependency findImportee( MavenProject project )
    {
        Properties properties = project.getProperties();
        Dependency importee = new Dependency();

        // original Pax-Construct
        importee.setGroupId( properties.getProperty( "bundle.groupId" ) );
        importee.setArtifactId( properties.getProperty( "bundle.artifactId" ) );
        importee.setVersion( properties.getProperty( "bundle.version" ) );

        if( importee.getArtifactId() != null )
        {
            return importee;
        }

        return null;
    }

    /**
     * Analyze bundle project to find the namespace (ie. the key Java package)
     * 
     * @param project Maven bundle project
     * @return key namespace for the bundle
     */
    String findBundleNamespace( MavenProject project )
    {
        String namespace = null;

        Properties properties = project.getProperties();

        // original Pax-Construct
        namespace = properties.getProperty( "bundle.package" );
        if( null == namespace )
        {
            // Pax-Construct v2
            namespace = properties.getProperty( "bundle.namespace" );
        }
        if( null == namespace )
        {
            // non Pax-Construct OSGi bundle
            namespace = findTopJavaPackage( project );
        }

        return namespace;
    }

    /**
     * Try to identify the key Java package this bundle provides
     * 
     * @param project Maven bundle project
     * @return key Java package
     */
    String findTopJavaPackage( MavenProject project )
    {
        // TODO: find best package
        return null;
    }

    /**
     * Analyze bundle project and determine if any pax-create-bundle or pax-wrap-jar calls are needed
     * 
     * @param project Maven bundle project
     * @throws MojoExecutionException
     */
    void handleBundle( MavenProject project )
        throws MojoExecutionException
    {
        PaxCommandBuilder command;

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
                command = m_buildScript.call( PaxScript.CREATE_BUNDLE );
                command.option( 'p', namespace );
                command.option( 'n', project.getArtifactId() );
                command.option( 'v', project.getVersion() );

                Properties properties = new Properties();
                properties.setProperty( "package", namespace.replace( '.', '/' ) );

                createBundleArchetype( project, namespace );

                // TODO: clone bundle source code and BND settings

                command.maven().option( "internals", "false" );
            }
            else
            {
                m_skippedProjects.add( project.getId() );
                return;
            }
        }

        // TODO: merge in dependency and other POM fragments?

        setTargetDirectory( command, project.getBasedir().getParentFile() );
    }

    /**
     * Add a call to wrap the given artifact as done in the existing project
     * 
     * @param project Maven bundle project
     * @param wrappee artifact to wrap
     * @return Pax-Construct command builder
     */
    PaxCommandBuilder handleWrapper( MavenProject project, Dependency wrappee )
    {
        PaxCommandBuilder command = m_buildScript.call( PaxScript.WRAP_JAR );

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

    /**
     * Add a call to import the given bundle
     * 
     * @param project Maven bundle project
     * @param importee bundle to import
     * @return Pax-Construct command builder
     */
    PaxCommandBuilder handleImportee( MavenProject project, Dependency importee )
    {
        PaxCommandBuilder command = m_buildScript.call( PaxScript.IMPORT_BUNDLE );

        command.option( 'g', importee.getGroupId() );
        command.option( 'a', importee.getArtifactId() );
        command.option( 'v', importee.getVersion() );

        return command;
    }

    /**
     * Set the directory where the Pax-Construct command should be run
     * 
     * @param command Pax-Construct command
     * @param targetDir target directory
     */
    void setTargetDirectory( PaxCommandBuilder command, File targetDir )
    {
        String[] pivot = DirUtils.calculateRelativePath( m_basedir.getParentFile(), targetDir );
        if( pivot != null && pivot[2].length() > 0 )
        {
            // fix path to use the correct artifactId, in case directory tree has been renamed
            String relativePath = pivot[2].replaceFirst( m_basedir.getName(), m_rootArtifactId );
            command.maven().option( "targetDirectory", relativePath );
        }
    }

    /**
     * Archive the contents of this project so it can be unpacked later on
     * 
     * @param project non-OSGi Maven project
     */
    void cloneDirectory( MavenProject project )
    {
        // TODO: clone entire directory
    }

    void createBundleArchetype( MavenProject project, String namespace )
        throws MojoExecutionException
    {
        File archetypeDir = new File( m_tempdir, "archetype-fragment" );
        ArchetypeFragment fragment = new ArchetypeFragment( archetypeDir );

        fragment.addSources( project.getBasedir(), "src/main/java", namespace, false );
        fragment.addResources( project.getBasedir(), "src/main/resources", namespace, false );

        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId() + "-archetype";
        String version = project.getVersion();

        Artifact artifact = m_factory.createBuildArtifact( groupId, artifactId, version, "jar" );
        fragment.install( artifact, m_archiver, m_installer, m_localRepo );
    }
}
