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

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractCompilerMojo;
import org.apache.maven.plugin.CompilationFailureException;
import org.apache.maven.plugin.CompilerMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.ReflectMojo;

/**
 * Extends <a href="http://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html">CompilerMojo</a> to
 * support compiling against OSGi bundles with embedded jars.<br/>Inherited parameters can still be used, but
 * unfortunately don't appear in the generated docs.
 * 
 * @extendsPlugin compiler
 * @goal compile
 * @phase compile
 * @requiresDependencyResolution compile
 */
public class BundleCompilerMojo extends CompilerMojo
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

    /**
     * Copy additional compiler settings from maven-compiler-plugin section (only handles simple configuration items)
     * 
     * @param mojo compiler mojo
     * @param project maven project
     */
    protected static void mergeCompilerConfiguration( AbstractCompilerMojo mojo, MavenProject project )
    {
        Plugin core = new Plugin();
        core.setGroupId( "org.apache.maven.plugins" );
        core.setArtifactId( "maven-compiler-plugin" );

        Plugin pax = new Plugin();
        pax.setGroupId( "org.ops4j" );
        pax.setArtifactId( "maven-pax-plugin" );

        // load pluginManagement
        project.addPlugin( core );
        project.addPlugin( pax );

        Xpp3Dom coreConfig = project.getGoalConfiguration( core.getGroupId(), core.getArtifactId(), null, null );
        Xpp3Dom paxConfig = project.getGoalConfiguration( pax.getGroupId(), pax.getArtifactId(), null, null );

        if( null != coreConfig )
        {
            ReflectMojo baseMojo = new ReflectMojo( mojo, AbstractCompilerMojo.class );

            Xpp3Dom[] configuration = coreConfig.getChildren();
            for( int i = 0; i < configuration.length; i++ )
            {
                // don't override pax settings
                String name = configuration[i].getName();
                if( ( null == paxConfig || null == paxConfig.getChild( name ) ) && baseMojo.hasField( name ) )
                {
                    // only use non-empty settings
                    String value = configuration[i].getValue();
                    if( null != value )
                    {
                        mojo.getLog().debug( "Using compiler setting: " + name + "=" + value );
                        baseMojo.setField( name, value );
                    }
                }
            }
        }
    }
}
