package org.ops4j.pax.construct.project;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;
import org.ops4j.pax.construct.util.PomUtils;

/**
 * Import an externally provided bundle to the OSGi project.
 * 
 * @goal import-bundle
 */
public final class ImportBundleMojo extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * The groupId of the bundle to import.
     * 
     * @parameter expression="${groupId}"
     * @required
     */
    private String groupId;

    /**
     * The artifactId of the bundle to import.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version of the bundle to import.
     * 
     * @parameter expression="${version}"
     * @required
     */
    private String version;

    /**
     * Should the imported bundle be deployed?
     * 
     * @parameter expression="${deploy}" default-value="true"
     */
    private boolean deploy;

    /**
     * Should we attempt to overwrite entries?
     * 
     * @parameter expression="${overwrite}"
     */
    private boolean overwrite;

    /**
     * Only update one provisioning pom at a time.
     */
    private static boolean ignore = false;

    public void execute()
        throws MojoExecutionException
    {
        if( ignore )
        {
            return;
        }
        ignore = true;

        /*
         * the following code attempts to allow updates to the main provisioning pom from elsewhere in the project tree
         */
        MavenProject provisionProject = null;

        MavenProject rootProject = project;
        while( rootProject.getParent() != null )
        {
            // scan for the main provisioning pom used in this particular project
            if( "provision".equals( rootProject.getArtifactId() ) && rootProject.getGroupId().endsWith( ".build" ) )
            {
                provisionProject = rootProject;
            }
            rootProject = rootProject.getParent();
        }

        // try provision pom
        File targetFile = null;
        if( null != provisionProject )
        {
            targetFile = provisionProject.getFile();
        }

        // not in hierarchy, check default location...
        if( null == targetFile || !targetFile.exists() )
        {
            targetFile = new File( rootProject.getBasedir(), "provision/pom.xml" );
        }

        // fall back to using the current project
        if( null == targetFile || !targetFile.exists() )
        {
            targetFile = project.getFile();
        }

        Document pom = PomUtils.readPom( targetFile );

        Element projectElem = pom.getElement( null, "project" );
        Dependency dependency = new Dependency();
        dependency.setGroupId( groupId );
        dependency.setArtifactId( artifactId );
        dependency.setVersion( version );
        dependency.setScope( Artifact.SCOPE_PROVIDED );

        if( deploy )
        {
            // non-optional, must be deployed
            dependency.setOptional( false );
        }
        else
        {
            // optional (ie. framework package)
            dependency.setOptional( true );
        }

        PomUtils.addDependency( projectElem, dependency, overwrite );

        PomUtils.writePom( targetFile, pom );
    }
}
