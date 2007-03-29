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
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.eclipse.EclipseSourceDir;
import org.apache.maven.plugin.eclipse.writers.EclipseClasspathWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseProjectWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseSettingsWriter;
import org.apache.maven.plugin.eclipse.writers.EclipseWriterConfig;
import org.apache.maven.plugin.ide.IdeDependency;
import org.ops4j.pax.construct.facades.EclipsePluginFacade;

/**
 * Extend maven-eclipse-plugin to get better classpath generation.
 * 
 * @goal eclipse
 */
public class EclipseMojo extends EclipsePluginFacade
{
    protected static class JarFileFilter implements FileFilter
    {
        public boolean accept( File file )
        {
            return file.getName().endsWith( ".jar" );
        }
    }

    protected IdeDependency[] addClassFolder(IdeDependency[] dependencies)
    {
        IdeDependency[] newDeps = new IdeDependency[dependencies.length + 1];
        System.arraycopy( dependencies, 0, newDeps, 1, dependencies.length );

        File clazzFolder = new File( project.getBuild().getDirectory() );

        newDeps[0] = new IdeDependency(
                "groupId", "artifactId", "version",
                false, false, true, true, true, clazzFolder, "folder",
                false, null, 0 );

        return newDeps;
    }

    protected IdeDependency[] addLocalLibs(IdeDependency[] dependencies)
    {
        File lib = new File( project.getBuild().getDirectory(), "lib" );

        if ( lib.exists() )
        {
            List<IdeDependency> deps = new ArrayList<IdeDependency>( Arrays.asList( dependencies ) );

            FileFilter filter = new JarFileFilter();
            for ( File jar : lib.listFiles( filter ) )
            {
                IdeDependency pseudoDep = new IdeDependency(
                    "groupId", "artifactId", "version",
                    false, false, true, true, true, jar, "jar",
                    false, null, 0 );
    
                deps.add( pseudoDep );
            }

            return deps.toArray( new IdeDependency[deps.size()] );
        }

        return dependencies;
    }

    public void writeConfiguration( IdeDependency[] deps )
        throws MojoExecutionException
    {
        EclipseWriterConfig config = createEclipseWriterConfig( deps );

        if ( isWrappedJarFile )
        {
            config.setDeps( addLocalLibs( config.getDeps() ) );
        }

        if ( isImportedBundle )
        {
            config.setDeps( addClassFolder( config.getDeps() ) );
        }

        if ( isWrappedJarFile || isImportedBundle )
        {
            config.setEclipseProjectDirectory( new File( project.getBuild().getDirectory() ) );
            config.setProjectBaseDir( config.getEclipseProjectDirectory() );
            config.setSourceDirs( new EclipseSourceDir[0] );
        }
        else
        {
            EclipseSourceDir[] sourceDirs = config.getSourceDirs();

            int localDirCount = 0;
            for ( int i = 0; i < sourceDirs.length; i++ )
            {
                if ( new File( sourceDirs[i].getPath() ).isAbsolute() == false )
                {
                    sourceDirs[localDirCount++] = sourceDirs[i];
                }
            }

            EclipseSourceDir[] localSourceDirs = new EclipseSourceDir[localDirCount];
            System.arraycopy( sourceDirs, 0, localSourceDirs, 0, localDirCount );

            config.setSourceDirs( localSourceDirs );
        }

        new EclipseSettingsWriter().init( getLog(), config ).write();
        new EclipseClasspathWriter().init( getLog(), config ).write();
        new EclipseProjectWriter().init( getLog(), config ).write();

        if ( !isImportedBundle )
        {
            try
            {
                JarFile bundle = new JarFile(
                    project.getBuild().getDirectory() +
                        File.separator +
                            project.getBuild().getFinalName()+".jar" );
    
                File manifestFile = new File(
                    config.getEclipseProjectDirectory(),
                        "META-INF"+File.separator+"MANIFEST.MF" );
    
                manifestFile.mkdirs();
                manifestFile.delete();

                Manifest manifest = bundle.getManifest();
                manifest.write( new FileOutputStream( manifestFile ) );
            }
            catch ( IOException e )
            {
                System.out.println("oops"+e);
            }
        }
    }
}
