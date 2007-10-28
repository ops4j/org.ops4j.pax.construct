package org.ops4j.pax.construct.archetype;

import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.PomUtils.Pom;

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

/**
 * Create a new OSGi project tree that supports wrapping, compiling and provisioning of bundles
 * 
 * <code><pre>
 *   mvn org.ops4j:maven-pax-plugin:create-project -DgroupId=... -DartifactId=... [-Dversion=...]
 * </pre></code>
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal create-project
 */
public class OSGiProjectArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * The logical parent of the new project (use artifactId or groupId:artifactId). Default is no parent.
     * 
     * @parameter expression="${parentId}"
     */
    private String parentId;

    /**
     * The groupId for the new project.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId for the new project.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version for the new project.
     * 
     * @parameter expression="${version}" default-value="1.0-SNAPSHOT"
     */
    private String version;

    /**
     * {@inheritDoc}
     */
    protected void updateExtensionFields()
    {
        getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-project" );

        getArchetypeMojo().setField( "groupId", groupId );
        getArchetypeMojo().setField( "artifactId", artifactId );
        getArchetypeMojo().setField( "version", version );

        getArchetypeMojo().setField( "packageName", getCompoundId( groupId, artifactId ) );
    }

    /**
     * {@inheritDoc}
     */
    protected String getParentId()
    {
        return parentId;
    }

    /**
     * {@inheritDoc}
     */
    protected void postProcess( Pom pom, Bnd bnd )
        throws MojoExecutionException
    {
        // tie the pax-plugin to a specific version (helps with reproducible builds)
        pom.updatePluginVersion( "org.ops4j", "maven-pax-plugin", getArchetypeVersion() );
    }
}
