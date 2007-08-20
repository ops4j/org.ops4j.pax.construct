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

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Wrap a third-party jar as a bundle and add it to an existing OSGi project.
 * 
 * @goal wrap-jar
 */
public final class OSGiWrapperArchetypeMojo extends AbstractChildArchetypeMojo
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
        // this is the logical parent of the new bundle project
        if( "wrap-jar-as-bundle".equals( project.getArtifactId() ) )
        {
            linkChildToParent();
        }

        // only create archetype under physical parent (ie. the _root_ project)
        return super.checkEnvironment();
    }

    protected void updateExtensionFields()
        throws MojoExecutionException
    {
        setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );
        final String compoundName = getCompoundName( groupId, artifactId );

        setField( "groupId", getCompoundName( project.getGroupId(), project.getArtifactId() ) );
        setField( "artifactId", compoundName );
        setField( "version", version );

        setField( "packageName", getGroupMarker( groupId, artifactId ) );

        setChildProjectName( compoundName );
    }
}
