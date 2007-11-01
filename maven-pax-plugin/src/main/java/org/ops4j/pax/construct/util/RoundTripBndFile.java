package org.ops4j.pax.construct.util;

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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.ops4j.pax.construct.util.BndUtils.Bnd;
import org.ops4j.pax.construct.util.BndUtils.ExistingInstructionException;

/**
 * Support round-trip editing of Bnd files, preserving formatting as much as possible
 */
public class RoundTripBndFile
    implements Bnd
{
    /**
     * Underlying Bnd file
     */
    private final File m_file;

    /**
     * Current instructions
     */
    private Properties m_newInstructions;

    /**
     * Last saved instructions
     */
    private Properties m_oldInstructions;

    /**
     * @param bndFile property file containing Bnd instructions
     * @throws IOException
     */
    public RoundTripBndFile( File bndFile )
        throws IOException
    {
        // protect against changes in working directory
        m_file = bndFile.getAbsoluteFile();

        m_oldInstructions = new Properties();
        m_newInstructions = new Properties();

        if( m_file.exists() )
        {
            FileInputStream bndStream = new FileInputStream( m_file );
            m_oldInstructions.load( bndStream );
            IOUtil.close( bndStream );
        }

        m_newInstructions.putAll( m_oldInstructions );
    }

    /**
     * {@inheritDoc}
     */
    public String getInstruction( String directive )
    {
        return m_newInstructions.getProperty( directive );
    }

    /**
     * {@inheritDoc}
     */
    public void setInstruction( String directive, String instruction, boolean overwrite )
        throws ExistingInstructionException
    {
        if( overwrite || !m_newInstructions.containsKey( directive ) )
        {
            if( null == instruction )
            {
                // map null instructions to the empty string
                m_newInstructions.setProperty( directive, "" );
            }
            else
            {
                m_newInstructions.setProperty( directive, instruction );
            }
        }
        else
        {
            throw new ExistingInstructionException( directive );
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeInstruction( String directive )
    {
        return null != m_newInstructions.remove( directive );
    }

    /**
     * {@inheritDoc}
     */
    public Set getDirectives()
    {
        return m_newInstructions.keySet();
    }

    /**
     * {@inheritDoc}
     */
    public void overlayInstructions( Bnd bnd )
    {
        for( Iterator i = bnd.getDirectives().iterator(); i.hasNext(); )
        {
            String directive = (String) i.next();
            if( "Private-Package".equals( directive ) || "Export-Package".equals( directive ) )
            {
                // old wrapper: maintain behaviour
                removeInstruction( "Embed-Dependency" );
            }

            String instruction = bnd.getInstruction( directive );
            m_newInstructions.setProperty( directive, instruction );
        }
    }

    /**
     * {@inheritDoc}
     */
    public File getFile()
    {
        return m_file;
    }

    /**
     * {@inheritDoc}
     */
    public File getBasedir()
    {
        return m_file.getParentFile();
    }

    /**
     * {@inheritDoc}
     */
    public void write()
        throws IOException
    {
        if( m_newInstructions.isEmpty() )
        {
            m_file.delete();
        }
        else if( !m_newInstructions.equals( m_oldInstructions ) || !m_file.exists() )
        {
            writeUpdatedInstructions();
        }

        m_oldInstructions.clear();
        m_oldInstructions.putAll( m_newInstructions );
    }

    /**
     * Write changes to disk, preserving formatting of unaffected lines
     * 
     * @throws IOException
     */
    private void writeUpdatedInstructions()
        throws IOException
    {
        List lines = readLines();

        boolean skip = false;
        boolean echo = true;

        Properties instructions = new Properties();
        instructions.putAll( m_newInstructions );

        BufferedWriter bndWriter = new BufferedWriter( WriterFactory.newPlatformWriter( m_file ) );
        for( Iterator i = lines.iterator(); i.hasNext(); )
        {
            String line = (String) i.next();

            if( isWhitespaceOrComment( line ) )
            {
                // check next line
                skip = false;
            }
            else
            {
                if( !skip )
                {
                    // check to see if we should update / remove / leave alone
                    echo = checkInstructionLine( instructions, bndWriter, line );
                }

                // continue skipping to end of line
                skip = isLineContinuation( line );
            }

            if( echo )
            {
                // preserve existing line
                bndWriter.write( line );
                bndWriter.newLine();
            }
        }

        // append any new instructions...
        for( Enumeration e = instructions.keys(); e.hasMoreElements(); )
        {
            String key = (String) e.nextElement();

            writeInstruction( bndWriter, key, instructions.getProperty( key ) );
        }

        IOUtil.close( bndWriter );
    }

    /**
     * This assumes most Bnd files will be relatively small
     * 
     * @return list of all the lines in the Bnd file
     * @throws IOException
     */
    private List readLines()
        throws IOException
    {
        List lines = new ArrayList();

        if( m_file.exists() )
        {
            BufferedReader bndReader = new BufferedReader( ReaderFactory.newPlatformReader( m_file ) );
            while( bndReader.ready() )
            {
                lines.add( bndReader.readLine() );
            }
            IOUtil.close( bndReader );
        }

        return lines;
    }

    /**
     * Check existing line against instructions and update if necessary
     * 
     * @param instructions the instructions to write to disk
     * @param writer line writer
     * @param line existing line
     * @return true if existing line should be echoed unchanged, otherwise false
     * @throws IOException
     */
    private boolean checkInstructionLine( Properties instructions, BufferedWriter writer, String line )
        throws IOException
    {
        String[] keyAndValue = line.split( "[=: \t\r\n\f]", 2 );
        String key = keyAndValue[0].trim();

        if( instructions.containsKey( key ) )
        {
            String newValue = (String) instructions.remove( key );
            if( newValue.equals( m_oldInstructions.getProperty( key ) ) )
            {
                // no change
                return true;
            }
            else
            {
                writeInstruction( writer, key, newValue );
            }
        }

        // instruction has been updated or removed
        return false;
    }

    /**
     * @param line existing line
     * @return true if line only contains whitespace or comments
     */
    private static boolean isWhitespaceOrComment( String line )
    {
        String comment = line.trim();
        if( comment.length() == 0 )
        {
            return true;
        }

        char c = comment.charAt( 0 );
        return '#' == c || '!' == c;
    }

    /**
     * @param line existing line
     * @return true if line ends in a continuation marker
     */
    private static boolean isLineContinuation( String line )
    {
        boolean continueLine = false;
        for( int c = line.length() - 1; c >= 0 && '\\' == line.charAt( c ); c-- )
        {
            continueLine = !continueLine;
        }
        return continueLine;
    }

    /**
     * Write instruction as a standard property, with continuation markers at every comma
     * 
     * @param writer line writer
     * @param key property key
     * @param value property value
     * @throws IOException
     */
    private static void writeInstruction( BufferedWriter writer, String key, String value )
        throws IOException
    {
        writer.write( key + ':' );

        int i = 0;
        for( int j = value.indexOf( ',' ); j >= 0; i = j + 1, j = value.indexOf( ',', i ) )
        {
            writer.write( '\\' );
            writer.newLine();

            writer.write( ' ' + value.substring( i, j ) + ',' );
        }

        if( i > 0 )
        {
            writer.write( '\\' );
            writer.newLine();
        }

        writer.write( ' ' + value.substring( i ) );

        writer.newLine();
        writer.newLine();
    }
}
