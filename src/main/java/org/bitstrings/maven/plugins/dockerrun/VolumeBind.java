package org.bitstrings.maven.plugins.dockerrun;

import java.util.ArrayList;
import java.util.List;

public class VolumeBind
{
    private final List<String> volumes = new ArrayList<>();

    public void addVolume(String volume)
    {
        volumes.add(volume);
    }

    public List<String> getVolumes()
    {
        return volumes;
    }
}
