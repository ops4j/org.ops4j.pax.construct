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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.ops4j.pax.construct.util.BndFileUtils.BndFile;
import org.ops4j.pax.construct.util.BndFileUtils.BndFileException;

public class RoundTripBndFile
    implements BndFile
{
    final File m_file;

    Properties m_newInstructions;
    Properties m_oldInstructions;

    public RoundTripBndFile( File bndFile )
    {
        m_file = bndFile.getAbsoluteFile();

        m_oldInstructions = new Properties();
        m_newInstructions = new Properties();

        try
        {
            InputStream bndStream = new FileInputStream( m_file );
            m_oldInstructions.load( bndStream );
            bndStream.close();
        }
        catch( Exception e )
        {
            // no file means empty instructions
        }

        m_newInstructions.putAll( m_oldInstructions );
    }

    public String getInstruction( String name )
    {
        return m_newInstructions.getProperty( name );
    }

    public void setInstruction( String name, String value, boolean overwrite )
    {
        if( overwrite || !m_newInstructions.containsKey( name ) )
        {
            m_newInstructions.setProperty( name, null == value ? "" : value );
        }
        else
        {
            throw new BndFileException( "Entry already exists, use -Doverwrite to replace it" );
        }
    }

    public boolean removeInstruction( String name )
    {
        return null != m_newInstructions.remove( name );
    }

    public File getFile()
    {
        return m_file;
    }

    public File getBasedir()
    {
        return m_file.getParentFile();
    }

    public void write()
    {
        try
        {
            if( m_newInstructions.isEmpty() )
            {
                m_file.delete();
            }
            else if( !m_newInstructions.equals( m_oldInstructions ) )
            {
                writeUpdatedInstructions();
            }
        }
        catch( Exception e )
        {
            throw new BndFileException( "Unable to write BND file " + m_file, e );
        }

        m_oldInstructions.clear();
        m_oldInstructions.putAll( m_newInstructions );
    }

    void writeUpdatedInstructions()
        throws IOException
    {
        List lines = new ArrayList();
        BufferedReader bndReader = new BufferedReader( new FileReader( m_file ) );
        while( bndReader.ready() )
        {
            lines.add( bndReader.readLine() );
        }
        bndReader.close();

        boolean skip = false;
        boolean echo = true;

        Properties instructions = new Properties();
        instructions.putAll( m_newInstructions );

        BufferedWriter bndWriter = new BufferedWriter( new FileWriter( m_file ) );
        for( Iterator i = lines.iterator(); i.hasNext(); )
        {
            String line = (String) i.next();

            if( isWhitespaceOrComment( line ) )
            {
                skip = false;
            }
            else if( skip )
            {
                skip = isLineContinuation( line );
            }
            else
            {
                String[] keyAndValue = line.split( "[=: \t\r\n\f]", 2 );
                String key = keyAndValue[0].trim();

                skip = true;
                echo = false;

                if( instructions.containsKey( key ) )
                {
                    String newValue = (String) instructions.remove( key );
                    if( newValue.equals( m_oldInstructions.getProperty( key ) ) )
                    {
                        echo = true;
                    }
                    else
                    {
                        writeInstruction( bndWriter, key, newValue );
                    }
                }
            }

            if( echo )
            {
                bndWriter.write( line );
                bndWriter.newLine();
            }
        }

        for( Enumeration e = instructions.keys(); e.hasMoreElements(); )
        {
            String key = (String) e.nextElement();
            writeInstruction( bndWriter, key, instructions.getProperty( key ) );
        }

        bndWriter.close();
    }

    static boolean isWhitespaceOrComment( String line )
    {
        String comment = line.trim();
        if( comment.length() == 0 )
        {
            return true;
        }

        char c = comment.charAt( 0 );
        return '#' == c || '!' == c;
    }

    static boolean isLineContinuation( String line )
    {
        boolean continueLine = false;
        for( int c = line.length() - 1; c >= 0 && '\\' == line.charAt( c ); c-- )
        {
            continueLine = !continueLine;
        }
        return continueLine;
    }

    static void writeInstruction( BufferedWriter writer, String key, String value )
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
