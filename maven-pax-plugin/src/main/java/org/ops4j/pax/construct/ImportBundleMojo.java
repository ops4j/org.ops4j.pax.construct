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

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.kxml2.kdom.Document;
import org.kxml2.kdom.Element;

/**
 * Import a bundle to the OSGi project.
 * 
 * @goal import-bundle
 */
public final class ImportBundleMojo extends AbstractMojo
{
    /**
     * The containing OSGi project
     * 
     * @parameter expression="${project}"
     */
    protected MavenProject project;

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
     * Should the imported bundle be deployed.
     * 
     * @parameter expression="${deployable}" default-value="true"
     */
    private boolean deployable;

    /**
     * Should we attempt to overwrite entries.
     * 
     * @parameter expression="${overwrite}" default-value="false"
     */
    private boolean overwrite;

    // fudge one-shot behaviour...
    private static boolean ignore = false;

    public void execute()
        throws MojoExecutionException
    {
        if( ignore )
        {
            return;
        }
        ignore = true;

        // scan for the "imported" pom
        MavenProject importProject = null;
        MavenProject rootProject = project;
        while( rootProject.getParent() != null )
        {
            if( "imported-bundles".equals( rootProject.getArtifactId() ) )
            {
                importProject = rootProject;
            }
            rootProject = rootProject.getParent();
        }

        // try "imported" pom
        File targetFile = null;
        if( null != importProject )
        {
            targetFile = importProject.getFile();
        }

        // not in hierarchy, so check default location...
        if( null == targetFile || !targetFile.exists() )
        {
            targetFile = new File( rootProject.getBasedir(), "poms/imported/pom.xml" );
        }

        // fall back to using the current project
        if( null == targetFile || !targetFile.exists() )
        {
            targetFile = project.getFile();
        }

        try
        {
            Document pom = PomUtils.readPom( targetFile );

            Element projectElem = pom.getElement( null, "project" );
            Dependency dependency = new Dependency();
            dependency.setGroupId( groupId );
            dependency.setArtifactId( artifactId );
            dependency.setVersion( version );
            dependency.setScope( "provided" );

            if( deployable )
            {
                dependency.setClassifier( "deployable" );
            }
            else
            {
                dependency.setClassifier( "framework" );
            }

            PomUtils.addDependency( projectElem, dependency, overwrite );

            PomUtils.writePom( targetFile, pom );
        }
        catch( Exception e )
        {
            throw new MojoExecutionException( "Unable to import the requested bubdle", e );
        }
    }
}
