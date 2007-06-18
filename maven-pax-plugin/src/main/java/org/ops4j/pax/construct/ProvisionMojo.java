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
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Create configuration pom file for provisioning via Pax-Runner
 * 
 * @goal provision
 */
public final class ProvisionMojo extends AbstractMojo
{
    /**
     * The target OSGi project
     * 
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
     * @parameter expression="${platform}" default-value="equinox"
     */
    private String platform;

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

    private static MavenProject m_runnerPom;
    private static List<Dependency> m_dependencies;
    private static int m_projectCount;

    public void execute()
        throws MojoExecutionException
    {
        if( m_runnerPom == null )
        {
            m_runnerPom = new MavenProject( new Model() );
            m_dependencies = new ArrayList<Dependency>();
            m_projectCount = 0;
        }

        if( project.hasParent() )
        {
            addBundleDependency();
        }
        else
        {
            initializeRunnerPom();
        }

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

        m_runnerPom.getModel().setProperties( project.getProperties() );

        String targetPath = project.getFile().getParent() + File.separator + "target";
        String runnerPath = targetPath + File.separator + "runner" + File.separator + "pom.xml";

        File runnerPomFile = new File( runnerPath );
        runnerPomFile.getParentFile().mkdirs();
        m_runnerPom.setFile( runnerPomFile );
    }

    private void addBundleDependency()
    {
        if( "bundle".equals( project.getPackaging() ) )
        {
            Dependency dependency = new Dependency();
            dependency.setGroupId( project.getGroupId() );
            dependency.setArtifactId( project.getArtifactId() );
            dependency.setVersion( project.getVersion() );
            m_dependencies.add( dependency );
        }
        else
        {
            Properties props = project.getProperties();

            if( props.containsKey( "bundle.artifactId" ) )
            {
                Dependency dependency = new Dependency();
                dependency.setGroupId( props.getProperty( "bundle.groupId" ) );
                dependency.setArtifactId( props.getProperty( "bundle.artifactId" ) );
                dependency.setVersion( props.getProperty( "bundle.version" ) );
                m_dependencies.add( dependency );
            }
        }
    }

    private void installRunnerPom()
        throws MojoExecutionException
    {
        try
        {
            File pomFile = m_runnerPom.getFile();

            if( m_dependencies.size() == 0 )
            {
                getLog().info( "~~~~~~~~~~~~~~~~~~~" );
                getLog().info( " No bundles found! " );
                getLog().info( "~~~~~~~~~~~~~~~~~~~" );
            }

            m_runnerPom.setDependencies( m_dependencies );
            m_runnerPom.writeModel( new FileWriter( pomFile ) );

            Artifact artifact = factory.createProjectArtifact( m_runnerPom.getGroupId(), m_runnerPom.getArtifactId(),
                m_runnerPom.getVersion() );

            installer.install( pomFile, artifact, localRepository );

            if( deploy )
            {
                String workDir = pomFile.getParent() + File.separator + "work";

                File cachedPomFile = new File( workDir + File.separator + "lib" + File.separator
                    + m_runnerPom.getArtifactId() + "_" + m_runnerPom.getVersion() + ".pom" );

                // Force reload of pom
                cachedPomFile.delete();

                // Pass on current repo list to Pax-Runner
                StringBuilder repoListBuilder = new StringBuilder();
                for( Object repo : remoteArtifactRepositories )
                {
                    if( repoListBuilder.length() > 0 )
                    {
                        repoListBuilder.append( ',' );
                    }
                    repoListBuilder.append( ((ArtifactRepository) repo).getUrl() );
                }

                String[] deployAppCmds =
                {
                    "--dir=" + workDir, "--no-md5", "--platform=" + platform, "--profile=default",
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
