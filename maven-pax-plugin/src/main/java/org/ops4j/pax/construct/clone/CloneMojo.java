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
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.StringUtils;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomIterator;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Clones an existing project and produces a script (plus archetypes) to mimic its structure using Pax-Construct
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
     * Component factory for various archivers
     * 
     * @component
     */
    private ArchiverManager m_archiverManager;

    /**
     * Component for installing Maven artifacts
     * 
     * @component
     */
    private ArtifactInstaller m_installer;

    /**
     * The local Maven repository.
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
     * Ids of Maven project that we've already handled
     */
    private List m_handledProjectIds;

    /**
     * Standard Maven mojo entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        // general purpose Pax-Construct script
        PaxScript buildScript = new PaxScriptImpl();

        m_handledProjectIds = new ArrayList();
        List majorProjectIds = new ArrayList();

        for( Iterator i = m_reactorProjects.iterator(); i.hasNext(); )
        {
            // potential project to be converted / captured
            MavenProject project = (MavenProject) i.next();
            String packaging = project.getPackaging();

            if( "bundle".equals( packaging ) )
            {
                handleBundleProject( buildScript, project );
            }
            else if( "pom".equals( packaging ) )
            {
                if( isMajorProject( project ) )
                {
                    majorProjectIds.add( project.getId() );
                }
                else
                {
                    handleBundleImport( buildScript, project );
                }
            }
            // else handled by the major project(s)
        }

        // collect up all non-bundle projects and settings
        archiveMajorProjects( buildScript, majorProjectIds );
        writePlatformScripts( buildScript );
    }

    /**
     * Write out various platform-specific scripts based on the abstract build script
     * 
     * @param script build script
     */
    private void writePlatformScripts( PaxScript script )
    {
        String scriptName = "pax-clone-" + PomUtils.getCompoundId( m_rootGroupId, m_rootArtifactId );

        File winScript = new File( m_tempdir, scriptName + ".bat" );
        File nixScript = new File( m_tempdir, scriptName );

        try
        {
            getLog().info( "Saving UNIX shell script " + nixScript );
            script.write( nixScript );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write " + nixScript );
        }

        try
        {
            getLog().info( "Saving Windows batch file " + winScript );
            script.write( winScript );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write " + winScript );
        }
    }

    /**
     * Analyze bundle project and determine if any pax-create-bundle or pax-wrap-jar calls are needed
     * 
     * @param script build script
     * @param project Maven bundle project
     * @throws MojoExecutionException
     */
    private void handleBundleProject( PaxScript script, MavenProject project )
        throws MojoExecutionException
    {
        PaxCommandBuilder command;
        String contents;

        Dependency wrappee = findWrappee( project );
        if( wrappee != null )
        {
            command = script.call( PaxScript.WRAP_JAR );
            command.option( 'g', wrappee.getGroupId() );
            command.option( 'a', wrappee.getArtifactId() );
            command.option( 'v', wrappee.getVersion() );

            if( project.getArtifactId().endsWith( wrappee.getVersion() ) )
            {
                command.maven().flag( "addVersion" );
            }

            contents = createBundleArchetype( project, null );
        }
        else
        {
            String namespace = findBundleNamespace( project );

            command = script.call( PaxScript.CREATE_BUNDLE );
            command.option( 'p', namespace );
            command.option( 'n', project.getArtifactId() );
            command.option( 'v', project.getVersion() );

            contents = createBundleArchetype( project, namespace );
        }

        // customized sources, POMs, Bnd instructions
        command.maven().option( "contents", contents );

        // enable overwrite
        command.flag( 'o' );

        setTargetDirectory( command, project.getBasedir().getParentFile() );
        m_handledProjectIds.add( project.getId() );
    }

    /**
     * Analyze POM project and determine if any pax-import-bundle calls are needed
     * 
     * @param script build script
     * @param project Maven POM project
     * @throws MojoExecutionException
     */
    private void handleBundleImport( PaxScript script, MavenProject project )
        throws MojoExecutionException
    {
        Dependency importee = findImportee( project );
        if( importee != null )
        {
            PaxCommandBuilder command;

            command = script.call( PaxScript.IMPORT_BUNDLE );
            command.option( 'g', importee.getGroupId() );
            command.option( 'a', importee.getArtifactId() );
            command.option( 'v', importee.getVersion() );

            // enable overwrite
            command.flag( 'o' );

            // imported bundles now in provision POM
            setTargetDirectory( command, m_basedir );
            m_handledProjectIds.add( project.getId() );
        }
        // else handled by the major project
    }

    /**
     * Analyze the position of this project in the tree, as not all projects need their own distinct set of "poms"
     * 
     * @param project Maven POM project
     * @return true if this project requires a pax-create-project call
     */
    private boolean isMajorProject( MavenProject project )
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
    private Dependency findWrappee( MavenProject project )
    {
        Properties properties = project.getProperties();
        Dependency wrappee = new Dependency();

        // Pax-Construct v2
        wrappee.setGroupId( properties.getProperty( "wrapped.groupId" ) );
        wrappee.setArtifactId( properties.getProperty( "wrapped.artifactId" ) );
        wrappee.setVersion( properties.getProperty( "wrapped.version" ) );

        if( null == wrappee.getArtifactId() )
        {
            // original Pax-Construct
            wrappee.setGroupId( properties.getProperty( "jar.groupId" ) );
            wrappee.setArtifactId( properties.getProperty( "jar.artifactId" ) );
            wrappee.setVersion( properties.getProperty( "jar.version" ) );

            if( null == wrappee.getArtifactId() )
            {
                // has someone customized their wrapper?
                return findCustomizedWrappee( project );
            }
        }

        return wrappee;
    }

    /**
     * Analyze project structure to try to deduce if this really is a wrapper
     * 
     * @param project Maven bundle project
     * @return wrapped artifact, null if it isn't a wrapper project
     */
    private Dependency findCustomizedWrappee( MavenProject project )
    {
        List dependencies = project.getDependencies();
        String sourcePath = project.getBuild().getSourceDirectory();

        // assume first dependency is wrapped artifact (unless has source)
        if( dependencies.size() > 0 && !new File( sourcePath ).exists() )
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
    private Dependency findImportee( MavenProject project )
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
        else
        {
            return null;
        }
    }

    /**
     * Analyze bundle project to find the primary namespace it provides
     * 
     * @param project Maven project
     * @return primary Java namespace
     */
    private String findBundleNamespace( MavenProject project )
    {
        Properties properties = project.getProperties();

        // Pax-Construct v2
        String namespace = properties.getProperty( "bundle.namespace" );
        if( null == namespace )
        {
            // original Pax-Construct
            namespace = properties.getProperty( "bundle.package" );
            String sourcePath = project.getBuild().getSourceDirectory();
            if( null == namespace && new File( sourcePath ).exists() )
            {
                namespace = findPrimaryPackage( sourcePath );
            }
        }

        return namespace;
    }

    /**
     * Find the most likely candidate for the primary Java package
     * 
     * @param dir source directory
     * @return primary Java package
     */
    private String findPrimaryPackage( String dir )
    {
        String[] pathInclude = new String[]
        {
            // look for bundle activators or any internal source code
            "**/*Activator.java", "**/internal/*.java", "**/impl/*.java"
        };

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setIncludes( pathInclude );
        scanner.setFollowSymlinks( false );
        scanner.addDefaultExcludes();
        scanner.setBasedir( dir );

        scanner.scan();

        String javaFile = null;

        String[] candidates = scanner.getIncludedFiles();
        for( int i = 0; i < scanner.getIncludedFiles().length; i++ )
        {
            // favour bundle activators over everything
            if( null == javaFile || candidates[i].endsWith( "Activator.java" ) )
            {
                javaFile = candidates[i];
            }
            // otherwise pick the internal package with the shortest path
            else if( !javaFile.endsWith( "Activator.java" ) && candidates[i].length() < javaFile.length() )
            {
                javaFile = candidates[i];
            }
        }

        return getJavaNamespace( javaFile );
    }

    /**
     * Convert source code location into dotted Java namespace
     * 
     * @param javaFile source location
     * @return Java namespace
     */
    private String getJavaNamespace( String javaFile )
    {
        if( null == javaFile )
        {
            return null;
        }

        // strip the classname and any internal package
        File packageDir = new File( javaFile ).getParentFile();
        if( "internal".equals( packageDir.getName() ) || "impl".equals( packageDir.getName() ) )
        {
            packageDir = packageDir.getParentFile();
        }

        // standard slashes to dots conversion
        return packageDir.getPath().replaceAll( "[/\\\\]+", "." );
    }

    /**
     * Set the directory where the Pax-Construct command should be run
     * 
     * @param command Pax-Construct command
     * @param targetDir target directory
     */
    private void setTargetDirectory( PaxCommandBuilder command, File targetDir )
    {
        String[] pivot = DirUtils.calculateRelativePath( m_basedir.getParentFile(), targetDir );
        if( pivot != null && pivot[2].length() > 0 )
        {
            // fix path to use the correct artifactId, in case directory tree has been renamed
            String relativePath = StringUtils.replaceOnce( pivot[2], m_basedir.getName(), m_rootArtifactId );
            command.maven().option( "targetDirectory", relativePath );
        }
    }

    /**
     * Create a new archetype for a bundle project, with potentially customized POM and Bnd settings
     * 
     * @param project Maven project
     * @param namespace Java namespace, can be null
     * @return clause identifying the archetype fragment
     * @throws MojoExecutionException
     */
    private String createBundleArchetype( MavenProject project, String namespace )
        throws MojoExecutionException
    {
        ArchetypeFragment fragment = new ArchetypeFragment( m_tempdir, namespace );

        fragment.addPom( project.getBasedir() );
        fragment.addResources( project.getBasedir(), "osgi.bnd", false );

        if( null != namespace )
        {
            fragment.addSources( project.getBasedir(), project.getBuild().getSourceDirectory(), false );
            fragment.addSources( project.getBasedir(), project.getBuild().getTestSourceDirectory(), true );
        }

        for( Iterator i = project.getResources().iterator(); i.hasNext(); )
        {
            fragment.addResources( project.getBasedir(), ( (Resource) i.next() ).getDirectory(), false );
        }
        for( Iterator i = project.getTestResources().iterator(); i.hasNext(); )
        {
            fragment.addResources( project.getBasedir(), ( (Resource) i.next() ).getDirectory(), true );
        }

        // archetype must use different id
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId() + "-archetype";
        String version = project.getVersion();

        // install in local repository so it's accessible to the Pax-Construct scripts later on
        Artifact artifact = m_factory.createBuildArtifact( groupId, artifactId, version, "jar" );
        fragment.install( artifact, newJarArchiver(), m_installer, m_localRepo );

        return groupId + ':' + artifactId + ':' + version;
    }

    /**
     * Go through local project tree looking for non-bundle files to archive
     * 
     * @param script build script
     * @param majorProjectIds list of major projects
     * @throws MojoExecutionException
     */
    private void archiveMajorProjects( PaxScript script, List majorProjectIds )
        throws MojoExecutionException
    {
        PaxCommandBuilder command = null;
        List resourcePaths = new ArrayList();

        Pom majorPom = null;
        for( Iterator i = new PomIterator( m_basedir, true ); i.hasNext(); )
        {
            Pom pom = (Pom) i.next();
            if( majorProjectIds.contains( pom.getId() ) )
            {
                // flush previous major archetype to local repo
                createMajorArchetype( majorPom, command, resourcePaths );

                command = script.call( PaxScript.CREATE_PROJECT );
                command.option( 'g', pom.getGroupId() );
                command.option( 'a', pom.getArtifactId() );
                command.option( 'v', pom.getVersion() );

                // enable overwrite
                command.flag( 'o' );

                setTargetDirectory( command, pom.getBasedir().getParentFile() );

                resourcePaths.clear();
                majorPom = pom;
            }
            else if( !m_handledProjectIds.contains( pom.getId() ) )
            {
                if( "pom".equals( pom.getPackaging() ) )
                {
                    // only copy the POM, not subfolders
                    resourcePaths.add( pom.getFile() );
                }
                else
                {
                    // add the entire non-bundle project
                    resourcePaths.add( pom.getBasedir() );
                }
            }
        }

        // flush previous major archetype to local repo
        createMajorArchetype( majorPom, command, resourcePaths );
    }

    /**
     * Archive all the selected resources under a single Maven archetype
     * 
     * @param majorPom containing Maven project
     * @param command create-project command
     * @param resourcePaths selected resources
     * @throws MojoExecutionException
     */
    private void createMajorArchetype( Pom majorPom, PaxCommandBuilder command, List resourcePaths )
        throws MojoExecutionException
    {
        // nothing to do!
        if( null == majorPom )
        {
            return;
        }

        ArchetypeFragment fragment = new ArchetypeFragment( m_tempdir, null );
        fragment.addPom( majorPom.getBasedir() );

        for( Iterator i = resourcePaths.iterator(); i.hasNext(); )
        {
            fragment.addResources( majorPom.getBasedir(), i.next().toString(), false );
        }

        // archetype must use different id
        String groupId = majorPom.getGroupId();
        String artifactId = majorPom.getArtifactId() + "-archetype";
        String version = majorPom.getVersion();

        // install in local repository so it's accessible to the Pax-Construct scripts later on
        Artifact artifact = m_factory.createBuildArtifact( groupId, artifactId, version, "jar" );
        fragment.install( artifact, newJarArchiver(), m_installer, m_localRepo );
        String fragmentId = groupId + ':' + artifactId + ':' + version;

        // customized POMs and non-bundle projects
        command.maven().option( "contents", fragmentId );
    }

    /**
     * @return new Jar archiver
     * @throws MojoExecutionException
     */
    private Archiver newJarArchiver()
        throws MojoExecutionException
    {
        try
        {
            return m_archiverManager.getArchiver( "jar" );
        }
        catch( NoSuchArchiverException e )
        {
            throw new MojoExecutionException( "Unable to find Jar archiver", e );
        }
    }
}
