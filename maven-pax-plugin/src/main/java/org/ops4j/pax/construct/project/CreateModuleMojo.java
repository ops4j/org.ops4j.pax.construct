package org.ops4j.pax.construct.project;

/*
 * Copyright 2008 Stuart McCulloch
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.ops4j.pax.construct.util.DirUtils;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.PomUtils.Pom;

/**
 * Create a new module inside a project.
 * 
 * <code><pre>
 *   mvn org.ops4j:maven-pax-plugin:create-module [-DgroupId=...] -DartifactId=... [-Dversion=...]
 * </pre></code>
 * 
 * @goal create-module
 * @aggregator true
 * 
 * @requiresProject false
 */
public class CreateModuleMojo extends AbstractMojo
{
    /**
     * The groupId for the new module.
     * 
     * @parameter expression="${groupId}"
     */
    private String groupId;

    /**
     * The artifactId or the path for the new module.
     * 
     * @parameter expression="${artifactId}"
     * @required
     */
    private String artifactId;

    /**
     * The version for the new module.
     * 
     * @parameter expression="${version}"
     */
    private String version;

    /**
     * The directory containing the POM to be updated.
     * 
     * @parameter expression="${targetDirectory}" default-value="${project.basedir}"
     */
    private File targetDirectory;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        try
        {
            File modulePath = new File( targetDirectory, artifactId );
            if( new File( modulePath, "pom.xml" ).exists() )
            {
                getLog().warn( "Module " + modulePath + " already exists" );
                return;
            }

            Pom modulePom = DirUtils.createModuleTree( targetDirectory, modulePath );
            if( null == modulePom )
            {
                throw new MojoExecutionException( "module path is outside of this project" );
            }

            // customized groupId?
            if( PomUtils.isNotEmpty( groupId ) )
            {
                modulePom.setGroupId( groupId );
            }

            // customized version?
            if( PomUtils.isNotEmpty( version ) )
            {
                modulePom.setVersion( version );
            }

            modulePom.write();

            getLog().info( "Created new module " + modulePath );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Unable to create module tree", e );
        }
    }
}
