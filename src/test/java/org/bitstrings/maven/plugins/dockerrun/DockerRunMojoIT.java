/*
 *=============================================================================
 *                      THIS FILE AND ITS CONTENTS ARE THE
 *                    EXCLUSIVE AND CONFIDENTIAL PROPERTY OF
 *
 *                          EXPRETIO TECHNOLOGIES, INC.
 *
 * Any unauthorized use of this file or any of its parts, including, but not
 * limited to, viewing, editing, copying, compiling, and distributing, is
 * strictly prohibited.
 *
 * Copyright ExPretio Technologies, Inc., 2024. All rights reserved.
 *=============================================================================
 */
package org.bitstrings.maven.plugins.dockerrun;

import static com.soebes.itf.jupiter.extension.MavenCLIOptions.NO_TRANSFER_PROGRESS;

import com.soebes.itf.extension.assertj.MavenITAssertions;
import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;

@MavenJupiterExtension
@MavenOption(NO_TRANSFER_PROGRESS)
public class DockerRunMojoIT
{
    @MavenTest
    @MavenGoal("verify")
    void run(MavenExecutionResult result)
    {
        MavenITAssertions.assertThat(result).isSuccessful();
    }

    @MavenTest
    @MavenGoal("verify")
    void run_sub_module(MavenExecutionResult result)
    {
        MavenITAssertions.assertThat(result).isSuccessful();
    }
}
