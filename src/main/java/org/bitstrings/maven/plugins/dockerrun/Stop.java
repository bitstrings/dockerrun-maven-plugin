package org.bitstrings.maven.plugins.dockerrun;

import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;

import lombok.Getter;
import lombok.Setter;

public class Stop
    extends DockerRunOperation
{
    @Getter
    @Setter
    private boolean ignoreContainerNotFound = true;

    @Getter
    @Setter
    private int stopContainerTimeout = 30;

    public void stop(AbstractDockerRunMojo dockerRunMojo)
        throws MojoExecutionException
    {
        Set<String> dockerRunIds =
            MavenUtils.getRequestDockerRunData(dockerRunMojo.getMavenSession().getRequest()).keySet();

        for (String id : getContainersIds(dockerRunMojo))
        {
            if (!dockerRunIds.contains(id))
            {
                if (ignoreContainerNotFound)
                {
                    continue;
                }

                throw new MojoExecutionException("Container id " + id + " not found for this build.");
            }

            try
            {
                stop(dockerRunMojo, id);
            }
            catch (NotFoundException e)
            {
                if (!ignoreContainerNotFound)
                {
                    throw new MojoExecutionException("Container id " + id + " not found.");
                }
            }
        }
    }

    public void stop(AbstractDockerRunMojo dockerRunMojo, String id)
    {
        Run run = MavenUtils.getRequestDockerRunData(dockerRunMojo.getMavenSession().getRequest()).get(id);

        if (!dockerRunMojo.isQuiet())
        {
            dockerRunMojo.getLog().info("Stopping container " + id + run.getAliasNameLogAppend() + ".");
        }

        try
        {
            dockerRunMojo.getDockerHelper().stopContainer(id, stopContainerTimeout);
        }
        catch (NotModifiedException e)
        {
            if (!dockerRunMojo.isQuiet())
            {
                dockerRunMojo.getLog().info("Container " + id + run.getAliasNameLogAppend() + " already stopped.");
            }

            return;
        }

        if (!dockerRunMojo.isQuiet())
        {
            dockerRunMojo.getLog().info("Container " + id + run.getAliasNameLogAppend() + " stopped.");
        }
    }
}
