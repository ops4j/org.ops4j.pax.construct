package org.ops4j.pax.construct.lifecycle;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.eclipse.EclipsePlugin;
import org.apache.maven.plugin.eclipse.EclipseSourceDir;
import org.apache.maven.plugin.eclipse.writers.EclipseClasspathWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseProjectWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseSettingsWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.shared.osgi.Maven2OsgiConverter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.DirUtils.EntryFilter;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.ReflectMojo;
import org.ops4j.pax.construct.util.StreamFactory;

/**
 * Extends <a href="http://maven.apache.org/plugins/maven-eclipse-plugin/eclipse-mojo.html">EclipsePlugin</a> to
 * provide customized Eclipse project files for Pax-Construct projects.<br/>Inherited parameters can still be used, but
 * unfortunately don't appear in the generated docs.
 * 
 * @extendsPlugin eclipse
 * @goal eclipse
 * @phase package
 * 
 * @execute phase="none"
 */
public class EclipseOSGiMojo extends EclipsePlugin
{
    /**
     * Component factory for Maven projects
     * 
     * @component
     */
    private MavenProjectBuilder m_mavenProjectBuilder;

    /**
     * Component to convert between Maven and OSGi versions
     * 
     * @component
     */
    private Maven2OsgiConverter m_maven2OsgiConverter;

    /**
     * Provide access to the private fields of the Eclipse mojo
     */
    private ReflectMojo m_eclipseMojo;

    /**
     * The main project when generating Eclipse project files for imported bundles
     */
    private MavenProject m_provisionProject;

    /**
     * IDE dependencies that might be embedded inside the bundle
     */
    private List m_embeddableDependencies;

    /**
     * {@inheritDoc}
     */
    public boolean setup()
        throws MojoExecutionException
    {
        // we don't fork eclipse goal
        setExecutedProject( project );

        if( null != m_provisionProject )
        {
            enablePDE(); // imported OSGi bundle
        }
        else if( PomUtils.isBundleProject( executedProject ) )
        {
            enablePDE(); // compiled OSGi bundle

            setUseProjectReferences( false );
        }
        else if( ProvisionMojo.isProvisioningPom( executedProject ) )
        {
            try
            {
                /*
                 * unpack imported bundles and generate Eclipse files one-by-one
                 */
                setupImportedBundles();
            }
            catch( InvalidDependencyVersionException e )
            {
                getLog().warn( "Unable to generate Eclipse files for project " + executedProject.getId() );
            }

            /*
             * don't create Eclipse files for the provisioning POM itself!
             */
            return false;
        }

        // default to normal behaviour
        return super.setup();
    }

    /**
     * Enable PDE support
     */
    private void enablePDE()
    {
        if( null == m_eclipseMojo )
        {
            // by default enable creation of PDE project files for OSGi
            m_eclipseMojo = new ReflectMojo( this, EclipsePlugin.class );
        }

        m_eclipseMojo.setField( "pde", Boolean.TRUE );
        setWtpversion( "none" );
    }

