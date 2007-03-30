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

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Creates a new skeleton OSGi project.
 * 
 * @requiresProject false
 * @goal create-project
 */
public class OSGiProjectArchetypeMojo extends AbstractArchetypeMojo
{
    /**
     * The groupId of the new OSGi project.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId of the new OSGi project.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        if ( project.getFile() != null )
        {
            throw new MojoExecutionException( "Cannot use this plugin inside an existing project." );
        }

        return true;
    }

    protected void addAdditionalArguments( Commandline commandLine )
    {
        commandLine.createArgument().setValue( "-DarchetypeArtifactId=maven-archetype-osgi-project" );

        commandLine.createArgument().setValue( "-DgroupId=" + groupId );
        commandLine.createArgument().setValue( "-DartifactId=" + artifactId );

        String bundleGroupId = getCompoundName( groupId, artifactId );
        commandLine.createArgument().setValue( "-DpackageName=" + bundleGroupId );
    }
}
