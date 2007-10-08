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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.ops4j.pax.construct.util.PomUtils;
import org.xml.sax.SAXException;

/**
 * Provision all local and imported bundles onto the selected OSGi framework
 * 
 * @goal provision
 */
public class ProvisionMojo extends AbstractMojo
{
    private static MavenProject m_runnerPom;

    private static Properties m_properties;

    private static Set m_bundleArtifacts;

    private static int m_projectCount;

    /**
     * Component factory for Maven artifacts
     * 
     * @component
     */
    private ArtifactFactory m_artifactFactory;

    /**
     * Component for installing Maven artifacts
     * 
     * @component
     */
    private ArtifactInstaller m_installer;

    /**
     * Component factory for Maven projects
     * 
     * @component
     */
    private MavenProjectBuilder m_projectBuilder;

    /**
     * List of remote Maven repositories for the containing project.
     * 
     * @parameter alias="remoteRepositories" expression="${remoteRepositories}"
     *            default-value="${project.remoteArtifactRepositories}"
     */
    private List m_remoteRepos;

    /**
     * The local Maven repository for the containing project.
     * 
     * @parameter alias="localRepository" expression="${localRepository}"
     * @required
     */
    private ArtifactRepository m_localRepo;

    /**
     * The current Maven project.
     * 
     * @parameter default-value="${project}"
     * @readonly
     */
    private MavenProject m_project;

    /**
     * The current Maven reactor.
     * 
     * @parameter default-value="${reactorProjects}"
     * @readonly
     */
    private List m_reactorProjects;

    /**
     * Name of the OSGi framework to deploy onto.
     * 
     * @parameter alias="framework" expression="${framework}" default-value="felix"
     */
    private String m_framework;

    /**
     * When true, start the OSGi framework and deploy the provisioned bundles.
     * 
     * @parameter alias="deploy" expression="${deploy}" default-value="true"
     */
    private boolean m_deploy;

    /**
     * Comma separated list of additional POMs with bundles as provided dependencies.
     * 
     * @parameter alias="deployPoms" expression="${deployPoms}"
     */
    private String m_deployPoms;

    /**
     * Standard Maven mojo entry-point
     */
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

        addBundleDependencies( m_project );

        if( ++m_projectCount == m_reactorProjects.size() )
        {
            installRunnerPom();
        }
    }

    void initializeRunnerPom()
    {
        m_runnerPom.setGroupId( m_project.getGroupId() );
        m_runnerPom.setArtifactId( m_project.getArtifactId() + "-deployment" );
        m_runnerPom.setVersion( m_project.getVersion() );

        m_runnerPom.setName( m_project.getName() );
        m_runnerPom.setDescription( m_project.getDescription() );
        m_runnerPom.setModelVersion( "4.0.0" );
        m_runnerPom.setPackaging( "pom" );

        m_properties.putAll( m_project.getProperties() );

        String runnerPath = m_project.getBasedir() + "/target/deployed/pom.xml";

        File runnerPomFile = new File( runnerPath );
        runnerPomFile.getParentFile().mkdirs();
        m_runnerPom.setFile( runnerPomFile );

        if( m_deployPoms != null )
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
            Set artifacts = deployableProject.createArtifacts( m_artifactFactory, null, null );
            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if( Artifact.SCOPE_PROVIDED.equals( artifact.getScope() ) && !artifact.isOptional() )
                {
                    m_bundleArtifacts.add( artifact );
                }
            }
        }
        catch( InvalidDependencyVersionException e )
        {
            getLog().error( e );
        }
    }

    void addAdditionalPoms()
    {
        String[] pomPaths = m_deployPoms.split( "," );
        for( int i = 0; i < pomPaths.length; i++ )
        {
            File pomFile = new File( pomPaths[i] );
            if( pomFile.exists() )
            {
                try
                {
                    addBundleDependencies( m_projectBuilder.build( pomFile, m_localRepo, null ) );
                }
                catch( ProjectBuildingException e )
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

            Artifact artifact = m_artifactFactory.createProjectArtifact( groupId, artifactId, version );
            m_installer.install( pomFile, artifact, m_localRepo );

            if( m_deploy )
            {
                String workDir = pomFile.getParent() + "/work";
                File cachedPomFile = new File( workDir + "/lib/" + artifactId + '_' + version + ".pom" );

                // Force reload of pom
                cachedPomFile.delete();

                StringBuffer repoListBuilder = new StringBuffer();
                for( Iterator i = m_remoteRepos.iterator(); i.hasNext(); )
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
                    "--dir=" + workDir, "--no-md5", "--platform=" + m_framework, "--profile=default",
                    "--repository=" + repoListBuilder.toString(), "--localRepository=" + m_localRepo.getBasedir(),
                    m_runnerPom.getGroupId(), m_runnerPom.getArtifactId(), m_runnerPom.getVersion()
                };

                org.ops4j.pax.runner.Run.main( deployAppCmds );
            }
        }
        catch( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( "Installation error", e );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Read/Write error", e );
        }
        catch( ParserConfigurationException e )
        {
            throw new MojoExecutionException( "Parsing error", e );
        }
        catch( SAXException e )
        {
            throw new MojoExecutionException( "Parsing error", e );
        }
    }
}
