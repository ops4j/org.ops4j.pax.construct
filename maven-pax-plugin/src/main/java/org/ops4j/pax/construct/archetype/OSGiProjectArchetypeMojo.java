package org.ops4j.pax.construct.archetype;

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

import org.apache.maven.plugin.MojoExecutionException;

/**
 * @goal archetype:create=create-project
 * @requiresProject false
 */
public class OSGiProjectArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * @parameter expression="${parentId}"
     */
    String parentId;

    /**
     * @parameter expression="${groupId}"
     * @required
     */
    String groupId;

    /**
     * @parameter expression="${artifactId}"
     * @required
     */
    String artifactId;

    /**
     * @parameter expression="${version}" default-value="1.0-SNAPSHOT"
     */
    String version;

    void updateExtensionFields()
    {
        m_mojo.setField( "archetypeArtifactId", "maven-archetype-osgi-project" );

        m_mojo.setField( "groupId", groupId );
        m_mojo.setField( "artifactId", artifactId );
        m_mojo.setField( "version", version );

        m_mojo.setField( "packageName", getCompactName( groupId, artifactId ) );
    }

    void postProcess()
        throws MojoExecutionException
    {
        super.postProcess();

        File projectFolder = new File( targetDirectory, artifactId );
        File[] pruneFolders = new File[3];

        pruneFolders[0] = new File( projectFolder, "src" );
        pruneFolders[1] = new File( pruneFolders[0], "main" );
        pruneFolders[2] = new File( pruneFolders[1], "resources" );

        pruneFolders[2].delete();
        pruneFolders[1].delete();
        pruneFolders[0].delete();
    }

    String getParentId()
    {
        return parentId;
    }
}
