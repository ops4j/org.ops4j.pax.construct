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
 * Import a bundle from Maven/OBR and add it to an existing OSGi project.
 * 
 * @goal import-bundle
 */
public final class OSGiImportArchetypeMojo extends AbstractChildArchetypeMojo
{
    /**
     * The groupId of the bundle to import.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId of the bundle to import.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the bundle to import.
     * 
     * @parameter expression="${version}"
     * @required
     */
    private String version;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        // this is the logical parent of the new bundle project
        if( project.getArtifactId().equals( "import-bundle" ) )
        {
            linkChildToParent();
        }

        // only create archetype under physical parent (ie. the _root_ project)
        return super.checkEnvironment();
    }

    protected void updateExtensionFields()
        throws MojoExecutionException
    {
        setField( "archetypeArtifactId", "maven-archetype-osgi-import" );
        final String compoundName = getCompoundName( groupId, artifactId );

        setField( "groupId", project.getGroupId() + "." + project.getArtifactId() + ".imports" );
        setField( "artifactId", compoundName );
        setField( "version", version );

        setField( "packageName", getGroupMarker( groupId, artifactId ) );

        setChildProjectName( compoundName );
    }
}
