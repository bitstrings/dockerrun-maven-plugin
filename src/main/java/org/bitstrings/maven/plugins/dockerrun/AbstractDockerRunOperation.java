package org.bitstrings.maven.plugins.dockerrun;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.bitstrings.maven.plugins.dockerrun.util.MavenUtils;

import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;

import lombok.Getter;
import lombok.Setter;

public abstract class AbstractDockerRunOperation
{
    @Getter
    @Setter
    private List<String> ids;

    @Getter
    @Setter
    private List<String> aliases;

    @Getter
    @Setter
    private List<Pattern> namePatterns;

    @Getter
    private final AbstractDockerRunMojo mojo;

    public AbstractDockerRunOperation(AbstractDockerRunMojo mojo)
    {
        this.mojo = mojo;
    }

    public Set<String> getContainersIds()
        throws MojoExecutionException
    {
        DockerRunMavenProperties dockerRunMavenProperties =
            new DockerRunMavenProperties(mojo.getMavenProject(), mojo.getPropertyPrefix());

        Set<String> containersIds = new HashSet<>();

        if (ids != null)
        {
            containersIds.addAll(ids);
        }

        if (ObjectUtils.isNotEmpty(aliases))
        {
            for (String alias : aliases)
            {
                String id =
                    dockerRunMavenProperties.getDockerRunProperty(alias, DockerRunMavenProperties.PropertyType.ID);

                if (id == null)
                {
                    throw new MojoExecutionException("Container alias " + alias + " not found.");
                }

                containersIds.add(id);
            }
        }

        if (ObjectUtils.isNotEmpty(namePatterns))
        {
            try (ListContainersCmd listContainersCmd = mojo.getDockerClient().listContainersCmd())
            {
                List<Container> containers = listContainersCmd.withShowAll(true).exec();

                for (Container container : containers)
                {
                    for (Pattern namePattern : namePatterns)
                    {
                        if (
                            Arrays.stream(container.getNames())
                                .anyMatch(containerName -> namePattern.matcher(containerName).find())
                        )
                        {
                            containersIds.add(container.getId());
                        }
                    }
                }
            }
        }

        MavenUtils.getRequestDockerRunData(mojo.getMavenSession().getRequest());

        if (containersIds.isEmpty())
        {
            containersIds.addAll(MavenUtils.getRequestDockerRunData(mojo.getMavenSession()).keySet());
        }

        return containersIds;
    }

    public abstract void exec()
        throws MojoExecutionException;
}
