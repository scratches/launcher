/*
 * Copyright 2012-2015 the original author or authors.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.boot.loader.tools.CustomLoaderLayout;
import org.springframework.boot.loader.tools.Layout;
import org.springframework.boot.loader.tools.LibraryScope;
import org.springframework.boot.loader.tools.LoaderClassesWriter;

/**
 * @author Dave Syer
 *
 */
public class ThinLayout implements Layout, CustomLoaderLayout {

	@Override
	public String getLauncherClassName() {
		return "org.springframework.boot.loader.wrapper.ThinJarWrapper";
	}

	@Override
	public String getLibraryDestination(String libraryName, LibraryScope scope) {
		return null;
	}

	@Override
	public String getClassesLocation() {
		return "";
	}

	@Override
	public boolean isExecutable() {
		return true;
	}

	@Override
	public void writeLoadedClasses(LoaderClassesWriter writer) throws IOException {
		writer.writeLoaderClasses("META-INF/loader/spring-boot-thin-wrapper.jar");
		writer.writeEntry("lib/.empty", new ByteArrayInputStream(new byte[0]));
	}

}
