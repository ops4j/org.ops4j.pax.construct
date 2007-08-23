package org.ops4j.pax.construct.lifecycle;

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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.ops4j.pax.construct.util.CacheUtils;

/**
 * Clean up various generated files.
 * 
 * @goal clean
 * @phase clean
 */
public final class CleanMojo extends AbstractMojo
{
    /**
     * The directory containing generated files.
     * 
     * @parameter expression="${project.basedir}"
     */
    private File basedir;

    /**
     * @parameter expression="${debug}"
     */
    private boolean debug;

    public void execute()
        throws MojoExecutionException
    {
        // cache files that we might have problems re-generating during the current lifecycle
        CacheUtils.pushFile( this, "MANIFEST.MF", new File( basedir, "META-INF/MANIFEST.MF" ) );
        CacheUtils.pushFile( this, ".project", new File( basedir, ".project" ) );
        CacheUtils.pushFile( this, ".classpath", new File( basedir, ".classpath" ) );

        FileSet generatedPaxFiles = new FileSet();
        generatedPaxFiles.setDirectory( basedir.getPath() );

        // remove Eclipse/PDE files (keep .settings)
        generatedPaxFiles.addInclude( "META-INF" );
        generatedPaxFiles.addInclude( "OSGI-INF" );
        generatedPaxFiles.addInclude( ".project" );
        generatedPaxFiles.addInclude( ".classpath" );

        try
        {
            new FileSetManager( getLog(), debug ).delete( generatedPaxFiles );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error while deleting files", e );
        }
    }
}
