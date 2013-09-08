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

package com.google.jimfs.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.AbstractIterator;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * Implementation of {@link DirectoryStream}.
 *
 * @author Colin Decker
 */
abstract class JimfsDirectoryStream implements DirectoryStream<Path> {

  private final JimfsPath dirPath;
  private final Filter<? super Path> filter;
  private volatile DirectoryIterator iterator;

  public JimfsDirectoryStream(JimfsPath dirPath, Filter<? super Path> filter) {
    this.dirPath = checkNotNull(dirPath);
    this.filter = checkNotNull(filter);
    this.iterator = new DirectoryIterator();
  }

  /**
   *
   * @return
   */
  protected JimfsPath path() {
    return dirPath;
  }

  @Override
  public Iterator<Path> iterator() {
    if (iterator == null) {
      throw new IllegalStateException("iterator() has already been called once");
    }
    Iterator<Path> result = iterator;
    iterator = null;
    return result;
  }

  @Override
  public void close() throws IOException {
  }

  /**
   * Returns a snapshot of names of the entries in the directory.
   */
  protected abstract Iterable<String> snapshotEntryNames() throws IOException;

  private final class DirectoryIterator extends AbstractIterator<Path> {

    @Nullable
    private Iterator<String> fileNames;

    @Override
    protected Path computeNext() {
      try {
        if (fileNames == null) {
          fileNames = snapshotEntryNames().iterator();
        }

        while (fileNames.hasNext()) {
          String name = fileNames.next();
          Path path = dirPath.resolve(name);

          if (filter.accept(path)) {
            return path;
          }
        }

        return endOfData();
      } catch (IOException e) {
        throw new DirectoryIteratorException(e);
      }
    }
  }

  /**
   * A stream filter that always returns true.
   */
  public static final Filter<Object> ALWAYS_TRUE_FILTER = new Filter<Object>() {
    @Override
    public boolean accept(Object entry) throws IOException {
      return true;
    }
  };
}