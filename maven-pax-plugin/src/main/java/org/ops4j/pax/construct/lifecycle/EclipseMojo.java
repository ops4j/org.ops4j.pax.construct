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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.eclipse.EclipsePlugin;
import org.apache.maven.plugin.eclipse.writers.EclipseClasspathWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseProjectWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseSettingsWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Extend maven-eclipse-plugin to better handle OSGi bundles.
 * 
 * @goal eclipse
 */
public class EclipseMojo extends EclipsePlugin
{
    /*
     * THE FOLLOWING MEMBERS ARE ONLY REQUIRED TO DUPLICATE THE MOJO INJECTION
     */

    /**
     * @component role="org.apache.maven.artifact.factory.ArtifactFactory"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @component role="org.apache.maven.artifact.resolver.ArtifactResolver"
     * @required
     * @readonly
     */
    protected ArtifactResolver artifactResolver;

    /**
     * @component role="org.apache.maven.artifact.resolver.ArtifactCollector"
     * @required
     * @readonly
     */
    protected ArtifactCollector artifactCollector;

    /**
     * @component role="org.apache.maven.artifact.metadata.ArtifactMetadataSource" hint="maven"
     * @required
     * @readonly
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * @component role="org.codehaus.plexus.archiver.manager.ArchiverManager"
     * @required
     * @readonly
     */
    protected ArchiverManager archiverManager;

    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * @parameter expression="${project}"
     * @readonly
     */
    protected MavenProject executedProject;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    protected List remoteArtifactRepositories;

    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    protected List reactorProjects;

    /**
     * @parameter expression="${downloadSources}"
     */
    protected boolean downloadSources;

    /**
     * @parameter expression="${downloadJavadocs}"
     */
    protected boolean downloadJavadocs;

    protected List resolvedDependencies;

    protected void patchPlugin()
    {
        // components need upwards injection...
        super.artifactFactory = artifactFactory;
        super.artifactResolver = artifactResolver;
        super.artifactCollector = artifactCollector;
        super.artifactMetadataSource = artifactMetadataSource;

        // ...but params need downwards injection!
        project = super.project;
        executedProject = super.executedProject;
        remoteArtifactRepositories = super.remoteArtifactRepositories;
        localRepository = super.localRepository;
        reactorProjects = super.reactorProjects;
        downloadSources = super.downloadSources;
        downloadJavadocs = super.downloadJavadocs;

        // fix private params
        setFlag( "pde", true );
        setWtpversion( "none" );
    }

    public boolean setup()
        throws MojoExecutionException
    {
        patchPlugin();

        if( getBuildOutputDirectory() == null )
        {
            setBuildOutputDirectory( new File( executedProject.getBuild().getOutputDirectory() ) );
        }

        return super.setup();
    }

    private void setFlag( String name, boolean flag )
    {
        try
        {
            // Attempt to bypass normal private field protection
            Field f = EclipsePlugin.class.getDeclaredField( name );

            f.setAccessible( true );
            f.setBoolean( this, flag );
        }
        catch( Exception e )
        {
            System.out.println( "Cannot set " + name + " to " + flag + " exception=" + e );
        }
    }

    public void writeConfiguration( IdeDependency[] deps )
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
            config.getEclipseProjectDirectory().mkdirs();

            new EclipseSettingsWriter().init( getLog(), config ).write();
            new EclipseClasspathWriter().init( getLog(), config ).write();
            new EclipseProjectWriter().init( getLog(), config ).write();

            String bundleDir = "target/bundle";
            unpackBundle( executedProject.getArtifact().getFile(), bundleDir );
            refactorForEclipse( bundleDir );
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }
    }

    protected static String getEclipseProjectName( MavenProject project, boolean addVersion )
    {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();

        boolean isWrapper = (project.getProperties().getProperty( "jar.artifactId" ) != null);

        String projectName;

        if( isWrapper || artifactId.startsWith( groupId + "." ) || artifactId.equals( groupId ) )
        {
            projectName = artifactId;
        }
        else if( groupId.endsWith( "." + artifactId ) )
        {
            projectName = groupId;
        }
        else
        {
            projectName = groupId + "." + artifactId;
        }

        if( addVersion )
        {
            return projectName + " [" + project.getVersion() + "]";
        }
        else
        {
            return projectName;
        }
    }

    protected void unpackBundle( File bundle, String to )
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

    protected void refactorForEclipse( String bundleLocation )
        throws IOException,
        XmlPullParserException
    {
        File baseDir = executedProject.getBasedir();
        File bundleDir = new File( baseDir, bundleLocation );

        List metaFiles = FileUtils.getFiles( bundleDir, "META-INF/**,OSGI-INF/**", null, false );
        for( Iterator i = metaFiles.iterator(); i.hasNext(); )
        {
            String metaEntry = ((File) i.next()).getPath();
            File bundleMeta = new File( bundleDir, metaEntry );
            FileUtils.rename( bundleMeta, new File( baseDir, metaEntry ) );
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
            String[] classPathEntries = bundleClassPath.split( "," );

            StringBuffer refactoredClassPath = new StringBuffer();
            for( int i = 0; i < classPathEntries.length; i++ )
            {
                if( i > 0 )
                {
                    refactoredClassPath.append( ',' );
                }

                if( ".".equals( classPathEntries[i] ) )
                {
                    refactoredClassPath.append( ".," );
                    refactoredClassPath.append( bundleLocation );
                }
                else
                {
                    refactoredClassPath.append( bundleLocation );
                    refactoredClassPath.append( '/' );
                    refactoredClassPath.append( classPathEntries[i] );
                }
            }

            mainAttributes.putValue( "Bundle-ClassPath", refactoredClassPath.toString() );
        }

        updateEclipseClassPath( bundleLocation, mainAttributes.getValue( "Bundle-ClassPath" ) );

        manifestFile.getParentFile().mkdirs();
        manifest.write( new FileOutputStream( manifestFile ) );
    }

    protected void updateEclipseClassPath( String bundleLocation, String bundleClassPath )
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

    protected File findAttachedSource( String bundleLocation, String classPathEntry )
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
}
