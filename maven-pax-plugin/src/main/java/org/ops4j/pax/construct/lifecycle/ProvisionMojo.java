package org.ops4j.pax.construct.lifecycle;

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
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.IOUtil;
import org.ops4j.pax.construct.util.PomUtils;
import org.ops4j.pax.construct.util.StreamFactory;

/**
 * Provision all local and imported bundles onto the selected OSGi framework
 * 
 * <code><pre>
 *   mvn pax:provision [-Dframework={felix|equinox|kf...}] [-Ddeploy={minimal,spring-dm,war,...}]
 * </pre></code>
 * 
 * @goal provision
 * @aggregator true
 * 
 * @requiresProject false
 */
public class ProvisionMojo extends AbstractMojo
{
    /**
     * Maven groupId for the new Pax-Runner
     */
    private static final String PAX_RUNNER_GROUP = "org.ops4j.pax.runner";

    /**
     * Maven artifactId for the new Pax-Runner
     */
    private static final String PAX_RUNNER_ARTIFACT = "pax-runner";

    /**
     * Main entry-point for the new Pax-Runner
     */
    private static final String PAX_RUNNER_METHOD = "org.ops4j.pax.runner.Run";

    /**
     * Accumulated set of bundles to be deployed
     */
    private static List m_bundleIds;

    /**
     * Component for resolving Maven metadata
     * 
     * @component
     */
    private ArtifactMetadataSource m_source;

    /**
     * Component factory for Maven artifacts
     * 
     * @component
     */
    private ArtifactFactory m_factory;

    /**
     * Component for resolving Maven artifacts
     * 
     * @component
     */
    private ArtifactResolver m_resolver;

    /**
     * Component for installing Maven artifacts
     * 
     * @component
     */
    private ArtifactInstaller m_installer;

    /**
     * Component factory for Maven projects
     * 
     * @component
     */
    private MavenProjectBuilder m_projectBuilder;

    /**
     * The local Maven settings.
     * 
     * @parameter expression="${settings}"
     * @required
     * @readonly
     */
    private Settings m_settings;

    /**
     * List of remote Maven repositories for the containing project.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List m_remoteRepos;

    /**
     * The local Maven repository for the containing project.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository m_localRepo;

    /**
     * The current Maven reactor.
     * 
     * @parameter expression="${reactorProjects}"
     * @required
     * @readonly
     */
    private List m_reactorProjects;

    /**
     * Name of the OSGi framework to deploy onto.
     * 
     * @parameter expression="${framework}"
     */
    private String framework;

    /**
     * Comma separated list of additional Pax-Runner profiles to deploy.
     * 
     * @parameter expression="${deploy}"
     */
    private String deploy;

    /**
     * URL of file containing additional Pax-Runner arguments.
     * 
     * @parameter expression="${args}"
     */
    private String args;

    /**
     * Ignore bundle dependencies when deploying project.
     * 
     * @parameter expression="${noDependencies}"
     */
    private boolean noDependencies;

    /**
     * Comma separated list of additional POMs with bundles as dependencies.
     * 
     * @parameter expression="${deployPoms}"
     */
    private String deployPoms;

    /**
     * The version of Pax-Runner to use for provisioning.
     * 
     * @parameter expression="${runner}" default-value="RELEASE"
     */
    private String runner;

    /**
     * A set of provision commands for Pax-Runner.
     * 
     * @parameter expression="${provision}"
     */
    private String[] provision;

    /**
     * Component factory for Maven repositories.
     * 
     * @component
     */
    private ArtifactRepositoryFactory m_repoFactory;

    /**
     * @component roleHint="default"
     */
    private ArtifactRepositoryLayout m_defaultLayout;

    /**
     * {@inheritDoc}
     */
    public void execute()
        throws MojoExecutionException
    {
        m_bundleIds = new ArrayList();

        if( deployPoms != null )
        {
            addAdditionalPoms();
        }

        for( Iterator i = m_reactorProjects.iterator(); i.hasNext(); )
        {
            addProjectBundles( (MavenProject) i.next(), false == noDependencies );
        }

        deployBundles();
    }

