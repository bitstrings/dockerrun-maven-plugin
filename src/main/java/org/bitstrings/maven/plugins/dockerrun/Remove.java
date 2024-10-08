package org.bitstrings.maven.plugins.dockerrun;

import org.apache.maven.plugin.MojoExecutionException;

import com.github.dockerjava.api.model.Container;

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

    public Remove(AbstractDockerRunMojo mojo)
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
                "Removing container " + id
                    + "(" + getMojo().getDockerHelper().getContainerStateLogAppend(container) + ")"
                    + run.getAliasNameLogAppend()
                    + "."
            );
        }

        getMojo().getDockerHelper().removeContainer(
            id,
            stopContainerTimeout,
            removeVolumesOnContainerRemove,
            false,
            forceRemove,
            false
        );

        if (!getMojo().isQuiet())
        {
            getMojo().getLog().info("Container " + id + run.getAliasNameLogAppend() + " removed.");
        }
    }
}
