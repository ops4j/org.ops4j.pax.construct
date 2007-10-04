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

/**
 * Create a new OSGi project tree that supports wrapping, compiling and provisioning of bundles
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal create-project
 * @requiresProject false
 */
public class OSGiProjectArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * The logical parent of the new project (use artifactId or groupId:artifactId). Default is no parent.
     * 
     * @parameter alias="parentId" expression="${parentId}"
     */
    private String m_parentId;

    /**
     * The groupId for the new project.
     * 
     * @parameter alias="groupId" expression="${groupId}"
     * @required
     */
    private String m_groupId;

    /**
     * The artifactId for the new project.
     * 
     * @parameter alias="artifactId" expression="${artifactId}"
     * @required
     */
    private String m_artifactId;

    /**
     * The version for the new project.
     * 
     * @parameter alias="version" expression="${version}" default-value="1.0-SNAPSHOT"
     */
    private String m_version;

    /**
     * {@inheritDoc}
     */
    void updateExtensionFields()
    {
        getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-project" );

        getArchetypeMojo().setField( "groupId", m_groupId );
        getArchetypeMojo().setField( "artifactId", m_artifactId );
        getArchetypeMojo().setField( "version", m_version );

        getArchetypeMojo().setField( "packageName", getCompoundId( m_groupId, m_artifactId ) );
    }

    /**
     * {@inheritDoc}
     */
    String getParentId()
    {
        return m_parentId;
    }
}