    /**
     * Does this look like a provisioning POM? ie. artifactId of 'provision', packaging type 'pom', with dependencies
     * 
     * @param project a Maven project
     * @return true if this looks like a provisioning POM, otherwise false
     */
    public static boolean isProvisioningPom( MavenProject project )
    {
        // ignore POMs which don't have provision as their artifactId
        if( !"provision".equals( project.getArtifactId() ) )
        {
            return false;
        }

        // ignore POMs that produce actual artifacts
        if( !"pom".equals( project.getPackaging() ) )
        {
            return false;
        }

        // ignore POMs with no dependencies at all
        List dependencies = project.getDependencies();
        if( dependencies == null || dependencies.size() == 0 )
        {
            return false;
        }

        return true;
    }

    /**
     * Adds project artifact (if it's a bundle) to the deploy list as well as any non-optional bundle dependencies
     * 
     * @param project a Maven project
     * @param checkDependencies when true, check project dependencies for other bundles to provision
     */
    private void addProjectBundles( MavenProject project, boolean checkDependencies )
    {
        if( PomUtils.isBundleProject( project ) )
        {
            provisionBundle( project.getArtifact() );
        }

        if( checkDependencies || isProvisioningPom( project ) )
        {
            addProjectDependencies( project );
        }
    }

    /**
     * Adds any non-optional bundle dependencies to the deploy list
     * 
     * @param project a Maven project
     */
    private void addProjectDependencies( MavenProject project )
    {
        try
        {
            Set artifacts = project.createArtifacts( m_factory, null, null );
            for( Iterator i = artifacts.iterator(); i.hasNext(); )
            {
                Artifact artifact = (Artifact) i.next();
                if( !artifact.isOptional() && !Artifact.SCOPE_TEST.equals( artifact.getScope() ) )
                {
                    provisionBundle( artifact );
                }
            }
        }
        catch( InvalidDependencyVersionException e )
        {
            getLog().warn( "Bad version in dependencies for " + project.getId() );
        }
    }

    /**
     * @param bundle potential bundle artifact
     */
    private void provisionBundle( Artifact bundle )
    {
        // force download here, as next check tries to avoid downloading where possible
        if( !PomUtils.downloadFile( bundle, m_resolver, m_remoteRepos, m_localRepo ) )
        {
            getLog().warn( "Skipping missing artifact " + bundle );
            return;
        }

        if( PomUtils.isBundleArtifact( bundle, m_resolver, m_remoteRepos, m_localRepo, true ) )
        {
            String version = PomUtils.getMetaVersion( bundle );
            String bundleId = bundle.getGroupId() + ':' + bundle.getArtifactId() + ':' + version;
            if( !m_bundleIds.contains( bundleId ) )
            {
                m_bundleIds.add( bundleId );
            }
        }
        else
        {
            getLog().warn( "Skipping non-bundle artifact " + bundle );
        }
    }

    /**
     * Add user supplied POMs as if they were in the Maven reactor
     */
    private void addAdditionalPoms()
    {
        String[] pomPaths = deployPoms.split( "," );
        for( int i = 0; i < pomPaths.length; i++ )
        {
            File pomFile = new File( pomPaths[i].trim() );
            if( pomFile.exists() )
            {
                try
                {
                    addProjectBundles( m_projectBuilder.build( pomFile, m_localRepo, null ), true );
                }
                catch( ProjectBuildingException e )
                {
                    getLog().warn( "Unable to build Maven project for " + pomFile );
                }
            }
        }
    }

