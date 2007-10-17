package org.ops4j.pax.construct.snapshot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

public class DefaultPaxScriptBuilder
    implements PaxScriptBuilder
{
    private List m_script = new ArrayList();

    private int m_cursor = 0;

    private StringBuffer m_buf = new StringBuffer();

    void add( String text )
    {
        m_script.add( m_cursor, text );
        m_cursor = m_script.size();
    }

    public PaxScriptBuilder comment( String comment )
    {
        add( "# " + comment );

        return this;
    }

    public PaxScriptBuilder at( int index )
    {
        if( m_buf.length() > 0 )
        {
            add( m_buf.toString() );
            m_buf.setLength( 0 );
        }

        m_cursor = index;

        return this;
    }

    public PaxOptionBuilder command( String command )
    {
        if( m_buf.length() > 0 )
        {
            add( m_buf.toString() );
            m_buf.setLength( 0 );
        }

        m_buf.append( command );

        return new PaxOptions();
    }

    class PaxOptions
        implements PaxOptionBuilder
    {
        public PaxOptionBuilder flag( char flag )
        {
            m_buf.append( " -" + flag );
            return this;
        }

        public PaxOptionBuilder option( char option, String value )
        {
            m_buf.append( " -" + option + ' ' + value );
            return this;
        }

        public MavenOptionBuilder maven()
        {
            m_buf.append( " --" );
            return new MavenOptions();
        }
    }

    class MavenOptions
        implements MavenOptionBuilder
    {
        public MavenOptionBuilder flag( String flag )
        {
            m_buf.append( " -D" + flag );
            return this;
        }

        public MavenOptionBuilder option( String option, String value )
        {
            m_buf.append( " -D" + option + '=' + value );
            return this;
        }
    }

    public String toString()
    {
        command( "" );
        StringBuffer text = new StringBuffer();
        text.append( System.getProperty( "line.separator" ) );
        for( Iterator i = m_script.iterator(); i.hasNext(); )
        {
            text.append( i.next() );
            text.append( System.getProperty( "line.separator" ) );
        }
        return text.toString();
    }
}
