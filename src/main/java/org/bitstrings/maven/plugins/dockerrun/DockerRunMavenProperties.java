package org.bitstrings.maven.plugins.dockerrun;

import org.apache.maven.project.MavenProject;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class DockerRunMavenProperties
{
    @NonNull
    @Getter
    private final MavenProject mavenProject;

    @NonNull
    @Getter
    private final String propertyPrefix;

    @RequiredArgsConstructor
    public enum PropertyType
    {
        ID("id");

        @NonNull
        @Getter
        private final String name;
    }

    public String getPropertyName(String alias, PropertyType type)
    {
        return propertyPrefix + alias + "." + type.name;
    }

    public String getDockerRunProperty(String alias, PropertyType type)
    {
        return mavenProject.getProperties().getProperty(getPropertyName(alias, type));
    }

    public void setDockerRunProperty(String alias, PropertyType type, String value)
    {
        mavenProject.getProperties().setProperty(getPropertyName(alias, type), value);
    }
}
