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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.eclipse.EclipsePlugin;
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
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.ReflectMojo;

/**
 * Extends <a href="http://maven.apache.org/plugins/maven-eclipse-plugin/eclipse-mojo.html">EclipsePlugin</a> to
 * provide customized Eclipse project files for Pax-Construct projects.<br/>Inherited parameters can still be used, but
 * unfortunately don't appear in the generated docs.
 * 
 * @extendsPlugin eclipse
 * @goal eclipse
 * @phase package
 * 
 * @execute phase="package"
 */
public class EclipseOSGiMojo extends EclipsePlugin
{
    /**
     * Component factory for archivers and unarchivers
     * 
     * @component
     */
    private ArchiverManager m_archiverManager;

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
     * 
     */
    private MavenProject m_provisionProject;

    private List m_resolvedDependencies;

    public boolean setup()
        throws MojoExecutionException
    {
        if( null == m_eclipseMojo )
        {
            m_eclipseMojo = new ReflectMojo( this, EclipsePlugin.class );
            m_eclipseMojo.setField( "pde", Boolean.TRUE );
            setWtpversion( "none" );
        }

        if( null == m_provisionProject && "pom".equals( executedProject.getPackaging() ) )
        {
            try
            {
                setupImportedBundles();
            }
            catch( InvalidDependencyVersionException e )
            {
                getLog().warn( "Unable to generate Eclipse files for project " + executedProject.getId() );
            }

            return false;
        }

        return super.setup();
    }

    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        if( null == m_provisionProject )
        {
            writeBundleConfiguration( deps );
        }
        else
        {
            writeImportedConfiguration( deps );
        }
    }

    void writeBundleConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        m_resolvedDependencies = new ArrayList();
        for( int i = 0; i < deps.length; i++ )
        {
            if( deps[i].isAddedToClasspath() && !deps[i].isTestDependency() && !deps[i].isProvided() )
            {
                m_resolvedDependencies.add( deps[i] );
                deps[i].setAddedToClasspath( false );
            }
        }

        EclipseWriterConfig config = createEclipseWriterConfig( deps );

        config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );

        new EclipseSettingsWriter().init( getLog(), config ).write();
        new EclipseClasspathWriter().init( getLog(), config ).write();
        new EclipseProjectWriter().init( getLog(), config ).write();

        File bundleFile = executedProject.getArtifact().getFile();
        if( bundleFile == null || !bundleFile.exists() )
        {
            getLog().warn( "Bundle has not been built, reverting to basic behaviour" );
            return;
        }

        refactorForEclipse( bundleFile, "target/contents" );
    }

    static String getEclipseProjectName( MavenProject project, boolean addVersion )
    {
        String projectName = project.getProperties().getProperty( "bundle.symbolicName" );
        if( null == projectName )
        {
            projectName = PomUtils.getCompoundId( project.getGroupId(), project.getArtifactId() );
        }

        if( addVersion )
        {
            String projectVersion = project.getProperties().getProperty( "wrapped.version" );
            if( null == projectVersion )
            {
                projectVersion = project.getVersion();
            }

            return projectName + " [" + projectVersion + ']';
        }
        else
        {
            return projectName;
        }
    }

    void refactorForEclipse( File bundleFile, String bundleLocation )
    {
        File baseDir = executedProject.getBasedir();
        File unpackDir = new File( baseDir, bundleLocation );

        DirUtils.unpackBundle( m_archiverManager, bundleFile, unpackDir );

        copyMetadata( unpackDir, "META-INF", baseDir );
        copyMetadata( unpackDir, "OSGI-INF", baseDir );

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
        bundleClassPath = ".," + DirUtils.rebasePaths( bundleClassPath, bundleLocation, ',' );

        mainAttributes.putValue( "Bundle-ClassPath", bundleClassPath );
        updateEclipseClassPath( bundleLocation, bundleClassPath );

        try
        {
            manifestFile.getParentFile().mkdirs();
            manifest.write( new FileOutputStream( manifestFile ) );
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to update Eclipse manifest: " + manifestFile );
        }
    }

    Manifest getBundleManifest( File manifestFile )
    {
        Manifest manifest = new Manifest();

        try
        {
            manifest.read( new FileInputStream( manifestFile ) );
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

    void copyMetadata( File fromDir, String metadata, File toDir )
    {
        File metadataDir = new File( fromDir, metadata );
        if( metadataDir.exists() )
        {
            try
            {
                FileUtils.copyDirectoryStructure( metadataDir, new File( toDir, metadata ) );
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to copy " + metadata + " contents to base directory" );
            }
        }
    }

    void updateEclipseClassPath( String bundleLocation, String bundleClassPath )
    {
        String[] classPath = bundleClassPath.split( "," );

        try
        {
            File classPathFile = new File( executedProject.getBasedir(), ".classpath" );
            Xpp3Dom classPathXML = Xpp3DomBuilder.build( new FileReader( classPathFile ) );

            for( int i = 0; i < classPath.length; i++ )
            {
                if( ".".equals( classPath[i] ) == false )
                {
                    Xpp3Dom classPathEntry = new Xpp3Dom( "classpathentry" );
                    classPathEntry.setAttribute( "exported", "true" );
                    classPathEntry.setAttribute( "kind", "lib" );
                    classPathEntry.setAttribute( "path", classPath[i] );

                    File sourcePath = findAttachedSource( bundleLocation, classPath[i] );
                    if( sourcePath != null )
                    {
                        classPathEntry.setAttribute( "sourcepath", sourcePath.getPath() );
                    }

                    classPathXML.addChild( classPathEntry );
                }
            }

            FileWriter writer = new FileWriter( classPathFile );
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

    File findAttachedSource( String bundleLocation, String classPathEntry )
    {
        for( Iterator i = m_resolvedDependencies.iterator(); i.hasNext(); )
        {
            IdeDependency dependency = (IdeDependency) i.next();

            if( bundleLocation.equals( classPathEntry ) )
            {
                return dependency.getSourceAttachment();
            }
            else if( Pattern.matches( "^.*[\\/]" + dependency.getArtifactId() + "[-.][^\\/]*$", classPathEntry ) )
            {
                return dependency.getSourceAttachment();
            }
        }

        return null;
    }

    public void setupImportedBundles()
        throws MojoExecutionException,
        InvalidDependencyVersionException
    {
        m_provisionProject = getExecutedProject();
        setResolveDependencies( false );

        Set artifacts = m_provisionProject.createArtifacts( artifactFactory, null, null );
        for( Iterator i = artifacts.iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();

            if( Artifact.SCOPE_PROVIDED.equals( artifact.getScope() ) )
            {
                MavenProject dependencyProject = writeProjectPom( artifact );
                if( null == dependencyProject )
                {
                    continue;
                }

                setExecutedProject( dependencyProject );
                setProject( dependencyProject );

                File baseDir = dependencyProject.getBasedir();
                setBuildOutputDirectory( new File( baseDir, ".ignore" ) );
                setEclipseProjectDir( baseDir );

                try
                {
                    artifactResolver.resolve( artifact, remoteArtifactRepositories, localRepository );
                    DirUtils.unpackBundle( m_archiverManager, artifact.getFile(), executedProject.getBasedir() );

                    execute();
                }
                catch( ArtifactNotFoundException e )
                {
                    getLog().warn( "Unable to find bundle artifact " + artifact );
                }
                catch( ArtifactResolutionException e )
                {
                    getLog().warn( "Unable to resolve bundle artifact " + artifact );
                }
                catch( MojoFailureException e )
                {
                    getLog().warn( "Problem generating Eclipse files for artifact " + artifact );
                }
            }
        }
    }

    MavenProject writeProjectPom( Artifact artifact )
    {
        MavenProject pom = null;

        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();

        Artifact pomArtifact = artifactFactory.createProjectArtifact( groupId, artifactId, version );

        try
        {
            pom = m_mavenProjectBuilder.buildFromRepository( pomArtifact, remoteArtifactRepositories, localRepository );

            File projectDir = new File( m_provisionProject.getBasedir(), "target/" + groupId );
            File localDir = new File( projectDir, artifactId + '/' + version );
            localDir.mkdirs();

            File pomFile = new File( localDir, "pom.xml" );

            Writer writer = new FileWriter( pomFile );
            pom.writeModel( writer );
            pom.setFile( pomFile );
            writer.close();
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

    void writeImportedConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        EclipseWriterConfig config = createEclipseWriterConfig( new IdeDependency[0] );
        config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );

        new EclipseClasspathWriter().init( getLog(), config ).write();
        new EclipseProjectWriter().init( getLog(), config ).write();

        Artifact artifact = artifactFactory.createArtifactWithClassifier( executedProject.getGroupId(), executedProject
            .getArtifactId(), executedProject.getVersion(), "java-source", "sources" );

        try
        {
            if( downloadSources )
            {
                artifactResolver.resolve( artifact, remoteArtifactRepositories, localRepository );
            }
            else
            {
                artifactResolver.resolve( artifact, Collections.EMPTY_LIST, localRepository );
            }

            attachImportedSource( artifact.getFile().getPath() );
        }
        catch( ArtifactNotFoundException e )
        {
            getLog().debug( "Unable to find source artifact " + artifact );
        }
        catch( ArtifactResolutionException e )
        {
            getLog().debug( "Unable to resolve source artifact " + artifact );
        }
    }

    void attachImportedSource( String sourcePath )
    {
        try
        {
            File classPathFile = new File( executedProject.getBasedir(), ".classpath" );
            Xpp3Dom classPathXML = Xpp3DomBuilder.build( new FileReader( classPathFile ) );

            Xpp3Dom classPathEntry = new Xpp3Dom( "classpathentry" );
            classPathEntry.setAttribute( "exported", "true" );
            classPathEntry.setAttribute( "kind", "lib" );
            classPathEntry.setAttribute( "path", "." );
            classPathEntry.setAttribute( "sourcepath", sourcePath );
            classPathXML.addChild( classPathEntry );

            FileWriter writer = new FileWriter( classPathFile );
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
