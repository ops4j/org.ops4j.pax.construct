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
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
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

/**
 * Extend maven-eclipse-plugin to get better classpath generation.
 * 
 * @goal eclipse
 */
public class EclipseMojo extends EclipsePlugin
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

    protected boolean isWrappedJarFile = false;
    protected boolean isImportedBundle = false;

    public boolean setup()
        throws MojoExecutionException
    {
        project = super.project;
        setWtpversion( "none" );
        setDownloadSources( true );

        String parentArtifactId = project.getParent().getArtifactId();

        isWrappedJarFile = parentArtifactId.equals( "wrap-jar-as-bundle" );
        isImportedBundle = parentArtifactId.equals( "import-bundle" );

        if ( isImportedBundle )
        {
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

        try
        {
            setField( "isJavaProject", true );
            setField( "pde", true );
        }
        catch ( Exception e )
        {
        }
    }

    private final void setField( String name, boolean flag )
    {
        try
        {
            Field f = EclipsePlugin.class.getDeclaredField( name );
            f.setAccessible( true );
            f.setBoolean( this, flag );
        }
        catch ( Exception e )
        {
            System.out.println( "Cannot set " + name + " to " + flag + " exception=" + e );
        }
    }

    protected static class JarFileFilter
        implements FileFilter
    {
        public boolean accept( File file )
        {
            return file.getName().endsWith( ".jar" );
        }
    }

    protected IdeDependency[] addClassFolder( IdeDependency[] dependencies, String folderPath )
    {
        IdeDependency[] newDeps = new IdeDependency[dependencies.length + 1];
        System.arraycopy( dependencies, 0, newDeps, 1, dependencies.length );

        newDeps[0] = new IdeDependency( "groupId", "artifactId", "version", false, false, true, true, true, new File(
            folderPath ), "classFolder", false, null, 0 );

        return newDeps;
    }

    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        EclipseWriterConfig config = createEclipseWriterConfig( deps );

        if ( isWrappedJarFile )
        {
            config.setDeps( addClassFolder( config.getDeps(), project.getBuild().getOutputDirectory() ) );
        }

        if ( isImportedBundle )
        {
            config.setDeps( addClassFolder( config.getDeps(), project.getBuild().getDirectory() ) );
        }

        if ( isWrappedJarFile || isImportedBundle )
        {
            config.setEclipseProjectDirectory( new File( project.getBuild().getDirectory() ) );
            config.setProjectBaseDir( config.getEclipseProjectDirectory() );
            config.setSourceDirs( new EclipseSourceDir[0] );
        }
        else
        {
            EclipseSourceDir[] sourceDirs = config.getSourceDirs();

            int localDirCount = 0;
            for ( int i = 0; i < sourceDirs.length; i++ )
            {
                if ( new File( sourceDirs[i].getPath() ).isAbsolute() == false )
                {
                    sourceDirs[localDirCount++] = sourceDirs[i];
                }
            }

            EclipseSourceDir[] localSourceDirs = new EclipseSourceDir[localDirCount];
            System.arraycopy( sourceDirs, 0, localSourceDirs, 0, localDirCount );

            config.setSourceDirs( localSourceDirs );
        }

        new EclipseSettingsWriter().init( getLog(), config ).write();
        new EclipseClasspathWriter().init( getLog(), config ).write();
        new EclipseProjectWriter().init( getLog(), config ).write();

        if ( !isImportedBundle )
        {
            File manifestFile = new File( config.getEclipseProjectDirectory(), "META-INF" + File.separator
                + "MANIFEST.MF" );

            try
            {
                JarFile bundle = new JarFile( project.getBuild().getDirectory() + File.separator
                    + project.getBuild().getFinalName() + ".jar" );

                manifestFile.mkdirs();
                manifestFile.delete();

                Manifest manifest = bundle.getManifest();
                manifest.write( new FileOutputStream( manifestFile ) );
            }
            catch ( IOException e )
            {
                System.out.println( "Cannot write manifest to " + manifestFile + " exception=" + e );
            }
        }
    }
}
