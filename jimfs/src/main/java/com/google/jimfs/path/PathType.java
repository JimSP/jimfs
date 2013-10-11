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

package com.google.jimfs.path;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import javax.annotation.Nullable;

/**
 * An object defining a specific type of path. Knows how to parse strings to a path and how to
 * render a path as a string as well as what the path separator is and what other separators are
 * recognized when parsing paths.
 *
 * @author Colin Decker
 */
public abstract class PathType {

  /**
   * Returns a Unix-style path type. "/" is both the root and the only separator. Any path starting
   * with "/" is considered absolute. Paths are case sensitive. The nul character ('\0') is
   * disallowed in paths.
   */
  public static PathType unix() {
    return UnixPathType.UNIX;
  }

  /**
   * Returns a Mac OS X style path type. This path type is the same as the {@linkplain #unix() Unix}
   * path type, but does Unicode normalization and uses case-insensitive lookup for ASCII
   * characters. Additionally, names in {@code Path} objects are Unicode NFC normalized in an
   * attempt to match the behavior of the real OS X {@code FileSystem} implementation.
   */
  public static PathType osx() {
    return UnixPathType.OS_X;
  }

  /**
   * Returns a Windows-style path type. The canonical separator character is "\". "/" is also
   * treated as a separator when parsing paths. Paths are case insensitive for ASCII characters.
   *
   * <p>As much as possible, this implementation follows the information provided in
   * <a href="http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx">
   * this article</a>. Paths with drive-letter roots (e.g. "C:\") and paths with UNC roots (e.g.
   * "\\host\share\") are supported.
   *
   * <p>One thing in particular is not currently supported: relative paths containing a drive-letter
   * root, for example "C:" or "C:foo\bar". Such paths have a root component and optionally have
   * names, but are <i>relative</i> paths, relative to the working directory of the drive identified
   * by the root. This has some fundamental conflicts with how JIMFS handles paths and file lookups,
   * and so is not currently supported.
   */
  public static PathType windows() {
    return WindowsPathType.INSTANCE;
  }

  private final Normalization lookupNormalization;
  private final Normalization pathNormalization;
  private final boolean allowsMultipleRoots;
  private final String separator;
  private final String otherSeparators;
  private final Joiner joiner;
  private final Splitter splitter;

  protected PathType(
      Normalization lookupNormalization, Normalization pathNormalization,
      boolean allowsMultipleRoots,
      char separator, char... otherSeparators) {
    this.lookupNormalization = checkNotNull(lookupNormalization);
    this.pathNormalization = checkNotNull(pathNormalization);
    this.separator = String.valueOf(separator);
    this.allowsMultipleRoots = allowsMultipleRoots;
    this.otherSeparators = String.valueOf(otherSeparators);
    this.joiner = Joiner.on(separator);
    this.splitter = createSplitter(separator, otherSeparators);
  }

  /**
   * Returns a new path type identical to this one except using the given normalization settings
   * for file lookups.
   */
  public abstract PathType lookupNormalization(Normalization normalization);

  /**
   * Returns a new path type identical to this one except using the given normalization settings
   * for {@code Path} objects.
   */
  public abstract PathType pathNormalization(Normalization normalization);

  private static final char[] regexReservedChars = "^$.?+*\\[]{}()".toCharArray();
  static {
    Arrays.sort(regexReservedChars);
  }

  private static boolean isRegexReserved(char c) {
    return Arrays.binarySearch(regexReservedChars, c) >= 0;
  }

  private static Splitter createSplitter(char separator, char... otherSeparators) {
    if (otherSeparators.length == 0) {
      return Splitter.on(separator).omitEmptyStrings();
    }

    // TODO(cgdecker): When CharMatcher is out of @Beta, us Splitter.on(CharMatcher)
    StringBuilder patternBuilder = new StringBuilder();
    patternBuilder.append("[");
    appendToRegex(separator, patternBuilder);
    for (char other : otherSeparators) {
      appendToRegex(other, patternBuilder);
    }
    patternBuilder.append("]");
    return Splitter.onPattern(patternBuilder.toString()).omitEmptyStrings();
  }

