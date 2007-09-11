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
import java.io.FileNotFoundException;
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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.eclipse.EclipsePlugin;
import org.apache.maven.plugin.eclipse.writers.EclipseClasspathWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseProjectWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseSettingsWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.archiver.UnArchiver;
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
import org.ops4j.pax.construct.util.ReflectUtils.ReflectMojo;

/**
 * @goal eclipse:eclipse
 * @phase package
 */
public class EclipseOSGiMojo extends EclipsePlugin
{
    /**
     * @component
     */
    ArchiverManager archiverManager;

    /**
     * @component
     */
    MavenProjectBuilder mavenProjectBuilder;

    MavenProject pomProject;
    List resolvedDependencies;

    public boolean setup()
        throws MojoExecutionException
    {
        if( null == pomProject && "pom".equals( executedProject.getPackaging() ) )
        {
            setupImportedBundles();
            return false;
        }

        new ReflectMojo( this, EclipsePlugin.class ).setField( "pde", Boolean.TRUE );
        setWtpversion( "none" );

        if( getBuildOutputDirectory() == null )
        {
            setBuildOutputDirectory( new File( executedProject.getBuild().getOutputDirectory() ) );
        }

        return super.setup();
    }

    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        if( null == pomProject )
        {
            writeBundleConfiguration( deps );
        }
        else
        {
            writeImportedConfiguration( deps );
        }
    }

    public void writeBundleConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        try
        {
            resolvedDependencies = new ArrayList();
            for( int i = 0; i < deps.length; i++ )
            {
                if( deps[i].isAddedToClasspath() && !deps[i].isTestDependency() && !deps[i].isProvided() )
                {
                    resolvedDependencies.add( deps[i] );
                    deps[i].setAddedToClasspath( false );
                }
            }

            EclipseWriterConfig config = createEclipseWriterConfig( deps );

            config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );

            new EclipseSettingsWriter().init( getLog(), config ).write();
            new EclipseClasspathWriter().init( getLog(), config ).write();
            new EclipseProjectWriter().init( getLog(), config ).write();

            String bundleDir = "target/bundle";
            Artifact bundleArtifact = executedProject.getArtifact();

            if( bundleArtifact.getFile() == null || !bundleArtifact.getFile().exists() )
            {
                artifactResolver.resolve( bundleArtifact, remoteArtifactRepositories, localRepository );
            }

            unpackBundle( bundleArtifact.getFile(), bundleDir );
            refactorForEclipse( bundleDir );
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }
    }

    static String getEclipseProjectName( MavenProject project, boolean addVersion )
    {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();

        String symbolicName = project.getProperties().getProperty( "bundle.symbolicName" );
        String projectName;

        if( symbolicName != null )
        {
            projectName = symbolicName;
        }
        else
        {
            projectName = PomUtils.getCompoundName( groupId, artifactId );
        }

        if( addVersion )
        {
            String wrappedVersion = project.getProperties().getProperty( "wrapped.version" );
            String projectVersion;

            if( wrappedVersion != null )
            {
                projectVersion = wrappedVersion;
            }
            else
            {
                projectVersion = project.getVersion();
            }

            return projectName + " [" + projectVersion + ']';
        }

        return projectName;
    }

    void unpackBundle( File bundle, String to )
        throws MojoExecutionException
    {
        try
        {
            UnArchiver unArchiver = archiverManager.getUnArchiver( bundle );
            File here = new File( executedProject.getBasedir(), to );

            here.mkdirs();
            unArchiver.setDestDirectory( here );
            unArchiver.setSourceFile( bundle );
            unArchiver.extract();
        }
        catch( Exception e )
        {
            getLog().error( "problem unpacking bundle", e );
        }
    }

    void refactorForEclipse( String bundleLocation )
        throws IOException,
        XmlPullParserException
    {
        File baseDir = executedProject.getBasedir();
        File bundleDir = new File( baseDir, bundleLocation );

        File metaInfDir = new File( bundleDir, "META-INF" );
        if( metaInfDir.exists() )
        {
            FileUtils.copyDirectoryStructure( metaInfDir, new File( baseDir, "META-INF" ) );
        }

        File osgiInfDir = new File( bundleDir, "OSGI-INF" );
        if( osgiInfDir.exists() )
        {
            FileUtils.copyDirectoryStructure( osgiInfDir, new File( baseDir, "OSGI-INF" ) );
        }

        File manifestFile = new File( baseDir, "META-INF/MANIFEST.MF" );
        Manifest manifest = new Manifest();
        Attributes mainAttributes;

        try
        {
            manifest.read( new FileInputStream( manifestFile ) );
            mainAttributes = manifest.getMainAttributes();
        }
        catch( Exception e )
        {
            mainAttributes = manifest.getMainAttributes();
            mainAttributes.putValue( "Manifest-Version", "1" );
            mainAttributes.putValue( "Bundle-ManifestVersion", "2" );
            mainAttributes.putValue( "Bundle-Name", project.getName() );
            mainAttributes.putValue( "Bundle-Version", project.getVersion().replace( '-', '.' ) );

            // some basic OSGi dependencies, to help people get their code compiling...
            mainAttributes.putValue( "Import-Package", "org.osgi.framework,org.osgi.util.tracker" );
        }

        if( mainAttributes.getValue( "Bundle-SymbolicName" ) == null )
        {
            // Eclipse mis-behaves if the bundle has no symbolic name :(
            String symbolicName = getEclipseProjectName( executedProject, false ).replace( '-', '_' );
            mainAttributes.putValue( "Bundle-SymbolicName", symbolicName );
        }

        String bundleClassPath = mainAttributes.getValue( "Bundle-ClassPath" );
        if( null == bundleClassPath )
        {
            mainAttributes.putValue( "Bundle-ClassPath", ".," + bundleLocation );
        }
        else
        {
            String rebasedClassPath = DirUtils.rebasePaths( bundleClassPath, bundleLocation, ',' );
            mainAttributes.putValue( "Bundle-ClassPath", ".," + rebasedClassPath );
        }

        updateEclipseClassPath( bundleLocation, mainAttributes.getValue( "Bundle-ClassPath" ) );

        manifestFile.getParentFile().mkdirs();
        manifest.write( new FileOutputStream( manifestFile ) );
    }

    void updateEclipseClassPath( String bundleLocation, String bundleClassPath )
        throws FileNotFoundException,
        XmlPullParserException,
        IOException
    {
        String[] classPath = bundleClassPath.split( "," );

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

    File findAttachedSource( String bundleLocation, String classPathEntry )
    {
        for( Iterator i = resolvedDependencies.iterator(); i.hasNext(); )
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
        throws MojoExecutionException
    {
        pomProject = getExecutedProject();
        setResolveDependencies( false );

        try
        {
            Set artifacts = pomProject.createArtifacts( artifactFactory, null, null );
            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();

                if( Artifact.SCOPE_PROVIDED.equals( artifact.getScope() ) )
                {
                    String groupId = artifact.getGroupId();
                    String artifactId = artifact.getArtifactId();
                    String version = artifact.getVersion();

                    Artifact pomArtifact = artifactFactory.createProjectArtifact( groupId, artifactId, version );

                    MavenProject dependencyProject = mavenProjectBuilder.buildFromRepository( pomArtifact,
                        remoteArtifactRepositories, localRepository );

                    File projectDir = new File( pomProject.getBasedir(), "target/" + groupId );
                    File localDir = new File( projectDir, artifactId + '/' + version );
                    localDir.mkdirs();

                    File pomFile = new File( localDir, "pom.xml" );

                    Writer writer = new FileWriter( pomFile );
                    dependencyProject.writeModel( writer );
                    dependencyProject.setFile( pomFile );
                    writer.close();

                    setBuildOutputDirectory( new File( localDir, ".ignore" ) );
                    setEclipseProjectDir( localDir );

                    artifactResolver.resolve( artifact, remoteArtifactRepositories, localRepository );

                    setProject( dependencyProject );
                    setExecutedProject( dependencyProject );
                    unpackBundle( artifact.getFile(), "." );

                    execute();
                }
            }
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }
    }

    public void writeImportedConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        try
        {
            EclipseWriterConfig config = createEclipseWriterConfig( new IdeDependency[0] );

            config.setEclipseProjectName( getEclipseProjectName( executedProject, true ) );

            new EclipseClasspathWriter().init( getLog(), config ).write();
            new EclipseProjectWriter().init( getLog(), config ).write();
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }

        try
        {
            List remoteRepos = downloadSources ? remoteArtifactRepositories : Collections.EMPTY_LIST;

            Artifact artifact = artifactFactory.createArtifactWithClassifier( executedProject.getGroupId(),
                executedProject.getArtifactId(), executedProject.getVersion(), "java-source", "sources" );

            artifactResolver.resolve( artifact, remoteRepos, localRepository );

            attachImportedSource( artifact.getFile().getPath() );
        }
        catch( Exception e )
        {
            // ignore missing sources
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
        catch( Exception e )
        {
            // nice to have source, but ignore errors if we can't
        }
    }
}
