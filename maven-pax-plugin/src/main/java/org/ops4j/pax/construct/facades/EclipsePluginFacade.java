package org.ops4j.pax.construct.facades;

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
import org.apache.maven.project.MavenProject;

public class EclipsePluginFacade extends EclipsePlugin
{
    protected boolean isWrappedJarFile = false;
    protected boolean isImportedBundle = false;

    public boolean setup() throws MojoExecutionException
    {
        project = super.project;
        setWtpversion( "none" );

        String parentArtifactId = project.getParent().getArtifactId();

        isWrappedJarFile = parentArtifactId.equals( "wrap-jar-as-bundle" );
        isImportedBundle = parentArtifactId.equals( "import-bundle" );

        if ( isImportedBundle )
        {
            setEclipseProjectDir( project.getFile().getParentFile() );
        }

        return super.setup();
    }

    protected void setupExtras() throws MojoExecutionException
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
            System.out.println("oops "+e);
        }
    }

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
     * @parameter
     */
    protected List projectnatures;

    /**
     * @parameter
     */
    protected List additionalProjectnatures;

    /**
     * @parameter
     */
    protected List buildcommands;

    /**
     * @parameter
     */
    protected List additionalBuildcommands;

    /**
     * @parameter
     */
    protected List classpathContainers;

    /**
     * @parameter expression="${eclipse.downloadSources}"
     * @deprecated use downloadSources
     */
    protected boolean eclipseDownloadSources;

    /**
     * @parameter expression="${eclipse.workspace}" alias="outputDir"
     */
    protected File eclipseProjectDir;

    /**
     * @parameter expression="${eclipse.useProjectReferences}" default-value="true"
     * @required
     */
    protected boolean useProjectReferences;

    /**
     * @parameter expression="${outputDirectory}" alias="outputDirectory" default-value="${project.build.outputDirectory}"
     * @required
     */
    protected File buildOutputDirectory;
}