  private static void appendToRegex(char separator, StringBuilder patternBuilder) {
    if (isRegexReserved(separator)) {
      patternBuilder.append("\\");
    }
    patternBuilder.append(separator);
  }

  /**
   * Returns whether or not this type of path allows multiple root directories.
   */
  public final boolean allowsMultipleRoots() {
    return allowsMultipleRoots;
  }

  /**
   * Returns the canonical separator for this path type. The returned string always has a length of
   * one.
   */
  public final String getSeparator() {
    return separator;
  }

  /**
   * Returns the other separators that are recognized when parsing a path. If no other separators
   * are recognized, the empty string is returned.
   */
  public final String getOtherSeparators() {
    return otherSeparators;
  }

  /**
   * Returns the path joiner for this path type.
   */
  public final Joiner joiner() {
    return joiner;
  }

  /**
   * Returns the path splitter for this path type.
   */
  public final Splitter splitter() {
    return splitter;
  }

  /**
   * Returns the normalization setting to be used for file lookups. This normalization does not
   * affect the equality, sort ordering or {@code toString()} form of {@code Path} objects.
   */
  public final Normalization lookupNormalization() {
    return lookupNormalization;
  }

  /**
   * Returns the normalization setting to be used for {@code Path} objects. This normalization does
   * affect the equality, sort ordering and {@code toString()} form of {@code Path} objects but
   * does not affect lookup.
   */
  public final Normalization pathNormalization() {
    return pathNormalization;
  }

  /**
   * Returns an empty path.
   */
  protected final ParseResult emptyPath() {
    return new ParseResult(null, ImmutableList.of(""));
  }

  /**
   * Parses the given strings as a path.
   */
  public abstract ParseResult parsePath(String path);

  /**
   * Returns the string form of the given path.
   */
  public abstract String toString(@Nullable String root, Iterable<String> names);

  /**
   * Returns the string form of the given path for use in the path part of a URI. The root element
   * is not nullable as the path must be absolute. The elements of the returned path <i>do not</i>
   * need to be escaped.
   */
  protected abstract String toUriPath(String root, Iterable<String> names);

  /**
   * Parses a path from the given URI path.
   */
  protected abstract ParseResult parseUriPath(String uriPath);

  /**
   * Creates a URI for the path with the given root and names in the file system with the given URI.
   */
  public final URI toUri(URI fileSystemUri, String root, Iterable<String> names) {
    String path = toUriPath(root, names);
    try {
      // it should not suck this much to create a new URI that's the same except with a path set =(
      // need to do it this way for automatic path escaping
      return new URI(
          fileSystemUri.getScheme(),
          fileSystemUri.getUserInfo(),
          fileSystemUri.getHost(),
          fileSystemUri.getPort(),
          path,
          null,
          null);
    } catch (URISyntaxException e) {
      throw new AssertionError(e);
    }
  }

  /**
   * Parses a path from the given URI.
   */
  public final ParseResult fromUri(URI uri) {
    return parseUriPath(uri.getPath());
  }

  /**
   * Simple result of parsing a path.
   */
  public static final class ParseResult {

    @Nullable
    private final String root;
    private final Iterable<String> names;

    public ParseResult(@Nullable String root, Iterable<String> names) {
      this.root = root;
      this.names = checkNotNull(names);
    }

    /**
     * Returns whether or not this result is an absolute path.
     */
    public boolean isAbsolute() {
      return root != null;
    }

    /**
     * Returns the parsed root element, or null if there was no root.
     */
    @Nullable
    public String root() {
      return root;
    }

    /**
     * Returns the parsed name elements.
     */
    public Iterable<String> names() {
      return names;
    }
  }
}
