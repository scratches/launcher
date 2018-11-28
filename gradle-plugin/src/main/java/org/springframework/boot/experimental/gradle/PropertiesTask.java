/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.experimental.gradle;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import org.springframework.util.StringUtils;

/**
 * Task to generate a thin.properties file including all runtime dependencies. It saves
 * some time on startup to have the dependencies pre-computed, but it makes it less
 * flexible, so this task is optional. If you enable it, you probably want to make it a
 * dependency of the main java plugin task so that it runs automatically on build.
 *
 * @author Andy Wilkinson
 * @author Dave Syer
 *
 */
public class PropertiesTask extends DefaultTask {

	@Input
	private Configuration configuration;

	@OutputFile
	private File output;

	@Input
	private String name = "thin";

	@Input
	private String profile;

	@TaskAction
	public void generate() {
		Properties properties = getThinProperties(configuration);
		try {
			String filename = getFileName();
			output.mkdirs();
			properties.store(new FileOutputStream(new File(output, filename)),
					"Generated by thin gradle plugin");
		}
		catch (Exception e) {
			throw new TaskExecutionException(this, e);
		}
	}

	protected Properties getThinProperties(Configuration configuration) {
		Properties properties = new Properties();
		// TODO: add computed flag to task and offer option not to compute transitives
		properties.setProperty("computed", "true");
		if (configuration != null) {
			for (ResolvedArtifact artifact : configuration.getResolvedConfiguration()
					.getResolvedArtifacts()) {
				properties.setProperty("dependencies." + key(artifact, properties),
						coordinates(artifact, true));
			}
		}
		return properties;
	}

	private String key(ResolvedArtifact dependency, Properties props) {
		String key = dependency.getModuleVersion().getId().getName();
		if (!StringUtils.isEmpty(dependency.getClassifier())) {
			key = key + "." + dependency.getClassifier();
		}
        int counter = 1;
        do {
            if (props.get(key) == null) {
                break;
            }
            key = key + "." + counter++;
        }while(true);
        return key;
	}

	private String coordinates(ResolvedArtifact artifact, boolean withVersion) {
		// group:artifact:extension:classifier:version
		String classifier = artifact.getClassifier();
		String extension = artifact.getType();
		ModuleVersionIdentifier artifactId = artifact.getModuleVersion().getId();
		return artifactId.getGroup() + ":" + artifactId.getName()
				+ (StringUtils.hasText(extension)
						&& (!"jar".equals(extension) || StringUtils.hasText(classifier))
								? ":" + extension
								: "")
				+ (StringUtils.hasText(classifier) ? ":" + classifier : "")
				+ (withVersion ? ":" + artifactId.getVersion() : "");
	}

	private String getFileName() {
		String profile = StringUtils.hasText(this.profile) ? "-" + this.profile : "";
		return this.name + profile + ".properties";
	}

	/**
	 * Sets the {@link Configuration} that will be used to resolve the dependencies that
	 * are listed in {@code lib.proeprties}.
	 *
	 * @param configuration the configuration
	 */
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Sets the location to which the properties file will be written. Defaults to
	 * META-INF in the compiled classes output (build/resources/main/META-INF).
	 *
	 * @param output the output location
	 */
	public void setOutput(File output) {
		this.output = output;
	}

	/**
	 * The name of the thin properties file (defaults to "thin").
	 */
	@Override
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * The profile to use for the generated properties (default null)
	 *
	 * @param profile the value of the profile
	 */
	public void setProfile(String profile) {
		this.profile = profile;
	}
}
