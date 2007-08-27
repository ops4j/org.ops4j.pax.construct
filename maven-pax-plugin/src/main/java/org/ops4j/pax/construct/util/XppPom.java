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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;
import org.ops4j.pax.construct.util.PomUtils.Pom;

public final class XppPom
    implements Pom
{
    private File file;

    private Xpp3Dom pom;

    public XppPom( File pomFile )
        throws MojoExecutionException
    {
        file = pomFile;

        try
        {
            XmlPullParser parser = new RoundTripParser();
            parser.setInput( new FileReader( file ) );
            pom = Xpp3DomBuilder.build( parser, false );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to parse POM " + file, e );
        }
    }

    public void setParent( MavenProject project, String relativePath, boolean overwrite )
        throws MojoExecutionException
    {
        if( pom.getChild( "parent" ) != null && !overwrite )
        {
            throw new MojoExecutionException( "Keeping existing data, use -Doverwrite to replace it" );
        }

        Xpp3Dom rPath = new Xpp3Dom( "relativePath" );
        Xpp3Dom groupId = new Xpp3Dom( "groupId" );
        Xpp3Dom artifactId = new Xpp3Dom( "artifactId" );
        Xpp3Dom version = new Xpp3Dom( "version" );

        rPath.setValue( relativePath );
        groupId.setValue( project.getGroupId() );
        artifactId.setValue( project.getArtifactId() );
        version.setValue( project.getVersion() );

        Xpp3Dom parent = new Xpp3Dom( "parent" );
        parent.addChild( rPath );
        parent.addChild( groupId );
        parent.addChild( artifactId );
        parent.addChild( version );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( parent );

        pom = Xpp3Dom.mergeXpp3Dom( newPom, pom );
    }

    public void adjustRelativePath( int offset )
    {
        Xpp3Dom node = pom.getChild( "parent" ).getChild( "relativePath" );

        String relativeText = node.getValue();

        for( int i = 0; i < offset; i++ )
        {
            relativeText = "../" + relativeText;
        }

        for( int i = 0; i > offset; i-- )
        {
            relativeText = relativeText.substring( 3 );
        }

        node.setValue( relativeText );
    }

    public void addRepository( Repository repository, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA

        Xpp3Dom id = new Xpp3Dom( "id" );
        Xpp3Dom url = new Xpp3Dom( "url" );

        id.setValue( repository.getId() );
        url.setValue( repository.getUrl() );

        Xpp3Dom repo = new Xpp3Dom( "repository" );
        repo.addChild( id );
        repo.addChild( url );

        Xpp3Dom list = new Xpp3DomList( "repositories" );
        list.addChild( repo );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void addModule( String module, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA

        Xpp3Dom mod = new Xpp3Dom( "module" );
        mod.setValue( module );

        Xpp3Dom list = new Xpp3DomList( "modules" );
        list.addChild( mod );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void removeModule( String module )
    {
        throw new UnsupportedOperationException( "TBD: refactoring in progress" );
    }

    public void addDependency( MavenProject project, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA

        Xpp3Dom groupId = new Xpp3Dom( "groupId" );
        Xpp3Dom artifactId = new Xpp3Dom( "artifactId" );
        Xpp3Dom version = new Xpp3Dom( "version" );
        Xpp3Dom scope = new Xpp3Dom( "scope" );

        groupId.setValue( project.getGroupId() );
        artifactId.setValue( project.getArtifactId() );
        version.setValue( project.getVersion() );
        scope.setValue( Artifact.SCOPE_PROVIDED );

        Xpp3Dom dep = new Xpp3Dom( "dependency" );
        dep.addChild( groupId );
        dep.addChild( artifactId );
        dep.addChild( version );
        dep.addChild( scope );

        Xpp3Dom list = new Xpp3DomList( "dependencies" );
        list.addChild( dep );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
    }

    public void addDependency( Dependency dependency, boolean overwrite )
    {
        // TBD: CHECK FOR EXISTING DATA + OPTIONAL FIELDS

        Xpp3DomTree dep = new Xpp3DomTree( "dependency" );
        dep.addLeaf( "groupId", dependency.getGroupId() );
        dep.addLeaf( "artifactId", dependency.getArtifactId() );
        dep.addLeaf( "version", dependency.getVersion() );
        dep.addLeaf( "scope", dependency.getScope() );
        dep.addLeaf( "type", dependency.getType() );
        dep.addLeaf( "optional", "" + dependency.isOptional() );

        Xpp3Dom list = new Xpp3DomList( "dependencies" );
        list.addChild( dep );

        Xpp3Dom newPom = new Xpp3Dom( "project" );
        newPom.addChild( list );

        Xpp3Dom.mergeXpp3Dom( pom, newPom );
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
            XmlSerializer serializer = new RoundTripSerializer();
            serializer.setOutput( new FileWriter( file ) );
            pom.writeToSerializer( null, serializer );
            serializer.flush();
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to serialize POM " + file, e );
        }
    }

    protected static class Xpp3DomTree extends Xpp3Dom
    {
        public Xpp3DomTree( String name )
        {
            super( name );
        }

        public void addLeaf( String name, String value )
        {
            Xpp3Dom leaf = new Xpp3Dom( name );
            leaf.setValue( value );
            addChild( leaf );
        }
    }

    protected static class Xpp3DomList extends Xpp3Dom
    {
        public Xpp3DomList( String name )
        {
            super( name );

            setAttribute( CHILDREN_COMBINATION_MODE_ATTRIBUTE, CHILDREN_COMBINATION_APPEND );
        }
    }

    protected static class RoundTripParser extends MXParser
    {
        boolean handleComment = false;

        public int next()
            throws XmlPullParserException,
            IOException
        {
            if( handleComment )
            {
                handleComment = false;
                return END_TAG;
            }

            int type = super.nextToken();

            if( COMMENT == eventType )
            {
                handleComment = true;
                return START_TAG;
            }

            return type;
        }

        public String getName()
        {
            if( handleComment )
            {
                return "!--" + getText();
            }
            return super.getName();
        }

        public boolean isEmptyElementTag()
            throws XmlPullParserException
        {
            if( handleComment )
            {
                return true;
            }
            return super.isEmptyElementTag();
        }

        public int getAttributeCount()
        {
            if( handleComment )
            {
                return 0;
            }
            return super.getAttributeCount();
        }
    }

    protected static class RoundTripSerializer extends MXSerializer
    {
        boolean handleComment = false;

        public RoundTripSerializer()
            throws IOException
        {
            setProperty( PROPERTY_SERIALIZER_INDENTATION, "  " );
        }

        public XmlSerializer startTag( String namespace, String name )
            throws IOException
        {
            if( name.startsWith( "!--" ) )
            {
                if( !handleComment )
                {
                    closeStartTag();
                    writeIndent();
                }

                handleComment = true;

                out.write( "<" + name + "-->" );
                if( getDepth() == 1 )
                {
                    out.write( lineSeparator );
                }
                writeIndent();

                return this;
            }

            handleComment = false;

            return super.startTag( namespace, name );
        }

        protected void closeStartTag()
            throws IOException
        {
            super.closeStartTag();
            if( getDepth() == 1 )
            {
                out.write( lineSeparator );
            }
        }

        public XmlSerializer endTag( String namespace, String name )
            throws IOException
        {
            if( !handleComment )
            {
                super.endTag( namespace, name );

                if( !("modelVersion".equals( name ) || "groupId".equals( name ) || "artifactId".equals( name )) )
                {
                    if( getDepth() <= 1 )
                    {
                        out.write( lineSeparator );
                    }
                }
            }

            return this;
        }

        public XmlSerializer attribute( String namespace, String name, String value )
            throws IOException
        {
            if( !Xpp3Dom.CHILDREN_COMBINATION_MODE_ATTRIBUTE.equals( name ) )
            {
                return super.attribute( namespace, name, value );
            }
            return this;
        }
    }
}
