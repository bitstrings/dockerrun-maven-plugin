package org.bitstrings.maven.plugins.dockerrun;

import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.bitstrings.maven.plugins.dockerrun.AbstractDockerRunMojo.Verbosity;

import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;

import lombok.Getter;
import lombok.Setter;

public class Remove
    extends AbstractDockerRunOperation
{
    @Getter
    @Setter
    private boolean ignoreContainerNotFound = true;

    @Getter
    @Setter
    private int stopContainerTimeout = 30;

    @Getter
    @Setter
    private boolean stopBeforeContainerRemove = true;

    @Getter
    @Setter
    private boolean forceRemove = false;

    @Getter
    @Setter
    private boolean removeVolumesOnContainerRemove = true;

    @Override
    public void exec(AbstractDockerRunMojo dockerRunMojo)
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
                exec(dockerRunMojo, id);
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

    public void exec(AbstractDockerRunMojo dockerRunMojo, String id)
        throws NotFoundException
    {
        Run run = MavenUtils.getRequestDockerRunData(dockerRunMojo.getMavenSession().getRequest()).get(id);

        if (dockerRunMojo.getVerbosity() == Verbosity.HIGH)
        {
            dockerRunMojo.getLog().info("Removing container " + id + run.getAliasNameLogAppend() + ".");
        }

        try
        {
            dockerRunMojo.getDockerHelper().stopContainer(id, stopContainerTimeout);
        }
        catch (NotModifiedException e)
        {
            if (dockerRunMojo.getVerbosity() == Verbosity.HIGH)
            {
                dockerRunMojo.getLog().info("Container " + id + run.getAliasNameLogAppend() + " already removed.");
            }

            return;
        }

        if (!dockerRunMojo.isQuiet())
        {
            dockerRunMojo.getLog().info("Container " + id + run.getAliasNameLogAppend() + " removed.");
        }
    }
}
