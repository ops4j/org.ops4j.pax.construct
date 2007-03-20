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

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Goal which adds a wrapped jarfile to an existing OSGi project.
 *
 * @goal wrap
 */
public class WrapMojo
    extends AbstractArchetypeMojo
{
    /**
     * The groupId of the jarfile to wrap.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId of the jarfile to wrap.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the jarfile to wrap.
     * 
     * @parameter expression="${version}"
     * @required
     */
    private String version;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        return project.getArtifactId().equals("wrap-jar-as-bundle");
    }

    protected void createSubArguments( Commandline commandLine )
    {
        commandLine.createArgument().setValue( "-DarchetypeArtifactId=wrap-jar-archetype" );

        commandLine.createArgument().setValue( "-DgroupId="+project.getGroupId().replaceFirst( "\\.build$", "" ) );

        commandLine.createArgument().setValue( "-DpackageName="+groupId );
        commandLine.createArgument().setValue( "-DartifactId="+artifactId );
        commandLine.createArgument().setValue( "-Dversion="+version );

        commandLine.createArgument().setValue( "-Duser.dir="+project.getBasedir() );
    }
}

