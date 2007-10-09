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
     * @parameter expression="${parentId}" default-value="compiled-bundle-settings"
     */
    private String parentId;

    /**
     * The key Java package contained inside the bundle.
     * 
     * @parameter expression="${package}"
     * @required
     */
    private String packageName;

    /**
     * The symbolic-name for the bundle (defaults to packageName if empty).
     * 
     * @parameter expression="${bundleName}"
     */
    private String bundleName;

    /**
     * The version of the bundle.
     * 
     * @parameter expression="${version}" default-value="1.0-SNAPSHOT"
     */
    private String version;

    /**
     * When true, provide an example service API.
     * 
     * @parameter expression="${interface}" default-value="true"
     */
    private boolean provideInterface;

    /**
     * When false, don't provide any implementation code.
     * 
     * @parameter expression="${internals}" default-value="true"
     */
    private boolean provideInternals;

    /**
     * When true, provide an example Bundle-Activator class.
     * 
     * @parameter expression="${activator}" default-value="true"
     */
    private boolean provideActivator;

    /**
     * When true, do not add any basic OSGi dependencies to the project.
     * 
     * @parameter expression="${noDependencies}"
     */
    private boolean noDependencies;

    /**
     * {@inheritDoc}
     */
    String getParentId()
    {
        return parentId;
    }

    /**
     * {@inheritDoc}
     */
    void updateExtensionFields()
    {
        // use the Java package as the symbolic name if no name given
        if( null == bundleName || bundleName.trim().length() == 0 )
        {
            bundleName = packageName;
        }

        getArchetypeMojo().setField( "archetypeArtifactId", "maven-archetype-osgi-bundle" );

        getArchetypeMojo().setField( "groupId", getInternalGroupId() );
        getArchetypeMojo().setField( "artifactId", bundleName );
        getArchetypeMojo().setField( "version", version );

        getArchetypeMojo().setField( "packageName", packageName );
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
        if( !provideInterface )
        {
            addTempFiles( "src/main/java/**/ExampleService.java" );
        }
        if( !provideInternals )
        {
            addTempFiles( "src/main/java/**/internal" );
        }
        if( !provideActivator )
        {
            addTempFiles( "src/main/java/**/Activator.java" );
        }

        // remove files, etc.
        super.postProcess();

        if( !noDependencies )
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

        if( provideInternals && !provideInterface )
        {
            // internals, but nothing to export
            bndFile.setInstruction( "Export-Package", null, canOverwrite() );
        }
        if( !provideInternals && provideInterface )
        {
            // public api, but no internals left
            bndFile.setInstruction( "Private-Package", null, canOverwrite() );
        }
        if( !provideActivator || !provideInternals )
        {
            bndFile.removeInstruction( "Bundle-Activator" );
        }

        bndFile.write();
    }
}
