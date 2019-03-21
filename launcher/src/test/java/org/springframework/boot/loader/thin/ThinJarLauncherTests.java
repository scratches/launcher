/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.loader.thin;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ThinJarLauncherTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();
	
	@Rule
	public OutputCapture output = new OutputCapture();

	@Test
	public void dryrun() throws Exception {
		String[] args = new String[] { "--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/basic", "--debug" };
		ThinJarLauncher.main(args);
	}

	@Test
	public void classpath() throws Exception {
		String[] args = new String[] { "--thin.classpath",
				"--thin.archive=src/test/resources/apps/basic"};
		ThinJarLauncher.main(args);
		assertThat(output.toString()).contains("spring-web-4.3.3.RELEASE.jar" + File.pathSeparator);
	}

	@Test
	public void compute() throws Exception {
		String[] args = new String[] { "--thin.classpath=properties",
				"--thin.archive=src/test/resources/apps/basic"};
		ThinJarLauncher.main(args);
		assertThat(output.toString()).contains("dependencies.spring-web=org.springframework:spring-web:4.3.3.RELEASE\n");
	}

	@Test
	public void thinRoot() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.dryrun=true",
				"--thin.root=target/thin/test",
				"--thin.location=file:src/test/resources/apps/db/META-INF",
				"--thin.archive=src/test/resources/apps", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void overrideLocalRepository() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/basic",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void missingThinRootWithPom() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		expected.expect(RuntimeException.class);
		expected.expectMessage("spring-web:jar:X.X.X");
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/missing",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void missingThinRootWithoutPom() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		expected.expect(RuntimeException.class);
		expected.expectMessage("nonexistent:jar:0.0.1");
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/missingthin", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void overrideExistingRepository() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/repositories", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void overrideSnapshotVersion() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		FileSystemUtils.deleteRecursively(new File(
				"target/thin/test/repository/org/springframework/boot/spring-boot-starter-parent"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/snapshots",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void declareSnapshotRepository() throws Exception {
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/snapshots-with-repos",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void settingsReadFromRoot() throws Exception {
		DependencyResolver.close();
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("src/test/resources/settings/local").getAbsolutePath());
		try {
			FileSystemUtils.deleteRecursively(new File(
					"target/thin/test/repository/org/springframework/spring-core"));
			String[] args = new String[] { "--thin.dryrun=true",
					"--thin.archive=src/test/resources/apps/snapshots-with-repos",
					"--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void commandLineOffline() throws Exception {
		settingsReadFromRoot();
		DependencyResolver.close();
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.offline=true",
				"--thin.archive=src/test/resources/apps/snapshots-with-repos",
				"--debug" };
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		expected.expect(RuntimeException.class);
		expected.expectMessage("spring-core");
		ThinJarLauncher.main(args);
	}

	@Test
	public void settingsOffline() throws Exception {
		settingsReadFromRoot();
		DependencyResolver.close();
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("src/test/resources/settings/offline").getAbsolutePath());
		try {
			String[] args = new String[] { "--thin.root=target/thin/test",
					"--thin.dryrun=true",
					"--thin.archive=src/test/resources/apps/snapshots-with-repos",
					"--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void repositorySettingsMissing() throws Exception {
		DependencyResolver.close();
		FileSystemUtils.deleteRecursively(
				new File("target/thin/test/repository/com/github/jitpack"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/jitpack",
				"--debug" };
		expected.expect(RuntimeException.class);
		expected.expectMessage("maven-simple:jar:1.1");
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository/com/github/jitpack/maven-simple")
				.exists()).isFalse();
	}

	@Test
	public void repositorySettingsPresent() throws Exception {
		DependencyResolver.close();
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("src/test/resources/settings/profile").getAbsolutePath());
		try {
			FileSystemUtils.deleteRecursively(
					new File("target/thin/test/repository/com/github/jitpack"));
			String[] args = new String[] { "--thin.root=target/thin/test",
					"--thin.dryrun=true",
					"--thin.archive=src/test/resources/apps/jitpack", "--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/com/github/jitpack/maven-simple")
				.exists()).isTrue();
	}

}
