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

public class BndFileUtils
{
    public static class BndFileException extends RuntimeException
    {
        static final long serialVersionUID = 1L;

        public BndFileException( String message )
        {
            super( message );
        }

        public BndFileException( String message, Throwable cause )
        {
            super( message, cause );
        }
    }

    public interface BndFile
    {
        public String getInstruction( String name );

        public void setInstruction( String name, String value, boolean overwrite );

        public boolean removeInstruction( String name );

        public File getFile();

        public File getBasedir();

        public void write();
    }

    public static BndFile readBndFile( File here )
    {
        if( here.isDirectory() )
        {
            here = new File( here, "osgi.bnd" );
        }

        return new RoundTripBndFile( here );
    }
}
