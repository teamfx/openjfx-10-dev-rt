/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.tools.packager;

import com.oracle.tools.packager.windows.WindowsBundlerParam;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.*;
import com.oracle.tools.packager.IOUtils;

public abstract class AbstractBundler implements Bundler {

    private static final ResourceBundle I18N =
            ResourceBundle.getBundle(AbstractBundler.class.getName());

    public static final BundlerParamInfo<File> IMAGES_ROOT = new WindowsBundlerParam<>(
            I18N.getString("param.images-root.name"),
            I18N.getString("param.images-root.description"),
            "imagesRoot",
            File.class,
            params -> new File(StandardBundlerParam.BUILD_ROOT.fetchFrom(params), "images"),
            (s, p) -> null);

    //do not use file separator -
    // we use it for classpath lookup and there / are not platform specific
    public final static String BUNDLER_PREFIX = "package/";

    protected Class baseResourceLoader = null;

    protected void fetchResource(
            String publicName, String category,
            String defaultName, File result, boolean verbose, File publicRoot)
            throws IOException {
        InputStream is = streamResource(publicName, category, defaultName, verbose, publicRoot);
        if (is != null) {
            Files.copy(is, result.toPath());
        } else {
            if (verbose) {
                Log.info(MessageFormat.format(I18N.getString("message.using-default-resource"), category == null ? "" : "[" + category + "] ", publicName));
            }
        }
    }

    protected void fetchResource(
            String publicName, String category,
            File defaultFile, File result, boolean verbose, File publicRoot)
            throws IOException {
        InputStream is = streamResource(publicName, category, null, verbose, publicRoot);
        if (is != null) {
            Files.copy(is, result.toPath());
        } else {
            IOUtils.copyFile(defaultFile, result);
            if (verbose) {
                Log.info(MessageFormat.format(I18N.getString("message.using-custom-resource-from-file"), category == null ? "" : "[" + category + "] ", defaultFile.getAbsoluteFile()));
            }
        }
    }

    private InputStream streamResource(String publicName, String category,
                               String defaultName, boolean verbose, File publicRoot) throws IOException {
        boolean custom = false;
        InputStream is = null;
        if (publicName != null) {
            if (publicRoot != null) {
                File publicResource = new File(publicRoot, publicName);
                if (publicResource.exists() && publicResource.isFile()) {
                    is = new FileInputStream(publicResource);
                }
            } else {
                is = baseResourceLoader.getClassLoader().getResourceAsStream(publicName);
            }
            custom = (is != null);
        }
        if (is == null && defaultName != null) {
            is = baseResourceLoader.getResourceAsStream(defaultName);
        }
        String msg = null;
        if (custom) {
            msg = MessageFormat.format(I18N.getString("message.using-custom-resource-from-classpath"), category == null ? "" : "[" + category + "] ", publicName);
        } else if (is != null) {
            msg = MessageFormat.format(I18N.getString("message.using-default-resource-from-classpath"), category == null ? "" : "[" + category + "] ", publicName);
        }
        if (verbose && is != null) {
            Log.info(msg);
        }
        return is;
    }

    protected String preprocessTextResource(String publicName, String category,
                                            String defaultName, Map<String, String> pairs,
                                            boolean verbose, File publicRoot) throws IOException {
        InputStream inp = streamResource(publicName, category, defaultName, verbose, publicRoot);
        if (inp == null) {
            throw new RuntimeException("Jar corrupt? No "+defaultName+" resource!");
        }

        //read fully into memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inp.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }

        //substitute
        String result = new String(baos.toByteArray());
        for (Map.Entry<String, String> e : pairs.entrySet()) {
            if (e.getValue() != null) {
                result = result.replace(e.getKey(), e.getValue());
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public void cleanup(Map<String, ? super Object> params) {
        if (!StandardBundlerParam.VERBOSE.fetchFrom(params)) {
            try {
                IOUtils.deleteRecursive(StandardBundlerParam.BUILD_ROOT.fetchFrom(params));
            } catch (FileNotFoundException ignored) {}
        }
    }
}
