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
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.plexus.util.IOUtil;

/**
 * Create a new skeleton bundle and add it to an existing OSGi project.
 * 
 * @goal create-bundle
 */
public final class OSGiBundleArchetypeMojo extends AbstractChildArchetypeMojo
{
    /**
     * The package of the new bundle.
     * 
     * @parameter expression="${package}"
     * @required
     */
    private String packageName;

    /**
     * The name of the new bundle.
     * 
     * @parameter expression="${name}"
     * @required
     */
    private String bundleName;

    /**
     * The version of the new bundle.
     * 
     * @parameter expression="${version}" default-value="0.1.0-SNAPSHOT"
     */
    private String version;

    /**
     * @parameter expression="${interface}" default-value="true"
     */
    private boolean provideInterface;

    /**
     * @parameter expression="${activator}" default-value="true"
     */
    private boolean provideActivator;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        // this is the logical parent of the new bundle project
        if ( project.getArtifactId().equals( "compile-bundle" ) )
        {
            linkChildToParent();
        }

        // only create archetype under physical parent (ie. the _root_ project)
        return super.checkEnvironment();
    }

    protected void updateExtensionFields()
        throws MojoExecutionException
    {
        setField( "archetypeArtifactId", "maven-archetype-osgi-bundle" );

        setField( "groupId", project.getGroupId() + "." + project.getArtifactId() + ".bundles" );
        setField( "artifactId", bundleName );
        setField( "version", version );

        setField( "packageName", packageName );

        setChildProjectName( bundleName );
    }

    protected void postProcess()
        throws MojoExecutionException
    {
        FileSet activatorFiles = new FileSet();
        activatorFiles.setDirectory( project.getBasedir() + File.separator + bundleName );

        if ( !provideInterface )
        {
            activatorFiles.addInclude( "src/main/java/**/ExampleService.java" );
        }

        if ( !provideActivator )
        {
            activatorFiles.addInclude( "src/main/java/**/internal" );
            activatorFiles.addInclude( "src/main/resources" );
        }

        FileWriter out = null;

        try
        {
            if ( activatorFiles.getIncludes() != null && !activatorFiles.getIncludes().isEmpty() )
            {
                new FileSetManager( getLog(), false ).delete( activatorFiles );
            }

            if ( provideActivator && !provideInterface )
            {
                /*
                 * Interface x.Y will be in another bundle, so null the export packages (default is export package x)
                 * 
                 * If we don't do this then BND will automatically add interface Y to our generated bundle, as without
                 * interface Y we'd only be exporting part of package x, which is bad as it leads to a split package.
                 * 
                 * We only want interface Y in one bundle (the other one), so we reset our Export-Package setting...
                 */
                out = new FileWriter( activatorFiles.getDirectory() + "/src/main/resources/META-INF/details.bnd", true );
                out.write( "Export-Package:" + System.getProperty( "line.separator" ) );
            }
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "I/O error while patching files", e );
        }
        finally
        {
            IOUtil.close( out );
        }
    }
}
