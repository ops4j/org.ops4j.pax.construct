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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.eclipse.EclipsePlugin;
import org.apache.maven.plugin.eclipse.EclipseSourceDir;
import org.apache.maven.plugin.eclipse.writers.EclipseClasspathWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseProjectWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseSettingsWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.PrettyPrintXMLWriter;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Extend maven-eclipse-plugin to get better classpath generation.
 * 
 * @goal eclipse
 */
public final class EclipseMojo extends EclipsePlugin
{
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
     */
    protected ArtifactMetadataSource artifactMetadataSource;

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
     * @parameter expression="${outputDirectory}" alias="outputDirectory"
     *            default-value="${project.build.outputDirectory}"
     * @required
     */
    protected File buildOutputDirectory;

    /**
     * @parameter expression="${downloadSources}" default-value="false"
     */
    protected boolean downloadSources;

    protected boolean isWrappedJarFile = false;
    protected boolean isImportedBundle = false;

    public boolean setup()
        throws MojoExecutionException
    {
        project = super.project;

        setWtpversion( "none" );
        setDownloadSources( downloadSources );

        isWrappedJarFile = project.getProperties().containsKey( "jar.artifactId" );
        isImportedBundle = project.getProperties().containsKey( "bundle.artifactId" );

        if ( isImportedBundle )
        {
            // This forces eclipse plugin to work on pom packaging
            setEclipseProjectDir( project.getFile().getParentFile() );
        }

        return super.setup();
    }

    protected void setupExtras()
        throws MojoExecutionException
    {
        setArtifactFactory( artifactFactory );
        setArtifactResolver( artifactResolver );
        setArtifactMetadataSource( artifactMetadataSource );
        super.artifactCollector = artifactCollector;

        if ( project.getPackaging().equals( "bundle" ) || isImportedBundle )
        {
            // Inject values into private flags
            setField( "isJavaProject", true );
            setField( "pde", true );
        }
    }

    private void setField( String name, boolean flag )
    {
        try
        {
            // Attempt to bypass normal private field protection
            Field f = EclipsePlugin.class.getDeclaredField( name );
            f.setAccessible( true );
            f.setBoolean( this, flag );
        }
        catch ( Exception e )
        {
            System.out.println( "Cannot set " + name + " to " + flag + " exception=" + e );
        }
    }

    protected static EclipseSourceDir[] rejectLinkedSources( EclipseSourceDir[] sources )
    {
        int nonLinkedCount = 0;
        for ( int i = 0; i < sources.length; i++ )
        {
            // Remove external resources as these result in bogus links
            if ( new File( sources[i].getPath() ).isAbsolute() == false )
            {
                sources[nonLinkedCount++] = sources[i];
            }
        }

        EclipseSourceDir[] nonLinkedSources = new EclipseSourceDir[nonLinkedCount];
        System.arraycopy( sources, 0, nonLinkedSources, 0, nonLinkedCount );

        return nonLinkedSources;
    }

    protected static IdeDependency[] rejectLinkedDependencies( IdeDependency[] deps )
    {
        int nonLinkedCount = 0;
        for ( int i = 0; i < deps.length; i++ )
        {
            // Remove external compile/runtime dependencies as these result in bogus links
            if ( deps[i].isProvided() || deps[i].isTestDependency() || deps[i].getFile().isAbsolute() == false )
            {
                deps[nonLinkedCount++] = deps[i];
            }
        }

        IdeDependency[] nonLinkedDeps = new IdeDependency[nonLinkedCount];
        System.arraycopy( deps, 0, nonLinkedDeps, 0, nonLinkedCount );

        return nonLinkedDeps;
    }

    protected Manifest extractManifest( File projectFolder )
        throws FileNotFoundException,
        IOException
    {
        Manifest manifest = null;

        // Eclipse wants bundle manifests at the top of the project directory
        String manifestPath = "META-INF" + File.separator + "MANIFEST.MF";
        File manifestFile = new File( projectFolder, manifestPath );

        if ( isImportedBundle )
        {
            // Existing manifest, unpacked from imported bundle
            manifest = new Manifest( new FileInputStream( manifestFile ) );

            Attributes attributes = manifest.getMainAttributes();
            if ( attributes.getValue( "Bundle-SymbolicName" ) == null )
            {
                // Eclipse mis-behaves if the bundle has no symbolic name :(
                attributes.putValue( "Bundle-SymbolicName", project.getArtifactId() );
            }
        }
        else
        {
            // Manifest (generated by bnd) can simply be extracted from the new bundle
            JarFile bundle = new JarFile( project.getBuild().getDirectory() + File.separator
                + project.getBuild().getFinalName() + ".jar" );

            manifestFile.mkdirs();
            manifestFile.delete();

            manifest = bundle.getManifest();
        }

        manifest.write( new FileOutputStream( manifestFile ) );

        return manifest;
    }

    protected void patchClassPath( File projectFolder, String bundleClassPath )
        throws FileNotFoundException,
        XmlPullParserException,
        IOException
    {
        String[] paths =
        {
            "." // default classpath
        };

        if ( bundleClassPath != null )
        {
            paths = bundleClassPath.split( "," );
        }

        if ( isWrappedJarFile || isImportedBundle || paths.length > 1 )
        {
            File classPathFile = new File( projectFolder, ".classpath" );

            Xpp3Dom classPathXML = Xpp3DomBuilder.build( new FileReader( classPathFile ) );

            for ( int i = 0; i < paths.length; i++ )
            {
                if ( paths[i].equals( "." ) && !isWrappedJarFile && !isImportedBundle )
                {
                    continue;
                }

                // Add non-default bundle classpath entry
                Xpp3Dom classPathDot = new Xpp3Dom( "classpathentry" );
                classPathDot.setAttribute( "exported", "true" );
                classPathDot.setAttribute( "kind", "lib" );
                classPathDot.setAttribute( "path", paths[i] );
                classPathXML.addChild( classPathDot );
            }

            FileWriter writer = new FileWriter( classPathFile );
            Xpp3DomWriter.write( new PrettyPrintXMLWriter( writer ), classPathXML );
            IOUtil.close( writer );
        }
    }

    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        try
        {
            EclipseWriterConfig config = createEclipseWriterConfig( deps );

            if ( isWrappedJarFile || isImportedBundle )
            {
                // Fudge directories so project is in the build directory without requiring extra links
                config.setEclipseProjectDirectory( new File( project.getBuild().getOutputDirectory() ) );
                config.setBuildOutputDirectory( new File( config.getEclipseProjectDirectory(), "temp" ) );
                config.setProjectBaseDir( config.getEclipseProjectDirectory() );

                // No sources required to build these bundles
                config.setSourceDirs( new EclipseSourceDir[0] );
            }
            else
            {
                // Avoid links wherever possible, as they're a real pain
                config.setSourceDirs( rejectLinkedSources( config.getSourceDirs() ) );
                config.setDeps( rejectLinkedDependencies( config.getDeps() ) );
            }

            new EclipseSettingsWriter().init( getLog(), config ).write();
            new EclipseClasspathWriter().init( getLog(), config ).write();
            new EclipseProjectWriter().init( getLog(), config ).write();

            // Handle embedded jarfiles, etc...
            Manifest manifest = extractManifest( config.getEclipseProjectDirectory() );
            String classPath = manifest.getMainAttributes().getValue( "Bundle-ClassPath" );
            patchClassPath( config.getEclipseProjectDirectory(), classPath );
        }
        catch ( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }
    }
}
