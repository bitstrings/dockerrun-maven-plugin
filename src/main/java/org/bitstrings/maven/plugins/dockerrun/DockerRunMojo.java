package org.bitstrings.maven.plugins.dockerrun;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunProperties.PropertyType.ID;
import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.ALWAYS;
import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.IF_NOT_PRESENT;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import lombok.Getter;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;

@Mojo(
    name = "run",
    defaultPhase = PRE_INTEGRATION_TEST,
    threadSafe = true,
    requiresProject = true,
    requiresOnline = false
)
public class DockerRunMojo
    extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true)
    @Getter
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    @Getter
    private MavenSession mavenSession;

    @Parameter(defaultValue = "false")
    @Getter
    private boolean quiet;

    @Parameter(defaultValue = "dockerrun.")
    @Getter
    private String propertyPrefix;

    @Parameter(defaultValue = "true")
    @Getter
    private boolean removeContainersOnBuildComplete;

    @Parameter(defaultValue = "true")
    @Getter
    private boolean removeContainersOnVmShutdown;

    @Parameter
    @Getter
    private List<Run> runs;

    @Getter
    private DockerRunProperties dockerRunProperties;

    @Getter
    private String imageName;

    @Getter
    private String imageTag;

    @Getter
    private DockerClientConfig dockerClientConfig;

    @Getter
    private DockerHttpClient dockerHttpClient;

    @Getter
    private DockerClient dockerClient;

    @Getter
    private HostConfig hostConfig;

    @Getter
    private HashMap<String, Run> runById = new HashMap<>();

    @Getter
    private String aliasNameLogAppend;

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if (removeContainersOnVmShutdown)
        {
            Runtime.getRuntime().addShutdownHook(
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        removeContainers();
                    }
                }
            );
        }

        MavenUtils.proxyExecutionListener(
            mavenSession,
            true,
            new AbstractExecutionListener()
            {
                @Override
                public void sessionEnded(ExecutionEvent event)
                {
                    if (removeContainersOnBuildComplete)
                    {
                        removeContainers();
                    }
                }
            }
        );

        dockerRunProperties = new DockerRunProperties(mavenProject, propertyPrefix);

        for (Run run : runs)
        {
            String user = run.getUser();

            if (run.isSetUserToCurrent())
            {
                OSProcess osProcess = new SystemInfo().getOperatingSystem().getCurrentProcess();

                user = osProcess.getUserID() + ":" + osProcess.getGroupID();
            }

            extractImageParts(run);

            dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

            dockerHttpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.getDockerHost())
                .sslConfig(dockerClientConfig.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(60))
                .maxConnections(100)
                .responseTimeout(Duration.ofSeconds(60))
                .build();

            dockerClient = DockerClientBuilder.getInstance(dockerClientConfig)
                .withDockerHttpClient(dockerHttpClient)
                .build();

            hostConfig = new HostConfig()
                .withInit(run.isInit())
                .withAutoRemove(run.isAutoRemove());

            if (run.getDns() != null)
            {
                hostConfig.withDns(run.getDns());
            }

            if (run.getDnsSearch() != null)
            {
                hostConfig.withDnsSearch(run.getDnsSearch());
            }

            if (run.getVolumes() != null)
            {
                hostConfig.withBinds(getBindsFromVolumesParam(run.getVolumes()));
            }

            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(run.getImage())
                .withHostConfig(hostConfig)
                .withTty(run.isTty());

            if (quiet)
            {
                aliasNameLogAppend = "";
            }
            else
            {
                StringBuilder aliasLogBuilder = new StringBuilder();

                if (run.getAlias() != null)
                {
                    aliasLogBuilder.append(" alias ").append(run.getAlias());
                }

                if (run.getName() != null)
                {
                    aliasLogBuilder.append(" named ").append(run.getName());
                }

                aliasNameLogAppend = aliasLogBuilder.toString();
            }

            if (run.getName() != null)
            {
                containerCmd.withName(run.getName());
            }

            if (run.getArgs() != null)
            {
                containerCmd.withCmd(run.getArgs());
            }

            if (run.getEnv() != null)
            {
                containerCmd.withEnv(
                    run.getEnv().entrySet().stream()
                        .map(prop -> prop.getKey() + "=" + prop.getValue())
                        .collect(Collectors.toList())
                );
            }

            if (run.getDomainName() != null)
            {
                containerCmd.withDomainName(run.getDomainName());
            }

            if (user != null)
            {
                containerCmd.withUser(user);
            }

            CreateContainerResponse createContainerResponse = null;

            boolean retry = true;
            boolean pullImage = (run.getImagePullPolicy() == ALWAYS);

            do
            {
                if (pullImage)
                {
                    try
                    {
                        retry = false;

                        dockerClient.pullImageCmd(imageName)
                            .withTag(imageTag)
                            .exec(new PullImageResultCallback())
                            .awaitCompletion();

                        pullImage = false;
                    }
                    catch (InterruptedException e)
                    {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                }

                try
                {
                    createContainerResponse = containerCmd.exec();
                }
                catch (NotFoundException e)
                {
                    pullImage = (run.getImagePullPolicy() == IF_NOT_PRESENT);

                    if (pullImage)
                    {
                        throw new MojoExecutionException(e.getMessage(), e);
                    }
                }
            }
            while (retry);

            runById.put(createContainerResponse.getId(), run);

            if (run.getAlias() != null)
            {
                dockerRunProperties.setDockerRunProperty(run.getAlias(), ID, createContainerResponse.getId());
            }

            dockerClient.startContainerCmd(createContainerResponse.getId()).exec();

            try
            {
                dockerClient.logContainerCmd(createContainerResponse.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<>()
                    {
                        @Override
                        public void onNext(Frame object)
                        {
                            if (!quiet)
                            {
                                StreamType streamType = object.getStreamType();

                                if (
                                    (run.isEchoStdOut() && (streamType == StreamType.STDOUT))
                                        || (run.isEchoStdErr() && (streamType == StreamType.STDERR))
                                )
                                {
                                    System.out.println(new String(object.getPayload()));
                                }
                            }
                        }
                    }).awaitCompletion();
            }
            catch (InterruptedException e)
            {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }

    protected void removeContainers()
    {
        List<Container> containers =
            dockerClient.listContainersCmd().withIdFilter(runById.keySet()).withShowAll(true).exec();

        containers.forEach(container -> removeContainer(container));
    }

    protected void removeContainer(Container container)
    {
        if (!quiet)
        {
            getLog().info("Removing container " + container.getId() + aliasNameLogAppend + ".");
        }

        try
        {
            dockerClient.stopContainerCmd(container.getId()).withTimeout(30).exec();
        }
        catch (Exception e)
        {
        }

        try
        {
            dockerClient.removeContainerCmd(container.getId()).withForce(true).withRemoveVolumes(true).exec();
        }
        catch (Exception e)
        {
        }

        if (!quiet)
        {
            getLog().info("Removed container " + container.getId() + aliasNameLogAppend + ".");
        }
    }

    protected List<Bind> getBindsFromVolumesParam(Volumes volumes)
        throws MojoFailureException
    {
        List<Bind> binds = new ArrayList<>();

        for (String volumeBind : volumes.getBind().getVolumes())
        {
            String[] paths = StringUtils.split(volumeBind, ":", 2);

            if (paths.length != 2)
            {
                throw new MojoFailureException(
                    "Volume bind " + volumeBind + " should contain exactly two paths [source]:[destination]."
                );
            }

            binds.add(new Bind(paths[0], new Volume(paths[1])));
        }

        return binds;
    }

    protected void extractImageParts(Run run)
    {
        String[] imageParts = StringUtils.split(run.getImage(), ':');

        imageName = ArrayUtils.get(imageParts, 0);
        imageTag = ArrayUtils.get(imageParts, 1, "latest");
    }
}
