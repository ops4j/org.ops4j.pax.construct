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
import java.util.List;

import org.apache.maven.plugin.CompilationFailureException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.TestCompilerMojo;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.ops4j.pax.construct.util.DirUtils;

/**
 * Extends <a href="http://maven.apache.org/plugins/maven-compiler-plugin/test-compile-mojo.html">TestCompilerMojo</a>
 * to support compiling against OSGi bundles with embedded jars. All TestCompilerMojo parameters can be used with this
 * mojo.
 * 
 * @extendsPlugin compiler
 * @goal testCompile
 * @phase test-compile
 * @requiresDependencyResolution test
 */
public class BundleTestCompilerMojo extends TestCompilerMojo
{
    /**
     * Component factory for archivers and unarchivers
     * 
     * @component
     */
    private ArchiverManager m_archiverManager;

    /**
     * {@inheritDoc}
     */
    protected List getClasspathElements()
    {
        File outputDir = getOutputDirectory();
        List classpath = super.getClasspathElements();
        File tempDir = new File( outputDir.getParent(), "dependencies" );

        return DirUtils.expandOSGiClassPath( outputDir, classpath, m_archiverManager, tempDir );
    }

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException,
        CompilationFailureException
    {
        try
        {
            super.execute();
        }
        catch( CompilationFailureException e )
        {
            // recover cleaned metadata on failure
            SqueakyCleanMojo.recoverMetaData( this );

            throw e;
        }
    }
}
