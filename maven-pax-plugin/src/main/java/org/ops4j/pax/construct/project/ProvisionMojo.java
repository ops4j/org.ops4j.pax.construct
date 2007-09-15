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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.ops4j.pax.construct.util.PomUtils;

/**
 * @goal provision
 */
public class ProvisionMojo extends AbstractMojo
{
    /**
     * @parameter default-value="${project}"
     */
    MavenProject project;

    /**
     * @parameter default-value="${reactorProjects}"
     */
    List reactorProjects;

    /**
     * @parameter expression="${remoteRepositories}" default-value="${project.remoteArtifactRepositories}"
     */
    List remoteRepos;

    /**
     * @parameter expression="${localRepository}"
     * @required
     */
    ArtifactRepository localRepo;

    /**
     * @component
     */
    ArtifactFactory artifactFactory;

    /**
     * @component
     */
    ArtifactInstaller artifactInstaller;

    /**
     * @component
     */
    MavenProjectBuilder mavenProjectBuilder;

    /**
     * @parameter expression="${framework}" default-value="felix"
     */
    String framework;

    /**
     * @parameter expression="${deploy}" default-value="true"
     */
    boolean deploy;

    /**
     * @parameter expression="${deployPoms}"
     */
    String additionalPoms;

    static MavenProject m_runnerPom;
    static Properties m_properties;
    static Set m_bundleArtifacts;
    static int m_projectCount;

    public void execute()
        throws MojoExecutionException
    {
        if( m_runnerPom == null )
        {
            m_runnerPom = new MavenProject( new Model() );
            m_properties = new Properties();
            m_bundleArtifacts = new HashSet();
            m_projectCount = 0;

            initializeRunnerPom();
        }

        addBundleDependencies( project );

        if( ++m_projectCount == reactorProjects.size() )
        {
            installRunnerPom();
        }
    }

    void initializeRunnerPom()
    {
        m_runnerPom.setGroupId( project.getGroupId() );
        m_runnerPom.setArtifactId( project.getArtifactId() + "-deployment" );
        m_runnerPom.setVersion( project.getVersion() );

        m_runnerPom.setName( project.getName() );
        m_runnerPom.setDescription( project.getDescription() );
        m_runnerPom.setModelVersion( "4.0.0" );
        m_runnerPom.setPackaging( "pom" );

        m_properties.putAll( project.getProperties() );

        String runnerPath = project.getBasedir() + "/target/deployed/pom.xml";

        File runnerPomFile = new File( runnerPath );
        runnerPomFile.getParentFile().mkdirs();
        m_runnerPom.setFile( runnerPomFile );

        if( additionalPoms != null )
        {
            addAdditionalPoms();
        }
    }

    void addBundleDependencies( MavenProject deployableProject )
    {
        if( PomUtils.isBundleProject( deployableProject ) )
        {
            m_bundleArtifacts.add( deployableProject.getArtifact() );
        }

        try
        {
            Set artifacts = deployableProject.createArtifacts( artifactFactory, null, null );
            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if( Artifact.SCOPE_PROVIDED.equals( artifact.getScope() ) && !artifact.isOptional() )
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

    void addAdditionalPoms()
    {
        String[] pomPaths = additionalPoms.split( "," );
        for( int i = 0; i < pomPaths.length; i++ )
        {
            File pomFile = new File( pomPaths[i] );
            if( pomFile.exists() )
            {
                try
                {
                    addBundleDependencies( mavenProjectBuilder.build( pomFile, localRepo, null ) );
                }
                catch( Exception e )
                {
                    getLog().error( e );
                }
            }
        }
    }

    void installRunnerPom()
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
                dep.setVersion( PomUtils.getMetaVersion( artifact ) );

                dependencies.add( dep );
            }

            m_runnerPom.getModel().setProperties( m_properties );
            m_runnerPom.setDependencies( new ArrayList( dependencies ) );
            m_runnerPom.writeModel( new FileWriter( pomFile ) );

            String groupId = m_runnerPom.getGroupId();
            String artifactId = m_runnerPom.getArtifactId();
            String version = m_runnerPom.getVersion();

            Artifact artifact = artifactFactory.createProjectArtifact( groupId, artifactId, version );
            artifactInstaller.install( pomFile, artifact, localRepo );

            if( deploy )
            {
                String workDir = pomFile.getParent() + "/work";
                File cachedPomFile = new File( workDir + "/lib/" + artifactId + '_' + version + ".pom" );

                // Force reload of pom
                cachedPomFile.delete();

                StringBuffer repoListBuilder = new StringBuffer();
                for( Iterator i = remoteRepos.iterator(); i.hasNext(); )
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
                    "--repository=" + repoListBuilder.toString(), "--localRepository=" + localRepo.getBasedir(),
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
