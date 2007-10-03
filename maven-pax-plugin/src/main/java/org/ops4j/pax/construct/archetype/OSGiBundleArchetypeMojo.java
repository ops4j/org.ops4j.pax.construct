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

import java.io.IOException;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal create-bundle
 */
public class OSGiBundleArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * @parameter expression="${parentId}" default-value="compiled-bundle-settings"
     */
    String parentId;

    /**
     * @parameter expression="${provisionId}" default-value="provision"
     */
    String provisionId;

    /**
     * @parameter expression="${package}"
     * @required
     */
    String packageName;

    /**
     * @parameter expression="${bundleName}"
     * @required
     */
    String bundleName;

    /**
     * @parameter expression="${version}" default-value="1.0-SNAPSHOT"
     */
    String version;

    /**
     * @parameter expression="${interface}" default-value="true"
     */
    boolean provideInterface;

    /**
     * @parameter expression="${internals}" default-value="true"
     */
    boolean provideInternals;

    /**
     * @parameter expression="${activator}" default-value="true"
     */
    boolean provideActivator;

    /**
     * @parameter expression="${addOSGiDependencies}" default-value="true"
     */
    boolean addOSGiDependencies;

    void updateExtensionFields()
    {
        m_mojo.setField( "archetypeArtifactId", "maven-archetype-osgi-bundle" );

        m_mojo.setField( "groupId", getCompactName( project.getGroupId(), project.getArtifactId() ) );
        m_mojo.setField( "artifactId", bundleName );
        m_mojo.setField( "version", version );

        m_mojo.setField( "packageName", packageName );
    }

    void postProcess()
        throws MojoExecutionException
    {
        if( !provideInterface )
        {
            m_tempFiles.addInclude( "src/main/java/**/ExampleService.java" );
        }
        if( !provideInternals )
        {
            m_tempFiles.addInclude( "src/main/java/**/internal" );
        }
        if( !provideActivator )
        {
            m_tempFiles.addInclude( "src/main/java/**/Activator.java" );
        }

        super.postProcess();

        if( addOSGiDependencies )
        {
            Pom thisPom;

            try
            {
                thisPom = PomUtils.readPom( m_pomFile );
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Problem reading Maven POM: " + m_pomFile );
            }

            Dependency osgiCore = new Dependency();
            osgiCore.setGroupId( "org.osgi" );
            osgiCore.setArtifactId( "osgi_R4_core" );
            thisPom.addDependency( osgiCore, overwrite );

            Dependency osgiCompendium = new Dependency();
            osgiCompendium.setGroupId( "org.osgi" );
            osgiCompendium.setArtifactId( "osgi_R4_compendium" );
            thisPom.addDependency( osgiCompendium, overwrite );

            try
            {
                thisPom.write();
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Problem writing Maven POM: " + thisPom.getFile() );
            }
        }

        BndFile bndFile;

        try
        {
            bndFile = BndFileUtils.readBndFile( m_pomFile.getParentFile() );
        }
        catch( IOException e1 )
        {
            throw new MojoExecutionException( "Problem reading Bnd file: " + m_pomFile.getParentFile() + "/osgi.bnd" );
        }

        if( provideInternals && !provideInterface )
        {
            bndFile.setInstruction( "Export-Package", null, overwrite );
        }
        if( !provideInternals && provideInterface )
        {
            bndFile.setInstruction( "Private-Package", null, overwrite );
        }
        if( !provideActivator || !provideInternals )
        {
            bndFile.removeInstruction( "Bundle-Activator" );
        }

        try
        {
            bndFile.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem writing Bnd file: " + bndFile.getFile() );
        }
    }

    String getParentId()
    {
        return parentId;
    }
}
