package org.ops4j.pax.construct;

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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

/**
 * Foundation for all OSGi sub-project goals that use archetypes.
 */
public abstract class AbstractChildArchetypeMojo extends AbstractArchetypeMojo
{
    private static final String NL = System.getProperty( "line.separator" );

    /**
     * The newly generated POM file - this is set in the _root_ project execution
     */
    protected static File childPomFile;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        // only create files under root project
        return project.getParent() == null;
    }

    protected void setChildProjectName( final String childProjectName )
        throws MojoExecutionException
    {
        // This somehow forces Maven to keep POM formatting & XSD
        File dir = new File( targetDirectory, childProjectName );
        dir.mkdir();

        childPomFile = new File( dir, "pom.xml" );

        // update parent modules
        linkParentToChild();
    }

    private static Document readPom( File pomFile )
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

    private static void writePom( File pomFile, Document pom )
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

    protected void linkParentToChild()
        throws MojoExecutionException
    {
        try
        {
            final String childName = childPomFile.getParentFile().getName();

            Document parentPom = readPom( project.getFile() );

            Element projectElem = parentPom.getElement( null, "project" );
            Element modulesElem = projectElem.getElement( null, "modules" );

            for ( int i = 0; i < modulesElem.getChildCount(); i++ )
            {
                Element childElem = modulesElem.getElement( i );
                if ( childElem != null )
                {
                    if ( childName.equalsIgnoreCase( childElem.getChild( 0 ).toString() ) )
                    {
                        throw new IOException( "The project already has a module named " + childName );
                    }
                }
            }

            Element newModuleElem = modulesElem.createElement( null, "module" );
            newModuleElem.addChild( Element.TEXT, childName );

            modulesElem.addChild( Element.TEXT, "  " );
            modulesElem.addChild( Element.ELEMENT, newModuleElem );
            modulesElem.addChild( Element.TEXT, NL + "  " );

            writePom( project.getFile(), parentPom );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to link parent pom to child", e );
        }
    }

    protected void linkChildToParent()
        throws MojoExecutionException
    {
        try
        {
            Document childPom = readPom( childPomFile );
            Element projectElem = childPom.getElement( null, "project" );

            Element parentElem = projectElem.createElement( null, "parent" );
            projectElem.addChild( 1, Element.ELEMENT, parentElem );
            projectElem.addChild( 2, Element.TEXT, NL + NL + "  " );

            Element groupIdElem = parentElem.createElement( null, "groupId" );
            groupIdElem.addChild( Element.TEXT, project.getGroupId() );
            parentElem.addChild( Element.TEXT, NL + "    " );
            parentElem.addChild( Element.ELEMENT, groupIdElem );

            Element artifactIdElem = parentElem.createElement( null, "artifactId" );
            artifactIdElem.addChild( Element.TEXT, project.getArtifactId() );
            parentElem.addChild( Element.TEXT, NL + "    " );
            parentElem.addChild( Element.ELEMENT, artifactIdElem );

            Element versionElem = parentElem.createElement( null, "version" );
            versionElem.addChild( Element.TEXT, project.getVersion() );
            parentElem.addChild( Element.TEXT, NL + "    " );
            parentElem.addChild( Element.ELEMENT, versionElem );

            parentElem.addChild( Element.TEXT, NL + "  " );
            writePom( childPomFile, childPom );
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to link child pom to parent", e );
        }
    }
}
