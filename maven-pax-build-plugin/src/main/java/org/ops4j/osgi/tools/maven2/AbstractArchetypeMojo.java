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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.File;

/**
 * Foundation for all OSGi project goals that use archetypes.
 */
public abstract class AbstractArchetypeMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${maven.home}/bin/mvn"
     * @required
     */
    private File mvn;

    /**
     * @parameter expression="${debug}" default-value="false"
     */
    private boolean debug;

    /**
     * The containing OSGi project
     *
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    private final static String archetypeGroupId = "org.ops4j.pax.build";
    private final static String archetypeVersion = "0.1.0-SNAPSHOT"; // TODO: use RELEASE/LATEST when deployed?

    public void execute()
        throws MojoExecutionException
    {
        if ( checkEnvironment() == false )
        {
            return;
        }

        Commandline commandLine = new Commandline();

        commandLine.setExecutable( mvn.getAbsolutePath() );

        commandLine.createArgument().setValue( "archetype:create" );
        commandLine.createArgument().setValue( "-DarchetypeGroupId="+archetypeGroupId );
        commandLine.createArgument().setValue( "-DarchetypeVersion="+archetypeVersion );

        addAdditionalArguments( commandLine );

        StreamConsumer consumer = new StreamConsumer()
        {
            public void consumeLine( String line )
            {
                getLog().info( line );
            }
        };

        try
        {
            StreamConsumer stdOut = debug ? consumer : null;
            StreamConsumer stdErr = consumer;

            int result = CommandLineUtils.executeCommandLine( commandLine, stdOut, stdErr );

            if ( result != 0 )
            {
                throw new MojoExecutionException( "Result of " + commandLine + " execution is: '" + result + "'." );
            }
        }
        catch ( CommandLineException e )
        {
            throw new MojoExecutionException( "Command execution failed.", e );
        }
    }

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        return false;
    }

    abstract protected void addAdditionalArguments( Commandline commandLine );
}

