package org.whitesource.maven.utils.dependencies;

import java.io.File;

/**
 * Author: Itai Marko
 */
public interface AetherArtifact {

    String getGroupId();

    String getArtifactId();

    String getVersion();

    String getClassifier();

    String getExtension();

    String getProperty(String type, String s);

    File getFile();
}
