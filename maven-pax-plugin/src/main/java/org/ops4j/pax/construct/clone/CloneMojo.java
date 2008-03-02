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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Clones an existing project and produces a script (plus archetypes) to mimic its structure using Pax-Construct
 * 
 * <code><pre>
 *   mvn pax:clone
 * </pre></code>
 * 
 * @goal clone
 * @aggregator true
 */
public class CloneMojo extends AbstractMojo
{
    /**
     * Component factory for various archivers
     * 
     * @component
     */
    private ArchiverManager m_archiverManager;

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
     * @parameter expression="${project.build.directory}/clone"
     * @required
     * @readonly
     */
    private File m_tempdir;

    /**
     * The current Maven reactor.
     * 
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    private List m_reactorProjects;

    /**
     * When true, replace any local bundle dependencies with pax-import-bundle commands.
     * 
     * @parameter expression="${repair}"
     */
    private boolean repair;

    /**
     * When true, unify multiple projects under one single pax-create-project command. Warning: this doesn't merge
     * settings from sub-projects, such as &lt;dependencyManagement&gt;, so some manually editing will be necessary.
     * 
     * @parameter expression="${unify}"
     */
    private boolean unify;

    /**
     * List of directories that have already been processed
     */
    private List m_handledDirs;

    /**
     * Maps Maven POMs to pax-create-project commands
     */
    private Map m_majorProjectMap;

    /**
     * Maps groupId:artifactId to local bundle names
     */
    private Map m_bundleNameMap;

    /**
     * Sequence of archetypes with project/bundle content
     */
    private List m_installCommands;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        // general purpose Pax-Construct script
        PaxScript buildScript = new PaxScriptImpl();

        m_bundleNameMap = new HashMap();
        m_majorProjectMap = new HashMap();
        m_handledDirs = new ArrayList();

        m_installCommands = new ArrayList();

        getFragmentDir().mkdirs();

        for( Iterator i = m_reactorProjects.iterator(); i.hasNext(); )
        {
            // potential project to be converted / captured
            MavenProject project = (MavenProject) i.next();
            String packaging = project.getPackaging();

            // fixup standalone maven project
            if( m_reactorProjects.size() == 1 )
            {
                // always repair
                repair = true;

                // provide basic jar conversion
                if( "jar".equals( packaging ) )
                {
                    packaging = "bundle";
                }
            }

            if( "bundle".equals( packaging ) )
            {
                handleBundleProject( buildScript, project );
            }
            else if( "pom".equals( packaging ) )
            {
                if( isMajorProject( project ) )
                {
                    handleMajorProject( buildScript, project );
                }
                else
                {
                    handleBundleImport( buildScript, project );
                }
            }
            // else handled by the major project(s)
        }

        // grab everything else
        archiveMajorProjects();

