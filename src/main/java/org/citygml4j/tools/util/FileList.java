/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.util;

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class FileList {
    private final String[] files;
    private String defaultGlob = "**.{gml,xml}";
    private Predicate<Path> filter;

    private FileList(String[] files) {
        this.files = files;
    }

    public static FileList of(String... files) {
        return new FileList(files);
    }

    public FileList withDefaultGlob(String defaultGlob) {
        if (defaultGlob != null) {
            this.defaultGlob = defaultGlob;
        }

        return this;
    }

    public FileList withFilter(Predicate<Path> filter) {
        this.filter = filter;
        return this;
    }

    public List<Path> build() throws IOException {
        if (files != null) {
            List<Path> inputFiles = new ArrayList<>();

            for (String file : files) {
                LinkedList<String> elements = parseInputFile(file);
                Path path = Paths.get(elements.pop());

                // construct a glob pattern from the path and the truncated elements
                String glob = "glob:" + path.toAbsolutePath().normalize();
                if (!elements.isEmpty()) {
                    glob += File.separator + String.join(File.separator, elements);
                } else if (Files.isDirectory(path) && defaultGlob != null) {
                    glob += File.separator + defaultGlob;
                }

                // find files matching the glob pattern
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob.replace("\\", "\\\\"));
                try (Stream<Path> stream = Files.walk(path)) {
                    stream.filter(Files::isRegularFile).forEach(p -> {
                        if (matcher.matches(p.toAbsolutePath().normalize())) {
                            if (filter != null && filter.test(p)) {
                                Logger.getInstance().debug("Skipping file " + p.toAbsolutePath() + ".");
                            } else {
                                inputFiles.add(p);
                            }
                        }
                    });
                }
            }

            return inputFiles;
        }

        return Collections.emptyList();
    }

    private LinkedList<String> parseInputFile(String file) {
        LinkedList<String> elements = new LinkedList<>();
        Path path = null;

        do {
            try {
                path = Paths.get(file);
            } catch (Exception e) {
                // the file is not a valid path, possibly because of glob patterns.
                // so, let's iteratively truncate the last path element and try again.
                int index = file.lastIndexOf(File.separator);
                String pathElement = file.substring(index + 1);
                file = file.substring(0, index != -1 ? index : 0);

                // remember the truncated element
                elements.addFirst(pathElement);
            }
        } while (path == null && file.length() > 0);

        // resolve path against the working directory
        path = path == null ?
                CityGMLTools.WORKING_DIR :
                CityGMLTools.WORKING_DIR.resolve(path);

        elements.addFirst(path.toAbsolutePath().toString());
        return elements;
    }
}
