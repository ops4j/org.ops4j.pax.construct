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
import java.util.Set;

/**
 * Provide API {@link Bnd} and factory for editing Bnd instruction files
 */
public final class BndUtils
{
    /**
     * Hide constructor for utility class
     */
    private BndUtils()
    {
    }

    /**
     * API for editing Bnd files
     */
    public interface Bnd
    {
        /**
         * @param directive a Bnd directive
         * @return assigned Bnd instruction
         */
        String getInstruction( String directive );

        /**
         * @param directive a Bnd directive
         * @param instruction a Bnd instruction
         * @param overwrite overwrite existing instruction if true, otherwise throw {@link ExistingInstructionException}
         * @throws ExistingInstructionException
         */
        void setInstruction( String directive, String instruction, boolean overwrite )
            throws ExistingInstructionException;

        /**
         * @param directive a Bnd directive
         * @return true if there was an existing instruction, otherwise false
         */
        boolean removeInstruction( String directive );

        /**
         * @return set of current directive names
         */
        Set getDirectives();

        /**
         * Overlay existing instructions onto the current setup
         * 
         * @param bnd existing Bnd instructions
         */
        void overlayInstructions( Bnd bnd );

        /**
         * @return the underlying Bnd instruction file
         */
        File getFile();

        /**
         * @return the directory containing the Bnd file
         */
        File getBasedir();

        /**
         * @throws IOException
         */
        void write()
            throws IOException;
    }

    /**
     * Thrown when a Bnd instruction already exists and can't be overwritten {@link Bnd}
     */
    public static class ExistingInstructionException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        /**
         * @param directive directive name for the existing instruction
         */
        public ExistingInstructionException( String directive )
        {
            super( "Bnd file already has a " + directive + " directive, use -Doverwrite or -o to replace it" );
        }

        /**
         * {@inheritDoc}
         */
        public synchronized Throwable fillInStackTrace()
        {
            return this;
        }

        /**
         * {@inheritDoc}
         */
        public String toString()
        {
            return "[INFO] not available";
        }
    }

    /**
     * Factory method that provides an editor for an existing or new Bnd file
     * 
     * @param here a Bnd file, or a directory containing a file named 'osgi.bnd'
     * @return simple Bnd file editor
     * @throws IOException
     */
    public static Bnd readBnd( File here )
        throws IOException
    {
        File candidate = here;

        if( null == here )
        {
            throw new IOException( "null location" );
        }
        else if( here.isDirectory() )
        {
            candidate = new File( here, "osgi.bnd" );
        }

        return new RoundTripBndFile( candidate );
    }
}
