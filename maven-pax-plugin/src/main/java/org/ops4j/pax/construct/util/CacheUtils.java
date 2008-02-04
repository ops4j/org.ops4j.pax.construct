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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.maven.plugin.AbstractMojo;
import org.codehaus.plexus.util.IOUtil;

/**
 * Various utility methods for caching files between plugins
 */
public final class CacheUtils
{
    /**
     * Hide constructor for utility class
     */
    private CacheUtils()
    {
        /*
         * nothing to do
         */
    }

    /**
     * Cache a text-based file inside the plugin context
     * 
     * @param mojo currently executing mojo
     * @param key unique identifier
     * @param file text-based file
     */
    public static void pushFile( AbstractMojo mojo, String key, File file )
    {
        if( file.exists() )
        {
            try
            {
                Reader input = StreamFactory.newPlatformReader( file );
                mojo.getPluginContext().put( key, IOUtil.toString( input ) );
                IOUtil.close( input );
            }
            catch( IOException e )
            {
                mojo.getLog().warn( "Unable to read file into cache: " + file );
            }
        }
    }

    /**
     * Restore a text-based file from the plugin context
     * 
     * @param mojo currently executing mojo
     * @param key unique identifier
     * @param file text-based file
     */
    public static void pullFile( AbstractMojo mojo, String key, File file )
    {
        String input = (String) mojo.getPluginContext().get( key );

        if( input != null )
        {
            try
            {
                file.getParentFile().mkdirs();
                file.createNewFile();

                Writer output = StreamFactory.newPlatformWriter( file );
                IOUtil.copy( input, output );
                IOUtil.close( output );
            }
            catch( IOException e )
            {
                mojo.getLog().warn( "Unable to write file back from cache: " + file );
            }
        }
    }
}
