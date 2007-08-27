package org.ops4j.pax.construct.util;

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
import java.io.IOException;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.ops4j.pax.construct.util.PomUtils.Pom;

public final class XppPom
    implements Pom
{
    private File file;
    private Xpp3Dom dom;

    public XppPom( File pomFile )
        throws MojoExecutionException
    {
        file = pomFile;

        try
        {
            XmlPullParser parser = new MXParser();
            parser.setInput( new FileReader( file ) );
            dom = Xpp3DomBuilder.build( parser, false );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to parse POM " + file, e );
        }
    }

    public void setParent( MavenProject parent, String relativePath, boolean overwrite )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void adjustRelativePath( int offset )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void addRepository( Repository repository, boolean overwrite )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void addModule( String module, boolean overwrite )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void removeModule( String module )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void addDependency( MavenProject project, boolean overwrite )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void addDependency( Dependency dependency, boolean overwrite )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void removeDependency( MavenProject project )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void removeDependency( Dependency dependency )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void write()
        throws MojoExecutionException
    {
        try
        {
            XmlSerializer serializer = new PaxConstructSerializer();
            dom.writeToSerializer( null, serializer );
            serializer.flush();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to serialize POM " + file, e );
        }
    }

    protected class PaxConstructSerializer extends MXSerializer
    {
        public PaxConstructSerializer()
            throws IOException
        {
            setOutput( new FileWriter( file ) );

            setProperty( PROPERTY_SERIALIZER_INDENTATION, "  " );
        }
    }
}
