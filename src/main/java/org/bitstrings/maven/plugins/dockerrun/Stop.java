package org.bitstrings.maven.plugins.dockerrun;

import org.apache.maven.plugin.MojoExecutionException;

import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;

import lombok.Getter;
import lombok.Setter;

public class Stop
    extends AbstractDockerRunOperation
{
    @Getter
    @Setter
    private boolean ignoreContainerNotFound = true;

    @Getter
    @Setter
    private int stopContainerTimeout = Integer.MAX_VALUE;

    public Stop(AbstractDockerRunMojo mojo)
    {
        super(mojo);
    }

    @Override
    public void exec()
        throws MojoExecutionException
    {
        for (String id : getContainersIds())
        {
            exec(id);
        }
    }

    public void exec(String id)
        throws MojoExecutionException
    {
        Run run = MavenUtils.getRequestDockerRunData(getMojo().getMavenSession().getRequest()).get(id);

        if (run == null)
        {
            if (!ignoreContainerNotFound)
            {
                throw new MojoExecutionException("Container id " + id + " not found for this build.");
            }

            return;
        }

        Container container = getMojo().getDockerHelper().getContainer(id);

        if (container == null)
        {
            if (!ignoreContainerNotFound)
            {
                throw new MojoExecutionException("Container " + id + run.getAliasNameLogAppend() + " not found.");
            }

            return;
        }

        if (!getMojo().isQuiet())
        {
            getMojo().getLog().info(
                "Stopping container " + id
                    + "(" + getMojo().getDockerHelper().getContainerStateLogAppend(container) + ")"
                    + run.getAliasNameLogAppend()
                    + "."
            );
        }

        try
        {
            getMojo().getDockerClient().stopContainerCmd(id).withTimeout(stopContainerTimeout).exec();
        }
        catch (NotModifiedException e)
        {
            throw new MojoExecutionException("Container " + id + run.getAliasNameLogAppend() + " stop request failed.");
        }

        if (!getMojo().isQuiet())
        {
            getMojo().getLog().info("Container " + id + run.getAliasNameLogAppend() + " stopped.");
        }
    }
}
