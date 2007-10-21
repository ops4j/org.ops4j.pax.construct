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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.BndFileUtils;
import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.ExistingElementException;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Create a new bundle project inside an existing Pax-Construct OSGi project
 * 
 * <code><pre>
 *   mvn pax:create-bundle -Dpackage=... [-DbundleName=...] [-Dversion=...]
 * </pre></code>
 * 
 * or create a standalone version which doesn't require an existing project
 * 
 * <code><pre>
 *   cd some-empty-folder
 *   mvn org.ops4j:maven-pax-plugin:create-bundle ...etc...
 * </pre></code>
 * 
 * @extendsPlugin archetype
 * @extendsGoal create
 * @goal create-bundle
 * @requiresProject false
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
     * When true, provide an example Bean using the selected Spring version.
     * 
     * @parameter expression="${springVersion}"
     */
    private String springVersion;

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
        // locate parent
        super.postProcess();
        if( !hasParent() )
        {
            makeStandalone();
        }

        if( !noDependencies )
        {
            try
            {
                // standard R4 OSGi API
                addOSGiDependenciesToPom();

                if( springVersion != null )
                {
                    addSpringBeanSupport();
                }
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

        discardFiles();
    }

    /**
     * Mark any temporary or unnecessary files
     */
    void discardFiles()
    {
        if( !provideInterface )
        {
            addTempFiles( "src/main/java/**/Example*.java" );
        }
        if( !provideInternals )
        {
            addTempFiles( "src/main/resources" );
            addTempFiles( "src/main/java/**/internal" );
            addTempFiles( "src/test/resources" );
            addTempFiles( "src/test/java/**/internal" );
        }
        if( !provideActivator )
        {
            addTempFiles( "src/main/java/**/Activator.java" );
        }
        if( null == springVersion )
        {
            addTempFiles( "src/main/resources/**/spring" );
            addTempFiles( "src/main/java/**/*Bean*.java" );
            addTempFiles( "src/test/resources/**/spring" );
            addTempFiles( "src/test/java/**/*Bean*.java" );
        }
        addTempFiles( "poms" );
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
        if( !hasParent() )
        {
            osgiCore.setVersion( "1.0" );
            osgiCore.setScope( Artifact.SCOPE_PROVIDED );
            osgiCore.setOptional( true );
        }
        thisPom.addDependency( osgiCore, canOverwrite() );

        Dependency osgiCompendium = new Dependency();
        osgiCompendium.setGroupId( "org.osgi" );
        osgiCompendium.setArtifactId( "osgi_R4_compendium" );
        if( !hasParent() )
        {
            osgiCompendium.setVersion( "1.0" );
            osgiCompendium.setScope( Artifact.SCOPE_PROVIDED );
            osgiCompendium.setOptional( true );
        }
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

    /**
     * Add additional POM elements to make it work standalone
     */
    void makeStandalone()
    {
        try
        {
            File baseDir = getPomFile().getParentFile();

            Pom pluginSettings = PomUtils.readPom( new File( baseDir, "poms" ) );
            Pom compiledSettings = PomUtils.readPom( new File( baseDir, "poms/compiled" ) );

            Pom thisPom = PomUtils.readPom( baseDir );

            // Must merge plugin fragment first, so child elements combine properly!
            thisPom.merge( pluginSettings, "build/pluginManagement/plugins", "build" );
            thisPom.merge( compiledSettings, "build/plugins", "build" );

            thisPom.updatePluginVersion( "org.ops4j", "maven-pax-plugin", getArchetypeVersion() );
            thisPom.setGroupId( "org.ops4j.example" );

            // for latest bundle plugin
            Repository repository = new Repository();
            repository.setId( "ops4j-snapshots" );
            repository.setUrl( "http://repository.ops4j.org/mvn-snapshots" );
            thisPom.addRepository( repository, true, false, true, true );

            thisPom.write();
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to convert POM to work standalone" );
        }
        catch( ExistingElementException e )
        {
            // this should never happen
            throw new RuntimeException( e );
        }
    }

    /**
     * Add additional POM elements to support testing Spring Beans
     */
    void addSpringBeanSupport()
    {
        try
        {
            File baseDir = getPomFile().getParentFile();

            Pom thisPom = PomUtils.readPom( baseDir );

            // Spring milestone repository
            Repository repository = new Repository();
            repository.setId( "spring-milestones" );
            repository.setUrl( "http://s3.amazonaws.com/maven.springframework.org/milestone" );
            thisPom.addRepository( repository, false, true, canOverwrite(), false );

            Dependency junit = new Dependency();
            junit.setGroupId( "junit" );
            junit.setArtifactId( "junit" );
            junit.setVersion( "3.8.2" );
            junit.setScope( Artifact.SCOPE_TEST );

            Dependency springTest = new Dependency();
            springTest.setGroupId( "org.springframework" );
            springTest.setArtifactId( "spring-test" );
            springTest.setVersion( springVersion );
            springTest.setScope( Artifact.SCOPE_TEST );

            // mark as optional so we don't force deployment
            Dependency springContext = new Dependency();
            springContext.setGroupId( "org.springframework" );
            springContext.setArtifactId( "spring-context" );
            springContext.setVersion( springVersion );
            springContext.setScope( Artifact.SCOPE_PROVIDED );
            springContext.setOptional( true );

            thisPom.addDependency( junit, canOverwrite() );
            thisPom.addDependency( springTest, canOverwrite() );
            thisPom.addDependency( springContext, canOverwrite() );

            thisPom.write();
        }
        catch( IOException e )
        {
            getLog().warn( "Unable to add Spring Bean support" );
        }
        catch( ExistingElementException e )
        {
            // this should never happen
            throw new RuntimeException( e );
        }
    }
}