    /**
     * {@inheritDoc}
     */
    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        if( !isPdeProject() )
        {
            // non-OSGi project
            super.writeConfiguration( deps );
        }
        else
        {
            m_embeddableDependencies = new ArrayList();

            if( null == m_provisionProject )
            {
                // compiled OSGi bundle / wrapper
                writeBundleConfiguration( deps );
            }
            else
            {
                // imported (external) OSGi bundle
                writeImportedConfiguration();
            }
        }
    }

    /**
     * Customize Eclipse project files for Pax-Construct generated bundles
     * 
     * @param deps resolved project dependencies, potentially with sources and javadocs
     * @throws MojoExecutionException
     */
    private void writeBundleConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        for( int i = 0; i < deps.length; i++ )
        {
            if( deps[i].isAddedToClasspath() )
            {
                // cache here so we can attach sources to embedded entries
                if( !deps[i].isTestDependency() && !deps[i].isProvided() )
                {
                    m_embeddableDependencies.add( deps[i] );
                }

                /*
                 * Change potential test dependencies to be non-OSGi otherwise they won't appear as Required Libraries.
                 * We include provided dependencies here because PDE might not add them as Plug-in Dependencies, it all
                 * depends on the manifest (which won't necessarily import packages used during testing)
                 */
                deps[i] = fixOSGiTestDependency( deps[i] );
            }
        }

        EclipseWriterConfig config = createEclipseWriterConfig( deps );

        config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );

        new EclipseSettingsWriter().init( getLog(), config ).write();
        new EclipseClasspathWriter().init( getLog(), config ).write();
        new EclipseProjectWriter().init( getLog(), config ).write();

        /*
         * copy bundle manifest to where PDE expects it, but tweak it to fix embedded paths
         */
        refactorForEclipse( getBundleFile( executedProject ) );

        writeAdditionalConfig();
    }

    /**
     * @param bundleProject bundle project
     * @return recently built bundle
     */
    private File getBundleFile( MavenProject bundleProject )
    {
        Artifact artifact = bundleProject.getArtifact();
        File bundleFile = artifact.getFile();

        if( null == bundleFile || !bundleFile.exists() )
        {
            // no file attached in this cycle, so check local build
            String name = bundleProject.getBuild().getFinalName() + ".jar";
            bundleFile = new File( bundleProject.getBuild().getDirectory(), name );
        }

        if( !bundleFile.exists() )
        {
            // last chance: see if it is already has been installed locally
            PomUtils.getFile( artifact, artifactResolver, localRepository );
            bundleFile = artifact.getFile();
        }

        return bundleFile;
    }

    /**
     * The maven-eclipse-plugin has a bug where test dependencies that are also OSGi bundles are excluded from the
     * .classpath because they are expected to be on the Bundle-ClassPath. However, this is not a valid assumption
     * because the Maven test-cases and their dependencies do not necessarily end-up packaged inside the bundle.
     * 
     * @param dependency an IDE test dependency
     * @return fixed IDE test dependency
     */
    private IdeDependency fixOSGiTestDependency( IdeDependency dependency )
    {
        // unfortunately there's no setIsOsgiBundle() method, so we have to replace the whole dependency...
        IdeDependency testDependency = new IdeDependency( dependency.getGroupId(), dependency.getArtifactId(),
            dependency.getVersion(), dependency.getClassifier(), dependency.isReferencedProject(), true, false, false,
            dependency.isAddedToClasspath(), dependency.getFile(), dependency.getType(), false, null, 0,
            dependency.getEclipseProjectName() );

        testDependency.setSourceAttachment( dependency.getSourceAttachment() );
        testDependency.setJavadocAttachment( dependency.getJavadocAttachment() );

        return testDependency;
    }

    /**
     * Provide better naming for Pax-Construct generated OSGi bundles
     * 
     * @param project current Maven project
     * @param addVersion when true, add the project version to the name
     * @return an Eclipse friendly name for the bundle
     */
    private static String getEclipseProjectName( MavenProject project, boolean addVersion )
    {
        String projectName = project.getProperties().getProperty( "bundle.symbolicName" );
        if( null == projectName )
        {
            // fall back to standard "groupId.artifactId" but try to eliminate duplicate segments
            projectName = PomUtils.getCompoundId( project.getGroupId(), project.getArtifactId() );
        }

        if( addVersion )
        {
            // check for wrapper version, which reflects the version of the wrapped contents
            String projectVersion = project.getProperties().getProperty( "wrapped.version" );
            if( null == projectVersion )
            {
                projectVersion = project.getVersion();
            }

            return projectName + " [" + projectVersion + ']';
        }

        return projectName;
    }

    /**
     * Select files for unpacking that don't exist locally under target/classes
     */
    private static class IncludedContentFilter
        implements EntryFilter
    {
        private final File m_outputDir;

        /**
         * @param outputDir build output directory
         */
        public IncludedContentFilter( File outputDir )
        {
            m_outputDir = outputDir;
        }

        /**
         * {@inheritDoc}
         */
        public boolean accept( String name )
        {
            // always select metadata folders as we may need to refactor them
            if( name.startsWith( "META-INF" ) || name.startsWith( "OSGI-INF" ) )
            {
                return true;
            }
            // also select any embedded jars
            else if( name.endsWith( ".jar" ) )
            {
                return true;
            }

            // do we already have this file locally?
            return new File( m_outputDir, name ).exists() == false;
        }
    }

    /**
     * Copy OSGi metadata to where Eclipse PDE expects it, but adjust the Bundle-ClassPath so Eclipse can find any
     * embedded jars or directories in the unpacked bundle contents under the temporary directory
     * 
     * @param bundleFile the packaged bundle
     */
    private void refactorForEclipse( File bundleFile )
    {
        // temporary location in the output folder
        String tempPath = "target/pax-eclipse";
        boolean refactorManifest = false;

        // make relative to the provisioning POM
        File baseDir = executedProject.getBasedir();
        File unpackDir = new File( baseDir, tempPath );

        if( bundleFile == null || !bundleFile.exists() )
        {
            getLog().warn( "Bundle has not been built, reverting to basic behaviour" );
        }
        else
        {
            DirUtils.unpackBundle( bundleFile, unpackDir, new IncludedContentFilter( getBuildOutputDirectory() ) );

            moveMetadata( unpackDir, "META-INF", baseDir );
            moveMetadata( unpackDir, "OSGI-INF", baseDir );

            // test to see if it's empty
            unpackDir.delete();

            refactorManifest = unpackDir.exists();
        }

        File manifestFile = new File( baseDir, "META-INF/MANIFEST.MF" );

        Manifest manifest = getBundleManifest( manifestFile );
        Attributes mainAttributes = manifest.getMainAttributes();

        if( mainAttributes.getValue( "Bundle-SymbolicName" ) == null )
        {
            // Eclipse mis-behaves if the bundle has no symbolic name :(
            String name = getEclipseProjectName( executedProject, false );
            mainAttributes.putValue( "Bundle-SymbolicName", name.replace( '-', '_' ) );
        }

        String bundleClassPath = mainAttributes.getValue( "Bundle-ClassPath" );

        /*
         * refactor Bundle-ClassPath to help Eclipse find the unpacked contents
         */
        if( refactorManifest )
        {
            bundleClassPath = ".," + DirUtils.rebasePaths( bundleClassPath, tempPath, ',' );
            mainAttributes.putValue( "Bundle-ClassPath", bundleClassPath );

            // add the embedded entries back to the Eclipse classpath
            addEmbeddedEntriesToEclipseClassPath( tempPath, bundleClassPath );
        }

        try
        {
            manifestFile.getParentFile().mkdirs();
            FileOutputStream out = new FileOutputStream( manifestFile );
            manifest.write( out );
            IOUtil.close( out );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to update Eclipse manifest: " + manifestFile );
        }

        createBuildProperties( baseDir, tempPath );
    }

    /**
     * Create simple build.properties file so the export functionality from Eclipse/PDE works
     * 
     * @param baseDir project base directory
     * @param unpackPath path where the additional bundle contents were unpacked
     */
    private void createBuildProperties( File baseDir, String unpackPath )
    {
        File buildPropertiesFile = new File( baseDir, "build.properties" );
        if( !buildPropertiesFile.exists() )
        {
            BufferedWriter writer = null;
            try
            {
                writer = new BufferedWriter( new FileWriter( buildPropertiesFile ) );

                /* compiled/wrapped bundle */
                if( null != unpackPath )
                {
                    File sourceDir = new File( baseDir, "src/main/java" );
                    if( sourceDir.exists() )
                    {
                        writer.write( "source.. = src/main/java/,src/main/resources/" );
                        writer.newLine();
                    }

                    writer.write( "output.. = target/classes/" );
                    writer.newLine();
                    writer.write( "bin.includes = META-INF/,." );
                    if( new File( baseDir, unpackPath ).exists() )
                    {
                        writer.write( ',' + unpackPath + '/' );
                    }
                    writer.newLine();
                }
                /* imported bundle */
                else
                {
                    File bundleFile = executedProject.getArtifact().getFile();
                    if( null != bundleFile && bundleFile.isFile() )
                    {
                        writer.write( "install.location = " + bundleFile.toURI() );
                        writer.newLine();
                    }
                    writer.write( "source.. = ." );
                    writer.newLine();
                    writer.write( "output.. = ." );
                    writer.newLine();
                    writer.write( "bin.includes = META-INF/,." );
                    writer.newLine();
                }
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to create build.properties file" );
            }
            finally
            {
                IOUtil.close( writer );
            }
        }
    }

    /**
     * @param manifestFile path to the local bundle manifest
     * @return the bundle manifest, or sane defaults if the manifest couldn't be opened
     */
    private Manifest getBundleManifest( File manifestFile )
    {
        Manifest manifest = new Manifest();

        try
        {
            FileInputStream in = new FileInputStream( manifestFile );
            manifest.read( in );
            IOUtil.close( in );
        }
        catch( IOException e )
        {
            Attributes mainAttributes = manifest.getMainAttributes();

            String osgiVersion = m_maven2OsgiConverter.getVersion( project.getVersion() );

            // standard OSGi entries
            mainAttributes.putValue( "Manifest-Version", "1" );
            mainAttributes.putValue( "Bundle-ManifestVersion", "2" );
            mainAttributes.putValue( "Bundle-Name", project.getName() );
            mainAttributes.putValue( "Bundle-Version", osgiVersion );

            // some basic OSGi dependencies, to help people get their code compiling...
            mainAttributes.putValue( "Import-Package", "org.osgi.framework,org.osgi.util.tracker" );
        }

        return manifest;
    }

    /**
     * @param fromDir source directory
     * @param metadata metadata folder
     * @param toDir target directory
     */
    private void moveMetadata( File fromDir, String metadata, File toDir )
    {
        File metadataDir = new File( fromDir, metadata );
        if( metadataDir.exists() )
        {
            try
            {
                FileUtils.copyDirectoryStructure( metadataDir, new File( toDir, metadata ) );
                FileUtils.deleteDirectory( metadataDir );
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to copy " + metadata + " contents to base directory" );
            }
        }
    }

    /**
     * Add any embedded Bundle-ClassPath entries to the Eclipse classpath and re-attach sources/javadocs
     * 
     * @param bundleLocation relative path to the unpacked bundle
     * @param bundleClassPath the refactored Bundle-ClassPath
     */
    private void addEmbeddedEntriesToEclipseClassPath( String bundleLocation, String bundleClassPath )
    {
        String[] classPath = bundleClassPath.split( "," );
        File basedir = executedProject.getBasedir();

        try
        {
            File classPathFile = new File( basedir, ".classpath" );
            Reader reader = StreamFactory.newXmlReader( classPathFile );
            Xpp3Dom classPathXML = Xpp3DomBuilder.build( reader );
            IOUtil.close( reader );

            for( int i = 0; i < classPath.length; i++ )
            {
                String binaryPath = classPath[i].trim();

                if( !".".equals( binaryPath ) && new File( basedir, binaryPath ).exists() )
                {
                    // embedded jar/directory needs to be a 'lib' entry
                    Xpp3Dom classPathEntry = new Xpp3Dom( "classpathentry" );
                    classPathEntry.setAttribute( "exported", "true" );
                    classPathEntry.setAttribute( "kind", "lib" );
                    classPathEntry.setAttribute( "path", binaryPath );

                    // find attached sources using the previously cached IDE dependencies
                    File sourcePath = findAttachedSource( bundleLocation, binaryPath );
                    if( sourcePath != null )
                    {
                        classPathEntry.setAttribute( "sourcepath", sourcePath.getPath() );
                    }

                    classPathXML.addChild( classPathEntry );
                }
            }

            Writer writer = StreamFactory.newXmlWriter( classPathFile );
            Xpp3DomWriter.write( new PrettyPrintXMLWriter( writer ), classPathXML );
            IOUtil.close( writer );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to find Eclipse .classpath file" );
        }
        catch( XmlPullParserException e )
        {
            getLog().warn( "Unable to parse Eclipse .classpath file" );
        }
    }

    /**
     * Search cached IDE dependencies for an entry matching the given classpath element
     * 
     * @param bundleLocation relative path to the unpacked bundle
     * @param classPathEntry classpath element
     * @return path to the attached sources
     */
    private File findAttachedSource( String bundleLocation, String classPathEntry )
    {
        for( Iterator i = m_embeddableDependencies.iterator(); i.hasNext(); )
        {
            IdeDependency dependency = (IdeDependency) i.next();

            // equivalent to '.' - source is first in list
            if( bundleLocation.equals( classPathEntry ) )
            {
                return dependency.getSourceAttachment();
            }
            else if( Pattern.matches( "^.*[/\\\\]" + dependency.getArtifactId() + "[-.][^/\\\\]*$", classPathEntry ) )
            {
                return dependency.getSourceAttachment();
            }
        }

        return null;
    }

    /**
     * Unpack each imported bundle in turn and generate the relevant Eclipse project files
     * 
     * @throws InvalidDependencyVersionException
     * @throws MojoExecutionException
     */
    private void setupImportedBundles()
        throws InvalidDependencyVersionException,
        MojoExecutionException
    {
        // don't process dependencies of imported bundles
        m_provisionProject = getExecutedProject();
        setResolveDependencies( false );

        Set artifacts = m_provisionProject.createArtifacts( artifactFactory, null, null );
        for( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();

            // store project locally underneath the provisioning POM's directory
            File groupDir = new File( m_provisionProject.getBasedir(), "target/" + artifact.getGroupId() );
            File baseDir = new File( groupDir, artifact.getArtifactId() + '-' + artifact.getVersion() );

            // download and unpack the bundle
            if( !PomUtils.downloadFile( artifact, artifactResolver, remoteArtifactRepositories, localRepository ) )
            {
                getLog().warn( "Skipping missing bundle " + artifact );
                continue;
            }

            DirUtils.unpackBundle( artifact.getFile(), baseDir, null );

            // download the bundle POM and store locally
            MavenProject dependencyProject = writeProjectPom( baseDir, artifact );
            if( null == dependencyProject )
            {
                getLog().warn( "Skipping missing bundle " + artifact );
                continue;
            }

            dependencyProject.setArtifact( artifact );

            setExecutedProject( dependencyProject );
            setProject( dependencyProject );

            // trick Eclipse plugin to do the right thing
            setBuildOutputDirectory( new File( baseDir, ".ignore" ) );
            setEclipseProjectDir( baseDir );

            try
            {
                // call the Eclipse plugin
                getLog().info( "Generating Eclipse project for bundle " + artifact );
                execute();
            }
            catch( MojoFailureException e )
            {
                getLog().warn( "Problem generating Eclipse files for artifact " + artifact );
            }
        }
    }

    /**
     * Download and save the Maven POM for the given artifact
     * 
     * @param baseDir base directory
     * @param artifact Maven artifact
     * @return the downloaded project
     */
    private MavenProject writeProjectPom( File baseDir, Artifact artifact )
    {
        MavenProject pom = null;

        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();

        Artifact pomArtifact = artifactFactory.createProjectArtifact( groupId, artifactId, version );

        try
        {
            pom = m_mavenProjectBuilder.buildFromRepository( pomArtifact, remoteArtifactRepositories, localRepository );

            File pomFile = new File( baseDir, "pom.xml" );

            Writer writer = StreamFactory.newXmlWriter( pomFile );
            pom.writeModel( writer );
            pom.setFile( pomFile );
            IOUtil.close( writer );
        }
        catch( ProjectBuildingException e )
        {
            getLog().warn( "Unable to build POM for artifact " + pomArtifact );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to write POM for artifact " + pomArtifact );
        }

        return pom;
    }

    /**
     * Customize Eclipse project files for imported (unpacked) bundles
     * 
     * @throws MojoExecutionException
     */
    private void writeImportedConfiguration()
        throws MojoExecutionException
    {
        EclipseWriterConfig config = createEclipseWriterConfig( new IdeDependency[0] );
        config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );
        config.setClasspathContainers( Collections.EMPTY_LIST );
        config.setSourceDirs( new EclipseSourceDir[0] );

        // not compiling, so just need project and classpath files
        new EclipseClasspathWriter().init( getLog(), config ).write();
        new EclipseProjectWriter().init( getLog(), config ).write();

        Artifact sourceArtifact = artifactFactory.createArtifactWithClassifier( executedProject.getGroupId(),
            executedProject.getArtifactId(), executedProject.getVersion(), "java-source", "sources" );

        if( downloadSources )
        {
            PomUtils.downloadFile( sourceArtifact, artifactResolver, remoteArtifactRepositories, localRepository );
        }
        else
        {
            // check to see if we already have the source downloaded...
            PomUtils.getFile( sourceArtifact, artifactResolver, localRepository );
        }

        // set PDE classpath to point to unpacked bundle
        attachImportedContent( sourceArtifact.getFile() );

        String baseDir = executedProject.getBasedir().getPath();
        File manifestFile = new File( baseDir, "META-INF/MANIFEST.MF" );

        Manifest manifest = getBundleManifest( manifestFile );
        Attributes mainAttributes = manifest.getMainAttributes();

        String bundleClassPath = mainAttributes.getValue( "Bundle-ClassPath" );
        if( null != bundleClassPath )
        {
            // add any embedded entries to the default Eclipse classpath
            addEmbeddedEntriesToEclipseClassPath( baseDir, bundleClassPath );
        }

        createBuildProperties( new File( baseDir ), null );
    }

    /**
     * Add a classpath entry for the unpacked imported bundle and attach it to the given source
     * 
     * @param sources attached bundle sources
     */
    private void attachImportedContent( File sources )
    {
        try
        {
            File classPathFile = new File( executedProject.getBasedir(), ".classpath" );
            Reader reader = StreamFactory.newXmlReader( classPathFile );
            Xpp3Dom classPathXML = Xpp3DomBuilder.build( reader );
            IOUtil.close( reader );

            Xpp3Dom classPathEntry = new Xpp3Dom( "classpathentry" );
            classPathEntry.setAttribute( "exported", "true" );
            classPathEntry.setAttribute( "kind", "lib" );
            classPathEntry.setAttribute( "path", "." );
            if( sources != null && sources.exists() )
            {
                classPathEntry.setAttribute( "sourcepath", sources.getPath() );
            }
            classPathXML.addChild( classPathEntry );

            Writer writer = StreamFactory.newXmlWriter( classPathFile );
            Xpp3DomWriter.write( new PrettyPrintXMLWriter( writer ), classPathXML );
            IOUtil.close( writer );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to find Eclipse .classpath file" );
        }
        catch( XmlPullParserException e )
        {
            getLog().warn( "Unable to parse Eclipse .classpath file" );
        }
    }
}
