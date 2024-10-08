package org.bitstrings.maven.plugins.dockerrun;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import lombok.Getter;

@Mojo(
    name = "stop",
    defaultPhase = PRE_INTEGRATION_TEST,
    threadSafe = true,
    requiresProject = true,
    requiresOnline = false
)
public class DockerRunStopMojo
    extends AbstractDockerRunMojo
{
    @Parameter(defaultValue = "${project}", readonly = true)
    @Getter
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    @Getter
    private MavenSession mavenSession;

    @Parameter
    @Getter
    private List<Stop> stops = emptyList();

    @Parameter(defaultValue = "false")
    @Getter
    private boolean allowExternalIds;

    @Override
    public void dockerrunExec()
        throws MojoExecutionException, MojoFailureException
    {
        if (stops.isEmpty())
        {
            stops = singletonList(new Stop(this));
        }

        for (Stop stop : stops)
        {
            stop.exec();
        }
    }
}
