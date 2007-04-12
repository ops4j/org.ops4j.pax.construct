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

/**
 * Creates a new skeleton OSGi project.
 * 
 * @requiresProject false
 * @goal create-project
 */
public final class OSGiProjectArchetypeMojo extends AbstractArchetypeMojo
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

    /**
     * The version of the new OSGi project.
     * 
     * @parameter expression="${version}" default-value="0.1.0-SNAPSHOT"
     */
    private String version;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        if ( project.getFile() != null )
        {
            throw new MojoExecutionException( "Cannot use this plugin inside an existing project." );
        }

        return true;
    }

    protected void updateExtensionFields()
        throws MojoExecutionException
    {
        setField( "archetypeArtifactId", "maven-archetype-osgi-project" );

        setField( "groupId", groupId );
        setField( "artifactId", artifactId );
        setField( "version", version );

        setField( "packageName", getCompoundName( groupId, artifactId ) );
    }
}
