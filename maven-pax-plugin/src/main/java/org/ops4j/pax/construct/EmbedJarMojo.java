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

import static org.ops4j.pax.construct.PomUtils.readPom;
import static org.ops4j.pax.construct.PomUtils.writePom;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.PropertyUtils;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Embeds a jarfile inside a bundle project.
 * 
 * @goal embed-jar
 */
public final class EmbedJarMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

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
     * @parameter default-value="${reactorProjects}"
     * @required
     * @readonly
     */
    private List reactorProjects;

    /**
     * Should the jar be unpacked inside the bundle.
     * 
     * @parameter expression="${unpack}" default-value="false"
     */
    private boolean unpack;

    /**
     * Should we attempt to overwrite entries.
     * 
     * @parameter expression="${overwrite}" default-value="false"
     */
    private boolean overwrite;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        boolean isCompiledBundle = false;
        for ( MavenProject p = project; p != null; p = p.getParent() )
        {
            if ( p.getArtifactId().equals( "compile-bundle" ) )
            {
                isCompiledBundle = true;
                break;
            }
        }

        // execute only if inside a compiled bundle project
        if ( reactorProjects.size() > 1 || !isCompiledBundle )
        {
            throw new MojoExecutionException( "Can only embed jars inside a compiled bundle sub-project" );
        }

        try
        {
            Document pom = readPom( project.getFile() );

            Element projectElem = pom.getElement( null, "project" );
            Dependency dependency = new Dependency();
            dependency.setGroupId( groupId );
            dependency.setArtifactId( artifactId );
            dependency.setVersion( version );
            dependency.setScope( "compile" );

            // make it optional to limit transitive nature
            dependency.setOptional( true );

            PomUtils.addDependency( projectElem, dependency, overwrite );

            writePom( project.getFile(), pom );

            if ( overwrite )
            {
                // no need to update the BND file if overwriting with a new version
                return;
            }

            File bndConfig = new File( project.getBasedir(), "src/main/resources/META-INF/details.bnd" );

            Properties properties = new Properties();

            if ( bndConfig.exists() )
            {
                properties = PropertyUtils.loadProperties( bndConfig );
            }
            else
            {
                bndConfig.getParentFile().mkdirs();
                bndConfig.createNewFile();
            }

            // UPDATE BUNDLE DETAILS TO EMBED DEPENDENT JAR

            String classpath = properties.getProperty( "Bundle-ClassPath", "." );
            String resources = properties.getProperty( "Include-Resource", "" );

            String jarPath = "target/" + artifactId + ".jar";

            classpath += "," + jarPath;
            if ( resources.length() > 0 )
            {
                resources += ",";
            }

            if ( unpack )
            {
                // Keep Eclipse PDE happy by providing pseudo-entry in the Bundle-ClassPath
                // so the original jar will be used when deploying the bundle from Eclipse.
                // ( this is the least messy compromise... )
                resources += "@" + jarPath + "," + jarPath + "=_placeholder_.jar";
                new File( project.getBasedir(), "_placeholder_.jar" ).createNewFile();
            }
            else
            {
                resources += jarPath + "=" + jarPath;
            }

            properties.setProperty( "Bundle-ClassPath", classpath );
            properties.setProperty( "Include-Resource", resources );

            OutputStream propertyStream = new BufferedOutputStream( new FileOutputStream( bndConfig ) );

            try
            {
                properties.store( propertyStream, null );
            }
            finally
            {
                IOUtil.close( propertyStream );
            }
        }
        catch ( Exception e )
        {
            throw new MojoExecutionException( "Unable to embed the requested jar artifact", e );
        }
    }

}
