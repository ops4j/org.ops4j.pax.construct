package org.ops4j.pax.construct.clone;

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

/**
 * Builder implementation for Pax-Construct based build scripts
 */
public class PaxScriptImpl
    implements PaxScript
{
    /**
     * Sequence of Pax-Construct commands
     */
    private final List m_commands;

    /**
     * Create a new Pax-Construct script builder
     */
    public PaxScriptImpl()
    {
        m_commands = new ArrayList();
    }

    /**
     * {@inheritDoc}
     */
    public PaxCommandBuilder call( String command )
    {
        return new PaxCommand( command );
    }

    /**
     * Represents a simple command flag, such as -x or -Dy
     */
    private class Flag
    {
        /**
         * Flag name
         */
        private String m_flag;

        /**
         * @param flag name of the flag
         */
        public Flag( String flag )
        {
            m_flag = flag;
        }

        /**
         * {@inheritDoc}
         */
        public String toString()
        {
            return '-' + m_flag;
        }
    }

    /**
     * Represents a simple command option, such as -w foo or -Dz=bar
     */
    private class Option extends Flag
    {
        /**
         * Option separator
         */
        private char m_separator;

        /**
         * Specified value
         */
        private String m_value;

        /**
         * @param option name of the option
         * @param separator separator between option and value
         * @param value specified value
         */
        public Option( String option, char separator, String value )
        {
            super( option );
            m_separator = separator;
            m_value = value;
        }

        /**
         * {@inheritDoc}
         */
        public String toString()
        {
            if( ' ' == m_separator )
            {
                // just protect the value (works on Windows and UNIX)
                return super.toString() + m_separator + '\"' + m_value + '\"';
            }
            else
            {
                // protect entire option (works on Windows and UNIX)
                return '\"' + super.toString() + m_separator + m_value + '\"';
            }
        }
    }

    /**
     * Builder implementation for Pax-Construct commands
     */
    private class PaxCommand
        implements PaxCommandBuilder
    {
        /**
         * Name of the Pax-Construct script
         */
        private final String m_name;

        /**
         * Sequence of Pax-Construct options
         */
        private final List m_paxOptions;

        /**
         * Sequence of Maven specific options
         */
        private final List m_mvnOptions;

        /**
         * Target directory where the command should be run
         */
        private String m_targetDir;

        /**
         * @param command name of the Pax-Construct command
         */
        public PaxCommand( String command )
        {
            m_name = command;
            m_mvnOptions = new ArrayList();
            m_paxOptions = new ArrayList();
            m_targetDir = "";

            m_commands.add( this );
        }

        /**
         * @return target directory for this command
         */
        private String getTargetDir()
        {
            return m_targetDir;
        }

        /**
         * {@inheritDoc}
         */
        public PaxCommandBuilder flag( char flag )
        {
            m_paxOptions.add( new Flag( "" + flag ) );
            return this;
        }

        /**
         * {@inheritDoc}
         */
        public PaxCommandBuilder option( char option, String value )
        {
            m_paxOptions.add( new Option( "" + option, ' ', value ) );
            return this;
        }

        /**
         * {@inheritDoc}
         */
        public MavenOptionBuilder maven()
        {
            return new MavenOption();
        }

        /**
         * Builder implementation for Maven specific options
         */
        private class MavenOption
            implements MavenOptionBuilder
        {
            /**
             * {@inheritDoc}
             */
            public MavenOptionBuilder flag( String flag )
            {
                m_mvnOptions.add( new Flag( 'D' + flag ) );
                return this;
            }

            /**
             * {@inheritDoc}
             */
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

        /**
         * {@inheritDoc}
         */
        public String toString()
        {
            StringBuffer buf = new StringBuffer( "pax-" + m_name );

            for( Iterator i = m_paxOptions.iterator(); i.hasNext(); )
            {
                buf.append( ' ' );
                buf.append( i.next() );
            }

            // need option separator?
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

    /**
     * Sort Pax-Construct commands by their target directory, so projects are created before they are used
     */
    private static class ByTargetDir
        implements Comparator
    {
        /**
         * {@inheritDoc}
         */
        public int compare( Object lhs, Object rhs )
        {
            if( lhs instanceof PaxCommand )
            {
                if( rhs instanceof PaxCommand )
                {
                    return ( (PaxCommand) lhs ).getTargetDir().compareTo( ( (PaxCommand) rhs ).getTargetDir() );
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

    /**
     * {@inheritDoc}
     */
    public void write( File scriptFile )
        throws IOException
    {
        scriptFile.getParentFile().mkdirs();

        BufferedWriter writer = new BufferedWriter( WriterFactory.newPlatformWriter( scriptFile ) );

        // standard UNIX shell script header
        if( !scriptFile.getName().endsWith( ".bat" ) )
        {
            writer.write( "#!/bin/sh" );
            writer.newLine();
            writer.newLine();
        }

        // Sort so projects are created before their bundles
        Collections.sort( m_commands, new ByTargetDir() );

        for( Iterator i = m_commands.iterator(); i.hasNext(); )
        {
            if( scriptFile.getName().endsWith( ".bat" ) )
            {
                // need this in batch files
                writer.write( "call " );
            }

            writer.write( i.next().toString() );
            writer.newLine();
        }

        IOUtil.close( writer );
    }
}
