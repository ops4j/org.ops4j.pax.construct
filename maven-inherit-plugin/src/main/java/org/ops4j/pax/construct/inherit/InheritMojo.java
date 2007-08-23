package org.ops4j.pax.construct.inherit;

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

import org.apache.maven.plugin.AbstractMojo;
import org.codehaus.plexus.util.FileUtils;

/**
 * @goal inherit
 * @phase generate-resources
 */
public class InheritMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project.basedir}"
     */
    protected File baseDir;

    public void execute()
    {
        File patchedPluginXml = new File(baseDir, "src/main/resources/META-INF/maven/plugin.xml");
        File currentPluginXml = new File(baseDir, "target/classes/META-INF/maven/plugin.xml");
        try
        {
            FileUtils.copyFile( patchedPluginXml, currentPluginXml );
        }
        catch( IOException e )
        {
            // ignore, this is only placeholder code until proper patching is in place
        }
    }
}
