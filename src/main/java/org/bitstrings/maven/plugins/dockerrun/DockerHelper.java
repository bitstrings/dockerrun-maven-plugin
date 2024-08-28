package org.bitstrings.maven.plugins.dockerrun;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DockerHelper
{
    @Getter
    private final DockerClient dockerClient;

    public void stopContainer(String id, int stopTimeOut)
        throws NotFoundException, NotModifiedException
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
        throws NotFoundException, NotModifiedException
    {
        if (stopFirst)
        {
            stopContainer(id, stopTimeOut);
        }

        dockerClient.removeContainerCmd(id).withForce(forceKill).withRemoveVolumes(removeVolumes).exec();
    }

    public String[] extractImageParts(String image)
    {
        String[] imageParts = StringUtils.split(image, ":", 2);

        return new String[] { imageParts[0], ArrayUtils.get(imageParts, 1, "latest") };
    }
}
