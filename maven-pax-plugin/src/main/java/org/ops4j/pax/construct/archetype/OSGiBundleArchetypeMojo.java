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
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Create a new bundle project inside an existing Pax-Construct OSGi project
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal create-bundle
 */
public class OSGiBundleArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * The logical parent of the new project (use artifactId or groupId:artifactId).
     * 
     * @parameter alias="parentId" expression="${parentId}" default-value="compiled-bundle-settings"
     */
    private String m_parentId;

    /**
     * The key Java package contained inside the bundle.
     * 
     * @parameter alias="package" expression="${package}"
     * @required
     */
    private String m_packageName;

    /**
     * The symbolic-name for the bundle (defaults to packageName if empty).
     * 
     * @parameter alias="bundleName" expression="${bundleName}"
     */
    private String m_bundleName;

    /**
     * The version of the bundle.
     * 
     * @parameter alias="version" expression="${version}" default-value="1.0-SNAPSHOT"
     */
    private String m_version;

    /**
     * When true, provide an example service API.
     * 
     * @parameter alias="interface" expression="${interface}" default-value="true"
     */
    private boolean m_provideInterface;

    /**
     * When false, don't provide any implementation code.
     * 
     * @parameter alias="internals" expression="${internals}" default-value="true"
     */
    private boolean m_provideInternals;

    /**
     * When true, provide an example Bundle-Activator class.
     * 
     * @parameter alias="activator" expression="${activator}" default-value="true"
     */
    private boolean m_provideActivator;

    /**
     * When true, do not add any basic OSGi dependencies to the project.
     * 
     * @parameter alias="noDependencies" expression="${noDependencies}"
     */
    private boolean m_noDependencies;

    /**
     * {@inheritDoc}
     */
    String getParentId()
    {
        return m_parentId;
    }

    /**
     * {@inheritDoc}
     */
    void updateExtensionFields()
    {
        // use the Java package as the symbolic name if no name given
        if( null == m_bundleName || m_bundleName.trim().length() == 0 )
        {
            m_bundleName = m_packageName;
        }

        getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-bundle" );

        getArchetypeMojo().setField( "groupId", getInternalGroupId() );
        getArchetypeMojo().setField( "artifactId", m_bundleName );
        getArchetypeMojo().setField( "version", m_version );

        getArchetypeMojo().setField( "packageName", m_packageName );
    }

    /**
     * {@inheritDoc}
     */
    void postProcess()
        throws MojoExecutionException
    {
        /*
         * Unwanted files
         */
        if( !m_provideInterface )
        {
            addTempFiles( "src/main/java/**/ExampleService.java" );
        }
        if( !m_provideInternals )
        {
            addTempFiles( "src/main/java/**/internal" );
        }
        if( !m_provideActivator )
        {
            addTempFiles( "src/main/java/**/Activator.java" );
        }

        // remove files, etc.
        super.postProcess();

        if( !m_noDependencies )
        {
            try
            {
                // standard R4 OSGi API
                addOSGiDependenciesToPom();
            }
            catch( IOException e )
            {
                throw new MojoExecutionException( "Problem updating Maven POM: " + getPomFile() );
            }
        }

        try
        {
            // match with contents
            updateBndInstructions();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Problem updating Bnd instructions" );
        }
    }

    /**
     * Adds the standard R4 OSGi API to the compilation path
     * 
     * @throws IOException
     * @throws MojoExecutionException
     */
    void addOSGiDependenciesToPom()
        throws IOException,
        MojoExecutionException
    {
        Pom thisPom = PomUtils.readPom( getPomFile() );

        Dependency osgiCore = new Dependency();
        osgiCore.setGroupId( "org.osgi" );
        osgiCore.setArtifactId( "osgi_R4_core" );
        thisPom.addDependency( osgiCore, canOverwrite() );

        Dependency osgiCompendium = new Dependency();
        osgiCompendium.setGroupId( "org.osgi" );
        osgiCompendium.setArtifactId( "osgi_R4_compendium" );
        thisPom.addDependency( osgiCompendium, canOverwrite() );

        thisPom.write();
    }

    /**
     * Updates the default BND instructions to match the remaining contents
     * 
     * @throws IOException
     * @throws MojoExecutionException
     */
    void updateBndInstructions()
        throws IOException,
        MojoExecutionException
    {
        BndFile bndFile = BndFileUtils.readBndFile( getPomFile().getParentFile() );

        if( m_provideInternals && !m_provideInterface )
        {
            // internals, but nothing to export
            bndFile.setInstruction( "Export-Package", null, canOverwrite() );
        }
        if( !m_provideInternals && m_provideInterface )
        {
            // public api, but no internals left
            bndFile.setInstruction( "Private-Package", null, canOverwrite() );
        }
        if( !m_provideActivator || !m_provideInternals )
        {
            bndFile.removeInstruction( "Bundle-Activator" );
        }

        bndFile.write();
    }
}
