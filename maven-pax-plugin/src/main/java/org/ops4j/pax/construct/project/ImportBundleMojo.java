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
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Import an externally provided bundle to the OSGi project.
 * 
 * @goal import-bundle
 * @aggregator true
 */
public final class ImportBundleMojo extends AbstractMojo
{
    /**
     * @parameter expression="${provisionId}" default-value="provision"
     */
    String provisionId;

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
     */
    private String version;

    /**
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    protected File targetDirectory;

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

    public void execute()
        throws MojoExecutionException
    {
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

        Pom targetPom = null;

        Pom localBundlePom = DirUtils.findPom( targetDirectory, groupId + ':' + artifactId );
        if( null == localBundlePom )
        {
            targetPom = DirUtils.findPom( targetDirectory, provisionId );
        }
        if( null == targetPom )
        {
            targetPom = PomUtils.readPom( targetDirectory );
        }

        targetPom.addDependency( dependency, overwrite );
        targetPom.write();
    }
}
