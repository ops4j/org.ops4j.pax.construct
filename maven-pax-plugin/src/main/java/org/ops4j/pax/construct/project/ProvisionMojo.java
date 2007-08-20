package org.ops4j.pax.construct.project;

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
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;

/**
 * Create configuration pom file for provisioning via Pax-Runner
 * 
 * @goal provision
 */
public final class ProvisionMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * @parameter default-value="${reactorProjects}"
     * @required
     * @readonly
     */
    private List reactorProjects;

    /**
     * @parameter expression="${deploy}" default-value="true"
     */
    private boolean deploy;

    /**
     * @parameter expression="${excludeTransitive}"
     */
    private boolean excludeTransitive;

    /**
     * @parameter expression="${deploy.poms}"
     */
    private String additionalPoms;

    /**
     * @parameter expression="${framework}" default-value="felix"
     */
    private String framework;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List remoteArtifactRepositories;

    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    private ArtifactFactory factory;

    /**
     * @parameter expression="${component.org.apache.maven.artifact.installer.ArtifactInstaller}"
     * @required
     * @readonly
     */
    private ArtifactInstaller installer;

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
     * @component role="org.apache.maven.project.MavenProjectBuilder"
     * @required
     * @readonly
     */
    protected MavenProjectBuilder mavenProjectBuilder;

    private static MavenProject m_runnerPom;
    private static Properties m_properties;
    private static Set m_bundleArtifacts;
    private static int m_projectCount;

    public void execute()
        throws MojoExecutionException
    {
        if( m_runnerPom == null )
        {
            m_runnerPom = new MavenProject( new Model() );
            m_properties = new Properties();
            m_bundleArtifacts = new TreeSet();
            m_projectCount = 0;
        }

        if( !project.hasParent() )
        {
            initializeRunnerPom();
        }

        addBundleDependencies( project );

        if( ++m_projectCount == reactorProjects.size() )
        {
            installRunnerPom();
        }
    }

    private void initializeRunnerPom()
    {
        m_runnerPom.setGroupId( project.getGroupId() + "." + project.getArtifactId() );
        m_runnerPom.setArtifactId( "runner" );
        m_runnerPom.setVersion( project.getVersion() );

        m_runnerPom.setName( project.getName() );
        m_runnerPom.setDescription( project.getDescription() );
        m_runnerPom.setModelVersion( "4.0.0" );
        m_runnerPom.setPackaging( "pom" );

        m_properties.putAll( project.getProperties() );

        String runnerPath = project.getFile().getParent() + "/target/runner/pom.xml";

        File runnerPomFile = new File( runnerPath );
        runnerPomFile.getParentFile().mkdirs();
        m_runnerPom.setFile( runnerPomFile );

        if( additionalPoms != null )
        {
            addAdditionalPoms();
        }
    }

    private void addBundleDependencies( MavenProject deployableProject )
    {
        Artifact keyArtifact = deployableProject.getArtifact();
        if( deployableProject.getPackaging().indexOf( "bundle" ) >= 0 )
        {
            m_bundleArtifacts.add( keyArtifact );
        }

        try
        {
            Set artifacts = deployableProject.createArtifacts( factory, null, null );

            if( !excludeTransitive )
            {
                ArtifactResolutionResult result = artifactResolver.resolveTransitively( artifacts, keyArtifact,
                    remoteArtifactRepositories, localRepository, artifactMetadataSource );

                artifacts = result.getArtifacts();
            }

            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if( "provided".equals( artifact.getScope() ) && !artifact.isOptional() )
                {
                    m_bundleArtifacts.add( artifact );
                }
            }
        }
        catch( Exception e )
        {
            getLog().error( e );
        }
    }

    private void addAdditionalPoms()
    {
        String[] pomPaths = additionalPoms.split( "," );
        for( int i = 0; i < pomPaths.length; i++ )
        {
            File pomFile = new File( pomPaths[i] );
            if( pomFile.exists() )
            {
                try
                {
                    addBundleDependencies( mavenProjectBuilder.build( pomFile, localRepository, null ) );
                }
                catch( Exception e )
                {
                    getLog().error( e );
                }
            }
        }
    }

    private void installRunnerPom()
        throws MojoExecutionException
    {
        try
        {
            File pomFile = m_runnerPom.getFile();

            if( m_bundleArtifacts.size() == 0 )
            {
                getLog().info( "~~~~~~~~~~~~~~~~~~~" );
                getLog().info( " No bundles found! " );
                getLog().info( "~~~~~~~~~~~~~~~~~~~" );
            }

            List dependencies = new ArrayList();
            for( Iterator i = m_bundleArtifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();

                Dependency dep = new Dependency();
                dep.setGroupId( artifact.getGroupId() );
                dep.setArtifactId( artifact.getArtifactId() );

                try
                {
                    // use symbolic version if available (ie. 1.0.0-SNAPSHOT)
                    dep.setVersion( artifact.getSelectedVersion().toString() );
                }
                catch( Exception e )
                {
                    dep.setVersion( artifact.getVersion() );
                }

                dependencies.add( dep );
            }

            m_runnerPom.getModel().setProperties( m_properties );
            m_runnerPom.setDependencies( new ArrayList( dependencies ) );
            m_runnerPom.writeModel( new FileWriter( pomFile ) );

            String groupId = m_runnerPom.getGroupId();
            String artifactId = m_runnerPom.getArtifactId();
            String version = m_runnerPom.getVersion();

            Artifact artifact = factory.createProjectArtifact( groupId, artifactId, version );
            installer.install( pomFile, artifact, localRepository );

            if( deploy )
            {
                String workDir = pomFile.getParent() + "/work";
                File cachedPomFile = new File( workDir + "/lib/" + artifactId + "_" + version + ".pom" );

                // Force reload of pom
                cachedPomFile.delete();

                // Pass on current repo list to Pax-Runner
                StringBuffer repoListBuilder = new StringBuffer();
                for( Iterator i = remoteArtifactRepositories.iterator(); i.hasNext(); )
                {
                    ArtifactRepository repo = (ArtifactRepository) i.next();
                    if( repoListBuilder.length() > 0 )
                    {
                        repoListBuilder.append( ',' );
                    }
                    repoListBuilder.append( repo.getUrl() );
                }

                String[] deployAppCmds =
                {
                    "--dir=" + workDir, "--no-md5", "--platform=" + framework, "--profile=default",
                    "--repository=" + repoListBuilder.toString(), "--localRepository=" + localRepository.getBasedir(),
                    m_runnerPom.getGroupId(), m_runnerPom.getArtifactId(), m_runnerPom.getVersion()
                };

                org.ops4j.pax.runner.Run.main( deployAppCmds );
            }
        }
        catch( Exception ex )
        {
            throw new MojoExecutionException( "Installation error", ex );
        }
    }
}
