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

import java.io.IOException;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;

public final class RoundTripXml
{
    public static XmlPullParser createParser()
    {
        return new RoundTripParser();
    }

    public static XmlSerializer createSerializer()
    {
        return new RoundTripSerializer();
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
