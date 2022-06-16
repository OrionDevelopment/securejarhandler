package cpw.mods.jarhandling;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.function.BiPredicate;

/**
 * Defines the requirements of a file system for a secure jar implementation.
 */
public interface IJarFileSystem
{

    /**
     * The underlying file system.
     *
     * @return The underlying file system.
     */
    FileSystem fileSystem();

    /**
     * Returns all root entries of the file system.
     *
     * @return All root entries of the file system.
     */
    Iterable<Path> getRootDirectories();

    /**
     * Returns the primary path of the jar file.
     * Note this might be a directory if the jar file is a directory (for example during development scenarios).
     *
     * @return The primary path of the jar file.
     */
    Path primaryPath();

    /**
     * Returns the root path of the jar file.
     *
     * @return The root path of the jar file.
     */
    Path root();

    /**
     * The file system access filter applied to the jar file system.
     *
     * @return The file system access filter applied to the jar file system.
     */
    BiPredicate<String, String> filesystemFilter();
}
