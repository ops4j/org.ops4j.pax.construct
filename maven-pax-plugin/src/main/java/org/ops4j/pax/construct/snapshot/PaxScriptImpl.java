package org.ops4j.pax.construct.snapshot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;

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

public class PaxScriptImpl
    implements PaxScript
{
    private final List m_commands;

    public PaxScriptImpl()
    {
        m_commands = new ArrayList();
    }

    public PaxCommandBuilder call( String command )
    {
        return new PaxCommand( command );
    }

    private class Flag
    {
        private String m_flag;

        public Flag( String flag )
        {
            m_flag = flag;
        }

        public String toString()
        {
            return '-' + m_flag;
        }
    }

    private class Option extends Flag
    {
        private char m_equals;

        private String m_value;

        public Option( String option, char equals, String value )
        {
            super( option );
            m_equals = equals;
            m_value = value;
        }

        public String toString()
        {
            if( ' ' == m_equals )
            {
                return super.toString() + m_equals + '\"' + m_value + '\"';
            }
            else
            {
                return '\"' + super.toString() + m_equals + m_value + '\"';
            }
        }
    }

    private class PaxCommand
        implements PaxCommandBuilder
    {
        private final String m_name;

        private final List m_paxOptions;

        private final List m_mvnOptions;

        private String m_targetDir;

        public PaxCommand( String command )
        {
            m_name = command;
            m_mvnOptions = new ArrayList();
            m_paxOptions = new ArrayList();
            m_targetDir = "";

            m_commands.add( this );
        }

        public PaxCommandBuilder flag( char flag )
        {
            m_paxOptions.add( new Flag( "" + flag ) );
            return this;
        }

        public PaxCommandBuilder option( char option, String value )
        {
            m_paxOptions.add( new Option( "" + option, ' ', value ) );
            return this;
        }

        public MavenOptionBuilder maven()
        {
            return new MavenOption();
        }

        private class MavenOption
            implements MavenOptionBuilder
        {
            public MavenOptionBuilder flag( String flag )
            {
                m_mvnOptions.add( new Flag( 'D' + flag ) );
                return this;
            }

            public MavenOptionBuilder option( String option, String value )
            {
                if( "targetDirectory".equals( option ) )
                {
                    m_targetDir = value;
                }

                m_mvnOptions.add( new Option( 'D' + option, '=', value ) );
                return this;
            }
        }

        public String toString()
        {
            StringBuffer buf = new StringBuffer( m_name );

            for( Iterator i = m_paxOptions.iterator(); i.hasNext(); )
            {
                buf.append( ' ' );
                buf.append( i.next() );
            }

            if( !m_mvnOptions.isEmpty() )
            {
                buf.append( " --" );
            }

            for( Iterator i = m_mvnOptions.iterator(); i.hasNext(); )
            {
                buf.append( ' ' );
                buf.append( i.next() );
            }

            return buf.toString();
        }
    }

    private static class ByTargetDir
        implements Comparator
    {
        public int compare( Object lhs, Object rhs )
        {
            if( lhs instanceof PaxCommand )
            {
                if( rhs instanceof PaxCommand )
                {
                    return ( (PaxCommand) lhs ).m_targetDir.compareTo( ( (PaxCommand) rhs ).m_targetDir );
                }
                else
                {
                    return 1;
                }
            }
            else if( rhs instanceof PaxCommand )
            {
                return -1;
            }

            return 0;
        }
    }

    public void write( File scriptFile, String linePrefix )
        throws IOException
    {
        scriptFile.getParentFile().mkdirs();

        BufferedWriter writer = new BufferedWriter( WriterFactory.newPlatformWriter( scriptFile ) );

        Collections.sort( m_commands, new ByTargetDir() );
        for( Iterator i = m_commands.iterator(); i.hasNext(); )
        {
            writer.write( linePrefix + i.next() );
            writer.newLine();
        }

        IOUtil.close( writer );
    }
}
