package org.whitesource.maven.utils.dependencies.impl.eclipse;

import org.eclipse.aether.artifact.Artifact;
import org.whitesource.maven.utils.dependencies.AetherArtifact;

import java.io.File;

/**
 * Author: Itai Marko
 */
public class EclipseAetherArtifact implements AetherArtifact {

    private Artifact delegate;

    EclipseAetherArtifact(Artifact artifact) {
        this.delegate = artifact;
    }

    @Override
    public String getGroupId() {
        return delegate.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return delegate.getArtifactId();
    }

    @Override
    public String getVersion() {
        return delegate.getVersion();
    }

    @Override
    public String getClassifier() {
        return delegate.getClassifier();
    }

    @Override
    public String getExtension() {
        return delegate.getExtension();
    }

    @Override
    public String getProperty(String type, String s) {
        return delegate.getProperty(type, s);
    }

    @Override
    public File getFile() {
        return delegate.getFile();
    }
}
