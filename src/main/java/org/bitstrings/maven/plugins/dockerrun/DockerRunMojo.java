package org.bitstrings.maven.plugins.dockerrun;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunMavenProperties.PropertyType.ID;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunMavenProperties.PropertyType.STATE;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunMavenProperties.PropertyType.STATUS_CODE;
import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.ALWAYS;
import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.IF_NOT_PRESENT;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.bitstrings.maven.plugins.dockerrun.util.Data;
import org.bitstrings.maven.plugins.dockerrun.util.MavenUtils;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Event;
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

    private static final PullImageResultCallback PULL_IMAGE_RESULT_CALLBACK_NOOP = new PullImageResultCallback();

    private static final ResultCallback.Adapter<?> RESULT_CALLBACK_NOOP = new ResultCallback.Adapter<>();

    @Override
    public void dockerrunExec()
        throws MojoExecutionException, MojoFailureException
    {
        synchronized (DockerRunMojo.class)
        {
            if (removeContainersOnVmShutdown)
            {
                Boolean vmShutdownHooked = MavenUtils.getRequestData(getMavenSession(), "vmShutdownHook");

                if (BooleanUtils.isNotTrue(vmShutdownHooked))
                {
                    Runtime.getRuntime().addShutdownHook(
                        new Thread(() -> {
                            try
                            {
                                if (!isQuiet())
                                {
                                    getLog().info("VM shutdown container clean up.");
                                }

                                removeContainers();
                            }
                            catch (MojoExecutionException e)
                            {
                            }
                        })
                    );
                }

                MavenUtils.setRequestData(getMavenSession(), "vmShutdownHook", true);
            }

            Boolean sessionHooked = MavenUtils.getRequestData(getMavenSession(), "sessionHook");

            if (BooleanUtils.isNotTrue(sessionHooked))
            {
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
                                if (!isQuiet())
                                {
                                    getLog().info("End build container clean up.");
                                }

                                try
                                {
                                    removeContainers();
                                }
                                catch (MojoExecutionException e)
                                {
                                }

                                if (!isQuiet())
                                {
                                    showSummary();
                                }
                            }
                        }
                    }
                );

                MavenUtils.setRequestData(getMavenSession(), "sessionHook", true);
            }
        }

        dockerRunMavenProperties = new DockerRunMavenProperties(getMavenProject(), getPropertyPrefix());

        Data<Run> dataRunById = MavenUtils.getRequestDockerRunData(getMavenSession());

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

            String containerId = createContainerResponse.getId();

            runById.put(containerId, run);

            MavenUtils.getRequestDockerRunData(getMavenSession());

            dataRunById.put(containerId, run);

            if (run.getAlias() != null)
            {
                dockerRunMavenProperties.setDockerRunProperty(run.getAlias(), ID, containerId);
            }

            if (!isQuiet())
            {
                if (Verbosity.HIGH == getVerbosity())
                {
                    StringBuilder info = new StringBuilder()
                        .append("Container ")
                        .append(containerId)
                        .append(" ")
                        .append(run.getAliasNameLogAppend())
                        .append(System.lineSeparator())
                        .append("[Environment]")
                        .append(System.lineSeparator());

                    if (run.getEnv() != null)
                    {
                        run.getEnv().entrySet().forEach(envEntry ->
                            info
                                .append(envEntry.getKey())
                                .append("=")
                                .append(envEntry.getValue())
                                .append(System.lineSeparator())
                        );
                    }

                    info.append("[Volumes]")
                        .append(System.lineSeparator());

                    if (run.getVolumes() != null)
                    {
                        run.getVolumes().getBind().getVolumes().forEach(
                            volume -> {
                                info
                                    .append(volume.getSource())
                                    .append(":")
                                    .append(volume.getDestination());

                                if (volume.getOptions() != null)
                                {
                                    info.append(":");
                                    info.append(volume.getOptions());
                                }

                                info.append(" - create source: ")
                                    .append(volume.getCreateSource())
                                    .append(System.lineSeparator());
                            }
                        );
                    }

                    getLog().info(info.toString());
                }
            }

            try (
                ResultCallback.Adapter<Event> eventResultCallback =
                    getDockerClient().eventsCmd().withContainerFilter(containerId).withEventFilter("destroy").exec(
                        new ResultCallback.Adapter<>()
                        {
                            @Override
                            public void onNext(Event event)
                            {
                                try
                                {
                                    close();
                                }
                                catch (IOException e)
                                {
                                }
                            }
                        }
                    );
                WaitContainerResultCallback waitContainerResultCallback =
                    getDockerClient().waitContainerCmd(containerId).start()
            )
            {
                eventResultCallback.awaitStarted();

                getDockerClient().startContainerCmd(containerId).exec();

                if (!isQuiet())
                {
                    getLog().info("Container " + containerId + run.getAliasNameLogAppend() + " started.");
                }

                if (!run.isDetach())
                {
                    getDockerClient().logContainerCmd(containerId)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withFollowStream(true)
                        .withTailAll()
                        .exec(isQuiet()
                            ? resultCallbackAdapterNoop()
                            : new ResultCallback.Adapter<>()
                            {
                                @Override
                                public void onNext(Frame frame)
                                {
                                    StreamType streamType = frame.getStreamType();

                                    if (
                                        (run.isEchoStdOut() && (streamType == StreamType.STDOUT))
                                            || (run.isEchoStdErr() && (streamType == StreamType.STDERR))
                                    )
                                    {
                                        System.out.print(new String(frame.getPayload()));
                                    }
                                }
                            }).awaitCompletion(run.getCompletionTimeout(), TimeUnit.SECONDS);

                    Integer statusCode =
                        waitContainerResultCallback.awaitStatusCode(120, TimeUnit.SECONDS);

                    dockerRunMavenProperties.setDockerRunProperty(
                        run.getAlias(),
                        STATUS_CODE,
                        String.valueOf(statusCode)
                    );

                    dockerRunMavenProperties.setDockerRunProperty(run.getAlias(), STATE, "completed");

                    if (!isQuiet())
                    {
                        getLog().info(
                            "Container " + containerId + run.getAliasNameLogAppend()
                                + " returned status code " + statusCode
                                + "."
                        );
                    }

                    if (run.isAutoRemove())
                    {
                        try
                        {
                            eventResultCallback.awaitCompletion();
                        }
                        catch (InterruptedException e)
                        {
                        }
                    }

                    if (run.isFailOnError() && !Objects.equals(0, statusCode))
                    {
                        throw new MojoFailureException(
                            "Container " + containerId + run.getAliasNameLogAppend()
                                + " returned status code " + statusCode
                                + "."
                        );
                    }
                }
            }
            catch (MojoFailureException e)
            {
                throw e;
            }
            catch (Exception e)
            {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }

    private void showSummary()
    {
        List<String> ids = MavenUtils.getRequestDockerRunData(getMavenSession()).asList();

        if (CollectionUtils.isEmpty(ids))
        {
            return;
        }

        List<Container> containers =
            getDockerClient().listContainersCmd().withIdFilter(ids).withShowAll(true).exec();

        StringBuilder summary =
            new StringBuilder()
                .append("Containers started: ")
                .append(runById.size())
                .append(" / remaining: ")
                .append(containers.size());

        for (Container container : containers)
        {
            summary
                .append("\n")
                .append("[container] ")
                .append("id: ")
                .append(container.getId())
                .append(", state: ")
                .append(container.getState())
                .append(", image: ")
                .append(container.getImage());
        }

        getLog().info(summary.toString());
    }

    protected void removeContainers()
        throws MojoExecutionException
    {
        List<String> ids = MavenUtils.getRequestDockerRunData(getMavenSession()).asList();

        if (CollectionUtils.isEmpty(ids))
        {
            return;
        }

        Remove remove = new Remove(this);

        remove.setIds(ids);
        remove.setRemoveVolumesOnContainerRemove(removeVolumesOnContainerRemove);
        remove.setStopBeforeContainerRemove(true);
        remove.setStopContainerTimeout(120);
        remove.setIgnoreContainerNotFound(true);
        remove.setForceRemove(true);

        remove.exec();
    }

    protected List<Bind> getBindsFromVolumesParam(Volumes volumes)
        throws MojoFailureException
    {
        List<Bind> binds = new ArrayList<>();

        for (VolumeBind.Mount volumeBind : volumes.getBind().getVolumes())
        {
            if (StringUtils.isEmpty(volumeBind.getSource()) || StringUtils.isEmpty(volumeBind.getDestination()))
            {
                throw new MojoFailureException(
                    "Volume bind " + volumeBind + " should contain exactly two paths [source]:[destination]."
                );
            }

            binds.add(new Bind(volumeBind.getSource(), new Volume(volumeBind.getDestination())));

            try
            {
                switch (volumeBind.getCreateSource())
                {
                    case DIR:
                        Files.createDirectories(Path.of(volumeBind.getSource()));
                        getLog().info("Directory " + volumeBind.getSource() + " created.");
                        break;
                    case FILE:
                        Files.createFile(Path.of(volumeBind.getSource()));
                        getLog().info("File " + volumeBind.getSource() + " created.");
                        break;
                }
            }
            catch (IOException e)
            {
                throw new MojoFailureException(e.getMessage(), e);
            }
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
        @SuppressWarnings("unchecked")
        ResultCallback.Adapter<T> resultCallbackNoop = (ResultCallback.Adapter<T>) RESULT_CALLBACK_NOOP;

        return resultCallbackNoop;
    }

    public static PullImageResultCallback pullImageResultCallbackNoop()
    {
        return PULL_IMAGE_RESULT_CALLBACK_NOOP;
    }
}
