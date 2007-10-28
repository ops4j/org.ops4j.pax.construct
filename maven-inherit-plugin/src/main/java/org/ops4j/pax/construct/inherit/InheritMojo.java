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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.DocletTag;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaSource;

/**
 * Support mojo inheritance by merging the local plugin metadata with metadata from dependent plugins.
 * 
 * Mojo inheritance is requested using a custom javadoc tag:
 * 
 * <code><pre>
 *   ...
 *   &#64;extendsPlugin archetype
 *   &#64;goal create
 *   ...
 * </pre></code>
 * 
 * By default the current goal is taken as the goal to be extended. To extend a different goal use:
 * 
 * <code><pre>
 *   ...
 *   &#64;extendsPlugin archetype
 *   &#64;extendsGoal create
 *   &#64;goal create-project
 *   ...
 * </pre></code>
 * 
 * @goal inherit
 * @phase compile
 * @requiresDependencyResolution compile
 */
public class InheritMojo extends AbstractMojo
{
    /**
     * classic mojo tag
     */
    private static final String GOAL = "goal";

    /**
     * new inheritance mojo tag
     */
    private static final String EXTENDS_PLUGIN = "extendsPlugin";

    /**
     * new inheritance mojo tag
     */
    private static final String EXTENDS_GOAL = "extendsGoal";

    /**
     * local plugin project
     * 
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject m_project;

    /**
     * output directory for the plugin project
     * 
     * @parameter expression="${project.build.outputDirectory}"
     * @readonly
     */
    private File m_outputDirectory;

    /**
     * support for accessing archives
     * 
     * @component
     */
    private ArchiverManager m_archiverManager;

    /**
     * Maven plugin entry-point
     */
    public void execute()
        throws MojoExecutionException
    {
        // pre-load available plugin metadata - local and dependencies
        PluginXml targetPlugin = loadPluginMetadata( m_outputDirectory );
        Map dependentPluginsByName = loadDependentPluginMetaData();

        // select maven source directories
        JavaDocBuilder builder = new JavaDocBuilder();
        for( Iterator i = m_project.getCompileSourceRoots().iterator(); i.hasNext(); )
        {
            builder.addSourceTree( new File( (String) i.next() ) );
        }

        // scan local source for javadoc tags
        JavaSource[] javaSources = builder.getSources();
        for( int i = 0; i < javaSources.length; i++ )
        {
            JavaClass mojoClass = javaSources[i].getClasses()[0];

            // need plugin inheritance
            DocletTag extendsTag = mojoClass.getTagByName( EXTENDS_PLUGIN );
            if( null != extendsTag )
            {
                String pluginName = extendsTag.getValue();
                getLog().info( "Extending " + pluginName + " plugin" );

                // lookup using simple plugin name (ie. compiler, archetype, etc.)
                PluginXml superPlugin = (PluginXml) dependentPluginsByName.get( pluginName );
                if( null == superPlugin )
                {
                    getLog().warn( pluginName + " plugin is not a dependency" );
                }
                else
                {
                    mergePluginMojo( mojoClass, targetPlugin, superPlugin );
                }
            }
        }

        try
        {
            targetPlugin.write();
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "cannot update local plugin metadata", e );
        }
    }

    /**
     * Loads plugin metadata for the given plugin location
     * 
     * @param pluginDir root directory for the compiled plugin
     * @return plugin metadata
     * @throws MojoExecutionException
     */
    private PluginXml loadPluginMetadata( File pluginDir )
        throws MojoExecutionException
    {
        File metadata = new File( pluginDir, "META-INF/maven/plugin.xml" );

        try
        {
            return new PluginXml( metadata );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "cannot read plugin metadata " + metadata, e );
        }
        catch( XmlPullParserException e )
        {
            throw new MojoExecutionException( "cannot parse plugin metadata " + metadata, e );
        }
    }

    /**
     * Loads plugin metadata for all plugins found in this project's dependencies
     * 
     * @return mapping from simple plugin name to plugin metadata
     * @throws MojoExecutionException
     */
    private Map loadDependentPluginMetaData()
        throws MojoExecutionException
    {
        File buildArea = new File( m_project.getBuild().getDirectory(), "plugins" );

        Map pluginsByName = new HashMap();
        for( Iterator i = m_project.getDependencyArtifacts().iterator(); i.hasNext(); )
        {
            Artifact artifact = (Artifact) i.next();
            if( "maven-plugin".equals( artifact.getType() ) && artifact.getFile() != null )
            {
                File unpackDir = new File( buildArea, artifact.getArtifactId() );
                unpackPlugin( artifact, unpackDir );

                // extract simple plugin name by applying the standard maven naming rules in reverse
                String name = artifact.getArtifactId().replaceAll( "(?:maven-)?(\\w+)(?:-maven)?-plugin", "$1" );

                pluginsByName.put( name, loadPluginMetadata( unpackDir ) );
            }
        }

        return pluginsByName;
    }

    /**
     * Unpacks a maven plugin artifact to the given directory
     * 
     * @param artifact maven plugin
     * @param unpackDir directory to unpack to
     * @throws MojoExecutionException
     */
    private void unpackPlugin( Artifact artifact, File unpackDir )
        throws MojoExecutionException
    {
        File pluginFile = artifact.getFile();
        unpackDir.mkdirs();

        try
        {
            UnArchiver unArchiver = m_archiverManager.getUnArchiver( pluginFile );
            unArchiver.setDestDirectory( unpackDir );
            unArchiver.setSourceFile( pluginFile );
            unArchiver.extract();
        }
        catch( NoSuchArchiverException e )
        {
            throw new MojoExecutionException( "cannot find unarchiver for " + pluginFile, e );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "problem reading file " + pluginFile, e );
        }
        catch( ArchiverException e )
        {
            throw new MojoExecutionException( "problem unpacking file " + pluginFile, e );
        }
    }

    /**
     * Inherits a mojo descriptor from a dependent plugin and merge it with the local plugin metadata
     * 
     * @param mojoClass local mojo code requiring inheritance
     * @param targetPlugin local plugin metadata
     * @param superPlugin plugin metadata being extended
     */
    private void mergePluginMojo( JavaClass mojoClass, PluginXml targetPlugin, PluginXml superPlugin )
    {
        DocletTag goalTag = mojoClass.getTagByName( GOAL );
        if( null == goalTag )
        {
            return;
        }

        DocletTag superGoalTag = mojoClass.getTagByName( EXTENDS_GOAL );
        if( null == superGoalTag )
        {
            superGoalTag = goalTag;
        }

        String goal = goalTag.getValue();
        String superGoal = superGoalTag.getValue();

        getLog().info( superGoal + " => " + goal );

        Xpp3Dom targetMojoXml = targetPlugin.findMojo( goal );
        Xpp3Dom superMojoXml = superPlugin.findMojo( superGoal );

        PluginXml.mergeMojo( targetMojoXml, superMojoXml );
    }
}
