/*
 *=============================================================================
 *                      THIS FILE AND ITS CONTENTS ARE THE
 *                    EXCLUSIVE AND CONFIDENTIAL PROPERTY OF
 *
 *                          EXPRETIO TECHNOLOGIES, INC.
 *
 * Any unauthorized use of this file or any of its parts, including, but not
 * limited to, viewing, editing, copying, compiling, and distributing, is
 * strictly prohibited.
 *
 * Copyright ExPretio Technologies, Inc., 2024. All rights reserved.
 *=============================================================================
 */
package org.bitstrings.maven.plugins.dockerrun;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.maven.plugin.MojoExecutionException;

import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;

import lombok.Getter;
import lombok.Setter;

public class DockerRunOperation
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

    public Set<String> getContainersIds(AbstractDockerRunMojo dockerRunMojo)
        throws MojoExecutionException
    {
        DockerRunMavenProperties dockerRunMavenProperties =
            new DockerRunMavenProperties(dockerRunMojo.getMavenProject(), dockerRunMojo.getPropertyPrefix());

        Set<String> toStopIds = new HashSet<>();

        if (ids != null)
        {
            toStopIds.addAll(ids);
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

                toStopIds.add(id);
            }
        }

        if (ObjectUtils.isNotEmpty(namePatterns))
        {
            try (ListContainersCmd listContainersCmd = dockerRunMojo.getDockerClient().listContainersCmd())
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
                            toStopIds.add(container.getId());
                        }
                    }
                }
            }
        }

        MavenUtils.getRequestDockerRunData(dockerRunMojo.getMavenSession().getRequest());

        if (toStopIds.isEmpty())
        {
            toStopIds.addAll(MavenUtils.getRequestDockerRunData(dockerRunMojo.getMavenSession().getRequest()).keySet());
        }

        return toStopIds;
    }
}
