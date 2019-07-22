package com.google.common.jimfs;

import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.hamcrest.core.IsEqual;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JimsfCustomMapStorageTest {

    private static final String NAME = "test";
    private static final String EXPECTED = "Hello Word";
    
    @Test
    public void testTreeMap() throws IOException {

        Map<Name, Directory> roots = new TreeMap<Name, Directory>();
        
        test(roots);
    }
    
    @Test
    public void testHashMap() throws IOException {

        Map<Name, Directory> roots = new HashMap<Name, Directory>();
        
        test(roots);
    }
    
    @Test
    public void testSynchronizedNavigableMap() throws IOException {

        Map<Name, Directory> roots = Collections.synchronizedNavigableMap(new TreeMap<Name, Directory>());
        
        test(roots);
    }

    private void test(Map<Name, Directory> roots) throws IOException {
        
        FileSystem fileSystem = Jimfs.newFileSystem(roots);
        
        Files.write(fileSystem.getPath(NAME), EXPECTED.getBytes());
        
        String readContent = new String(Files.readAllBytes(fileSystem.getPath(NAME)));

        assertThat(readContent, IsEqual.equalTo(EXPECTED));
    }
}
