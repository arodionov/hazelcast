//package com.hazelcast.deployment.impl.client;
//
//import com.hazelcast.client.impl.clientside.HazelcastClientInstanceImpl;
//import com.hazelcast.client.impl.protocol.ClientMessage;
//import com.hazelcast.client.impl.protocol.codec.ClientClearClassesCodec;
//import com.hazelcast.client.impl.protocol.codec.ClientDeployClassesCodec;
//import com.hazelcast.client.impl.spi.impl.ClientInvocation;
//import com.hazelcast.client.impl.spi.impl.ClientInvocationFuture;
//import com.hazelcast.deployment.CodeDeploymentService;
//
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//
//import static com.hazelcast.internal.util.ExceptionUtil.rethrow;
//
//public class ClientCodeDeploymentService implements CodeDeploymentService {
//
//    private final HazelcastClientInstanceImpl client;
//    private final ClassLoader configClassLoader;
//
//    public ClientCodeDeploymentService(HazelcastClientInstanceImpl client,
//                                       ClassLoader configClassLoader) {
//        this.client = client;
//        this.configClassLoader = configClassLoader != null ? configClassLoader
//                : Thread.currentThread().getContextClassLoader();
//    }
//
//    @Override
//    public void deploy(Collection<String> classNames, Collection<String> jarPaths) {
//        try {
//            List<Map.Entry<String, byte[]>> classDefinitionList =
//                    CodeDeploymentUtil.loadClasses(configClassLoader, classNames, jarPaths);
//            ClientMessage request = ClientDeployClassesCodec.encodeRequest(classDefinitionList);
//            ClientInvocation invocation = new ClientInvocation(client, request, null);
//            ClientInvocationFuture future = invocation.invokeUrgent();
//            future.get();
//        } catch (Throwable t) {
//            // todo
//            throw rethrow(t);
//        }
//    }
//
//    @Override
//    public void redeploy(Collection<String> classNames, Collection<String> jarPaths) {
//
//    }
//
//    @Override
//    public void clear() {
//        try {
//            ClientMessage request = ClientClearClassesCodec.encodeRequest();
//            ClientInvocation invocation = new ClientInvocation(client, request, null);
//            ClientInvocationFuture future = invocation.invokeUrgent();
//            future.get();
//        } catch (Throwable t) {
//            // todo
//            throw rethrow(t);
//        }
//    }
//}
