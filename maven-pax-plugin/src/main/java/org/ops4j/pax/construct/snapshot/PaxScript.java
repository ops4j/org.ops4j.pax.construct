package org.ops4j.pax.construct.snapshot;

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

public interface PaxScript
{
    public static String CREATE_PROJECT = "pax-create-project";
    public static String CREATE_BUNDLE = "pax-create-bundle";
    public static String IMPORT_BUNDLE = "pax-import-bundle";
    public static String EMBED_JAR = "pax-embed-jar";
    public static String WRAP_JAR = "pax-wrap-jar";

    public PaxCommandBuilder call( String command );

    public void write( File scriptFile, String linePrefix )
        throws IOException;
}
