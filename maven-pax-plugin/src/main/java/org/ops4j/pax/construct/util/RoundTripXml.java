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
import java.util.Arrays;
import java.util.List;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.MXSerializer;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.plexus.util.xml.pull.XmlSerializer;

/**
 * Provide XML parser and serializer that work in tandem to preserve comments (and some formatting)
 */
public final class RoundTripXml
{
    /**
     * Hide constructor for utility class
     */
    private RoundTripXml()
    {
    }

    /**
     * @return round-trip XML parser
     */
    public static XmlPullParser createParser()
    {
        return new RoundTripParser();
    }

    /**
     * @return round-trip XML serializer
     */
    public static XmlSerializer createSerializer()
    {
        return new RoundTripSerializer();
    }

    /**
     * Customize parser to preserve comments as special tags
     */
    static final class RoundTripParser extends MXParser
    {
        /**
         * Are we parsing a comment?
         */
        private boolean m_handleComment = false;

        /**
         * Use default config
         */
        private RoundTripParser()
        {
            super();
        }

        /**
         * {@inheritDoc}
         */
        public int next()
            throws XmlPullParserException,
            IOException
        {
            if( m_handleComment )
            {
                // end pseudo-tag
                m_handleComment = false;
                return END_TAG;
            }

            int type = super.nextToken();

            if( COMMENT == eventType )
            {
                // start pseudo-tag
                m_handleComment = true;
                return START_TAG;
            }

            return type;
        }

        /**
         * {@inheritDoc}
         */
        public String getName()
        {
            if( m_handleComment )
            {
                // use comment text as name
                return "!--" + getText();
            }

            return super.getName();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isEmptyElementTag()
            throws XmlPullParserException
        {
            if( m_handleComment )
            {
                // comments don't have embedded tags
                return true;
            }

            return super.isEmptyElementTag();
        }

        /**
         * {@inheritDoc}
         */
        public int getAttributeCount()
        {
            if( m_handleComment )
            {
                // comments don't have attributes
                return 0;
            }

            return super.getAttributeCount();
        }
    }

    /**
     * Customize serializer to output comments stored in special tags
     */
    static final class RoundTripSerializer extends MXSerializer
    {
        /**
         * Are we serializing a comment?
         */
        private boolean m_handleComment = false;

        /**
         * Tweak config to use standard Maven layout
         */
        private RoundTripSerializer()
        {
            super();

            setProperty( PROPERTY_SERIALIZER_INDENTATION, "  " );
            setProperty( PROPERTY_SERIALIZER_LINE_SEPARATOR, System.getProperty( "line.separator" ) );
        }

        /**
         * {@inheritDoc}
         */
        public XmlSerializer startTag( String namespace, String name )
            throws IOException
        {
            // special comment tag
            if( name.startsWith( "!--" ) )
            {
                m_handleComment = true;

                closeStartTag();
                if( getDepth() <= 1 )
                {
                    // newline before top-level comments
                    out.write( lineSeparator );
                }
                writeIndent();

                comment( name.substring( 3 ) );
                return this;
            }
            else
            {
                List stickyTags = Arrays.asList( new String[]
                {
                    "groupId", "artifactId", "version"
                } );
                if( getDepth() <= 1 && !stickyTags.contains( name ) )
                {
                    // newline before top-level groups
                    closeStartTag();
                    out.write( lineSeparator );
                }
                if( m_handleComment )
                {
                    m_handleComment = false;
                    writeIndent();
                }
                return super.startTag( namespace, name );
            }
        }

        /**
         * {@inheritDoc}
         */
        public XmlSerializer endTag( String namespace, String name )
            throws IOException
        {
            if( !name.startsWith( "!--" ) )
            {
                if( getDepth() == 1 )
                {
                    // newline after final group
                    out.write( lineSeparator );
                }
                if( m_handleComment )
                {
                    m_handleComment = false;

                    --depth;
                    writeIndent();
                    ++depth;
                }
                super.endTag( namespace, name );
            }

            return this;
        }

        /**
         * {@inheritDoc}
         */
        public XmlSerializer attribute( String namespace, String name, String value )
            throws IOException
        {
            // don't output any internal attributes used when merging XML
            if( !Xpp3Dom.CHILDREN_COMBINATION_MODE_ATTRIBUTE.equals( name ) )
            {
                return super.attribute( namespace, name, value );
            }

            return this;
        }
    }
}
