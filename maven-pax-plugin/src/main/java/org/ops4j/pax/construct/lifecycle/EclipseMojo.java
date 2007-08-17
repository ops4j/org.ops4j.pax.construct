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
import java.lang.reflect.Field;
import java.util.List;

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

    public boolean setup()
        throws MojoExecutionException
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
            EclipseWriterConfig config = createEclipseWriterConfig( deps );

            config.setEclipseProjectName( getEclipseProjectName( executedProject ) );

            // make sure project folder exists
            config.getEclipseProjectDirectory().mkdirs();

            new EclipseSettingsWriter().init( getLog(), config ).write();
            new EclipseClasspathWriter().init( getLog(), config ).write();
            new EclipseProjectWriter().init( getLog(), config ).write();

            if( "bundle".equals( executedProject.getPackaging() ) )
            {
                File here = new File( getBuildOutputDirectory().getParent(), "bundle" );
                unpackBundle( executedProject.getArtifact().getFile(), here );
            }
        }
        catch( Exception e )
        {
            getLog().error( e );

            throw new MojoExecutionException( "ERROR creating Eclipse files", e );
        }
    }

    protected static String getEclipseProjectName( MavenProject project )
    {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();

        String projectName;

        if( artifactId.startsWith( groupId + "." ) || artifactId.equals( groupId ) || groupId.endsWith( "bundles" ) )
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

        return projectName + " [" + project.getVersion() + "]";
    }

    protected void unpackBundle( File bundle, File here )
        throws MojoExecutionException
    {
        try
        {
            String bundleName = bundle.getName();
            if( bundleName.endsWith( ".pom" ) )
            {
                bundle = new File( bundle.getParent(), bundleName.replaceAll( ".pom$", ".jar" ) );
            }

            UnArchiver unArchiver = archiverManager.getUnArchiver( bundle );

            here.mkdirs();
            unArchiver.setDestDirectory( here );
            unArchiver.setSourceFile( bundle );
            unArchiver.extract();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "ERROR unpacking bundle", e );
        }
    }
}
