package cpw.mods.jarhandling.impl;

import cpw.mods.jarhandling.IJarFileSystem;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.function.BiPredicate;

record SimpleJarFileSystem(FileSystem fileSystem, Path primaryPath, BiPredicate<String, String> filesystemFilter) implements IJarFileSystem
{
    @SuppressWarnings("resource")
    @Override
    public Iterable<Path> getRootDirectories()
    {
        return fileSystem().getRootDirectories();
    }

    @Override
    public Path root()
    {
        if (!getRootDirectories().iterator().hasNext())
            throw new IllegalStateException("No root directories found");

        return getRootDirectories().iterator().next();
    }
}
