package org.bitstrings.maven.plugins.dockerrun;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunMavenProperties.PropertyType.ID;
import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.ALWAYS;
import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.IF_NOT_PRESENT;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;

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
    extends AbstractDockerRunMojo
{
    @Parameter
    @Getter
    private List<Run> runs;

    @Parameter
    @Getter
    private boolean removeVolumesOnContainerRemove = true;

    @Parameter(defaultValue = "true")
    @Getter
    private boolean removeContainersOnBuildComplete;

    @Parameter(defaultValue = "true")
    @Getter
    private boolean removeContainersOnVmShutdown;

    @Getter
    private DockerRunMavenProperties dockerRunMavenProperties;

    @Getter
    private String imageRepository;

    @Getter
    private String imageTag;

    @Getter
    private HostConfig hostConfig;

    @Getter
    private LinkedMap<String, Run> runById = new LinkedMap<>();

    private Map<String, Object> data;

    private static final PullImageResultCallback PULL_IMAGE_RESULT_CALLBACK_NOOP = new PullImageResultCallback();

    private static final ResultCallback.Adapter<?> RESULT_CALLBACK_NOOP = new ResultCallback.Adapter<>();

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        data = getMavenSession().getRequest().getData();

        if (removeContainersOnVmShutdown)
        {
            Runtime.getRuntime().addShutdownHook(
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            removeContainers();
                        }
                        catch (MojoExecutionException e)
                        {
                        }
                    }
                }
            );
        }

        MavenUtils.proxyExecutionListener(
            getMavenSession(),
            true,
            new AbstractExecutionListener()
            {
                @Override
                public void sessionEnded(ExecutionEvent event)
                {
                    if (removeContainersOnBuildComplete)
                    {
                        try
                        {
                            removeContainers();
                        }
                        catch (MojoExecutionException e)
                        {
                        }
                    }
                }
            }
        );

        dockerRunMavenProperties = new DockerRunMavenProperties(getMavenProject(), getPropertyPrefix());

        LinkedMap<String, Run> dataRunById = MavenUtils.getRequestDockerRunData(getMavenSession().getRequest());

        if (dataRunById == null)
        {
            dataRunById = new LinkedMap<>();

            data.put(DockerRunMojo.class.getName(), dataRunById);
        }

        for (Run run : runs)
        {
            String user = run.getUser();

            if (run.isSetUserToCurrent())
            {
                OSProcess osProcess = new SystemInfo().getOperatingSystem().getCurrentProcess();

                user = osProcess.getUserID() + ":" + osProcess.getGroupID();
            }

            extractImageParts(run);

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

            CreateContainerCmd containerCmd = getDockerClient().createContainerCmd(run.getImage())
                .withHostConfig(hostConfig)
                .withTty(run.isTty());

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

                        getDockerClient().pullImageCmd(imageRepository)
                            .withTag(imageTag)
                            .exec(isQuiet()
                                ? pullImageResultCallbackNoop()
                                : new PullImageResultCallback()
                                {
                                    @Override
                                    public void onStart(Closeable stream)
                                    {
                                        getLog().info("Pulling image " + run.getImage() + " started.");

                                        super.onStart(stream);
                                    }

                                    @Override
                                    public void onComplete()
                                    {
                                        getLog().info("Image " + run.getImage() + " pull completed.");

                                        super.onComplete();
                                    }

                                    @Override
                                    public void onError(Throwable throwable)
                                    {
                                        getLog().error("Error pulling image " + run.getImage() + ".", throwable);

                                        super.onError(throwable);
                                    }
                                }
                            )
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

            MavenUtils.getRequestDockerRunData(getMavenSession().getRequest());

            dataRunById.put(createContainerResponse.getId(), run);

            if (run.getAlias() != null)
            {
                dockerRunMavenProperties.setDockerRunProperty(run.getAlias(), ID, createContainerResponse.getId());
            }

            getDockerClient().startContainerCmd(createContainerResponse.getId()).exec();

            if (!isQuiet())
            {
                getLog().info(
                    "Container " + createContainerResponse.getId() + run.getAliasNameLogAppend() + " started."
                );
            }

            try
            {
                getDockerClient().logContainerCmd(createContainerResponse.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTailAll()
                    .withFollowStream(true)
                    .exec(isQuiet()
                        ? resultCallbackAdapterNoop()
                        : new ResultCallback.Adapter<>()
                        {
                            @Override
                            public void onNext(Frame object)
                            {
                                StreamType streamType = object.getStreamType();

                                if (
                                    (run.isEchoStdOut() && (streamType == StreamType.STDOUT))
                                        || (run.isEchoStdErr() && (streamType == StreamType.STDERR))
                                )
                                {
                                    System.out.print(new String(object.getPayload()));
                                }
                            }
                        }
                    )
                    .awaitCompletion();
            }
            catch (InterruptedException e)
            {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }

    protected void removeContainers()
        throws MojoExecutionException
    {
        Remove remove = new Remove();

        remove.setIds(runById.asList());
        remove.setRemoveVolumesOnContainerRemove(removeVolumesOnContainerRemove);
        remove.setStopBeforeContainerRemove(true);
        remove.setStopContainerTimeout(120);
        remove.setIgnoreContainerNotFound(true);

        remove.remove(this);
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
        String[] imageParts = getDockerHelper().extractImageParts(run.getImage());

        imageRepository = imageParts[0];
        imageTag = imageParts[1];
    }

    public static <T> ResultCallback.Adapter<T> resultCallbackAdapterNoop()
    {
        return (ResultCallback.Adapter<T>) RESULT_CALLBACK_NOOP;
    }

    public static PullImageResultCallback pullImageResultCallbackNoop()
    {
        return PULL_IMAGE_RESULT_CALLBACK_NOOP;
    }
}
