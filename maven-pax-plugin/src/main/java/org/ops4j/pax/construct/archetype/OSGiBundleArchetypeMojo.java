package org.ops4j.pax.construct.archetype;

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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.codehaus.plexus.util.IOUtil;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * @goal archetype:create=create-bundle
 */
public class OSGiBundleArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * @parameter expression="${parentId}" default-value="compiled-bundle-settings"
     */
    String parentId;

    /**
     * @parameter expression="${provisionId}" default-value="provision"
     */
    String provisionId;

    /**
     * @parameter expression="${package}"
     * @required
     */
    String packageName;

    /**
     * @parameter expression="${bundleName}"
     * @required
     */
    String bundleName;

    /**
     * @parameter expression="${version}" default-value="1.0-SNAPSHOT"
     */
    String version;

    /**
     * @parameter expression="${interface}" default-value="true"
     */
    boolean provideInterface;

    /**
     * @parameter expression="${activator}" default-value="true"
     */
    boolean provideActivator;

    protected void updateExtensionFields()
    {
        m_mojo.setField( "archetypeArtifactId", "maven-archetype-osgi-bundle" );

        m_mojo.setField( "groupId", getCompactName( project.getGroupId(), project.getArtifactId() ) );
        m_mojo.setField( "artifactId", bundleName );
        m_mojo.setField( "version", version );

        m_mojo.setField( "packageName", packageName );
    }

    protected void postProcess()
        throws MojoExecutionException
    {
        super.postProcess();

        Pom provisionPom = DirUtils.findPom( targetDirectory, provisionId );
        if( null != provisionPom )
        {
            Pom thisPom = PomUtils.readPom( m_pomFile );

            Dependency buildDependency = new Dependency();
            buildDependency.setGroupId( provisionPom.getGroupId() );
            buildDependency.setArtifactId( provisionPom.getArtifactId() );
            buildDependency.setType( "pom" );

            thisPom.addDependency( buildDependency, overwrite );

            thisPom.write();
        }

        FileSet activatorFiles = new FileSet();
        activatorFiles.setDirectory( targetDirectory + File.separator + bundleName );

        if( !provideInterface )
        {
            activatorFiles.addInclude( "src/main/java/**/ExampleService.java" );
        }

        if( !provideActivator )
        {
            activatorFiles.addInclude( "src/main/java/**/internal" );
            activatorFiles.addInclude( "osgi.bnd" );
        }

        FileWriter out = null;

        try
        {
            if( activatorFiles.getIncludes() != null && !activatorFiles.getIncludes().isEmpty() )
            {
                new FileSetManager( getLog(), false ).delete( activatorFiles );
            }

            if( provideActivator && !provideInterface )
            {
                /*
                 * Interface x.Y will be in another bundle, so null the export packages (default is export package x)
                 * 
                 * If we don't do this then BND will automatically add interface Y to our generated bundle, as without
                 * interface Y we'd only be exporting part of package x, which is bad as it leads to a split package.
                 * 
                 * We only want interface Y in one bundle (the other one), so we reset our Export-Package setting...
                 */
                out = new FileWriter( activatorFiles.getDirectory() + "/osgi.bnd", true );
                out.write( "Export-Package:\n" );
            }

            if( !provideActivator && provideInterface )
            {
                out = new FileWriter( activatorFiles.getDirectory() + "/osgi.bnd", true );
                out.write( "Export-Package: ${bundle.namespace};version=\"${pom.version}\"\n" );
                out.write( "Private-Package:\n" );
            }
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "I/O error while patching files", e );
        }
        finally
        {
            IOUtil.close( out );
        }
    }

    protected String getParentId()
    {
        return parentId;
    }
}
