package org.bitstrings.maven.plugins.dockerrun;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

public class VolumeBind
{
    private final List<Mount> volumes = new ArrayList<>();

    @NoArgsConstructor(force = true)
    @RequiredArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static final class Mount
    {
        private MountType type = MountType.BIND;

        private final String source;

        private final String destination;

        private final String options;

        private FileType createSource = FileType.NEVER;

        public enum FileType
        {
            NEVER, DIR, FILE
        }

        public enum MountType
        {
            BIND, VOLUME, TMPFS
        }
    }

    public void addVolume(String volume)
    {
        String[] paths = StringUtils.split(volume, ":", 3);

        volumes.add(
            new Mount(
                ArrayUtils.get(paths, 0, ""),
                ArrayUtils.get(paths, 1, ""),
                ArrayUtils.get(paths, 2, "")
            )
        );
    }

    public void addMount(Mount volume)
    {
        volumes.add(volume);
    }

    public List<Mount> getVolumes()
    {
        return volumes;
    }
}
