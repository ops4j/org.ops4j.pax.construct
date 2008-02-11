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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.model.Repository;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

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
    private static final String OSGI_PROJECT_ARCHETYPE_ID = "maven-archetype-osgi-project";

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
     * Important POMs related to Pax-Construct project settings that we don't want overwritten
     */
    private List m_settingPoms;

    /**
     * {@inheritDoc}
     */
    protected void updateExtensionFields()
    {
        setMainArchetype( OSGI_PROJECT_ARCHETYPE_ID );

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
    protected void cacheOriginalFiles( File baseDir )
    {
        m_settingPoms = new ArrayList();

        try
        {
            // additional POMs not captured by the abstract archetype class
            Pom pluginSettings = PomUtils.readPom( new File( baseDir, "poms" ) );
            Pom compiledSettings = PomUtils.readPom( new File( baseDir, "poms/compiled" ) );
            Pom wrapperSettings = PomUtils.readPom( new File( baseDir, "poms/wrappers" ) );

            m_settingPoms.add( pluginSettings );
            m_settingPoms.add( compiledSettings );
            m_settingPoms.add( wrapperSettings );

            // delete to allow customization
            pluginSettings.getFile().delete();
            compiledSettings.getFile().delete();
            wrapperSettings.getFile().delete();
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to cache project settings" );
        }
    }

    /**
     * {@inheritDoc}
     */
    protected void postProcess( Pom pom, Bnd bnd )
    {
        // always tie the pax-plugin to a specific version (helps with reproducible builds)
        pom.updatePluginVersion( "org.ops4j", "maven-pax-plugin", getPluginVersion() );

        // clear away some bogus files
        addTempFiles( "poms/imported/" );
        addTempFiles( "osgi.bnd" );

        // are there any customized POM settings that need merging?
        if( null == m_settingPoms || m_settingPoms.size() == 0 )
        {
            return;
        }

        // check the various POMs in case they've been customized
        for( Iterator i = m_settingPoms.iterator(); i.hasNext(); )
        {
            Pom settingsPom = (Pom) i.next();

            try
            {
                // merge and write updates back
                saveProjectModel( settingsPom );
                settingsPom.removeModule( "imported" );
                settingsPom.write();
            }
            catch( IOException e )
            {
                getLog().warn( "Unable to merge project settings " + settingsPom );
            }
        }
    }
}
