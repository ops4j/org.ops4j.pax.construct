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
        m_file = DirUtils.resolveFile( bndFile, true );

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
        Set directives = bnd.getDirectives();

        if( directives.contains( "Private-Package" ) || directives.contains( "Export-Package" ) )
        {
            // this might be an old converted wrapper project, so remove the default
            // embed dependency directive (only specified in osgi-wrapper archetype)
            removeInstruction( "Embed-Dependency" );
        }

        for( Iterator i = directives.iterator(); i.hasNext(); )
        {
            String directive = (String) i.next();

            String instruction = bnd.getInstruction( directive );
            setInstruction( directive, instruction, true );
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
        if( !m_newInstructions.equals( m_oldInstructions ) || !m_file.exists() )
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
        List block = new ArrayList();

        boolean skip = false;
        boolean echo = true;

        Properties instructions = new Properties();
        instructions.putAll( m_newInstructions );

        for( Iterator i = lines.iterator(); i.hasNext(); )
        {
            String line = (String) i.next();

            if( isWhitespaceOrComment( line ) )
            {
                block.add( line );
                skip = false;
            }
            else
            {
                if( !skip )
                {
                    // check to see if we should update / remove / leave alone
                    echo = checkInstructionLine( block, line, instructions );
                }

                if( echo )
                {
                    block.add( line );
                }

                // continue skipping to end of line
                skip = isLineContinuation( line );
            }
        }

        // append any new instructions...
        for( Enumeration e = instructions.keys(); e.hasMoreElements(); )
        {
            String key = (String) e.nextElement();
            String value = instructions.getProperty( key );
            writeInstruction( block, key, value );
        }

        // finally write updated text back to the file
        BufferedWriter writer = new BufferedWriter( StreamFactory.newPlatformWriter( m_file ) );
        writeInstructionBlock( writer, block );
        IOUtil.close( writer );
    }

    /**
     * Write a block of comments and instructions to the Bnd file
     * 
     * @param bndWriter writer for the Bnd file
     * @param block comment block
     * @throws IOException
     */
    private static void writeInstructionBlock( BufferedWriter bndWriter, List block )
        throws IOException
    {
        boolean needSpace = false;
        for( Iterator i = block.iterator(); i.hasNext(); )
        {
            String line = (String) i.next();

            boolean isSpace = isWhitespaceOrComment( line );

            if( needSpace && !isSpace )
            {
                bndWriter.newLine();
            }

            needSpace = false;

            if( !isSpace && !isLineContinuation( line ) )
            {
                needSpace = true;
            }

            bndWriter.write( line );
            bndWriter.newLine();
        }
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
            BufferedReader bndReader = new BufferedReader( StreamFactory.newPlatformReader( m_file ) );
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
     * @param block comment block
     * @param line existing line
     * @param instructions the instructions to write to disk
     * @return true if existing line should be echoed unchanged, otherwise false
     */
    private boolean checkInstructionLine( List block, String line, Properties instructions )
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

            // old instruction has been altered
            writeInstruction( block, key, newValue );
            return false;
        }

        // remove old instruction comment
        removeInstructionComment( block );
        return false;
    }

    /**
     * Remove the comment that's directly attached to the current instruction
     * 
     * @param block comment block
     */
    private static void removeInstructionComment( List block )
    {
        while( !block.isEmpty() )
        {
            // remove lines in reverse
            String line = (String) block.remove( block.size() - 1 );

            // assume comment ends once we see an empty line or a non-comment
            if( line.trim().length() == 0 || !isWhitespaceOrComment( line ) )
            {
                block.add( line );
                return;
            }
        }
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
     * Mark instruction clauses with line-continuation markers
     * 
     * @param instruction Bnd instruction
     * @return marked instruction
     */
    private static String markInstructionClauses( String instruction )
    {
        StringBuffer buf = new StringBuffer();
        boolean inQuotes = false;

        // add \\ markers between clauses
        char[] text = instruction.toCharArray();
        for( int i = 0; i < text.length; i++ )
        {
            char c = text[i];
            buf.append( c );

            switch( c )
            {
                case '\'':
                case '\"':
                    inQuotes = !inQuotes;
                    break;
                case ',':
                    if( !inQuotes )
                    {
                        buf.append( '\\' );
                    }
                    break;
                default:
                    break;
            }
        }

        return buf.toString();
    }

    /**
     * Write instruction as a standard property, with continuation markers at every comma
     * 
     * @param block comment block
     * @param key property key
     * @param value property value
     */
    private static void writeInstruction( List block, String key, String value )
    {
        StringBuffer buf = new StringBuffer( key + ':' );
        String instruction = markInstructionClauses( value );

        // heuristic: only wrap long instructions
        boolean multiLine = ( instruction.length() > 80 );

        // output clauses on single or multiple lines
        String[] clauses = instruction.split( "\\\\" );
        for( int i = 0; i < clauses.length; i++ )
        {
            if( multiLine )
            {
                buf.append( '\\' );
                block.add( buf.toString() );
                buf.setLength( 0 );
                buf.append( ' ' );
            }
            else if( i == 0 )
            {
                buf.append( ' ' );
            }

            buf.append( clauses[i].trim() );
        }

        block.add( buf.toString() );
    }
}
