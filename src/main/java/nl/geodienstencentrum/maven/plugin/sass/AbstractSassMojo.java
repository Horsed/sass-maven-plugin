/*
 * Copyright 2014-2015 Mark Prins, GeoDienstenCentrum.
 * Copyright 2010-2014 Jasig.
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.geodienstencentrum.maven.plugin.sass;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import nl.geodienstencentrum.maven.plugin.sass.compiler.CompilerCallback;

import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.shared.utils.io.IOUtil;

/**
 * Base for batching Sass Mojos.
 *
 */
public abstract class AbstractSassMojo extends AbstractMojo {

	/**
	 * Build directory for the plugin.
	 *
	 * @since 2.0
	 */
	@Parameter(defaultValue = "${project.build.directory}")
	protected File buildDirectory;

	/**
	 * Where to put the compiled CSS files.
	 *
	 * @since 2.0
	 */
	@Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}/css")
	protected File destination;

	/**
	 * Fail the build if errors occur during compilation of sass/scss templates.
	 *
	 * @since 2.0
	 */
	@Parameter(defaultValue = "true")
	protected boolean failOnError;

	/**
	 * Defines options for Sass::Plugin.options. See <a href=
	 * "http://sass-lang.com/docs/yardoc/file.SASS_REFERENCE.html#options">Sass
	 * options</a> If the value is a string it must by quoted in the maven
	 * configuration: &lt;cache_location&gt;'/tmp/sass'&lt;/cache_location&gt;
	 *
	 * If no options are set the default configuration set is used which is:
	 *
	 * <pre>
	 * &lt;unix_newlines&gt;true&lt;/unix_newlines&gt;
	 * &lt;cache&gt;true&lt;/cache&gt;
	 * &lt;always_update&gt;true&lt;/always_update&gt;
	 * &lt;cache_location&gt;${project.build.directory}/sass_cache&lt;/cache_location&gt;
	 * &lt;style&gt;:expanded&lt;/style&gt;
	 * </pre>
	 *
	 * @since 2.0
	 */
	@Parameter
	private Map<String, String> sassOptions = new HashMap<String, String>(
	        ImmutableMap.of("unix_newlines", "true", "cache", "true",
	                "always_update", "true", "style", ":expanded"));

	/**
	 * Sources for compilation with their destination directory containing Sass
	 * files. Allows for multiple resource sources and destinations. If
	 * specified it precludes the direct specification of
	 * sassSourceDirectory/relativeOutputDirectory/destination parameters.
	 *
	 * Example configuration:
	 *
	 * <pre>
	 *      &lt;resources&gt;
	 *        &lt;resource&gt;
	 *          &lt;source&gt;
	 *              &lt;directory&gt;${basedir}/src/main/webapp&lt;/directory&gt;
	 *              &lt;includes&gt;
	 *                  &lt;include&gt;**&#x0002F;*.scss&lt;/include&gt;
	 *              &lt;/includes&gt;
	 *          &lt;/source&gt;
	 *          &lt;relativeOutputDirectory&gt;..&lt;/relativeOutputDirectory&gt;
	 *          &lt;destination&gt;${project.build.directory}/${project.build.finalName}
	 *              &lt;/destination&gt;
	 *        &lt;/resource&gt;
	 *      &lt;/resources&gt;
	 * </pre>
	 *
	 * @since 2.0
	 */
	@Parameter
	private List<Resource> resources = Collections.emptyList();

	/**
	 * Defines paths where jruby will look for gems. E.g. a maven build could
	 * download gems into ${project.build.directory}/rubygems and a gemPath
	 * pointed to this directory. Finally, individual gems can be loaded via the
	 * &lt;gems&gt; configuration.
	 *
	 * @since 2.0
	 */
	@Parameter(defaultValue = "${project.build.directory}/rubygems")
	private String[] gemPaths = new String[0];

	/**
	 * Defines gems to be loaded before Sass/Compass. This is useful to add gems
	 * with custom Sass functions or stylesheets. Gems that hook into Compass
	 * are transparently added to Sass' load_path.
	 *
	 * @since 2.0
	 */
	@Parameter
	private String[] gems = new String[0];

	/**
	 * Enable the use of Compass style library mixins.
	 *
	 * @since 2.0
	 * 
	 * @deprecated Compass support will be removed in 3.0
	 */
	@Deprecated
	@Parameter(defaultValue = "false")
	private boolean useCompass;

	/**
	 * Enable the use of Bourbon style library mixins.
	 *
	 * @since 2.11
	 */
	@Parameter(defaultValue = "false")
	private boolean useBourbon;

	/**
	 * specify an optional compass configuration file, eg. {@code compass.rb}
	 *
	 * @since 2.5
	 * 
	 * @deprecated Compass support will be removed in 3.0
	 */
	@Deprecated
	@Parameter(property = "compassConfigFile")
	private File compassConfigFile;

	/**
	 * Directory containing Sass files, defaults to the Maven
	 * sources directory (${basedir}/src/main/sass).
	 *
	 * @since 2.0
	 */
	@Parameter(defaultValue = "${basedir}/src/main/sass", property = "sassSourceDirectory")
	private File sassSourceDirectory;

	/**
	 * Defines files in the source directories to include.
	 *
	 * Defaults to: {@code **&#x0002F;*.scss}
	 *
	 * @since 2.0
	 */
	@Parameter
	private String[] includes = new String[] { "**/*.scss" };

	/**
	 * Defines which of the included files in the source directories to exclude
	 * (none by default).
	 *
	 * @since 2.0
	 */
	@Parameter
	private String[] excludes;

	/**
	 * Defines an additional path section when calculating the destination for
	 * the SCSS file. Allows, for example
	 * "/media/skins/universality/coal/scss/portal.scss" to end up at
	 * "/media/skins/universality/coal/portal.css" by specifying ".."
	 *
	 * @since 2.0
	 */
	@Parameter(defaultValue = "..")
	private String relativeOutputDirectory;

	/**
	 * Ruby version to use. Valid versions depend on the currently embedded
	 * JRuby runtime. For JRuby 1.7.x this is {@code 1.8}, {@code 1.9}
	 * <em>(default)</em> or {@code 2.0} <em>(experimental)</em>.
	 *
	 * @deprecated ignored, since JRuby 9000 / v2.9 this is no longer supported
	 * @since 2.1
	 */
	@Deprecated
	@Parameter(defaultValue = "2.1")
	private Float rubyVersion;

	/**
	 * skip execution.
	 *
	 * @since 2.10
	 */
	@Parameter(defaultValue = "false")
	private boolean skip;

	/**
	 * Execute the Sass Compilation Ruby Script.
	 *
	 * @param sassScript
	 *            the sass script
	 * @throws MojoExecutionException
	 *             the mojo execution exception
	 * @throws MojoFailureException
	 *             the mojo failure exception
	 */
	protected void executeSassScript(final String sassScript)
            throws MojoExecutionException, MojoFailureException {
		if (this.skip) {
			return;
		}

		final Log log = this.getLog();
		System.setProperty("org.jruby.embed.localcontext.scope", "threadsafe");
		System.setProperty("jruby.compile.fastest", "true");
		System.setProperty("jruby.jit.codeCache", buildDirectory + File.pathSeparator + "jruby.codeCache");
		System.setProperty("jruby.native.enabled", "false");
		System.setProperty("jruby.aot.loadClasses", "true");

		log.debug("Execute Sass Ruby script:\n\n" + sassScript + "\n\n");
		final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
		final ScriptEngine jruby = scriptEngineManager.getEngineByName("jruby");
		try {
			final CompilerCallback compilerCallback = new CompilerCallback(log);
			jruby.getBindings(ScriptContext.ENGINE_SCOPE).put(
			        "compiler_callback", compilerCallback);
			jruby.eval(sassScript);
			if (this.failOnError && compilerCallback.hadError()) {
				throw new MojoFailureException(
				   "Sass compilation encountered errors (see above for details).");
			}
		} catch (final ScriptException e) {
			throw new MojoExecutionException(
			        "Failed to execute Sass ruby script:\n" + sassScript, e);
		}
		log.debug("\n");
	}

	/**
	 * Builds the basic sass script.
	 *
	 * @param sassScript
	 *            the sass script
	 * @throws MojoExecutionException
	 *             the mojo execution exception
	 */
	protected void buildBasicSassScript(final StringBuilder sassScript)
	        throws MojoExecutionException {
		final Log log = this.getLog();

		sassScript.append("require 'rubygems'\n");
		if (this.gemPaths.length > 0) {
			sassScript.append("env = { 'GEM_PATH' => [\n");
			for (final String gemPath : this.gemPaths) {
				sassScript.append("    '").append(gemPath).append("',\n");
			}

			final String gemPath = System.getenv("GEM_PATH");
			if (gemPath != null) {
				for (final String p : gemPath.split(File.pathSeparator)) {
					sassScript.append("    '").append(p).append("',\n");
				}
			}
			/* remove trailing comma+\n */
			sassScript.setLength(sassScript.length() - 2);
			sassScript.append("\n] }\n");
			sassScript.append("Gem.paths = env\n");
		}

		for (final String gem : this.gems) {
			sassScript.append("require '").append(gem).append("'\n");
		}

		sassScript.append("require 'sass/plugin'\n");
		sassScript.append("require 'java'\n");

		if (this.useCompass) {
			log.info("Running with Compass enabled.");
			log.warn("Compass support is deprecated, it will be removed in version 3.0, see https://github.com/GeoDienstenCentrum/sass-maven-plugin/issues/77");
			sassScript.append("require 'compass'\n");
			sassScript.append("require 'compass/exec'\n");
			sassScript.append("require 'compass/core'\n");
			sassScript.append("require 'compass/import-once'\n");
			if (compassConfigFile != null) {
				sassScript.append("Compass.add_project_configuration '")
				        .append(compassConfigFile.getAbsolutePath())
				        .append("'\n");
			} else {
				sassScript.append("Compass.add_project_configuration \n");
			}
			this.sassOptions.put("load_paths",
			        "Compass.configuration.sass_load_paths");
		}

		// Get all template locations from resources and set option
		// 'template_location' and
		// 'css_location' (to override default "./public/stylesheets/sass",
		// "./public/stylesheets")
		// remaining locations are added later with 'add_template_location'
		final Iterator<Entry<String, String>> templateLocations = this
		        .getTemplateLocations();
		if (templateLocations.hasNext()) {
			final Entry<String, String> location = templateLocations.next();
			this.sassOptions.put("template_location",
			                     "'" + location.getKey() + "'");
			this.sassOptions.put("css_location",
			                     "'" + location.getValue() + "'");
		}

		// If not explicitly set place the cache location in the target dir
		if (!this.sassOptions.containsKey("cache_location")) {
			final File sassCacheDir = new File(this.buildDirectory,
			        "sass_cache");
			final String sassCacheDirStr = sassCacheDir.toString();
			this.sassOptions.put("cache_location",
			        "'" + FilenameUtils.separatorsToUnix(sassCacheDirStr) + "'");
		}

		// Add the plugin configuration options
		sassScript.append("Sass::Plugin.options.merge!(\n");
		for (final Iterator<Entry<String, String>> entryItr = this.sassOptions
		        .entrySet().iterator(); entryItr.hasNext();) {
			final Entry<String, String> optEntry = entryItr.next();
			final String opt = optEntry.getKey();
			final String value = optEntry.getValue();
			sassScript.append("    :").append(opt).append(" => ").append(value);
			if (entryItr.hasNext()) {
				sassScript.append(",");
			}
			sassScript.append('\n');
		}
		sassScript.append(")\n");

		// add remaining template locations with 'add_template_location' (need
		// to be done after options.merge)
		while (templateLocations.hasNext()) {
			final Entry<String, String> location = templateLocations.next();
			sassScript.append("Sass::Plugin.add_template_location('")
			        .append(location.getKey()).append("', '")
			        .append(location.getValue()).append("')\n");
		}

		if (this.useBourbon) {
			log.info("Running with Bourbon enabled.");
			final String bDest = this.buildDirectory + "/bourbon";
			this.extractBourbonResources(bDest);
			// sassScript.append("require 'bourbon'\n");
			sassScript.append("Sass::Plugin.add_template_location('")
                    .append(bDest)
                    .append("/app/assets/stylesheets', '")
                    .append(destination).append("')\n");
		}

		// set up sass compiler callback for reporting
		sassScript
		        .append("Sass::Plugin.on_compilation_error {|error, template, css| $compiler_callback.compilationError(error.message, template, css) }\n");
		sassScript
		        .append("Sass::Plugin.on_updated_stylesheet {|template, css| $compiler_callback.updatedStylesheeet(template, css) }\n");
		sassScript
		        .append("Sass::Plugin.on_template_modified {|template| $compiler_callback.templateModified(template) }\n");
		sassScript
		        .append("Sass::Plugin.on_template_created {|template| $compiler_callback.templateCreated(template) }\n");
		sassScript
		        .append("Sass::Plugin.on_template_deleted {|template| $compiler_callback.templateDeleted(template) }\n");

		// make ruby give use some debugging info when requested
		if (log.isDebugEnabled()) {
			sassScript.append("require 'pp'\n");
			sassScript.append("pp Sass::Plugin.options\n");
			if (this.useCompass) {
				sassScript.append("pp Compass.base_directory\n");
				sassScript.append("pp Compass::Core.base_directory\n");
				sassScript.append("pp Compass::configuration\n");
			}
		}
	}

	/**
	 * Gets the template locations.
	 *
	 * @return the template locations
	 */
	private Iterator<Entry<String, String>> getTemplateLocations() {
		final Log log = this.getLog();
		List<Resource> resList = this.resources;

		if (resList.isEmpty()) {
			log.info("No resource element was specified, using short configuration.");
			// If no resources specified, create a resource based on the other
			// parameters and defaults
			final Resource resource = new Resource();
			resource.source = new FileSet();

			if (this.sassSourceDirectory != null) {
				log.debug("Setting source directory: "
						+ this.sassSourceDirectory.toString());
				resource.source.setDirectory(this.sassSourceDirectory
				        .toString());
			} else {
				log.error("\"" + this.sassSourceDirectory
				        + "\" is not a directory.");
				resource.source.setDirectory("./src/main/sass");
			}
			if (this.includes != null) {
				log.debug("Setting includes: " + Arrays.toString(this.includes));
				resource.source.setIncludes(Arrays.asList(this.includes));
			}
			if (this.excludes != null) {
				log.debug("Setting excludes: " + Arrays.toString(this.excludes));
				resource.source.setExcludes(Arrays.asList(this.excludes));
			}
			resource.relativeOutputDirectory = this.relativeOutputDirectory;
			resource.destination = this.destination;
			resList = ImmutableList.of(resource);
		}

		final List<Entry<String, String>> locations = new ArrayList<Entry<String, String>>();
		for (final Resource source : resList) {
			for (final Entry<String, String> entry : source
			        .getDirectoriesAndDestinations(log).entrySet()) {
				log.info("Queueing Sass template for compile: "
				        + entry.getKey() + " => " + entry.getValue());
				locations.add(entry);
			}
		}
		return locations.iterator();
	}

	/**
	 * Extract the Bourbon assets to the build directory.
	 * @param destinationDir directory for the Bourbon resources
	 */
	private void extractBourbonResources(String destinationDir) {
		final Log log = this.getLog();
		try {
			File destDir = new File(destinationDir);
			if (destDir.isDirectory()) {
				// skip extracting Bourbon, as it seems to hav been done
				log.info("Bourbon resources seems to have been extracted before.");
				return;
			}
			log.info("Extracting Bourbon resources to: " + destinationDir);
			destDir.mkdirs();
			// find the jar with the Bourbon directory in the classloader
			URL urlJar = this.getClass().getClassLoader().getResource("scss-report.xsl");
			String resourceFilePath = urlJar.getFile();
			int index = resourceFilePath.indexOf("!");
			String jarFileURI = resourceFilePath.substring(0, index);
			File jarFile = new File(new URI(jarFileURI));
			JarFile jar = new JarFile(jarFile);

			// extract app/assets/stylesheets to destinationDir
			for (Enumeration<JarEntry> enums = jar.entries(); enums.hasMoreElements();) {
				JarEntry entry = enums.nextElement();

				if (entry.getName().contains("app/assets/stylesheets")) {
					// shorten the path a bit
					index = entry.getName().indexOf("app/assets/stylesheets");
					String fileName = destinationDir + File.separator + entry.getName().substring(index);

					File f = new File(fileName);
					if (fileName.endsWith("/")) {
						f.mkdirs();
					} else {
						FileOutputStream fos = new FileOutputStream(f);
						try {
							IOUtil.copy(jar.getInputStream(entry), fos);
						} finally {
							IOUtil.close(fos);
						}
					}
				}
			}
		} catch (IOException | URISyntaxException ex) {
			log.error("Error extracting Bourbon resources.", ex);
		}
	}

	/**
	 * Resources accessor.
	 * @return the resources
	 */
	protected List<Resource> getResources() {
		return this.resources;
	}

	/**
	 * Compass enabled accessor.
	 * @return the useCompass
	 * 
	 * @deprecated Compass support will be removed in 3.0
	 */
	@Deprecated
	protected boolean isUseCompass() {
		return this.useCompass;
	}

	/**
	 * skip accessor.
	 * @return whether to skip execution or not
	 */
	protected boolean isSkip() {
		return this.skip;
	}

	/**
	 * Sass sources directory accessor.
	 * @return the sassSourceDirectory
	 */
	protected File getSassSourceDirectory() {
		return this.sassSourceDirectory;
	}
}