        writePlatformScripts( buildScript );
    }

    /**
     * Write out various platform-specific scripts based on the abstract build script
     * 
     * @param script build script
     */
    private void writePlatformScripts( PaxScript script )
    {
        String cloneId = PomUtils.getCompoundId( m_rootGroupId, m_rootArtifactId );
        String scriptName = "pax-clone-" + cloneId;

        File winScript = new File( m_tempdir, scriptName + ".bat" );
        File nixScript = new File( m_tempdir, scriptName );

        getLog().info( "" );
        getLog().info( "SUCCESSFULLY CLONED " + cloneId );
        getLog().info( "" );

        String title = m_rootGroupId + ':' + m_rootArtifactId;

        try
        {
            getLog().info( "Saving UNIX shell script " + nixScript );
            script.write( title, nixScript, m_installCommands );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write " + nixScript );
        }

        try
        {
            getLog().info( "Saving Windows batch file " + winScript );
            script.write( title, winScript, m_installCommands );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write " + winScript );
        }

        getLog().info( "" );
        getLog().info( "FRAGMENT DIRECTORY " + getFragmentDir() );
    }

    /**
     * Analyze major project and build the right pax-create-project call
     * 
     * @param script build script
     * @param project major Maven project
     */
    private void handleMajorProject( PaxScript script, MavenProject project )
    {
        if( unify && !project.isExecutionRoot() )
        {
            // exclude the local poms settings from the unified project
            m_handledDirs.add( new File( project.getBasedir(), "poms" ) );
            return;
        }

        PaxCommandBuilder command = script.call( PaxScript.CREATE_PROJECT );

        command.option( 'g', project.getGroupId() );
        command.option( 'a', project.getArtifactId() );
        command.option( 'v', project.getVersion() );

        // enable overwrite
        command.flag( 'o' );

        setTargetDirectory( command, project.getBasedir().getParentFile() );
        registerProject( project );

        m_majorProjectMap.put( project, command );
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
        String bundleName;

        String namespace = findBundleNamespace( project );
        if( null != namespace )
        {
            bundleName = project.getArtifactId();

            command = script.call( PaxScript.CREATE_BUNDLE );
            command.option( 'p', namespace );
            if( !bundleName.equals( namespace ) )
            {
                command.option( 'n', bundleName );
            }
            command.option( 'v', project.getVersion() );
            command.maven().flag( "noDeps" );
        }
        else
        {
            Dependency wrappee = findWrappee( project );
            if( wrappee != null )
            {
                command = script.call( PaxScript.WRAP_JAR );
                command.option( 'g', wrappee.getGroupId() );
                command.option( 'a', wrappee.getArtifactId() );
                command.option( 'v', wrappee.getVersion() );

                if( repair )
                {
                    // this is expected to be the generated bundle name
                    bundleName = PomUtils.getCompoundId( wrappee.getGroupId(), wrappee.getArtifactId() );

                    // detect if we need to add the version back later on...
                    if( project.getArtifactId().endsWith( wrappee.getVersion() ) )
                    {
                        command.maven().flag( "addVersion" );
                    }
                }
                else
                {
                    bundleName = project.getArtifactId();

                    // need to retain the old name and version settings
                    command.maven().option( "bundleName", bundleName );
                    command.maven().option( "bundleVersion", project.getVersion() );
                }
            }
            else
            {
                getLog().warn( "Unable to clone bundle project " + project.getId() );
                return;
            }
        }

        Pom customizedPom = null;

        if( repair )
        {
            // fix references to local bundles by re-importing
            customizedPom = repairBundleImports( script, project );
        }
        else
        {
            // need to keep old groupId intact (name is already retained)
            command.maven().option( "bundleGroupId", project.getGroupId() );
        }

        addFragmentToCommand( command, createBundleArchetype( project, namespace, customizedPom ) );

        // enable overwrite
        command.flag( 'o' );

        setTargetDirectory( command, project.getBasedir().getParentFile() );
        registerProject( project );

        registerBundleName( project, bundleName );
    }

    /**
     * Analyze POM project and determine if any pax-import-bundle calls are needed
     * 
     * @param script build script
     * @param project Maven POM project
     */
    private void handleBundleImport( PaxScript script, MavenProject project )
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
            registerProject( project );
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

        // disconnected project, or project with its own set of "poms" where settings can be customized
        return ( null == project.getParent() || new File( project.getBasedir(), "poms" ).isDirectory() );
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

        // try to find a dependency that relates to the wrapper project
        if( dependencies.size() > 0 && !new File( sourcePath ).exists() )
        {
            for( Iterator i = dependencies.iterator(); i.hasNext(); )
            {
                Dependency dependency = (Dependency) i.next();
                if( project.getArtifactId().indexOf( dependency.getArtifactId() ) >= 0 )
                {
                    return dependency; // closest match
                }
            }

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

        return null;
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
            "**/*.java" // consider all Java sources
        };

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setIncludes( pathInclude );
        scanner.setFollowSymlinks( false );
        scanner.addDefaultExcludes();
        scanner.setBasedir( dir );

        scanner.scan();

        String path = null;

        String[] candidates = scanner.getIncludedFiles();
        for( int i = 0; i < scanner.getIncludedFiles().length; i++ )
        {
            String newPath = candidates[i];
            if( null == path || ( validCandidate( path, newPath ) && path.length() > newPath.length() ) )
            {
                path = newPath;
            }
        }

        return getJavaNamespace( path );
    }

    /**
     * @param current current primary java path
     * @param candidate candidate java path
     * @return true if the candidate might be a better primary java path, otherwise false
     */
    private boolean validCandidate( String current, String candidate )
    {
        // internal packages are good candidates to find the primary package
        String candidateFolder = new File( candidate ).getParentFile().getName();
        if( "internal".equalsIgnoreCase( candidateFolder ) || "impl".equalsIgnoreCase( candidateFolder ) )
        {
            return true;
        }

        // but if we already have an internal package, ignore non-internal packages
        String currentFolder = new File( current ).getParentFile().getName();
        if( "internal".equalsIgnoreCase( currentFolder ) || "impl".equalsIgnoreCase( currentFolder ) )
        {
            return false;
        }

        // neither is an internal package, so candidate package may be better
        return true;
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
        if( pivot != null && pivot[0].length() == 0 && pivot[2].length() > 0 )
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
     * @param namespace Java namespace, may be null
     * @param customizedPom customized Maven project model, may be null
     * @return clause identifying the archetype fragment
     * @throws MojoExecutionException
     */
    private String createBundleArchetype( MavenProject project, String namespace, Pom customizedPom )
        throws MojoExecutionException
    {
        File baseDir = project.getBasedir();

        getLog().info( "Cloning bundle project " + project.getArtifactId() );
        ArchetypeFragment fragment = new ArchetypeFragment( getFragmentDir(), namespace, false );
        fragment.addPom( baseDir, customizedPom );

        if( null != namespace )
        {
            fragment.addSources( baseDir, project.getBuild().getSourceDirectory(), false );
            fragment.addSources( baseDir, project.getBuild().getTestSourceDirectory(), true );
        }

        for( Iterator i = project.getTestResources().iterator(); i.hasNext(); )
        {
            Resource r = (Resource) i.next();
            fragment.addResources( baseDir, r.getDirectory(), r.getIncludes(), r.getExcludes(), true );
        }

        List excludes = new ArrayList();
        excludes.addAll( fragment.getIncludedFiles() );
        excludes.add( "target/" );
        excludes.add( "pom.xml" );

        // consider everything else in the bundle directory to be a resource
        fragment.addResources( baseDir, baseDir.getPath(), null, excludes, false );

        // archetype must use different id
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId() + "-archetype";
        String version = project.getVersion();

        // archive customized bundle sources, POM and Bnd instructions
        String fragmentId = groupId + ':' + artifactId + ':' + version;
        fragment.createArchive( fragmentId.replace( ':', '_' ), newJarArchiver() );

        return fragmentId;
    }

    /**
     * Attempt to repair bundle imports by replacing them with pax-import-bundle commands
     * 
     * @param script build script
     * @param project Maven project
     * @return customized Maven project model
     */
    private Pom repairBundleImports( PaxScript script, MavenProject project )
    {
        Pom pom;
        try
        {
            File tempFile = File.createTempFile( "pom", ".xml", m_tempdir );
            FileUtils.copyFile( project.getFile(), tempFile );
            pom = PomUtils.readPom( tempFile );
            tempFile.deleteOnExit();
        }
        catch( IOException e )
        {
            pom = null;
        }

        for( Iterator i = project.getDependencies().iterator(); i.hasNext(); )
        {
            Dependency dependency = (Dependency) i.next();

            String bundleId = dependency.getGroupId() + ':' + dependency.getArtifactId();
            String bundleName = (String) m_bundleNameMap.get( bundleId );

            // is this a local bundle project?
            if( null != bundleName )
            {
                PaxCommandBuilder command;

                command = script.call( PaxScript.IMPORT_BUNDLE );
                command.option( 'a', bundleName );

                // enable overwrite
                command.flag( 'o' );

                // only apply to the importing bundle project
                setTargetDirectory( command, project.getBasedir() );

                if( null != pom )
                {
                    pom.removeDependency( dependency );
                }
            }
        }

        try
        {
            if( null != pom )
            {
                pom.write();
            }
            return pom;
        }
        catch( IOException e )
        {
            return null;
        }
    }

    /**
     * Create archetype fragments for all recorded major projects (as well as any non-bundle modules)
     * 
     * @throws MojoExecutionException
     */
    private void archiveMajorProjects()
        throws MojoExecutionException
    {
        for( Iterator i = m_majorProjectMap.entrySet().iterator(); i.hasNext(); )
        {
            Map.Entry entry = (Map.Entry) i.next();
            MavenProject project = (MavenProject) entry.getKey();

            addFragmentToCommand( (PaxCommandBuilder) entry.getValue(), createProjectArchetype( project ) );
        }
    }

    /**
     * Archive all the selected resources under a single Maven archetype
     * 
     * @param project containing Maven project
     * @return clause identifying the archetype fragment
     * @throws MojoExecutionException
     */
    private String createProjectArchetype( MavenProject project )
        throws MojoExecutionException
    {
        File baseDir = project.getBasedir();

        getLog().info( "Cloning primary project " + project.getArtifactId() );
        ArchetypeFragment fragment = new ArchetypeFragment( getFragmentDir(), null, unify );
        fragment.addPom( baseDir, null );

        List excludes = new ArrayList();
        excludes.addAll( getExcludedPaths( project ) );
        excludes.add( "**/target/" );
        excludes.add( "pom.xml" );

        // consider everything else that's not been handled to be a resource
        fragment.addResources( baseDir, baseDir.getPath(), null, excludes, false );

        // archetype must use different id
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId() + "-archetype";
        String version = project.getVersion();

        // archive all the customized non-bundle POMs and projects
        String fragmentId = groupId + ':' + artifactId + ':' + version;
        fragment.createArchive( fragmentId.replace( ':', '_' ), newJarArchiver() );

        return fragmentId;
    }

    /**
     * Find which paths in this Maven project have already been collected, and should therefore be excluded
     * 
     * @param project major Maven project
     * @return list of excluded paths
     */
    private List getExcludedPaths( MavenProject project )
    {
        List excludes = new ArrayList();
        File baseDir = project.getBasedir();

        for( Iterator i = m_handledDirs.iterator(); i.hasNext(); )
        {
            File dir = (File) i.next();
            String[] pivot = DirUtils.calculateRelativePath( baseDir, dir );
            if( pivot != null && pivot[0].length() == 0 && pivot[2].length() > 0 )
            {
                excludes.add( pivot[2] );
            }
        }

        return excludes;
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

    /**
     * @return temporary fragment directory
     */
    private File getFragmentDir()
    {
        return new File( m_tempdir, "fragments" );
    }

    /**
     * @param project Maven project
     */
    private void registerProject( MavenProject project )
    {
        m_handledDirs.add( project.getBasedir() );
    }

    /**
     * @param project Maven bundle project
     * @param bundleName expected symbolic name of the bundle (null if imported)
     */
    private void registerBundleName( MavenProject project, String bundleName )
    {
        m_bundleNameMap.put( project.getGroupId() + ':' + project.getArtifactId(), bundleName );
    }

    /**
     * @param command one of the create commands
     * @param fragmentId archetype fragment id
     */
    private void addFragmentToCommand( PaxCommandBuilder command, String fragmentId )
    {
        if( null == fragmentId )
        {
            return;
        }

        command.maven().option( "contents", fragmentId );

        StringBuffer buffer = new StringBuffer();
        String[] ids = fragmentId.split( ":" );

        // add Maven command to install the archetype fragment before using it
        buffer.append( "mvn -N install:install-file \"-Dpackaging=jar\" \"-DgroupId=" );
        buffer.append( ids[0] );
        buffer.append( "\" \"-DartifactId=" );
        buffer.append( ids[1] );
        buffer.append( "\" \"-Dversion=" );
        buffer.append( ids[2] );
        buffer.append( "\" \"-Dfile=${_SCRIPTDIR_}/fragments/" );
        buffer.append( fragmentId.replace( ':', '_' ) );
        buffer.append( ".jar\"" );

        m_installCommands.add( buffer );
    }
}
