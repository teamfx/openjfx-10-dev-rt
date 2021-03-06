/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javafx.tools.packager;

import com.sun.javafx.tools.resource.PackagerResource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SignJarParams extends CommonParams {

    final List<PackagerResource> resources = new ArrayList<PackagerResource>();

    File keyStore;
    String alias;
    String storePass;
    String keyPass;
    String storeType = "jks";
    Boolean verbose = false;

    public void setVerbose(boolean v) {
        verbose = v;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public void setKeyPass(String keyPass) {
        this.keyPass = keyPass;
    }

    public void setKeyStore(File keyStore) {
        this.keyStore = keyStore;
    }

    public void setStorePass(String storePass) {
        this.storePass = storePass;
    }

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    @Override
    public void addResource(File baseDir, String path) {
        resources.add(new PackagerResource(baseDir, path));
    }

    @Override
    public void addResource(File baseDir, File file) {
        resources.add(new PackagerResource(baseDir, file));
    }


    @Override
    public void validate() throws PackagerException {
        if (keyStore == null) {
            throw new PackagerException("ERR_InvalidStoreFile",
                    "null keystore");
        }
        if (!keyStore.isFile()) {
            throw new PackagerException("ERR_InvalidStoreFile", keyStore.getAbsolutePath());
        }
        if (alias == null) {
            throw new PackagerException("ERR_MissingArgument", "alias");
        }
        if (storeType == null) {
            storeType = "jks";
        }

        if (outdir != null) {
            outdir.mkdirs();
        }
    }

}
