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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.PropertyUtils;

/**
 * @goal archetype:create=wrap-jar
 */
public class OSGiWrapperArchetypeMojo extends AbstractPaxArchetypeMojo
{
    /**
     * @parameter expression="${parentId}" default-value="wrapper-bundle-settings"
     */
    String parentId;

    /**
     * @parameter expression="${groupId}"
     * @required
     */
    String groupId;

    /**
     * @parameter expression="${artifactId}"
     * @required
     */
    String artifactId;

    /**
     * @parameter expression="${version}"
     * @required
     */
    String version;

    /**
     * @parameter expression="${includeResource}"
     */
    String includeResource;

    /**
     * @parameter expression="${importPackage}"
     */
    String importPackage;

    /**
     * @parameter expression="${exportContents}"
     */
    String exportContents;

    /**
     * @parameter expression="${requireBundle}"
     */
    String requireBundle;

    /**
     * @parameter expression="${dynamicImportPackage}"
     */
    String dynamicImportPackage;

    /**
     * @parameter expression="${addVersion}"
     */
    boolean addVersion;

    /**
     * @component
     */
    MavenProjectBuilder mavenProjectBuilder;

    void updateExtensionFields()
    {
        m_mojo.setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );
        String compoundWrapperName = getCompactName( groupId, artifactId );

        if( addVersion )
        {
            compoundWrapperName += '-' + version;
        }

        m_mojo.setField( "groupId", getCompactName( project.getGroupId(), project.getArtifactId() ) );
        m_mojo.setField( "artifactId", compoundWrapperName );
        m_mojo.setField( "version", (addVersion ? '+' : ' ') + version );

        m_mojo.setField( "packageName", calculateGroupMarker( groupId, artifactId ) );
    }

    void postProcess()
        throws MojoExecutionException
    {
        super.postProcess();

        File bndConfig = new File( m_pomFile.getParentFile(), "osgi.bnd" );

        if( !bndConfig.exists() )
        {
            try
            {
                bndConfig.getParentFile().mkdirs();
                bndConfig.createNewFile();
            }
            catch( Exception e )
            {
                throw new MojoExecutionException( "Unable to create BND tool config file", e );
            }
        }

        Properties properties = PropertyUtils.loadProperties( bndConfig );

        if( includeResource != null )
            properties.setProperty( "Include-Resource", includeResource );

        if( importPackage != null )
            properties.setProperty( "Import-Package", importPackage );

        if( exportContents != null )
            properties.setProperty( "-exportcontents", exportContents );

        if( requireBundle != null )
            properties.setProperty( "Require-Bundle", requireBundle );

        if( dynamicImportPackage != null )
            properties.setProperty( "DynamicImport-Package", dynamicImportPackage );

        OutputStream propertyStream = null;

        try
        {
            propertyStream = new BufferedOutputStream( new FileOutputStream( bndConfig ) );
            properties.store( propertyStream, null );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to save the new BND tool instructions", e );
        }
        finally
        {
            IOUtil.close( propertyStream );
        }
    }

    String getParentId()
    {
        return parentId;
    }
}