    /**
     * Create deployment POM and pass it onto Pax-Runner for provisioning
     * 
     * @throws MojoExecutionException
     */
    private void deployBundles()
        throws MojoExecutionException
    {
        if( m_bundleIds.size() == 0 )
        {
            getLog().info( "~~~~~~~~~~~~~~~~~~~" );
            getLog().info( " No bundles found! " );
            getLog().info( "~~~~~~~~~~~~~~~~~~~" );
        }

        List bundles = resolveProvisionedBundles();
        MavenProject deployProject = createDeploymentProject( bundles );
        installDeploymentPom( deployProject );

        if( "false".equalsIgnoreCase( deploy ) )
        {
            getLog().info( "Skipping deployment" );
            return;
        }

        m_remoteRepos.add( getOps4jRepository() ); // can remove this once runner is on central

        String delim = "";
        StringBuffer repoListBuilder = new StringBuffer();
        for( Iterator i = m_remoteRepos.iterator(); i.hasNext(); )
        {
            repoListBuilder.append( delim );

            ArtifactRepository repo = (ArtifactRepository) i.next();
            Mirror mirror = getRepositoryMirror( repo );
            if( null == mirror )
            {
                repoListBuilder.append( repo.getUrl() );
            }
            else
            {
                repoListBuilder.append( mirror.getUrl() );
            }

            delim = ",";
        }

        if( PomUtils.needReleaseVersion( runner ) )
        {
            // find the latest release of Pax-Runner by querying the local and remote repos...
            Artifact runnerProject = m_factory.createProjectArtifact( PAX_RUNNER_GROUP, PAX_RUNNER_ARTIFACT, runner );
            runner = PomUtils.getReleaseVersion( runnerProject, m_source, m_remoteRepos, m_localRepo, null );
        }

        /*
         * Dynamically load the correct Pax-Runner code
         */
        if( runner.compareTo( "0.5.0" ) < 0 )
        {
            Class clazz = loadRunnerClass( "org.ops4j.pax", "runner", PAX_RUNNER_METHOD, false );
            deployRunnerClassic( clazz, deployProject, repoListBuilder.toString() );
        }
        else
        {
            Class clazz = loadRunnerClass( PAX_RUNNER_GROUP, PAX_RUNNER_ARTIFACT, PAX_RUNNER_METHOD, true );
            deployRunnerNG( clazz, deployProject, repoListBuilder.toString() );
        }
    }

    /**
     * @param repo remote Maven repository
     * @return repository mirror, or null if it doesn't have one
     */
    private Mirror getRepositoryMirror( ArtifactRepository repo )
    {
        Mirror mirror = m_settings.getMirrorOf( repo.getId() );
        if( null == mirror )
        {
            mirror = m_settings.getMirrorOf( "*" );
        }
        return mirror;
    }

    /**
     * Attempt to resolve each provisioned bundle, and warn about any we can't find
     * 
     * @return list of bundles to be deployed (as Maven dependencies)
     */
    private List resolveProvisionedBundles()
    {
        List dependencies = new ArrayList();
        for( Iterator i = m_bundleIds.iterator(); i.hasNext(); )
        {
            String id = (String) i.next();
            String[] fields = id.split( ":" );

            Dependency dep = new Dependency();
            dep.setGroupId( fields[0] );
            dep.setArtifactId( fields[1] );
            dep.setVersion( fields[2] );
            dep.setScope( "provided" );

            dependencies.add( dep );
        }
        return dependencies;
    }

