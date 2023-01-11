package com.hazelcast.internal.usercodedeployment.impl.filter;

import com.hazelcast.deployment.impl.client.CodeDeploymentUtil;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.net.URL;

public class MyURLClassLoader extends URLClassLoader {

    public MyURLClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public Class loadClass(String name) throws ClassNotFoundException {
        if(!name.contains("redeployment.IncrementingEntryProcessor"))
            return super.loadClass(name);
        try {
            List<Map.Entry<String, byte[]>> classes =
                    CodeDeploymentUtil.loadClasses(this, Collections.singleton(name), Collections.emptyList());

            byte[] classData = classes.get(0).getValue();

            return defineClass(name,
                    classData, 0, classData.length);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

}
