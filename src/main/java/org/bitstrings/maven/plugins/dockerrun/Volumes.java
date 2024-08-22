package org.bitstrings.maven.plugins.dockerrun;

public class Volumes
{
    private VolumeBind volumeBind;

    public void setBind(VolumeBind volumeBind)
    {
        this.volumeBind = volumeBind;
    }

    public VolumeBind getBind()
    {
        return volumeBind;
    }
}
