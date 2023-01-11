/*
 * Copyright (c) 2008-2022, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.usercodedeployment.impl.filter;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.UserCodeDeploymentConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.EntryProcessor;
import com.hazelcast.map.IMap;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.annotation.ParallelJVMTest;
import com.hazelcast.test.annotation.QuickTest;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelJVMTest.class})
public class UserCodeReDeploymentBasicTest extends HazelcastTestSupport {

    static final String SRC_JAVA_V1 =
            "package redeployment;\n" +
                    "\n" +
                    "import com.hazelcast.map.EntryProcessor;\n" +
                    "\n" +
                    "import java.util.Map;\n" +
                    "\n" +
                    "public class IncrementingEntryProcessor implements EntryProcessor<Integer, Integer, Integer> {\n" +
                    "\n" +
                    "    @Override\n" +
                    "    public Integer process(Map.Entry<Integer, Integer> entry) {\n" +
                    "        Integer origValue = entry.getValue();\n" +
                    "        entry.setValue(origValue);\n" +
                    "\n" +
                    "        return origValue;\n" +
                    "    }\n" +
                    "}";

    static final String SRC_JAVA_V2 =
            "package redeployment;\n" +
                    "\n" +
                    "import com.hazelcast.map.EntryProcessor;\n" +
                    "\n" +
                    "import java.util.Map;\n" +
                    "\n" +
                    "public class IncrementingEntryProcessor implements EntryProcessor<Integer, Integer, Integer> {\n" +
                    "\n" +
                    "    @Override\n" +
                    "    public Integer process(Map.Entry<Integer, Integer> entry) {\n" +
                    "        Integer origValue = entry.getValue();\n" +
                    "        Integer newValue = origValue + 1;\n" +
                    "        entry.setValue(newValue);\n" +
                    "\n" +
                    "        return newValue;\n" +
                    "    }\n" +
                    "}";

    static final String PATH = "target/generated/classes/";

    protected TestHazelcastFactory factory = new TestHazelcastFactory();

    @After
    public void tearDown() {
        factory.terminateAll();
    }


    @Test
    public void testBasic() throws Exception {
        Config config = new Config();
        config.getUserCodeDeploymentConfig()
                .setEnabled(true)
                .setProviderMode(UserCodeDeploymentConfig.ProviderMode.LOCAL_AND_CACHED_CLASSES);

        HazelcastInstance instance = factory.newHazelcastInstance(config);

        String className = "redeployment.IncrementingEntryProcessor";

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClassLoader(getMyURLClassLoader(PATH));
        clientConfig.getUserCodeDeploymentConfig()
                .setEnabled(true)
                .addClass(className);

        compileJava(className, SRC_JAVA_V1);
        EntryProcessor ep1 = newClassInstance(className);
        System.out.println("ep1: " + ep1);

        HazelcastInstance client1 = factory.newHazelcastClient(clientConfig);
        String mapName = randomMapName();
        IMap<Integer, Integer> map1 = client1.getMap(mapName);

        map1.put(1, 1);
        map1.executeOnEntries(ep1);
        System.out.println("map1: " + map1.get(1));
        assertEquals(1, (int) map1.get(1));

        compileJava(className, SRC_JAVA_V2);
        EntryProcessor ep2 = newClassInstance(className);
        System.out.println("ep2: " + ep2);

        HazelcastInstance client2 = factory.newHazelcastClient(clientConfig);
        IMap<Integer, Integer> map2 = client2.getMap(mapName);
        map2.executeOnEntries(ep2);

        System.out.println("map2: " + map2.get(1));
        assertEquals(2, (int) map2.get(1));
    }

    @Test
    // Not pass
    public void testLoadClassFromAnotherMember() throws Exception {
        Config config = new Config();
        config.getUserCodeDeploymentConfig()
                .setEnabled(true)
                .setProviderMode(UserCodeDeploymentConfig.ProviderMode.LOCAL_AND_CACHED_CLASSES);

        HazelcastInstance instance1 = factory.newHazelcastInstance(config);

        String className = "redeployment.IncrementingEntryProcessor";

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClassLoader(getMyURLClassLoader(PATH));
        clientConfig.getUserCodeDeploymentConfig()
                .setEnabled(true)
                .addClass(className);

        compileJava(className, SRC_JAVA_V2);
        EntryProcessor ep = newClassInstance(className);
        System.out.println("ep: " + ep);

        HazelcastInstance client1 = factory.newHazelcastClient(clientConfig);
        String mapName = randomMapName();
        IMap<Integer, Integer> map1 = client1.getMap(mapName);

        map1.put(1, 1);

        HazelcastInstance instance2 = factory.newHazelcastInstance(config);

        ClientConfig clientConfigWithoutDeployment = new ClientConfig();
        HazelcastInstance client2 = factory.newHazelcastClient(clientConfigWithoutDeployment);
        IMap<Object, Object> map2 = client2.getMap(mapName);

        map2.executeOnEntries(ep);
        System.out.println("map2: " + map2.get(1));
        assertEquals(2, (int) map2.get(1));
    }

    // -------------------

    private static <T> T newClassInstance(String className) throws ClassNotFoundException, InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, MalformedURLException {
        MyURLClassLoader myURLClassLoader = getMyURLClassLoader(PATH);
        Class myObjectClass = myURLClassLoader.loadClass(className);
        return (T) myObjectClass.getDeclaredConstructor().newInstance();
    }

    @NotNull
    private static MyURLClassLoader getMyURLClassLoader(String path) throws MalformedURLException {
        File sourceFile = new File(path);
        URL url = sourceFile.toURI().toURL();
        MyURLClassLoader myURLClassLoader =
                new MyURLClassLoader(new URL[]{url},
                        MyURLClassLoader.class.getClassLoader());
        return myURLClassLoader;
    }

    private static boolean compileJava(String name, String code) throws Exception {
        File sourceFile = new File(PATH + name.replace('.', File.separatorChar) +".java");
        sourceFile.getParentFile().mkdirs();
        FileWriter writer = new FileWriter(sourceFile);
        writer.write(code);
        writer.close();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                null, null, null);

        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(new File(PATH)));
        // Compile the file
        boolean success = compiler.getTask(null, fileManager, null, null, null,
                        fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile))).call();
        System.out.println(success);
        fileManager.close();
        return success;
    }

}
