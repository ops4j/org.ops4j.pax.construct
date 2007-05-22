package org.ops4j.pax.construct;

/*
 * Copyright 2007 Stuart McCulloch, Alin Dreghiciu
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
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/**
 * Foundation for all OSGi sub-project goals that use archetypes.
 */
public class PomUtils
{

    /**
     * New line character.
     */
    protected static final String NL = System.getProperty( "line.separator" );

    /**
     * White space.
     */
    protected static final String WS = "  ";

    /**
     * Reads a pom.xml specified by the input file.
     * 
     * @param pomFile the pom File to read
     * @return the Document represing the pom
     * @throws MojoExecutionException re-thrown
     */
    public static Document readPom( File pomFile )
        throws MojoExecutionException
    {
        try
        {
            XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
            FileReader input = new FileReader( pomFile );

            Document pom = new Document();

            parser.setInput( input );
            pom.parse( parser );
            input.close();

            return pom;
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to parse POM", e );
        }
    }

    /**
     * Writes a pom to the input File.
     * 
     * @param pomFile the File representing th epom
     * @param pom The Document representing the pom to be written
     * @throws MojoExecutionException re-thrown.
     */
    public static void writePom( File pomFile, Document pom )
        throws MojoExecutionException
    {
        try
        {
            XmlSerializer serial = XmlPullParserFactory.newInstance().newSerializer();
            FileWriter output = new FileWriter( pomFile );

            serial.setOutput( output );
            pom.write( serial );
            output.close();
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to serialize POM", e );
        }
    }

    /**
     * Add a named module to the given project, checking for duplicates
     * 
     * @param project The XML element for the project
     * @param moduleName The module to be added
     * 
     * @throws MojoExecutionException
     */
    public static void addModule( Element project, String moduleName )
        throws MojoExecutionException
    {
        Element modules;

        try
        {
            modules = project.getElement( null, "modules" );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Please add the following to the project pom:" + NL + NL + "  <modules>"
                + NL + "    <module>poms</module>" + NL + "  </modules>" + NL + NL + "and repeat this command" + NL );
        }

        for ( int i = 0; i < modules.getChildCount(); i++ )
        {
            Element childElem = modules.getElement( i );
            if ( childElem != null )
            {
                if ( moduleName.equalsIgnoreCase( childElem.getChild( 0 ).toString() ) )
                {
                    throw new MojoExecutionException( "The project already has a module named " + moduleName );
                }
            }
        }

        Element newModule = modules.createElement( null, "module" );
        newModule.addChild( Element.TEXT, moduleName );

