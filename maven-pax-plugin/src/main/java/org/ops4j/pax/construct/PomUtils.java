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
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/**
 * Various utility methods for working with Maven poms.
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
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to parse POM", e );
        }
    }

    /**
     * Writes a pom to the input File.
     * 
     * @param pomFile the File representing the pom
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
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to serialize POM", e );
        }
    }

    public static boolean containsDir( File rootDir, File targetDir )
    {
        while( rootDir != null && targetDir != null )
        {
            if( rootDir.equals( targetDir ) )
            {
                return true;
            }

            targetDir = targetDir.getParentFile();
        }

        return false;
    }

    /**
     * Create a module tree pointing to the input File.
     * 
     * @param pomFile the File representing the pom
     * @throws MojoExecutionException re-thrown.
     */
    public static File createModuleTree( File rootDir, File targetDir )
        throws MojoExecutionException
    {
        try
        {
            rootDir = rootDir.getCanonicalFile();
            targetDir = targetDir.getCanonicalFile();
        }
        catch( Exception e )
        {
            // ignore, assume original paths will be ok
        }

        return createModuleTreeChecked( rootDir, targetDir );
    }

    private static File createModuleTreeChecked( File rootDir, File targetDir )
        throws MojoExecutionException
    {
        if( !containsDir( rootDir, targetDir ) )
        {
            return null;
        }

        File pomFile = new File( targetDir, "pom.xml" );
        if( pomFile.exists() )
        {
            return pomFile;
        }

        // recurse to top-most missing pom, then create poms downwards from there
        File parentPomFile = createModuleTreeChecked( rootDir, targetDir.getParentFile() );

        // link parent to child
        Document parentPom = readPom( parentPomFile );
        Element parentProjectElem = parentPom.getElement( null, "project" );
        addModule( parentProjectElem, targetDir.getName(), true );
        writePom( parentPomFile, parentPom );

        Model parentModel;
        try
        {
            // read back in maven format
            MavenXpp3Reader modelReader = new MavenXpp3Reader();
            parentModel = modelReader.read( new FileReader( parentPomFile ) );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to read parent POM", e );
        }

        // create new modules pom
        Document pom = new Document();

        Element project = pom.createElement( null, "project" );
        project.setAttribute( null, "xmlns", "http://maven.apache.org/POM/4.0.0" );
        project.setAttribute( null, "xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance" );
        project.setAttribute( null, "xsi:schemaLocation",
            "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd" );

        final String groupIdText = parentModel.getGroupId() + "." + parentModel.getArtifactId();
        final String artifactIdText = targetDir.getName();

        Element name = project.createElement( null, "name" );
        name.addChild( Element.TEXT, groupIdText + "." + artifactIdText );

        Element modelVersion = project.createElement( null, "modelVersion" );
        modelVersion.addChild( Element.TEXT, "4.0.0" );
        Element groupId = project.createElement( null, "groupId" );
        groupId.addChild( Element.TEXT, groupIdText );
        Element artifactId = project.createElement( null, "artifactId" );
        artifactId.addChild( Element.TEXT, artifactIdText );

        Element packaging = project.createElement( null, "packaging" );
        packaging.addChild( Element.TEXT, "pom" );

        Element modules = project.createElement( null, "modules" );
        modules.addChild( Element.TEXT, NL + "  " );

        pom.addChild( Element.TEXT, NL );
        pom.addChild( Element.ELEMENT, project );

        project.addChild( Element.TEXT, NL + NL + "  " );
        project.addChild( Element.ELEMENT, name );

        project.addChild( Element.TEXT, NL + NL + "  " );
        project.addChild( Element.ELEMENT, modelVersion );
        project.addChild( Element.TEXT, NL + "  " );
        project.addChild( Element.ELEMENT, groupId );
        project.addChild( Element.TEXT, NL + "  " );
        project.addChild( Element.ELEMENT, artifactId );

        project.addChild( Element.TEXT, NL + NL + "  " );
        project.addChild( Element.ELEMENT, packaging );

        project.addChild( Element.TEXT, NL + NL + "  " );
        project.addChild( Element.ELEMENT, modules );
        project.addChild( Element.TEXT, NL + NL );

        // link child to parent
        MavenProject parentProject = new MavenProject( parentModel );
        setParent( project, parentProject, null, true );

        try
        {
            targetDir.mkdir();
            XmlSerializer serial = XmlPullParserFactory.newInstance().newSerializer();
            FileWriter output = new FileWriter( pomFile );

            serial.setOutput( output );
            pom.write( serial );
            output.close();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to serialize POM", e );
        }

        return pomFile;
    }

    /**
     * Add a named module to the given project, checking for duplicates
     * 
     * @param project The XML element for the project
     * @param moduleName The module to be added
     * @param overwrite Overwrite existing entries
     * 
     * @throws MojoExecutionException
     */
    public static void addModule( Element project, String moduleName, boolean overwrite )
        throws MojoExecutionException
    {
        Element modules;

        try
        {
            modules = project.getElement( null, "modules" );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Please add the following to the project pom:" + NL + NL + "  <modules>"
                + NL + "    <module>poms</module>" + NL + "  </modules>" + NL + NL + "and repeat this command" + NL );
        }

        Element modElem = null;
        for( int i = 0; i < modules.getChildCount(); i++ )
        {
            Element childElem = modules.getElement( i );
            if( childElem != null )
            {
                if( moduleName.equalsIgnoreCase( childElem.getChild( 0 ).toString() ) )
                {
                    if( overwrite )
                    {
                        modElem = childElem;
                        break;
                    }

                    throw new MojoExecutionException( "The project already has a module named " + moduleName );
                }
            }
        }

        if( null == modElem )
        {
            // add a new module
            modElem = modules.createElement( null, "module" );

            modules.addChild( Element.TEXT, "  " );
            modules.addChild( Element.ELEMENT, modElem );
            modules.addChild( Element.TEXT, NL + "  " );
        }
        else
        {
            // update module
            modElem.clear();
        }

        modElem.addChild( Element.TEXT, moduleName );
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
        catch( Exception e )
        {
            // nothing to remove
            return;
        }

        for( int i = 0; i < modules.getChildCount(); i++ )
        {
            Element childElem = modules.getElement( i );
            if( childElem != null )
            {
                if( moduleName.equalsIgnoreCase( childElem.getChild( 0 ).toString() ) )
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
     * @param overwrite Overwrite existing entries
     * 
     * @throws MojoExecutionException
     */
    public static void addRepository( Element project, Repository repository, boolean overwrite )
        throws MojoExecutionException
    {
        Element repositories;

        try
        {
            repositories = project.getElement( null, "repositories" );
        }
        catch( Exception e )
        {
            repositories = project.createElement( null, "repositories" );
            repositories.addChild( Element.TEXT, NL + "  " );
            project.addChild( Element.TEXT, "  " );
            project.addChild( Element.ELEMENT, repositories );
            project.addChild( Element.TEXT, NL + NL );
        }

        Element repoElem = null;
        for( int i = 0; i < repositories.getChildCount(); i++ )
        {
            Element childElem = repositories.getElement( i );
            if( childElem != null )
            {
                Element idElem = childElem.getElement( null, "id" );
                Element urlElem = childElem.getElement( null, "url" );

                if( repository.getId().equalsIgnoreCase( idElem.getChild( 0 ).toString() ) )
                {
                    if( overwrite )
                    {
                        repoElem = childElem;
                        break;
                    }

                    throw new MojoExecutionException( "The project already has a repository with id "
                        + repository.getId() );
                }
                if( repository.getUrl().equalsIgnoreCase( urlElem.getChild( 0 ).toString() ) )
                {
                    if( overwrite )
                    {
                        repoElem = childElem;
                        break;
                    }

                    throw new MojoExecutionException( "The project already has a repository with url "
                        + repository.getUrl() );
                }
            }
        }

        if( null == repoElem )
        {
            // add a new repository
            repoElem = repositories.createElement( null, "repository" );

            repositories.addChild( Element.TEXT, WS );
            repositories.addChild( Element.ELEMENT, repoElem );
            repositories.addChild( Element.TEXT, NL + WS );
        }
        else
        {
            // update repository
            repoElem.clear();
        }

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
     * Add a dependency to the given project, checking for duplicates
     * 
     * @param project The XML element for the project
     * @param repository The Maven repository to add
     * @param overwrite Overwrite existing entries
     * 
     * @throws MojoExecutionException
     */
    public static void addDependency( Element project, Dependency dependency, boolean overwrite )
        throws MojoExecutionException
    {
        Element dependencies;

        try
        {
            dependencies = project.getElement( null, "dependencies" );
        }
        catch( Exception e )
        {
            dependencies = project.createElement( null, "dependencies" );
            dependencies.addChild( Element.TEXT, NL + "  " );
            project.addChild( Element.TEXT, "  " );
            project.addChild( Element.ELEMENT, dependencies );
            project.addChild( Element.TEXT, NL + NL );
        }

        Element depElem = null;
        for( int i = 0; i < dependencies.getChildCount(); i++ )
        {
            Element childElem = dependencies.getElement( i );
            if( childElem != null )
            {
                Element groupIdElem = childElem.getElement( null, "groupId" );
                Element artifactIdElem = childElem.getElement( null, "artifactId" );

                if( dependency.getGroupId().equalsIgnoreCase( groupIdElem.getChild( 0 ).toString() )
                    && dependency.getArtifactId().equalsIgnoreCase( artifactIdElem.getChild( 0 ).toString() ) )
                {
                    if( overwrite )
                    {
                        depElem = childElem;
                        break;
                    }

                    throw new MojoExecutionException( "The project already has a dependency to "
                        + dependency.getGroupId() + ":" + dependency.getArtifactId() );
                }
            }
        }

        if( null == depElem )
        {
            // add a new dependency
            depElem = dependencies.createElement( null, "dependency" );

            dependencies.addChild( Element.TEXT, WS );
            dependencies.addChild( Element.ELEMENT, depElem );
            dependencies.addChild( Element.TEXT, NL + WS );
        }
        else
        {
            // update dependency
            depElem.clear();
        }

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

        // is it optional?
        if( dependency.isOptional() )
        {
            Element optionalElem = depElem.createElement( null, "optional" );
            optionalElem.addChild( Element.TEXT, "true" );
            depElem.addChild( Element.TEXT, NL + WS + WS + WS );
            depElem.addChild( Element.ELEMENT, optionalElem );
        }

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
        catch( Exception e )
        {
            // nothing to remove
            return;
        }

        for( int i = 0; i < dependencies.getChildCount(); i++ )
        {
            Element childElem = dependencies.getElement( i );
            if( childElem != null )
            {
                Element groupIdElem = childElem.getElement( null, "groupId" );
                Element artifactIdElem = childElem.getElement( null, "artifactId" );

                if( dependency.getGroupId().equalsIgnoreCase( groupIdElem.getChild( 0 ).toString() )
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

        if( properties.containsKey( "jar.artifactId" ) )
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

    /**
     * Add parent to the given project, checking for duplicates
     * 
     * @param project The XML element for the project
     * @param parentProject The parent project to be added
     * @param overwrite Overwrite existing entries
     * 
     * @throws MojoExecutionException
     */
    public static void setParent( Element project, MavenProject parentProject, String relativePath, boolean overwrite )
        throws MojoExecutionException
    {
        Element parent;

        try
        {
            parent = project.getElement( null, "parent" );

            if( overwrite )
            {
                parent.clear();
            }
            else
            {
                throw new MojoExecutionException( "The project already has a parent." );
            }
        }
        catch( Exception e )
        {
            parent = project.createElement( null, "parent" );

            project.addChild( 1, Element.ELEMENT, parent );
            project.addChild( 2, Element.TEXT, NL + NL + "  " );
        }

        if( relativePath != null )
        {
            Element parentPath = parent.createElement( null, "relativePath" );
            parentPath.addChild( Element.TEXT, relativePath );
            parent.addChild( Element.TEXT, NL + "    " );
            parent.addChild( Element.ELEMENT, parentPath );
        }

        Element groupId = parent.createElement( null, "groupId" );
        groupId.addChild( Element.TEXT, parentProject.getGroupId() );
        parent.addChild( Element.TEXT, NL + "    " );
        parent.addChild( Element.ELEMENT, groupId );

        Element artifactId = parent.createElement( null, "artifactId" );
        artifactId.addChild( Element.TEXT, parentProject.getArtifactId() );
        parent.addChild( Element.TEXT, NL + "    " );
        parent.addChild( Element.ELEMENT, artifactId );

        Element version = parent.createElement( null, "version" );
        version.addChild( Element.TEXT, parentProject.getVersion() );
        parent.addChild( Element.TEXT, NL + "    " );
        parent.addChild( Element.ELEMENT, version );

        parent.addChild( Element.TEXT, NL + "  " );
    }

    public static File findBundlePom( File baseDir, String bundleName )
    {
        String[] includes = new String[1];
        includes[0] = "**/" + bundleName + "/pom.xml";

        DirectoryScanner scanner = new DirectoryScanner();

        scanner.setBasedir( baseDir );
        scanner.setIncludes( includes );
        scanner.scan();

        String candidates[] = scanner.getIncludedFiles();
        if( candidates != null && candidates.length > 0 )
        {
            return new File( baseDir, candidates[0] );
        }
        else
        {
            return null;
        }
    }

    public static String calculateRelativePath( File sourcePath, File targetPath )
    {
        try
        {
            sourcePath = sourcePath.getCanonicalFile();
            targetPath = targetPath.getCanonicalFile();
        }
        catch( Exception e )
        {
            // ignore, assume original paths will be ok
        }

        String dottedPath = "";
        String descentPath = "";
        while( sourcePath != null && targetPath != null && !sourcePath.equals( targetPath ) )
        {
            if( sourcePath.getPath().length() < targetPath.getPath().length() )
            {
                descentPath = targetPath.getName() + "/" + descentPath;
                targetPath = targetPath.getParentFile();
            }
            else
            {
                dottedPath = "../" + dottedPath;
                sourcePath = sourcePath.getParentFile();
            }
        }

        String relativePath = null;
        if( sourcePath != null && targetPath != null )
        {
            relativePath = dottedPath + descentPath;
        }

        return relativePath;
    }

    public static void adjustRelativePath( Element project, int offset )
    {
        try
        {
            Element parent = project.getElement( null, "parent" );
            Element relativePath = parent.getElement( null, "relativePath" );
            String relativeText = (String) relativePath.getChild( 0 );
            relativePath.clear();

            for( int i = 0; i < offset; i++ )
            {
                relativeText = "../" + relativeText;
            }

            for( int i = 0; i > offset; i-- )
            {
                relativeText = relativeText.substring( 3 );
            }

            relativePath.addChild( Element.TEXT, relativeText );
        }
        catch( Exception e )
        {
        }
    }
}
