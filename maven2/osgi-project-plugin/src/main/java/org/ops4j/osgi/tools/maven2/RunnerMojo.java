package org.ops4j.osgi.tools.maven2;

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
 * Create pom file for Pax-Runner, to enable provisioning
 *
 * @goal runner
 */
public class RunnerMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${maven.home}/bin/mvn"
     * @required
     */
    private File mvn;

    /**
     * The containing OSGi project
     *
     * @parameter expression="${project}"
     */
    private MavenProject project;

    private static MavenProject m_runnerPom;
    private static List m_dependencies;
    private static Thread m_shutdownHook;

    public void execute()
        throws MojoExecutionException
    {
        if ( m_runnerPom == null )
        {
            m_runnerPom = new MavenProject( new Model() );
            m_dependencies = new ArrayList();

            m_shutdownHook = new Thread( new WriteRunnerPom() );
            Runtime.getRuntime().addShutdownHook( m_shutdownHook );
        }

        MavenProject parentProject = project.getParent();

        if ( parentProject == null )
        {
            m_runnerPom.setGroupId( project.getGroupId() );
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
        else
        {
            String thisGroupId = project.getGroupId();

            if ( thisGroupId.endsWith( ".dependencies" ) )
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
    }

    private final class WriteRunnerPom
        implements Runnable
    {
        public void run()
        {
            try
            {
                m_runnerPom.setDependencies( m_dependencies );
                m_runnerPom.writeModel( new FileWriter( m_runnerPom.getFile() ) );

                Commandline installPomCmd = new Commandline();
                installPomCmd.setExecutable( mvn.getAbsolutePath() );
                installPomCmd.createArgument().setValue( "-N" );
                installPomCmd.createArgument().setValue( "-f" );
                installPomCmd.createArgument().setValue( m_runnerPom.getFile().getAbsolutePath() );
                installPomCmd.createArgument().setValue( "install" );

                CommandLineUtils.executeCommandLine( installPomCmd, null, null );
            }
            catch ( Exception ex )
            {
                getLog().error( ex );
            }
        }
    }
}

