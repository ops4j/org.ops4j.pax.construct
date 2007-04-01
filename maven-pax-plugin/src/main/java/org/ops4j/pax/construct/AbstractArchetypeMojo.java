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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

/**
 * Foundation for all OSGi project goals that use archetypes.
 */
public abstract class AbstractArchetypeMojo extends AbstractMojo
{
    private final static String archetypeGroupId = "org.ops4j.pax.construct";

    /**
     * @parameter expression="${maven.home}/bin/mvn"
     * @required
     */
    private File mvn;

    /**
     * @parameter expression="${archetypeVersion}" default-value="RELEASE"
     */
    private String archetypeVersion;

    /**
     * @parameter expression="${debug}" default-value="false"
     */
    private boolean debug;

    /**
     * @parameter expression="${compactNames}" default-value="true"
     */
    private boolean compactNames;

    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

    protected String getGroupMarker( String groupId, String artifactId )
    {
        if ( compactNames && artifactId.startsWith( groupId ) )
        {
            return "-" + groupId;
        }

        return "+" + groupId;
    }

    protected String getCompoundName( String groupId, String artifactId )
    {
        if ( compactNames && artifactId.startsWith( groupId ) )
        {
            return artifactId;
        }

        return groupId + "." + artifactId;
    }

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
        commandLine.createArgument().setValue( "-DarchetypeGroupId=" + archetypeGroupId );
        commandLine.createArgument().setValue( "-DarchetypeVersion=" + archetypeVersion );
        commandLine.createArgument().setValue( "-DremoteRepositories=http://repository.ops4j.org/maven2" );

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
