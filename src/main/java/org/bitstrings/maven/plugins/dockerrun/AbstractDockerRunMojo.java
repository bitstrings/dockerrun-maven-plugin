package org.bitstrings.maven.plugins.dockerrun;

import java.time.Duration;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import lombok.Getter;

public abstract class AbstractDockerRunMojo
    extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true)
    @Getter
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    @Getter
    private MavenSession mavenSession;

    @Parameter(defaultValue = "NORMAL", property = "dockerrun.verbosity")
    @Getter
    private Verbosity verbosity;

    @Parameter(defaultValue = "dockerrun.")
    @Getter
    private String propertyPrefix;

    @Parameter(defaultValue = "false", property = "dockerrun.skip")
    @Getter
    private boolean skip;

    @Getter
    private DockerClientConfig dockerClientConfig;

    @Getter
    private DockerHttpClient dockerHttpClient;

    private DockerClient dockerClient;

    private DockerHelper dockerHelper;

    public enum Verbosity
    {
        QUIET, NORMAL, HIGH
    }

    public DockerClient getDockerClient()
    {
        if (dockerClient == null)
        {
            dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

            dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.getDockerHost())
                .sslConfig(dockerClientConfig.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(60))
                .maxConnections(250)
                .responseTimeout(Duration.ofSeconds(60))
                .build();

            dockerClient = DockerClientBuilder.getInstance(dockerClientConfig)
                .withDockerHttpClient(dockerHttpClient)
                .build();
        }

        return dockerClient;
    }

    public DockerHelper getDockerHelper()
    {
        if (dockerHelper == null)
        {
            dockerHelper = new DockerHelper(getDockerClient());
        }

        return dockerHelper;
    }

    public boolean isQuiet()
    {
        return verbosity == Verbosity.QUIET;
    }

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if (skip)
        {
            getLog().info("Skipping dockerrun execution.");

            return;
        }

        dockerrunExec();
    }

    public abstract void dockerrunExec()
        throws MojoExecutionException, MojoFailureException;
}
