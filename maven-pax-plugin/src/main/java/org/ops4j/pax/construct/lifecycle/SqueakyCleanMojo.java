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
 * @goal clean
 * @phase clean
 */
public class SqueakyCleanMojo extends AbstractMojo
{
    /**
     * @parameter default-value="${project.basedir}"
     */
    File basedir;

    public void execute()
        throws MojoExecutionException
    {
        getLog().info( "[caching meta-data]" );

        // cache files that we might have problems re-generating during the current lifecycle
        CacheUtils.pushFile( this, "MANIFEST.MF", new File( basedir, "META-INF/MANIFEST.MF" ) );
        CacheUtils.pushFile( this, ".project", new File( basedir, ".project" ) );
        CacheUtils.pushFile( this, ".classpath", new File( basedir, ".classpath" ) );
        CacheUtils.pushFile( this, "build.properties", new File( basedir, "build.properties" ) );
        getPluginContext().put( "basedir", basedir.getPath() );

        FileSet generatedPaxFiles = new FileSet();
        generatedPaxFiles.setDirectory( basedir.getPath() );
        generatedPaxFiles.setUseDefaultExcludes( true );
        generatedPaxFiles.setFollowSymlinks( false );

        // remove Eclipse/PDE files (keep .settings)
        generatedPaxFiles.addInclude( "META-INF/**" );
        generatedPaxFiles.addInclude( "OSGI-INF/**" );
        generatedPaxFiles.addInclude( ".project" );
        generatedPaxFiles.addInclude( ".classpath" );
        generatedPaxFiles.addInclude( "build.properties" );

        try
        {
            new FileSetManager( getLog(), false ).delete( generatedPaxFiles );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error while deleting files", e );
        }
    }

    public static void recoverMetaData( AbstractMojo mojo )
    {
        mojo.getLog().info( "[recovering meta-data]" );

        String basedir = (String) mojo.getPluginContext().get( "basedir" );

        // Restore generated files (previously removed during clean phase) before re-generation
        CacheUtils.pullFile( mojo, "MANIFEST.MF", new File( basedir, "META-INF/MANIFEST.MF" ) );
        CacheUtils.pullFile( mojo, ".project", new File( basedir, ".project" ) );
        CacheUtils.pullFile( mojo, ".classpath", new File( basedir, ".classpath" ) );
        CacheUtils.pullFile( mojo, "build.properties", new File( basedir, "build.properties" ) );
    }
}
