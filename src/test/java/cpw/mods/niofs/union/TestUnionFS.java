package cpw.mods.niofs.union;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestUnionFS {
    @Test
    void testUnionFileSystem() throws IOException {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();

        final var fileSystem = FileSystems.newFileSystem(dir1, Map.of("additional", List.of(dir2)));
        assertAll(
                ()->assertTrue(fileSystem instanceof UnionFileSystem),
                ()->assertIterableEquals(fileSystem instanceof UnionFileSystem ufs ? ufs.getBasePaths(): List.of(), List.of(dir2, dir1))
        );
        UnionFileSystem ufs = (UnionFileSystem) fileSystem;
        final var masktest = ufs.getPath("masktest.txt");
        assertAll(
                ()->assertTrue(Files.exists(masktest)),
                ()->assertEquals(Files.readString(masktest), "dir2")
        );
        assertAll(
                Files.walk(masktest.toAbsolutePath().getRoot())
                .map(Files::exists)
                .map(f->()->assertTrue(f))
        );
        var p = ufs.root().resolve("subdir1/masktestd1.txt");
        p.subpath(1, 2);
    }

    @Test
    void testRelativize() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();

        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var ufs = fsp.newFileSystem((path, base) -> true, dir1, dir2);
        var p1 = ufs.getPath("path1");
        var p123 = ufs.getPath("path1/path2/path3");
        var p11 = ufs.getPath("path1/path1");
        var p12 = ufs.getPath("path1/path2");
        var p13 = ufs.getPath("path1/path3");
        var p23 = ufs.getPath("path2/path3");
        var p13plus = ufs.getPath("path1/path3");
        assertAll(
                ()->assertEquals("path2/path3", p1.relativize(p123).toString()),
                ()->assertEquals("../..", p123.relativize(p1).toString()),
                ()->assertEquals("path1", p1.relativize(p11).toString()),
                ()->assertEquals("path2", p1.relativize(p12).toString()),
                ()->assertEquals("path3", p1.relativize(p13).toString()),
                ()->assertEquals("../../path1/path1", p23.relativize(p11).toString()),
                ()->assertEquals("../../path1", p123.relativize(p11).toString()),
                ()->assertEquals(0, p13.relativize(p13plus).getNameCount())
        );
    }
    
    @Test
    void testRelativizeAbsolute() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();

        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var ufs = fsp.newFileSystem((path, base) -> true, dir1, dir2);
        var p1 = ufs.getPath("/path1");
        var p123 = ufs.getPath("/path1/path2/path3");
        var p11 = ufs.getPath("/path1/path1");
        var p12 = ufs.getPath("/path1/path2");
        var p13 = ufs.getPath("/path1/path3");
        var p23 = ufs.getPath("/path2/path3");
        var p13plus = ufs.getPath("/path1/path3");
        assertAll(
                ()->assertEquals("path2/path3", p1.relativize(p123).toString()),
                ()->assertEquals("../..", p123.relativize(p1).toString()),
                ()->assertEquals("path1", p1.relativize(p11).toString()),
                ()->assertEquals("path2", p1.relativize(p12).toString()),
                ()->assertEquals("path3", p1.relativize(p13).toString()),
                ()->assertEquals("../../path1/path1", p23.relativize(p11).toString()),
                ()->assertEquals("../../path1", p123.relativize(p11).toString()),
                ()->assertEquals(0, p13.relativize(p13plus).getNameCount())
        );
    }

    @Test
    void testPathFiltering() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1").toAbsolutePath().normalize();
        final var dir2 = Paths.get("src", "test", "resources", "dir2").toAbsolutePath().normalize();
        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var ufs = fsp.newFileSystem((path, base)->!path.startsWith("masktest2.txt"), dir1, dir2);
        var t1 = ufs.getPath("masktest.txt");
        var t3 = ufs.getPath("masktest3.txt");
        var t2 = ufs.getPath("masktest2.txt");
        assertTrue(Files.exists(t1));
        assertTrue(Files.exists(t3));
        assertTrue(Files.notExists(t2));
        var sd1 = ufs.getPath("subdir1");
        var sdt1 = sd1.resolve("masktestsd1.txt");
        var walk = Set.of(ufs.root(), t1, t3, sd1, sdt1);
        assertDoesNotThrow(()-> {
            try (var set = Files.walk(ufs.root())) {
                var paths = set.collect(Collectors.toSet());
                assertEquals(walk, paths);
            }
        });
    }

    @Test
    void testFilteredDuplicate() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1.zip").toAbsolutePath().normalize();
        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var all = fsp.newFileSystem((a,b) -> true, dir1);
        var all_expected = Set.of(
            all.getPath("masktest.txt"),
            all.getPath("masktest2.txt"),
            all.getPath("subdir1/masktestsd1.txt")
        );
        assertDoesNotThrow(() -> {
           try (var walk = Files.walk(all.root()))  {
               var paths = walk.filter(Files::isRegularFile).collect(Collectors.toSet());
               assertEquals(all_expected, paths);
           }
        });

        var some = assertDoesNotThrow(() -> fsp.newFileSystem((a,b) -> a.endsWith("/") || a.equals("masktest.txt"), dir1));
        var some_expected = Set.of(
            some.getPath("masktest.txt")
        );
        assertDoesNotThrow(() -> {
            try (var walk = Files.walk(some.root()))  {
                var paths = walk.filter(Files::isRegularFile).collect(Collectors.toSet());
                assertEquals(some_expected, paths);
            }
        });
    }

    @Test
    void testNested() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1.zip").toAbsolutePath().normalize();
        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var inner = fsp.newFileSystem((a,b) -> a.endsWith("/") || a.equals("masktest.txt"), dir1);
        var outer = fsp.newFileSystem((a, b) -> true, inner.root());
        var path = outer.getPath("masktest.txt");
        var expected = Set.of(path);
        assertDoesNotThrow(() -> {
            try (var walk = Files.walk(outer.root()))  {
                var paths = walk.filter(Files::isRegularFile).collect(Collectors.toSet());
                assertEquals(expected, paths);
            }
        });
        var uri = path.toUri();
        var npath = Paths.get(uri);
        var input = assertDoesNotThrow(() -> Files.newInputStream(npath));
        var data = assertDoesNotThrow(() -> input.readAllBytes());
    }

    @Test
    void testFileAttributes() {
        final var dir1 = Paths.get("src", "test", "resources", "dir1.zip").toAbsolutePath().normalize();
        var fsp = (UnionFileSystemProvider)FileSystemProvider.installedProviders().stream().filter(fs-> fs.getScheme().equals("union")).findFirst().orElseThrow();
        var ufs = fsp.newFileSystem((a,b) -> true, dir1);
        var path = (UnionPath) ufs.getPath("subdir1");
        var nonExistentPath = (UnionPath) ufs.getPath("non-existent-path");

        // Non-union path
        assertDoesNotThrow(() -> {
            assertNull(fsp.getFileAttributeView(Paths.get("subdir1"), BasicFileAttributeView.class));
        });
        // Unsupported attribute view
        assertDoesNotThrow(() -> {
            assertNull(fsp.getFileAttributeView(path, FileAttributeView.class));
        });
        // Non-existent path w/ supported attribute view
        assertThrows(NoSuchFileException.class, () -> {
            var nonExistentView = assertDoesNotThrow(() -> {
                var view = fsp.getFileAttributeView(nonExistentPath, BasicFileAttributeView.class);
                assertNotNull(view);
                return view;
            });
            nonExistentView.readAttributes();
        });
        // Non-existent path
        assertThrows(NoSuchFileException.class, () -> {
            ufs.readAttributes(nonExistentPath, BasicFileAttributes.class);
        });

        // Union path w/ supported attribute view
        var validViewAttributes = assertDoesNotThrow(() -> {
            var view = fsp.getFileAttributeView(path, BasicFileAttributeView.class);
            assertNotNull(view);
            return view.readAttributes();
        });
        // Known existing path
        var validAttributes = assertDoesNotThrow(() -> {
            var attributes = ufs.readAttributes(path, BasicFileAttributes.class);
            assertNotNull(attributes);
            return attributes;
        });
        // Ensure the attributes are the same through both methods
        assertEquals(validAttributes, validViewAttributes);
    }
}
