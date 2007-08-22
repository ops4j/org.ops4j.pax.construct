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
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.PropertyUtils;

/**
 * Wrap a third-party jar as a bundle and add it to an existing OSGi project.
 * 
 * @goal wrap-jar
 */
public final class OSGiWrapperArchetypeMojo extends AbstractChildArchetypeMojo
{
    /**
     * The groupId of the jarfile to wrap.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId of the jarfile to wrap.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the jarfile to wrap.
     * 
     * @parameter expression="${version}"
     * @required
     */
    private String version;

    /**
     * @parameter expression="${includeResource}"
     */
    private String includeResource;

    /**
     * @parameter expression="${importPackage}"
     */
    private String importPackage;

    /**
     * @parameter expression="${exportContents}"
     */
    private String exportContents;

    /**
     * @parameter expression="${requireBundle}"
     */
    private String requireBundle;

    /**
     * @parameter expression="${dynamicImportPackage}"
     */
    private String dynamicImportPackage;

    /**
     * @parameter expression="${excludeTransitive}"
     */
    private boolean excludeTransitive;

    /**
     * @parameter expression="${addVersion}"
     */
    private boolean addVersion;

    protected boolean checkEnvironment()
        throws MojoExecutionException
    {
        // this is the logical parent of the new bundle project
        if( "wrap-jar-as-bundle".equals( project.getArtifactId() ) )
        {
            linkChildToParent();
        }

        // only create archetype under physical parent (ie. the _root_ project)
        return super.checkEnvironment();
    }

    protected void updateExtensionFields()
        throws MojoExecutionException
    {
        setField( "archetypeArtifactId", "maven-archetype-osgi-wrapper" );
        String compoundWrapperName = getCompoundName( groupId, artifactId );

        if( addVersion )
        {
            compoundWrapperName += "-" + version;
        }

        setField( "groupId", getCompoundName( project.getGroupId(), project.getArtifactId() ) );
        setField( "artifactId", compoundWrapperName );
        setField( "version", (addVersion ? '+' : ' ') + version );

        setField( "packageName", getGroupMarker( groupId, artifactId ) );

        setChildProjectName( compoundWrapperName );
    }

    protected void postProcess()
        throws MojoExecutionException
    {
        File bndConfig = new File( childPomFile.getParentFile(), "src/main/resources/META-INF/details.bnd" );

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

        properties.setProperty( "Embed-Transitive", "" + !excludeTransitive );

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
}
