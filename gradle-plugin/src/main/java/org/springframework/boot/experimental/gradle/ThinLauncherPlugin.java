/*
 * Copyright 2012-2016 the original author or authors.
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

package org.springframework.boot.experimental.gradle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.jvm.Jvm;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Gradle {@link Plugin} for Spring Boot's thin launcher.
 * <p>
 * If the Java plugin is applied to the project, some tasks are added to the project.
 * <ul>
 * <li>"thinResolve": runs the project jar and download its dependencies. If you have more
 * than one jar task then an additional task is created for each one named
 * "thinResolve[JarTaskName]" (where "JarTaskName" is the capitalized name of the jar
 * task).</li>
 * <li>"thinResolvePrepare": copies the project jar to the "root" directory preparing for
 * the resolution. The same naming convention applies to multiple jar tasks.</li>
 * <li>"thinProperties": calculates thin.properties and puts them in the main build
 * output.</li>
 * <li>"thinPom": runs automatically if you apply the Maven plugin. Generates a pom.xml
 * and puts it in the main build output.</li>
 * </ul>
 *
 * @author Andy Wilkinson
 */
public class ThinLauncherPlugin implements Plugin<Project> {


	@Override
	public void apply(final Project project) {
		final Task thinJar = createCopyTask(project);
		final Task pomTask = createPomTask(project);
		createPropertiesTask(project);

		pomTask.dependsOn(thinJar);

		project.getTasks().withType(Jar.class, new Action<Jar>() {

			@Override
			public void execute(Jar jar) {
				String name = jar.getName();
				if (!name.equals("thinJar")) {
					String suffix = "jar".equals(name) || "bootJar".equals(name) ? "" : StringUtils.capitalize(name);

					if (project.getTasksByName("thinResolve" + suffix, true).isEmpty()) {
						Task thinResolvePrepare = createResolvePrepareTask(project, suffix);
						Task thinResolveTask = createResolveTask(project, suffix);

						thinResolvePrepare.dependsOn(thinJar);
						thinResolveTask.dependsOn(thinResolvePrepare);
					}
				}
			}

		});

		project.afterEvaluate(new Action<Project>() {

			@Override
			public void execute(final Project project) {
				Jar bootJar;
				if (project.getTasks().findByName("bootJar") != null)
					bootJar = (Jar) project.getTasks().getByName("bootJar");
				else
					bootJar = (Jar) project.getTasks().getByName("jar");

				thinJar.dependsOn(bootJar);

				project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME)
						.dependsOn(thinJar);
			}
		});
	}

	private Task createCopyTask(final Project project) {
		return project.getTasks().create("thinJar", Jar.class).doFirst(new Action<Task>() {
			@Override
			public void execute(Task task) {
				Jar thinJar = (Jar) task;
				Jar bootJar;
				if (project.getTasks().findByName("bootJar") != null)
					bootJar = (Jar) project.getTasks().getByName("bootJar");
				else
					bootJar = (Jar) project.getTasks().getByName("jar");

				Map<String, Object> attrs = new HashMap<>();
				attrs.put("Main-Class", "org.springframework.boot.loader.wrapper.ThinJarWrapper");
				attrs.put("Start-Class", getMainClass(bootJar));
				thinJar.getManifest().attributes(attrs);

				SourceSetContainer sources = (SourceSetContainer) project.getProperties().get("sourceSets");

				thinJar.from(project.zipTree(new Callable<File>() {
					@Override
					public File call() throws Exception {
						File file = File.createTempFile("tmp", ".jar",
								project.getBuildDir());
						file.delete();
						Files.copy(getClass().getClassLoader()
										.getResourceAsStream(
												"META-INF/loader/spring-boot-thin-wrapper.jar"),
								file.toPath());
						return file;
					}
				}));
				thinJar.from((Object) sources.findByName("main")
						.getRuntimeClasspath().filter(new Spec<File>() {
							@Override
							public boolean isSatisfiedBy(File element) {
								return element.isDirectory();
							}
						}).getFiles().toArray(new File[0]));
			}

			private Object getMainClass(Jar bootJar) {
				Object result = bootJar.getManifest().getAttributes().get("Start-Class");
				if (result != null) {
					return result;
				}
				return bootJar.getManifest().getAttributes().get("Main-Class");
			}
		});
	}

	private Task createPomTask(final Project project) {
		return project.getTasks().create("thinPom", PomTask.class).doFirst(new Action<Task>() {
			@Override
			public void execute(Task task) {
				PomTask thin = (PomTask) task;
				SourceSetContainer sourceSets = project.getConvention()
						.getPlugin(JavaPluginConvention.class).getSourceSets();
				File resourcesDir = sourceSets.getByName("main").getOutput()
						.getResourcesDir();
				thin.setOutput(new File(resourcesDir, "META-INF/maven/"
						+ project.getGroup() + "/" + project.getName()));
			}
		});
	}

	private Task createPropertiesTask(final Project project) {
		return project.getTasks().create("thinProperties", PropertiesTask.class).doFirst(new Action<Task>() {
			@Override
			public void execute(Task task) {
				PropertiesTask libPropertiesTask = (PropertiesTask) task;
				configureLibPropertiesTask(libPropertiesTask, project);
			}
		});
	}

	private void configureLibPropertiesTask(PropertiesTask thin, Project project) {
		thin.setConfiguration(findRuntimeClasspath(project));
		SourceSetContainer sourceSets = project.getConvention()
				.getPlugin(JavaPluginConvention.class).getSourceSets();
		File resourcesDir = sourceSets.getByName("main").getOutput().getResourcesDir();
		thin.setOutput(new File(resourcesDir, "META-INF"));
	}

	private Configuration findRuntimeClasspath(Project project) {
		Configuration configuration = project.getConfigurations()
				.getByName("runtimeClasspath");
		if (configuration == null) {
			configuration = project.getConfigurations()
					.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME);
		}
		return configuration;
	}

	private Task createResolvePrepareTask(final Project project, String suffix) {
		return project.getTasks().create("thinResolvePrepare" + suffix, Copy.class, new Action<Copy>() {
			@Override
			public void execute(Copy copy) {
				Jar thinJar = (Jar) project.getTasks().getByName("thinJar");
				copy.from(thinJar.getOutputs().getFiles());
				copy.into(new File(project.getBuildDir(), "thin/root"));
			}
		}).doLast(new Action<Task>() {
			@Override
			public void execute(Task task) {
				try {
					File wrapper = new File(project.getBuildDir(),
							"thin/spring-boot-thin-wrapper.jar");
					if (!wrapper.exists()) {
						wrapper.getParentFile().mkdirs();
						Files.copy(
								getClass().getClassLoader().getResourceAsStream(
										"META-INF/loader/spring-boot-thin-wrapper.jar"),
								wrapper.toPath());
					}
				}
				catch (IOException e) {
					throw new RuntimeException("Cannot copy thin jar wrapper", e);
				}
			}
		});
	}

	private Task createResolveTask(final Project project, final String suffix) {
		return project.getTasks().create("thinResolve" + suffix, Exec.class).doFirst(new Action<Task>() {
			@Override
			@SuppressWarnings("unchecked")
			public void execute(Task task) {
				Exec exec = (Exec) task;

				final Jar thinJar;
				if (project.getTasks().findByName("thinJar" + suffix) != null) {
					thinJar = (Jar) project.getTasks().getByName("thinJar" + suffix);
				}
				else {
					thinJar = (Jar) project.getTasks().getByName("jar");
				}
				final String prepareTask = "thinResolvePrepare" + suffix;
				Copy copy = (Copy) project.getTasks().getByName(prepareTask);
				exec.setWorkingDir(
						copy.getOutputs().getFiles().getSingleFile());
				exec.setCommandLine(Jvm.current().getJavaExecutable());
				List<String> args = new ArrayList<>(Arrays.asList(
						"-Dthin.root=.", "-Dthin.dryrun", "-jar",
						"../spring-boot-thin-wrapper.jar"));
				args.add(1, "-Dthin.archive=" + thinJar.getArchiveName());
				String thinRepo = getThinRepo(project);
				if (thinRepo != null) {
					args.add(1, "-Dthin.repo=" + thinRepo);
				}
				exec.args(args);
			}
		});
	}

	private String getThinRepo(Project project) {
		if (System.getProperty("thin.repo") != null) {
			return System.getProperty("thin.repo");
		}
		if (System.getenv("THIN_REPO") != null) {
			return System.getProperty("THIN_REPO");
		}
		Map<String, ?> properties = project.getProperties();
		if (properties != null && properties.get("thin.repo") != null) {
			return (String) properties.get("thin.repo");
		}
		return null;
	}
}
