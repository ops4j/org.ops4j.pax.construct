package org.ops4j.pax.construct.clone;

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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.ops4j.pax.construct.util.StreamFactory;

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
     * @return sequence of pax commands
     */
    List getCommands()
    {
        return m_commands;
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

            // protect entire option (works on Windows and UNIX)
            return '\"' + super.toString() + m_separator + m_value + '\"';
        }
    }

    /**
     * Builder implementation for Pax-Construct commands
     */
    class PaxCommand
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

            getCommands().add( this );
        }

        /**
         * @return command name
         */
        String getName()
        {
            return m_name;
        }

        /**
         * @return sequence of maven options
         */
        List getMvnOptions()
        {
            return m_mvnOptions;
        }

        /**
         * @return sequence of pax options
         */
        List getPaxOptions()
        {
            return m_paxOptions;
        }

        /**
         * @return target directory for this command
         */
        String getTargetDir()
        {
            return m_targetDir;
        }

        /**
         * @param targetDir new target directory
         */
        void setTargetDir( String targetDir )
        {
            m_targetDir = targetDir;
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
        class MavenOption
            implements MavenOptionBuilder
        {
            /**
             * {@inheritDoc}
             */
            public MavenOptionBuilder flag( String flag )
            {
                getMvnOptions().add( new Flag( 'D' + flag ) );
                return this;
            }

            /**
             * {@inheritDoc}
             */
            public MavenOptionBuilder option( String option, String value )
            {
                if( "targetDirectory".equals( option ) )
                {
                    setTargetDir( value );
                }

                getMvnOptions().add( new Option( 'D' + option, '=', value ) );
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
    static class ByTargetDir
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
                    return compare( (PaxCommand) lhs, (PaxCommand) rhs );
                }

                return 1;
            }
            else if( rhs instanceof PaxCommand )
            {
                return -1;
            }

            return 0;
        }

        /**
         * Compare two Pax commands to decide which should go first
         * 
         * @param lhsCommand Pax command
         * @param rhsCommand Pax command
         * @return -1 (lhs before rhs), 0 (lhs same time as rhs), or 1 (lhs after rhs)
         */
        private int compare( PaxCommand lhsCommand, PaxCommand rhsCommand )
        {
            boolean lhsIsImport = IMPORT_BUNDLE.equals( lhsCommand.getName() );
            boolean rhsIsImport = IMPORT_BUNDLE.equals( rhsCommand.getName() );

            if( lhsIsImport && !rhsIsImport )
            {
                return 1;
            }
            else if( !lhsIsImport && rhsIsImport )
            {
                return -1;
            }
            else
            {
                return lhsCommand.getTargetDir().compareTo( rhsCommand.getTargetDir() );
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void write( String title, File scriptFile, List setupCommands )
        throws IOException
    {
        // Sort so projects are created before their bundles
        Collections.sort( m_commands, new ByTargetDir() );

        scriptFile.getParentFile().mkdirs();
        BufferedWriter writer = new BufferedWriter( StreamFactory.newPlatformWriter( scriptFile ) );
        boolean isBatchFile = scriptFile.getName().endsWith( ".bat" );

        writeHeader( writer, isBatchFile );
        writer.newLine();

        writeMessage( writer, "INSTALLING ARCHETYPES CLONED FROM [" + title + ']' );
        writer.newLine();

        writeCommands( writer, isBatchFile, setupCommands );
        writer.newLine();

        writeMessage( writer, "RECREATING MAVEN PROJECT BASED ON [" + title + ']' );
        writer.newLine();

        writeCommands( writer, isBatchFile, m_commands );
        IOUtil.close( writer );
    }

    /**
     * Write standard script header
     * 
     * @param writer script writer
     * @param isBatchFile true if it's a batch file, false if it's a shell script
     * @throws IOException
     */
    private static void writeHeader( BufferedWriter writer, boolean isBatchFile )
        throws IOException
    {
        final String scriptHeader;
        if( isBatchFile )
        {
            scriptHeader = "/header.bat";
        }
        else
        {
            scriptHeader = "/header.sh";
        }

        String header = IOUtil.toString( PaxScriptImpl.class.getResourceAsStream( scriptHeader ) );

        writer.write( header );
    }

    /**
     * @param writer script writer
     * @param message the message
     * @throws IOException
     */
    private static void writeMessage( BufferedWriter writer, String message )
        throws IOException
    {
        String border = "++++" + message.replaceAll( ".", "+" );

        writer.write( "echo " + border );
        writer.newLine();
        writer.write( "echo + " + message + " +" );
        writer.newLine();
        writer.write( "echo " + border );
        writer.newLine();
    }

    /**
     * Write sequence of commands
     * 
     * @param writer script writer
     * @param isBatchFile true if it's a batch file, false if it's a shell script
     * @param commands sequence of commands
     * @throws IOException
     */
    private static void writeCommands( BufferedWriter writer, boolean isBatchFile, List commands )
        throws IOException
    {
        boolean standalone = ( commands.size() == 1 );
        for( Iterator i = commands.iterator(); i.hasNext(); )
        {
            String cmd = i.next().toString();

            if( isBatchFile )
            {
                // need this in batch files
                writer.write( "call " );

                // fix variable references to use %FOO% not ${FOO}
                cmd = cmd.replaceAll( "\\$\\{([^}]*)\\}", "%$1%" );
            }

            if( standalone )
            {
                // allow customization
                if( isBatchFile )
                {
                    cmd = StringUtils.replace( cmd, " -- ", " %1 %2 %3 %4 %5 %6 %7 %8 %9 -- " );
                }
                else
                {
                    cmd = StringUtils.replace( cmd, " -- ", " \"$@\" -- " );
                }
            }

            writer.write( cmd );
            writer.newLine();
        }
    }
}