        modules.addChild( Element.TEXT, "  " );
        modules.addChild( Element.ELEMENT, newModule );
        modules.addChild( Element.TEXT, NL + "  " );
    }

    /**
     * Remove a named module from the given project if it exists
     * 
     * @param project The XML element for the project
     * @param moduleName The module to be removed
     * 
     * @throws MojoExecutionException
     */
    public static void removeModule( Element project, String moduleName )
        throws MojoExecutionException
    {
        Element modules;

        try
        {
            modules = project.getElement( null, "modules" );
        }
        catch ( Exception e )
        {
            // nothing to remove
            return;
        }

        for ( int i = 0; i < modules.getChildCount(); i++ )
        {
            Element childElem = modules.getElement( i );
            if ( childElem != null )
            {
                if ( moduleName.equalsIgnoreCase( childElem.getChild( 0 ).toString() ) )
                {
                    // assume no duplicates
                    modules.removeChild( i );
                    return;
                }
            }
        }

        // no exception if not found
    }

    /**
     * Add a repository to the given project, checking for duplicates
     * 
     * @param project The XML element for the project
     * @param repository The Maven repository to add
     * 
     * @throws MojoExecutionException
     */
    public static void addRepository( Element project, Repository repository )
        throws MojoExecutionException
    {
        Element repositories;

        try
        {
            repositories = project.getElement( null, "repositories" );
        }
        catch ( Exception e )
        {
            repositories = project.createElement( null, "repositories" );
            repositories.addChild( Element.TEXT, NL + "  " );
            project.addChild( Element.TEXT, "  " );
            project.addChild( Element.ELEMENT, repositories );
            project.addChild( Element.TEXT, NL + NL );
        }

        for ( int i = 0; i < repositories.getChildCount(); i++ )
        {
            Element childElem = repositories.getElement( i );
            if ( childElem != null )
            {
                Element idElem = childElem.getElement( null, "id" );
                Element urlElem = childElem.getElement( null, "url" );

                if ( repository.getId().equalsIgnoreCase( idElem.getChild( 0 ).toString() ) )
                {
                    throw new MojoExecutionException( "The project already has a repository with id "
                        + repository.getId() );
                }
                if ( repository.getUrl().equalsIgnoreCase( urlElem.getChild( 0 ).toString() ) )
                {
                    throw new MojoExecutionException( "The project already has a repository with url "
                        + repository.getUrl() );
                }
            }
        }

        // add a new repository
        Element repoElem = repositories.createElement( null, "repository" );
        repositories.addChild( Element.TEXT, WS );
        repositories.addChild( Element.ELEMENT, repoElem );
        repositories.addChild( Element.TEXT, NL + WS );

        // add the id of the repository
        Element idElem = repoElem.createElement( null, "id" );
        idElem.addChild( Element.TEXT, repository.getId() );
        repoElem.addChild( Element.TEXT, NL + WS + WS + WS );
        repoElem.addChild( Element.ELEMENT, idElem );

        // add the url of the repository
        Element urlElem = repoElem.createElement( null, "url" );
        urlElem.addChild( Element.TEXT, repository.getUrl() );
        repoElem.addChild( Element.TEXT, NL + WS + WS + WS );
        repoElem.addChild( Element.ELEMENT, urlElem );

        repoElem.addChild( Element.TEXT, NL + WS + WS );
    }

    /**
     * Add a repository to the given project, checking for duplicates
     * 
     * @param project The XML element for the project
     * @param repository The Maven repository to add
     * 
     * @throws MojoExecutionException
     */
    public static void addDependency( Element project, Dependency dependency )
        throws MojoExecutionException
    {
        Element dependencies;

        try
        {
            dependencies = project.getElement( null, "dependencies" );
        }
        catch ( Exception e )
        {
            dependencies = project.createElement( null, "dependencies" );
            dependencies.addChild( Element.TEXT, NL + "  " );
            project.addChild( Element.TEXT, "  " );
            project.addChild( Element.ELEMENT, dependencies );
            project.addChild( Element.TEXT, NL + NL );
        }

        for ( int i = 0; i < dependencies.getChildCount(); i++ )
        {
            Element childElem = dependencies.getElement( i );
            if ( childElem != null )
            {
                Element groupIdElem = childElem.getElement( null, "groupId" );
                Element artifactIdElem = childElem.getElement( null, "artifactId" );

                if ( dependency.getGroupId().equalsIgnoreCase( groupIdElem.getChild( 0 ).toString() )
                    && dependency.getArtifactId().equalsIgnoreCase( artifactIdElem.getChild( 0 ).toString() ) )
                {
                    throw new MojoExecutionException( "The project already has a dependency to "
                        + dependency.getGroupId() + ":" + dependency.getArtifactId() );
                }
            }
        }

        // add a new dependency
        Element depElem = dependencies.createElement( null, "dependency" );
        dependencies.addChild( Element.TEXT, WS );
        dependencies.addChild( Element.ELEMENT, depElem );
        dependencies.addChild( Element.TEXT, NL + WS );

        // add the groupId
        Element groupIdElem = depElem.createElement( null, "groupId" );
        groupIdElem.addChild( Element.TEXT, dependency.getGroupId() );
        depElem.addChild( Element.TEXT, NL + WS + WS + WS );
        depElem.addChild( Element.ELEMENT, groupIdElem );

        // add the artifactId
        Element artifactIdElem = depElem.createElement( null, "artifactId" );
        artifactIdElem.addChild( Element.TEXT, dependency.getArtifactId() );
        depElem.addChild( Element.TEXT, NL + WS + WS + WS );
        depElem.addChild( Element.ELEMENT, artifactIdElem );

        // add the version
        Element versionElem = depElem.createElement( null, "version" );
        versionElem.addChild( Element.TEXT, dependency.getVersion() );
        depElem.addChild( Element.TEXT, NL + WS + WS + WS );
        depElem.addChild( Element.ELEMENT, versionElem );

        // add the scope
        Element scopeElem = depElem.createElement( null, "scope" );
        scopeElem.addChild( Element.TEXT, dependency.getScope() );
        depElem.addChild( Element.TEXT, NL + WS + WS + WS );
        depElem.addChild( Element.ELEMENT, scopeElem );

        depElem.addChild( Element.TEXT, NL + WS + WS );
    }

    /**
     * Remove a dependency from the given project if it exists
     * 
     * @param project The XML element for the project
     * @param dependency The dependency to remove
     * 
     * @throws MojoExecutionException
     */
    public static void removeDependency( Element project, Dependency dependency )
        throws MojoExecutionException
    {
        Element dependencies;

        try
        {
            dependencies = project.getElement( null, "dependencies" );
        }
        catch ( Exception e )
        {
            // nothing to remove
            return;
        }

        for ( int i = 0; i < dependencies.getChildCount(); i++ )
        {
            Element childElem = dependencies.getElement( i );
            if ( childElem != null )
            {
                Element groupIdElem = childElem.getElement( null, "groupId" );
                Element artifactIdElem = childElem.getElement( null, "artifactId" );

                if ( dependency.getGroupId().equalsIgnoreCase( groupIdElem.getChild( 0 ).toString() )
                    && dependency.getArtifactId().equalsIgnoreCase( artifactIdElem.getChild( 0 ).toString() ) )
                {
                    // assume no duplicates
                    dependencies.removeChild( i );
                    return;
                }
            }
        }

        // no exception if not found
    }

    /**
     * Create a Maven dependency object for the given bundle.
     * 
     * @param bundleModel The model representing the bundle in Maven
     * @return A dependency artifact referencing this bundle
     */
    public static Dependency getBundleDependency( Model bundleModel )
    {
        Properties properties = bundleModel.getProperties();

        Dependency dependency = new Dependency();
        dependency.setScope( "provided" );

        if ( properties.containsKey( "bundle.artifactId" ) )
        {
            // IMPORTED BUNDLE
            dependency.setGroupId( properties.getProperty( "bundle.groupId" ) );
            dependency.setArtifactId( properties.getProperty( "bundle.artifactId" ) );
            dependency.setVersion( properties.getProperty( "bundle.version" ) );
        }
        else if ( properties.containsKey( "jar.artifactId" ) )
        {
            // WRAPPED JARFILE
            dependency.setGroupId( bundleModel.getGroupId() );
            dependency.setArtifactId( bundleModel.getArtifactId() );
            dependency.setVersion( properties.getProperty( "jar.version" ) );
        }
        else
        {
            // COMPILED BUNDLE
            dependency.setGroupId( bundleModel.getGroupId() );
            dependency.setArtifactId( bundleModel.getArtifactId() );
            dependency.setVersion( bundleModel.getVersion() );
        }

        return dependency;
    }
}
