package org.ops4j.pax.construct;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Restore previous metadata in case compilation fails.
 * 
 * @goal backup
 */
public final class BackupMetadataMojo extends AbstractMojo
{
    /**
     * The directory containing generated pax files
     * 
     * @parameter expression="${project.basedir}"
     */
    private File basedir;

    public void execute()
        throws MojoExecutionException
    {
        CacheUtils.pullFile( this, "MANIFEST.MF", new File( basedir, "META-INF/MANIFEST.MF" ) );
        CacheUtils.pullFile( this, ".project",    new File( basedir, ".project" ) );
        CacheUtils.pullFile( this, ".classpath",  new File( basedir, ".classpath" ) );
    }
}