    /**
     * Create new POM (based on the root POM) which lists the deployed bundles as dependencies
     * 
     * @param bundles list of bundles to be deployed
     * @return deployment project
     * @throws MojoExecutionException
     */
    private MavenProject createDeploymentProject( List bundles )
        throws MojoExecutionException
    {
        MavenProject rootProject = (MavenProject) m_reactorProjects.get( 0 );
        MavenProject deployProject;

        if( null == rootProject.getFile() )
        {
            deployProject = new MavenProject();
            deployProject.setGroupId( "examples" );
            deployProject.setArtifactId( "pax-provision" );
            deployProject.setVersion( "1.0-SNAPSHOT" );
        }
        else
        {
            deployProject = new MavenProject( rootProject );
        }

        String internalId = PomUtils.getCompoundId( deployProject.getGroupId(), deployProject.getArtifactId() );
        deployProject.setGroupId( internalId + ".build" );
        deployProject.setArtifactId( "deployment" );

        // remove unnecessary cruft
        deployProject.setPackaging( "pom" );
        deployProject.getModel().setModules( null );
        deployProject.getModel().setDependencies( bundles );
        deployProject.getModel().setPluginRepositories( null );
        deployProject.getModel().setReporting( null );
        deployProject.setBuild( null );

        File deployFile = new File( deployProject.getBasedir(), "runner/pom.xml" );

        deployFile.getParentFile().mkdirs();
        deployProject.setFile( deployFile );

        try
        {
            Writer writer = StreamFactory.newXmlWriter( deployFile );
            deployProject.writeModel( writer );
            IOUtil.close( writer );
        }
        catch( IOException e )
        {
            throw new MojoExecutionException( "Unable to write deployment POM " + deployFile );
        }

        return deployProject;
    }

    /**
     * Install deployment POM in the local Maven repository
     * 
     * @param project deployment project
     * @throws MojoExecutionException
     */
    private void installDeploymentPom( MavenProject project )
        throws MojoExecutionException
    {
        String groupId = project.getGroupId();
        String artifactId = project.getArtifactId();
        String version = project.getVersion();

        Artifact pomArtifact = m_factory.createProjectArtifact( groupId, artifactId, version );

        try
        {
            m_installer.install( project.getFile(), pomArtifact, m_localRepo );
        }
        catch( ArtifactInstallationException e )
        {
            throw new MojoExecutionException( "Unable to install deployment POM " + pomArtifact );
        }
    }

    /**
     * Dynamically resolve and load the Pax-Runner class
     * 
     * @param groupId pax-runner group id
     * @param artifactId pax-runner artifact id
     * @param mainClass main pax-runner classname
     * @param needClassifier classify pax-runner artifact according to current JVM
     * @return main pax-runner class
     * @throws MojoExecutionException
     */
    private Class loadRunnerClass( String groupId, String artifactId, String mainClass, boolean needClassifier )
        throws MojoExecutionException
    {
        String jdk = null;
        if( needClassifier && System.getProperty( "java.class.version" ).compareTo( "49.0" ) < 0 )
        {
            jdk = "jdk14";
        }

        Artifact jarArtifact = m_factory.createArtifactWithClassifier( groupId, artifactId, runner, "jar", jdk );
        if( !PomUtils.downloadFile( jarArtifact, m_resolver, m_remoteRepos, m_localRepo ) )
        {
            throw new MojoExecutionException( "Unable to find Pax-Runner " + jarArtifact );
        }

        URL[] urls = new URL[1];
        try
        {
            urls[0] = jarArtifact.getFile().toURI().toURL();
        }
        catch( MalformedURLException e )
        {
            throw new MojoExecutionException( "Bad Jar location " + jarArtifact.getFile() );
        }

        try
        {
            ClassLoader loader = new URLClassLoader( urls );
            Thread.currentThread().setContextClassLoader( loader );
            return Class.forName( mainClass, true, loader );
        }
        catch( ClassNotFoundException e )
        {
            throw new MojoExecutionException( "Unable to find entry point " + mainClass + " in " + urls[0] );
        }
    }

