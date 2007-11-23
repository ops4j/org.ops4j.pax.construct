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
import org.apache.maven.project.MavenProject;
import org.ops4j.pax.construct.util.DirUtils;

/**
 * Extends <a href="http://maven.apache.org/plugins/maven-compiler-plugin/testCompile-mojo.html">TestCompilerMojo</a>
 * to support compiling against OSGi bundles with embedded jars.<br/>Inherited parameters can still be used, but
 * unfortunately don't appear in the generated docs.
 * 
 * @extendsPlugin compiler
 * @goal testCompile
 * @phase test-compile
 * @requiresDependencyResolution test
 */
public class BundleTestCompilerMojo extends TestCompilerMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject m_project;

    /**
     * {@inheritDoc}
     */
    protected List getClasspathElements()
    {
        File outputDir = getOutputDirectory();
        List classpath = super.getClasspathElements();
        File tempDir = new File( outputDir.getParent(), "pax-compiler" );

        return DirUtils.expandOSGiClassPath( outputDir, classpath, tempDir );
    }

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException,
        CompilationFailureException
    {
        BundleCompilerMojo.mergeCompilerConfiguration( this, m_project );

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
