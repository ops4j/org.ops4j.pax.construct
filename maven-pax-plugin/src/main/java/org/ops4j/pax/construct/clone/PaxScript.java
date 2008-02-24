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

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Builder interface for a build script based on Pax-Construct commands
 */
public interface PaxScript
{
    /**
     * Create a new OSGi project
     */
    static String CREATE_PROJECT = "create-project";

    /**
     * Create a new OSGi bundle
     */
    static String CREATE_BUNDLE = "create-bundle";

    /**
     * Import an existing OSGi bundle
     */
    static String IMPORT_BUNDLE = "import-bundle";

    /**
     * Embed a third-party jar inside an OSGi bundle
     */
    static String EMBED_JAR = "embed-jar";

    /**
     * Wrap a third-party jar as an OSGi bundle
     */
    static String WRAP_JAR = "wrap-jar";

    /**
     * Add a call to a Pax-Construct command
     * 
     * @param command name of a Pax-Construct command
     * @return builder for the Pax-Construct command
     */
    PaxCommandBuilder call( String command );

    /**
     * Write the current script to a file, the file extension is used to customize the contents for the target system
     * 
     * @param scriptFile where the script should be saved
     * @param setupCommands sequence of setup commands
     * @throws IOException
     */
    void write( File scriptFile, List setupCommands )
        throws IOException;
}