    /**
     * Deploy bundles using the 'classic' Pax-Runner
     * 
     * @param mainClass main Pax-Runner class
     * @param project deployment project
     * @param repositories comma separated list of Maven repositories
     * @throws MojoExecutionException
     */
    private void deployRunnerClassic( Class mainClass, MavenProject project, String repositories )
        throws MojoExecutionException
    {
        String workDir = project.getBasedir() + "/work";

        String cachedPomName = project.getArtifactId() + '_' + project.getVersion() + ".pom";
        File cachedPomFile = new File( workDir + "/lib/" + cachedPomName );

        // Force reload of pom
        cachedPomFile.delete();

        if( PomUtils.isEmpty( framework ) )
        {
            framework = "felix";
        }

        String[] deployAppCmds =
        {
            "--dir=" + workDir, "--no-md5", "--platform=" + framework, "--profile=default",
            "--repository=" + repositories, "--localRepository=" + m_localRepo.getBasedir(), project.getGroupId(),
            project.getArtifactId(), project.getVersion()
        };

        invokePaxRunner( mainClass, deployAppCmds );
    }

    /**
     * Deploy bundles using the new Pax-Runner codebase
     * 
     * @param mainClass main Pax-Runner class
     * @param project deployment project
     * @param repositories comma separated list of Maven repositories
     * @throws MojoExecutionException
     */
    private void deployRunnerNG( Class mainClass, MavenProject project, String repositories )
        throws MojoExecutionException
    {
        List deployAppCmds = new ArrayList();

        // only apply if explicitly configured
        if( PomUtils.isNotEmpty( framework ) )
        {
            deployAppCmds.add( "--platform=" + framework );
        }
        if( PomUtils.isNotEmpty( deploy ) )
        {
            deployAppCmds.add( "--profiles=" + deploy );
        }

        if( null != args )
        {
            try
            {
                new URL( args ); // check syntax
            }
            catch( MalformedURLException e )
            {
                // assume it's a local filename
                File argsFile = new File( args );
                args = argsFile.toURI().toString();
            }

            // custom Pax-Runner arguments file
            deployAppCmds.add( "--args=" + args );
        }

        // apply project provision settings before defaults
        deployAppCmds.addAll( Arrays.asList( provision ) );

        // main deployment pom with project bundles as dependencies
        deployAppCmds.add( project.getFile().getAbsolutePath() );

        // use project settings to access remote/local repositories
        deployAppCmds.add( "--localRepository=" + m_localRepo.getBasedir() );
        deployAppCmds.add( "--repositories=" + repositories );
        deployAppCmds.add( "--overwriteUserBundles" );

        getLog().debug( "Starting Pax-Runner " + runner + " with: " + deployAppCmds.toString() );
        invokePaxRunner( mainClass, (String[]) deployAppCmds.toArray( new String[deployAppCmds.size()] ) );
    }

    /**
     * Invoke Pax-Runner in-process
     * 
     * @param mainClass main Pax-Runner class
     * @param commands array of command-line options
     * @throws MojoExecutionException
     */
    private void invokePaxRunner( Class mainClass, String[] commands )
        throws MojoExecutionException
    {
        Class[] paramTypes = new Class[1];
        paramTypes[0] = String[].class;

        Object[] paramValues = new Object[1];
        paramValues[0] = commands;

        try
        {
            Method entryPoint = mainClass.getMethod( "main", paramTypes );
            entryPoint.invoke( null, paramValues );
        }
        catch( NoSuchMethodException e )
        {
            throw new MojoExecutionException( "Unable to find Pax-Runner entry point" );
        }
        catch( IllegalAccessException e )
        {
            throw new MojoExecutionException( "Unable to access Pax-Runner entry point" );
        }
        catch( InvocationTargetException e )
        {
            throw new MojoExecutionException( "Pax-Runner exception", e );
        }
    }

    /**
     * @return backup OPS4J remote repository
     */
    ArtifactRepository getOps4jRepository()
    {
        ArtifactRepositoryPolicy noSnapshots = new ArtifactRepositoryPolicy( false, null, null );
        ArtifactRepositoryPolicy releases = new ArtifactRepositoryPolicy( true,
            ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, null );

        return m_repoFactory.createArtifactRepository( "ops4j-repository", "http://repository.ops4j.org/maven2",
            m_defaultLayout, noSnapshots, releases );
    }
}
