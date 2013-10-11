/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.jimfs.path.PathType;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Static factory methods for JIMFS file systems.
 *
 * @author Colin Decker
 */
public final class Jimfs {

  /**
   * The URI scheme for the JIMFS file system ("jimfs").
   */
  public static final String URI_SCHEME = "jimfs";

  /**
   * The key used for mapping to the {@link Configuration} in the env map when creating a new file
   * system instance.
   */
  public static final String CONFIG_KEY = "config";

  private Jimfs() {}

  /**
   * Returns a new in-memory file system with semantics similar to UNIX.
   *
   * <p>The returned file system:
   *
   * <ul>
   *   <li>uses "/" as the path name separator (see {@link PathType#unix()} for more information on
   *   the path format)</li>
   *   <li>has root "/" and working directory "/work"</li>
   *   <li>supports symbolic links and hard links</li>
   *   <li>does case-sensitive lookup</li>
   *   <li>supports only the "basic" file attribute view</li>
   * </ul>
   *
   * <p>For more advanced configuration, including changing the working directory, supported
   * attribute views or supported features or for setting the host name to be used in the file
   * system's URI, use {@link #newUnixLikeConfiguration()}.
   */
  public static FileSystem newUnixLikeFileSystem() {
    return newUnixLikeConfiguration().createFileSystem();
  }

  /**
   * Returns a new {@link Configuration} instance with defaults for a UNIX-like file
   * system. If no changes are made to the configuration, the file system it creates will be
   * identical to that created by {@link #newUnixLikeFileSystem()}. Legal paths are described by
   * {@link PathType#unix()}. Only one root, "/", is allowed.
   *
   * <p>Example usage:
   *
   * <pre>
   *   // the returned file system has URI "jimfs://unix" and supports
   *   // the "basic", "owner", "posix" and "unix" attribute views
   *   FileSystem fs = Jimfs.newUnixLikeConfiguration()
   *       .setName("unix")
   *       .setWorkingDirectory("/home/user")
   *       .setAttributeViews(AttributeViews.unix())
   *       .createFileSystem(); </pre>
   */
  public static Configuration newUnixLikeConfiguration() {
    return newConfiguration(PathType.unix())
        .addRoots("/")
        .setWorkingDirectory("/work")
        .setSupportedFeatures(Feature.LINKS, Feature.SYMBOLIC_LINKS);

  }

  /**
   * Returns a new in-memory file system with semantics similar to Windows.
   *
   * <p>The returned file system:
   *
   * <ul>
   *   <li>uses "\" as the path name separator and recognizes "/" as a separator when parsing
   *   paths (see {@link PathType#windows()} for more information on path format)</li>
   *   <li>has root "C:\" and working directory "C:\work"</li>
   *   <li>supports symbolic links but not hard links</li>
   *   <li>does case-insensitive lookup (for ASCII characters only)</li>
   *   <li>supports only the "basic" file attribute view</li>
   * </ul>
   *
   * <p>For more advanced configuration, including changing the working directory, supported
   * attribute views or supported features or for setting the host name to be used in the file
   * system's URI, use {@link #newWindowsLikeConfiguration()}.
   */
  public static FileSystem newWindowsLikeFileSystem() {
    return newWindowsLikeConfiguration().createFileSystem();
  }

  /**
   * Returns a new {@link Configuration} instance with defaults for a Windows-like file system. If
   * no changes are made to the configuration, the file system it creates will be identical to that
   * created by {@link #newWindowsLikeFileSystem()}. Legal roots and paths are described by
   * {@link PathType#windows()}.
   *
   * <p>Example usage:
   *
   * <pre>
   *   // the returned file system has URI "jimfs://win", has root directories
   *   // "C:\", "E:\" and "F:\" and supports the "basic", "owner", "dos",
   *   // "acl and "user" attribute views
   *   FileSystem fs = Jimfs.newWindowsLikeConfiguration()
   *       .setName("win")
   *       .addRoots("E:\\", "F:\\")
   *       .setWorkingDirectory("C:\\Users\\user")
   *       .setAttributeViews(AttributeViews.windows())
   *       .createFileSystem(); </pre>
   */
  public static Configuration newWindowsLikeConfiguration() {
    return newConfiguration(PathType.windows())
        .addRoots("C:\\")
        .setWorkingDirectory("C:\\work")
        .setSupportedFeatures(Feature.SYMBOLIC_LINKS);
  }

  /**
   * Returns a new {@link Configuration} instance using the given path type. At least one
   * root must be added to the configuration before creating a file system with it.
   */
  public static Configuration newConfiguration(PathType pathType) {
    return new Configuration(pathType);
  }

