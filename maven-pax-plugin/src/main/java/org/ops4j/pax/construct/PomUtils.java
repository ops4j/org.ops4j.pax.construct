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

import org.apache.maven.plugin.MojoExecutionException;
import org.kxml2.kdom.Document;
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
     * @param pomFile the pom File to read
     * @return the Document represing the pom
     * @throws MojoExecutionException re-thrown
     */
    protected static Document readPom( File pomFile )
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
    protected static void writePom( File pomFile, Document pom )
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

}
