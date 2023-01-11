package com.hazelcast.deployment.impl.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.hazelcast.internal.nio.IOUtil.closeResource;
import static com.hazelcast.internal.nio.IOUtil.toByteArray;
import static com.hazelcast.internal.util.EmptyStatement.ignore;

public class CodeDeploymentUtil {
    private static final Pattern CLASS_PATTERN = Pattern.compile("(.*)\\.class$");

    private CodeDeploymentUtil() {
    }

    public static List<Map.Entry<String, byte[]>> loadClasses(ClassLoader configClassLoader,
                                                              Collection<String> classNames,
                                                              Collection<String> jarPaths)
            throws IOException, ClassNotFoundException {
        List<Map.Entry<String, byte[]>> classDefinitionList = new ArrayList<>();
        // load classes by class name
        for (String className : classNames) {
            String resource = className.replace('.', '/').concat(".class");
            InputStream is = null;
            try {
                is = configClassLoader.getResourceAsStream(resource);
                if (is == null) {
                    throw new ClassNotFoundException(resource);
                }
                byte[] bytes = toByteArray(is);
                classDefinitionList.add(new AbstractMap.SimpleEntry<>(className, bytes));
            } catch (IOException e) {
                ignore(e);
            } finally {
                closeResource(is);
            }
        }
        // load classes from JARs
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            for (String jarPath : jarPaths) {
                classDefinitionList.addAll(loadClassesFromJar(configClassLoader, os, jarPath));
            }
        } finally {
            closeResource(os);
        }
        return classDefinitionList;
    }

    private static List<Map.Entry<String, byte[]>> loadClassesFromJar(ClassLoader configClassLoader,
                                           ByteArrayOutputStream os,
                                           String jarPath) throws IOException {
        List<Map.Entry<String, byte[]>> classDefinitionList = new ArrayList<>();
        JarInputStream inputStream = null;
        try {
            inputStream = getJarInputStream(configClassLoader, jarPath);
            JarEntry entry;
            do {
                entry = inputStream.getNextJarEntry();
                if (entry == null) {
                    break;
                }

                String className = extractClassName(entry);
                if (className == null) {
                    continue;
                }
                byte[] classDefinition = readClassDefinition(inputStream, os);
                inputStream.closeEntry();
                classDefinitionList.add(new AbstractMap.SimpleEntry<>(className, classDefinition));
            } while (true);
        } finally {
            closeResource(inputStream);
        }
        return classDefinitionList;
    }

    private static JarInputStream getJarInputStream(ClassLoader configClassLoader, String jarPath) throws IOException {
        File file = new File(jarPath);
        if (file.exists()) {
            return new JarInputStream(new FileInputStream(file));
        }

        try {
            URL url = new URL(jarPath);
            return new JarInputStream(url.openStream());
        } catch (MalformedURLException e) {
            ignore(e);
        }

        InputStream inputStream = configClassLoader.getResourceAsStream(jarPath);
        if (inputStream == null) {
            throw new FileNotFoundException("File could not be found in " + jarPath + "  and resources/" + jarPath);
        }
        return new JarInputStream(inputStream);
    }

    private static byte[] readClassDefinition(JarInputStream inputStream, ByteArrayOutputStream os) throws IOException {
        os.reset();
        while (true) {
            int v = inputStream.read();
            if (v == -1) {
                break;
            }
            os.write(v);
        }
        return os.toByteArray();
    }

    private static String extractClassName(JarEntry entry) {
        String entryName = entry.getName();
        Matcher matcher = CLASS_PATTERN.matcher(entryName.replace('/', '.'));
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }
}
