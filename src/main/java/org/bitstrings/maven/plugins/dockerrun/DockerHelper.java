package org.bitstrings.maven.plugins.dockerrun;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerHelper
{
    @Getter
    private final DockerClient dockerClient;

    public void stopContainer(String id, int stopTimeOut)
        throws NotFoundException
    {
        dockerClient.stopContainerCmd(id).withTimeout(stopTimeOut).exec();
    }

    public void removeContainer(
        String id,
        int stopTimeOut,
        boolean removeVolumes,
        boolean stopFirst,
        boolean forceKill,
        boolean ignoreAlreadyStopped
    )
        throws NotFoundException
    {
        if (stopFirst)
        {
            try
            {
                stopContainer(id, stopTimeOut);
            }
            catch (NotModifiedException | ConflictException e)
            {
            }
        }

        try
        {
            dockerClient.removeContainerCmd(id).withForce(forceKill).withRemoveVolumes(removeVolumes).exec();
        }
        catch (ConflictException e)
        {
            // REVIEW: not the best
        }
    }

    public String[] extractImageParts(String image)
    {
        String[] imageParts = StringUtils.split(image, ":", 2);

        return new String[] { imageParts[0], ArrayUtils.get(imageParts, 1, "latest") };
    }

    public List<Container> getContainers(Collection<String> ids)
    {
        return dockerClient.listContainersCmd().withIdFilter(ids).withShowAll(true).exec();
    }

    public Container getContainer(String id)
        throws NotFoundException
    {
        return getContainers(Collections.singleton(id)).stream().findFirst().orElse(null);
    }

    public String getContainerStateLogAppend(String id)
    {
        return getContainerStateLogAppend(getContainer(id));
    }

    public String getContainerStateLogAppend(Container container)
    {
        return "state: " + (container == null ? "n/a" : container.getState());
    }
}