  @VisibleForTesting
  static FileSystem newFileSystem(URI uri, Configuration config) {
    checkArgument(URI_SCHEME.equals(uri.getScheme()),
        "uri (%s) must have scheme %s", uri, URI_SCHEME);

    ImmutableMap<String, ?> env = ImmutableMap.of(CONFIG_KEY, config);
    try {
      // Need to use Jimfs.class.getClassLoader() to ensure the class that loaded the jar is used
      // to locate the FileSystemProvider
      return FileSystems.newFileSystem(uri, env, Jimfs.class.getClassLoader());
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Configuration for an in-memory file system instance.
   */
  public static final class Configuration {

    private PathType pathType;
    private String name;

    private final Set<String> roots = new LinkedHashSet<>();
    private String workingDirectory;

    private AttributeViews attributes = AttributeViews.basic();
    private final Set<Feature> supportedFeatures = new HashSet<>();

    private Configuration(PathType pathType) {
      this.pathType = checkNotNull(pathType);
    }

    /**
     * Returns the configured path type for the file system.
     */
    public PathType getPathType() {
      return pathType;
    }

    /**
     * Returns the configured name for the file system or a random name if none was provided.
     */
    public String getName() {
      return name != null ? name : UUID.randomUUID().toString();
    }

    /**
     * Returns the configured roots for the file system.
     */
    public ImmutableList<String> getRoots() {
      return ImmutableList.copyOf(roots);
    }

    /**
     * Returns the configured working directory for the file system.
     */
    public String getWorkingDirectory() {
      if (workingDirectory == null) {
        String firstRoot = roots.iterator().next();
        return firstRoot + pathType.getSeparator() + "work";
      }
      return workingDirectory;
    }

    /**
     * Returns the configured set of attribute views for the file system.
     */
    public AttributeViews getAttributeViews() {
      return attributes;
    }

    /**
     * Returns the configured set of optional features the file system should support.
     */
    public Set<Feature> getSupportedFeatures() {
      return Collections.unmodifiableSet(supportedFeatures);
    }

    /**
     * Sets the name for the created file system, which will be used as the host part of the URI
     * that identifies the file system. For example, if the name is "foo" the file system's URI
     * will be "jimfs://foo" and the URI of the path "/bar" on the file system will be
     * "jimfs://foo/bar".
     *
     * <p>By default, a random unique name will be assigned to the file system.
     */
    public Configuration setName(String name) {
      this.name = checkNotNull(name);
      return this;
    }

    /**
     * Adds the given root directories to the file system.
     *
     * @throws IllegalStateException if the path type does not allow multiple roots
     */
    public Configuration addRoots(String first, String... more) {
      List<String> roots = Lists.asList(first, more);
      checkState(this.roots.size() + roots.size() == 1 || pathType.allowsMultipleRoots(),
          "this path type does not allow multiple roots");
      for (String root : roots) {
        checkState(!this.roots.contains(root), "root " + root + " is already configured");
        this.roots.add(checkNotNull(root));
      }
      return this;
    }

    /**
     * Sets the working directory for the file system.
     *
     * <p>If not set, the default working directory will be a directory called "work" located in
     * the first root directory in the list of roots.
     */
    public Configuration setWorkingDirectory(String workingDirectory) {
      this.workingDirectory = checkNotNull(workingDirectory);
      return this;
    }

    /**
     * Sets the attribute views to use for the file system.
     *
     * <p>The default is the {@link AttributeViews#basic() basic} view only, to minimize overhead of
     * storing attributes when other attributes aren't needed.
     */
    public Configuration setAttributeViews(AttributeViews... views) {
      if (views.length == 0) {
        this.attributes = AttributeViews.basic();
      } else {
        this.attributes = new AttributeViewsSet(views);
      }
      return this;
    }

    /**
     * Sets the optional features the file system should support. Any supported features that were
     * previously set are replaced.
     */
    public Configuration setSupportedFeatures(Feature... features) {
      supportedFeatures.clear();
      supportedFeatures.addAll(Arrays.asList(features));
      return this;
    }

    /**
     * Creates a new file system using this configuration.
     */
    public FileSystem createFileSystem() {
      return newFileSystem(URI.create(URI_SCHEME + "://" + getName()), this);
    }
  }

  /**
   * Optional features that may or may not be supported by a file system.
   */
  public static enum Feature {
    /**
     * Controls whether or not {@linkplain Files#createLink(Path, Path) hard links to regular files}
     * are supported.
     */
    LINKS,

    /**
     * Controls whether or not {@linkplain Files#createSymbolicLink(Path, Path, FileAttribute[])
     * symbolic links} are supported.
     */
    SYMBOLIC_LINKS
  }
}
