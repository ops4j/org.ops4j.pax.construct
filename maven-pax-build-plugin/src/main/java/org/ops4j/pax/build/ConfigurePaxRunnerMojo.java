package org.ops4j.pax.build;

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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Create configuration pom file for provisioning via Pax-Runner
 *
 * @goal provision
 */
public class ConfigurePaxRunnerMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${maven.home}/bin/mvn"
     * @required
     */
    private File mvn;

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

    private static MavenProject m_runnerPom;
    private static List m_dependencies;
    private static int m_projectCount;

    public void execute()
        throws MojoExecutionException
    {
        if ( m_runnerPom == null )
        {
            m_runnerPom = new MavenProject( new Model() );
            m_dependencies = new ArrayList();
            m_projectCount = 0;
        }

        if ( project.hasParent() )
        {
            addBundleDependency();
        }
        else
        {
            initializeRunnerPom();
        }

        if ( ++m_projectCount == reactorProjects.size() )
        {
            installRunnerPom();
        }
    }

    private void initializeRunnerPom()
    {
        m_runnerPom.setGroupId( project.getGroupId()+"."+project.getArtifactId() );
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
        String thisGroupId = project.getGroupId();

        if ( thisGroupId.endsWith( ".imports" ) )
        {
            Properties props = project.getProperties();

            Dependency dependency = new Dependency();
            dependency.setGroupId( props.getProperty( "bundle.groupId" ) );
            dependency.setArtifactId( props.getProperty( "bundle.artifactId" ) );
            dependency.setVersion( props.getProperty( "bundle.version" ) );
            m_dependencies.add( dependency );
        }
        else if ( thisGroupId.endsWith( ".bundles" ) )
        {
            Dependency dependency = new Dependency();
            dependency.setGroupId( project.getGroupId() );
            dependency.setArtifactId( project.getArtifactId() );
            dependency.setVersion( project.getVersion() );
            m_dependencies.add( dependency );
        }
    }

    private void installRunnerPom()
        throws MojoExecutionException
    {
        try
        {
            File pomFile = m_runnerPom.getFile();

            m_runnerPom.setDependencies( m_dependencies );
            m_runnerPom.writeModel( new FileWriter( pomFile ) );

            Commandline installPomCmd = new Commandline();
            installPomCmd.setExecutable( mvn.getAbsolutePath() );
            installPomCmd.createArgument().setValue( "-N" );
            installPomCmd.createArgument().setValue( "-f" );
            installPomCmd.createArgument().setValue( pomFile.getAbsolutePath() );
            installPomCmd.createArgument().setValue( "install" );

            CommandLineUtils.executeCommandLine( installPomCmd, null, null );

            if ( deploy )
            {
                String workDir = pomFile.getParent() + File.separator + "work";

                String[] deployAppCmds =
                {
                    "--dir="+workDir,
                    "--clean", "--no-md5",
                    "--platform="+platform,
                    m_runnerPom.getGroupId(),
                    m_runnerPom.getArtifactId(),
                    m_runnerPom.getVersion()
                };

                org.ops4j.pax.runner.Run.main( deployAppCmds );
            }
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "Installation error", ex );
        }
    }
}

