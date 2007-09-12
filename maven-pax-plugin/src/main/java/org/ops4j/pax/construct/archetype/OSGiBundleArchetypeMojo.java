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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @goal archetype:create=create-bundle
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
        super.postProcess();

        if( addOSGiDependencies )
        {
            Pom thisPom = PomUtils.readPom( m_pomFile );

            Dependency osgiCore = new Dependency();
            osgiCore.setGroupId( "org.osgi" );
            osgiCore.setArtifactId( "osgi_R4_core" );
            thisPom.addDependency( osgiCore, overwrite );

            Dependency osgiCompendium = new Dependency();
            osgiCompendium.setGroupId( "org.osgi" );
            osgiCompendium.setArtifactId( "osgi_R4_compendium" );
            thisPom.addDependency( osgiCompendium, overwrite );

            thisPom.write();
        }

        FileSet bogusFiles = new FileSet();
        bogusFiles.setDirectory( targetDirectory + File.separator + bundleName );
        bogusFiles.addInclude( "src/main/resources" );

        if( !provideInterface )
        {
            bogusFiles.addInclude( "src/main/java/**/ExampleService.java" );
        }

        if( !provideActivator )
        {
            bogusFiles.addInclude( "src/main/java/**/internal" );
        }

        try
        {
            new FileSetManager( getLog(), false ).delete( bogusFiles );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error while patching files", e );
        }

        BndFile bndFile = BndFileUtils.readBndFile( m_pomFile.getParentFile() );

        if( provideActivator && !provideInterface )
        {
            bndFile.setInstruction( "Export-Package", null, overwrite );
        }

        if( !provideActivator && provideInterface )
        {
            bndFile.removeInstruction( "Bundle-Activator" );
            bndFile.setInstruction( "Private-Package", null, overwrite );
        }

        bndFile.write();
    }

    String getParentId()
    {
        return parentId;
    }
}
