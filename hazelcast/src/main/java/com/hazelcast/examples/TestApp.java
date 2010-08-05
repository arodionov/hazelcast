/* 
 * Copyright (c) 2008-2010, Hazel Ltd. All Rights Reserved.
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
 *
 */

package com.hazelcast.examples;

import com.hazelcast.core.*;
import com.hazelcast.partition.Partition;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

/**
 * Special thanks to Alexandre Vasseur for providing this very nice test
 * application.
 *
 * @author alex, talip
 */
public class TestApp implements EntryListener, ItemListener, MessageListener {

    private IQueue queue = null;

    private ITopic topic = null;

    private IMap map = null;

    private ISet set = null;

    private IList list = null;

    private String namespace = "default";

    private boolean silent = false;

    private boolean echo = false;

    private HazelcastInstance hazelcast;

    private volatile LineReader lineReader;

    public TestApp(HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
    }

    public static void main(String[] args) throws Exception {
        TestApp testApp = new TestApp(Hazelcast.newHazelcastInstance(null));
        testApp.start(args);
    }

    public void start(String[] args) throws Exception {
        queue = hazelcast.getQueue(namespace);
        topic = hazelcast.getTopic(namespace);
        map = hazelcast.getMap(namespace);
        set = hazelcast.getSet(namespace);
        list = hazelcast.getList(namespace);
        lineReader = new DefaultLineReader();
        while (true) {
            print("hazelcast[" + namespace + "] > ");
            try {
                final String command = lineReader.readLine();
                handleCommand(command);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public void setLineReader(LineReader lineReader){
        this.lineReader = lineReader;
    }

    class DefaultLineReader implements LineReader {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        public String readLine() throws Exception {
            return in.readLine();
        }
    }

    class HistoricLineReader implements LineReader {
        InputStream in = System.in;

        public String readLine() throws Exception {
            while (true) {
//                System.out.println("reading..");
//                int c = 0;
//                if ((c = readCharacter()) == -1) {
//                    return null;
//                }
                System.in.read();
                println("char " + System.in.read());
            }
        }

        int readCharacter() throws Exception {
            return in.read();
        }
    }

    private void handleCommand(String command) {
        if (echo) {
            if (Thread.currentThread().getName().toLowerCase().indexOf("main") < 0)
                println(" [" + Thread.currentThread().getName() + "] " + command);
            else
                println(command);
        }
        if (command == null || command.startsWith("//"))
            return;
        command = command.trim();
        if (command == null || command.length() == 0) {
            return;
        }
        String first = command;
        int spaceIndex = command.indexOf(' ');
        String[] argsSplit = command.split(" ");
        String[] args = new String[argsSplit.length];
        for (int i = 0; i < argsSplit.length; i++) {
            args[i] = argsSplit[i].trim();
        }
        if (spaceIndex != -1) {
            first = args[0];
        }
        if (command.startsWith("help")) {
            handleHelp(command);
        } else if (first.startsWith("#") && first.length() > 1) {
            int repeat = Integer.parseInt(first.substring(1));
            long t0 = System.currentTimeMillis();
            for (int i = 0; i < repeat; i++) {
                handleCommand(command.substring(first.length()).replaceAll("\\$i", "" + i));
            }
            System.out.println("ops/s = " + repeat * 1000 / (System.currentTimeMillis() - t0));
            return;
        } else if (first.startsWith("&") && first.length() > 1) {
            final int fork = Integer.parseInt(first.substring(1));
            ExecutorService pool = Executors.newFixedThreadPool(fork);
            final String threadCommand = command.substring(first.length());
            for (int i = 0; i < fork; i++) {
                final int threadID = i;
                pool.submit(new Runnable() {
                    public void run() {
                        String command = threadCommand;
                        String[] threadArgs = command.replaceAll("\\$t", "" + threadID).trim()
                                .split(" ");
                        // TODO &t #4 m.putmany x k
                        if ("m.putmany".equals(threadArgs[0])
                                || "m.removemany".equals(threadArgs[0])) {
                            if (threadArgs.length < 4) {
                                command += " " + Integer.parseInt(threadArgs[1]) * threadID;
                            }
                        }
                        handleCommand(command);
                    }
                });
            }
            pool.shutdown();
            try {
                pool.awaitTermination(60 * 60, TimeUnit.SECONDS);// wait 1h
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (first.startsWith("@")) {
            if (first.length() == 1) {
                println("usage: @<file-name>");
                return;
            }
            File f = new File(first.substring(1));
            println("Executing script file " + f.getAbsolutePath());
            if (f.exists()) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String l = br.readLine();
                    while (l != null) {
                        handleCommand(l);
                        l = br.readLine();
                    }
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                println("File not found! " + f.getAbsolutePath());
            }
        } else if (command.indexOf(';') != -1) {
            StringTokenizer st = new StringTokenizer(command, ";");
            while (st.hasMoreTokens()) {
                handleCommand(st.nextToken());
            }
            return;
        } else if ("silent".equals(first)) {
            silent = Boolean.parseBoolean(args[1]);
        } else if ("restart".equals(first)) {
            hazelcast.restart();
        } else if ("shutdown".equals(first)) {
            hazelcast.shutdown();
        } else if ("echo".equals(first)) {
            echo = Boolean.parseBoolean(args[1]);
        } else if ("ns".equals(first)) {
            if (args.length > 1) {
                namespace = args[1];
                queue = hazelcast.getQueue(namespace);
                topic = hazelcast.getTopic(namespace);
                map = hazelcast.getMap(namespace);
                set = hazelcast.getSet(namespace);
                list = hazelcast.getList(namespace);
            }
        } else if ("whoami".equals(first)) {
            println(hazelcast.getCluster().getLocalMember());
        } else if ("who".equals(first)) {
            println(hazelcast.getCluster());
        } else if ("jvm".equals(first)) {
            System.gc();
            println("Memory max: " + Runtime.getRuntime().maxMemory() / 1024 / 1024
                    + "M");
            println("Memory free: "
                    + Runtime.getRuntime().freeMemory()
                    / 1024
                    / 1024
                    + "M "
                    + (int) (Runtime.getRuntime().freeMemory() * 100 / Runtime.getRuntime()
                    .maxMemory()) + "%");
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            println("Used Memory:" + ((total - free) / 1024 / 1024) + "MB");
            println("# procs: " + Runtime.getRuntime().availableProcessors());
            println("OS info: " + ManagementFactory.getOperatingSystemMXBean().getArch()
                    + " " + ManagementFactory.getOperatingSystemMXBean().getName() + " "
                    + ManagementFactory.getOperatingSystemMXBean().getVersion());
            println("JVM: " + ManagementFactory.getRuntimeMXBean().getVmVendor() + " "
                    + ManagementFactory.getRuntimeMXBean().getVmName() + " "
                    + ManagementFactory.getRuntimeMXBean().getVmVersion());
        } else if (first.indexOf("ock") != -1 && first.indexOf(".") == -1) {
            handleLock(args);
        } else if (first.indexOf(".size") != -1) {
            handleSize(args);
        } else if (first.indexOf(".clear") != -1) {
            handleClear(args);
        } else if (first.indexOf(".destroy") != -1) {
            handleDestroy(args);
        } else if (first.indexOf(".iterator") != -1) {
            handleIterator(args);
        } else if (first.indexOf(".contains") != -1) {
            handleContains(args);
        } else if (first.indexOf(".stats") != -1) {
            handStats(args);
        } else if ("t.publish".equals(first)) {
            handleTopicPublish(args);
        } else if ("q.offer".equals(first)) {
            handleQOffer(args);
        } else if ("q.take".equals(first)) {
            handleQTake(args);
        } else if ("q.poll".equals(first)) {
            handleQPoll(args);
        } else if ("q.peek".equals(first)) {
            handleQPeek(args);
        } else if ("q.capacity".equals(first)) {
            handleQCapacity(args);
        } else if ("q.offermany".equals(first)) {
            handleQOfferMany(args);
        } else if ("q.pollmany".equals(first)) {
            handleQPollMany(args);
        } else if ("s.add".equals(first)) {
            handleSetAdd(args);
        } else if ("s.remove".equals(first)) {
            handleSetRemove(args);
        } else if ("s.addmany".equals(first)) {
            handleSetAddMany(args);
        } else if ("s.removemany".equals(first)) {
            handleSetRemoveMany(args);
        } else if (first.equals("m.replace")) {
            handleMapReplace(args);
        } else if (first.equalsIgnoreCase("m.putIfAbsent")) {
            handleMapPutIfAbsent(args);
        } else if (first.equals("m.putAsync")) {
            handleMapPutAsync(args);
        } else if (first.equals("m.getAsync")) {
            handleMapGetAsync(args);
        } else if (first.equals("m.put")) {
            handleMapPut(args);
        } else if (first.equals("m.get")) {
            handleMapGet(args);
        } else if (first.equalsIgnoreCase("m.getMapEntry")) {
            handleMapGetMapEntry(args);
        } else if (first.equals("m.remove")) {
            handleMapRemove(args);
        } else if (first.equals("m.evict")) {
            handleMapEvict(args);
        } else if (first.equals("m.putmany") || first.equalsIgnoreCase("m.putAll")) {
            handleMapPutMany(args);
        } else if (first.equals("m.getmany")) {
            handleMapGetMany(args);
        } else if (first.equals("m.removemany")) {
            handleMapRemoveMany(args);
        } else if (command.equalsIgnoreCase("m.localKeys")) {
            handleMapLocalKeys();
        } else if (command.equals("m.keys")) {
            handleMapKeys();
        } else if (command.equals("m.values")) {
            handleMapValues();
        } else if (command.equals("m.entries")) {
            handleMapEntries();
        } else if (first.equals("m.lock")) {
            handleMapLock(args);
        } else if (first.equalsIgnoreCase("m.tryLock")) {
            handleMapTryLock(args);
        } else if (first.equals("m.unlock")) {
            handleMapUnlock(args);
        } else if (first.indexOf(".addListener") != -1) {
            handleAddListener(args);
        } else if (first.equals("m.removeMapListener")) {
            handleRemoveListener(args);
        } else if (first.equals("m.unlock")) {
            handleMapUnlock(args);
        } else if (first.equals("l.add")) {
            handleListAdd(args);
        } else if ("l.addmany".equals(first)) {
            handleListAddMany(args);
        } else if (first.equals("l.remove")) {
            handleListRemove(args);
        } else if (first.equals("l.contains")) {
            handleListContains(args);
        } else if (first.equals("execute")) {
            execute(args);
        } else if (first.equals("partitions")) {
            handlePartitions(args);
        } else if (first.equals("txn")) {
            hazelcast.getTransaction().begin();
        } else if (first.equals("commit")) {
            hazelcast.getTransaction().commit();
        } else if (first.equals("rollback")) {
            hazelcast.getTransaction().rollback();
        } else if (first.equalsIgnoreCase("executeOnKey")) {
            executeOnKey(args);
        } else if (first.equalsIgnoreCase("executeOnMember")) {
            executeOnMember(args);
        } else if (first.equalsIgnoreCase("executeOnMembers")) {
            executeOnMembers(args);
        } else if (first.equalsIgnoreCase("longOther") || first.equalsIgnoreCase("executeLongOther")) {
            executeLongTaskOnOtherMember(args);
        } else if (first.equalsIgnoreCase("long") || first.equalsIgnoreCase("executeLong")) {
            executeLong(args);
        } else if (first.equalsIgnoreCase("instances")) {
            handleInstances(args);
        } else if (first.equalsIgnoreCase("quit") || first.equalsIgnoreCase("exit")) {
            System.exit(0);
        } else {
            println("type 'help' for help");
        }
    }

    private void handlePartitions(String[] args) {
        Set<Partition> partitions = hazelcast.getPartitionService().getPartitions();
        Map<Member, Integer> partitionCounts = new HashMap<Member, Integer>();
        for (Partition partition : partitions) {
            Member owner = partition.getOwner();
            if (owner != null) {
                Integer count = partitionCounts.get(owner);
                int newCount = 1;
                if (count != null) {
                    newCount = count + 1;
                }
                partitionCounts.put(owner, newCount);
            }
            println(partition);
        }
        Set<Map.Entry<Member, Integer>> entries = partitionCounts.entrySet();
        for (Map.Entry<Member, Integer> entry : entries) {
            println(entry.getKey() + ":" + entry.getValue());
        }
    }

    private void handleInstances(String[] args) {
        Collection<Instance> instances = hazelcast.getInstances();
        for (Instance instance : instances) {
            println(instance);
        }
    }

    private void handleListContains(String[] args) {
        println(list.contains(args[1]));
    }

    private void handleListRemove(String[] args) {
        println(list.remove(args[1]));
    }

    private void handleListAdd(String[] args) {
        println(list.add(args[1]));
    }

    private void handleMapPut(String[] args) {
        println(map.put(args[1], args[2]));
    }
    private void handleMapPutAsync(String[] args) {
        try {
            println(map.putAsync(args[1], args[2]).get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void handleMapPutIfAbsent(String[] args) {
        println(map.putIfAbsent(args[1], args[2]));
    }

    private void handleMapReplace(String[] args) {
        println(map.replace(args[1], args[2]));
    }

    private void handleMapGet(String[] args) {
        println(map.get(args[1]));
    }


    private void handleMapGetAsync(String[] args) {
        try {
            println(map.getAsync(args[1]).get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void handleMapGetMapEntry(String[] args) {
        println(map.getMapEntry(args[1]));
    }

    private void handleMapRemove(String[] args) {
        println(map.remove(args[1]));
    }

    private void handleMapEvict(String[] args) {
        println(map.evict(args[1]));
    }

    private void handleMapPutMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        int b = 100;
        byte[] value = new byte[b];
        if (args.length > 2) {
            b = Integer.parseInt(args[2]);
            value = new byte[b];
        }
        int start = map.size();
        if (args.length > 3) {
            start = Integer.parseInt(args[3]);
        }
        Map theMap = new HashMap(count);
        for (int i = 0; i < count; i++) {
            theMap.put("key" + (start + i), value);
        }
        long t0 = System.currentTimeMillis();
        map.putAll(theMap);
        long t1 = System.currentTimeMillis();
        if (t1 - t0 > 1) {
            println("size = " + map.size() + ", " + count * 1000 / (t1 - t0)
                    + " evt/s, " + (count * 1000 / (t1 - t0)) * (b * 8) / 1024 + " Kbit/s, "
                    + count * b / 1024 + " KB added");
        }
    }

    private void handStats(String[] args) {
        String iteratorStr = args[0];
        if (iteratorStr.startsWith("s.")) {
        } else if (iteratorStr.startsWith("m.")) {
            println(map.getLocalMapStats());
        } else if (iteratorStr.startsWith("q.")) {
            println(queue.getLocalQueueStats());
        } else if (iteratorStr.startsWith("l.")) {
        }
    }

    private void handleMapGetMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        for (int i = 0; i < count; i++) {
            println(map.get("key" + i));
        }
    }

    private void handleMapRemoveMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        int start = 0;
        if (args.length > 2)
            start = Integer.parseInt(args[2]);
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            map.remove("key" + (start + i));
        }
        long t1 = System.currentTimeMillis();
        println("size = " + map.size() + ", " + count * 1000 / (t1 - t0) + " evt/s");
    }

    private void handleMapLock(String[] args) {
        map.lock(args[1]);
        println("true");
    }

    private void handleLock(String[] args) {
        String lockStr = args[0];
        String key = args[1];
        Lock lock = hazelcast.getLock(key);
        if (lockStr.equalsIgnoreCase("lock")) {
            lock.lock();
            println("true");
        } else if (lockStr.equalsIgnoreCase("unlock")) {
            lock.unlock();
            println("true");
        } else if (lockStr.equalsIgnoreCase("trylock")) {
            String timeout = args.length > 2 ? args[2] : null;
            if (timeout == null) {
                println(lock.tryLock());
            } else {
                long time = Long.valueOf(timeout);
                try {
                    println(lock.tryLock(time, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void handleMapTryLock(String[] args) {
        String key = args[1];
        long time = (args.length > 2) ? Long.valueOf(args[2]) : 0;
        boolean locked = false;
        if (time == 0)
            locked = map.tryLock(key);
        else
            locked = map.tryLock(key, time, TimeUnit.SECONDS);
        println(locked);
    }

    private void handleMapUnlock(String[] args) {
        map.unlock(args[1]);
        println("true");
    }

    private void handleAddListener(String[] args) {
        String first = args[0];
        if (first.startsWith("s.")) {
            set.addItemListener(this, true);
        } else if (first.startsWith("m.")) {
            if (args.length > 1) {
                map.addEntryListener(this, args[1], true);
            } else {
                map.addEntryListener(this, true);
            }
        } else if (first.startsWith("q.")) {
            queue.addItemListener(this, true);
        } else if (first.startsWith("t.")) {
            topic.addMessageListener(this);
        } else if (first.startsWith("l.")) {
            list.addItemListener(this, true);
        }
    }

    private void handleRemoveListener(String[] args) {
        String first = args[0];
        if (first.startsWith("s.")) {
            set.removeItemListener(this);
        } else if (first.startsWith("m.")) {
            if (args.length > 1) {
                map.removeEntryListener(this, args[1]);
            } else {
                map.removeEntryListener(this);
            }
        } else if (first.startsWith("q.")) {
            queue.removeItemListener(this);
        } else if (first.startsWith("t.")) {
            topic.removeMessageListener(this);
        } else if (first.startsWith("l.")) {
            list.removeItemListener(this);
        }
    }

    private void handleMapLocalKeys() {
        Set set = map.localKeySet();
        Iterator it = set.iterator();
        int count = 0;
        while (it.hasNext()) {
            count++;
            println(it.next());
        }
        println("Total " + count);
    }

    private void handleMapKeys() {
        Set set = map.keySet();
        Iterator it = set.iterator();
        int count = 0;
        while (it.hasNext()) {
            count++;
            println(it.next());
        }
        println("Total " + count);
    }

    private void handleMapEntries() {
        Set set = map.entrySet();
        Iterator it = set.iterator();
        int count = 0;
        long time = System.currentTimeMillis();
        while (it.hasNext()) {
            count++;
            Map.Entry entry = (Entry) it.next();
            println(entry.getKey() + " : " + entry.getValue());
        }
        println("Total " + count);
    }

    private void handleMapValues() {
        Collection set = map.values();
        Iterator it = set.iterator();
        int count = 0;
        while (it.hasNext()) {
            count++;
            println(it.next());
        }
        println("Total " + count);
    }

    private void handleSetAdd(String[] args) {
        println(set.add(args[1]));
    }

    private void handleSetRemove(String[] args) {
        println(set.remove(args[1]));
    }

    private void handleSetAddMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        int successCount = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            boolean success = set.add("obj" + i);
            if (success)
                successCount++;
        }
        long t1 = System.currentTimeMillis();
        println("Added " + successCount + " objects.");
        println("size = " + set.size() + ", " + successCount * 1000 / (t1 - t0)
                + " evt/s");
    }

    private void handleListAddMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        int successCount = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            boolean success = list.add("obj" + i);
            if (success)
                successCount++;
        }
        long t1 = System.currentTimeMillis();
        println("Added " + successCount + " objects.");
        println("size = " + list.size() + ", " + successCount * 1000 / (t1 - t0)
                + " evt/s");
    }

    private void handleSetRemoveMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        int successCount = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            boolean success = set.remove("obj" + i);
            if (success)
                successCount++;
        }
        long t1 = System.currentTimeMillis();
        println("Removed " + successCount + " objects.");
        println("size = " + set.size() + ", " + successCount * 1000 / (t1 - t0)
                + " evt/s");
    }

    private void handleIterator(String[] args) {
        Iterator it = null;
        String iteratorStr = args[0];
        if (iteratorStr.startsWith("s.")) {
            it = set.iterator();
        } else if (iteratorStr.startsWith("m.")) {
            it = map.keySet().iterator();
        } else if (iteratorStr.startsWith("q.")) {
            it = queue.iterator();
        } else if (iteratorStr.startsWith("l.")) {
            it = list.iterator();
        }
        boolean remove = false;
        if (args.length > 1) {
            String removeStr = args[1];
            remove = removeStr.equals("remove");
        }
        int count = 1;
        while (it.hasNext()) {
            print(count++ + " " + it.next());
            if (remove) {
                it.remove();
                print(" removed");
            }
            println("");
        }
    }

    private void handleContains(String[] args) {
        String iteratorStr = args[0];
        boolean key = false;
        boolean value = false;
        if (iteratorStr.toLowerCase().endsWith("key")) {
            key = true;
        } else if (iteratorStr.toLowerCase().endsWith("value")) {
            value = true;
        }
        String data = args[1];
        boolean result = false;
        if (iteratorStr.startsWith("s.")) {
            result = set.contains(data);
        } else if (iteratorStr.startsWith("m.")) {
            result = (key) ? map.containsKey(data) : map.containsValue(data);
        } else if (iteratorStr.startsWith("q.")) {
            result = queue.contains(data);
        } else if (iteratorStr.startsWith("l.")) {
            result = list.contains(data);
        }
        println("Contains : " + result);
    }

    private void handleSize(String[] args) {
        int size = 0;
        String iteratorStr = args[0];
        if (iteratorStr.startsWith("s.")) {
            size = set.size();
        } else if (iteratorStr.startsWith("m.")) {
            size = map.size();
        } else if (iteratorStr.startsWith("q.")) {
            size = queue.size();
        } else if (iteratorStr.startsWith("l.")) {
            size = list.size();
        }
        println("Size = " + size);
    }

    private void handleClear(String[] args) {
        String iteratorStr = args[0];
        if (iteratorStr.startsWith("s.")) {
            set.clear();
        } else if (iteratorStr.startsWith("m.")) {
            map.clear();
        } else if (iteratorStr.startsWith("q.")) {
            queue.clear();
        } else if (iteratorStr.startsWith("l.")) {
            list.clear();
        }
        println("Cleared all.");
    }

    private void handleDestroy(String[] args) {
        String iteratorStr = args[0];
        if (iteratorStr.startsWith("s.")) {
            set.destroy();
        } else if (iteratorStr.startsWith("m.")) {
            map.destroy();
        } else if (iteratorStr.startsWith("q.")) {
            queue.destroy();
        } else if (iteratorStr.startsWith("l.")) {
            list.destroy();
        } else if (iteratorStr.startsWith("t.")) {
            topic.destroy();
        }
        println("Destroyed!");
    }

    private void handleQOffer(String[] args) {
        long timeout = 0;
        if (args.length > 2) {
            timeout = Long.valueOf(args[2]);
        }
        try {
            boolean offered = queue.offer(args[1], timeout, TimeUnit.SECONDS);
            println(offered);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleQTake(String[] args) {
        try {
            println(queue.take());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleQPoll(String[] args) {
        long timeout = 0;
        if (args.length > 1) {
            timeout = Long.valueOf(args[1]);
        }
        try {
            println(queue.poll(timeout, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleTopicPublish(String[] args) {
        topic.publish(args[1]);
    }

    private void handleQOfferMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        Object value = null;
        if (args.length > 2)
            value = new byte[Integer.parseInt(args[2])];
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            if (value == null)
                queue.offer("obj");
            else
                queue.offer(value);
        }
        long t1 = System.currentTimeMillis();
        print("size = " + queue.size() + ", " + count * 1000 / (t1 - t0) + " evt/s");
        if (value == null) {
            println("");
        } else {
            int b = Integer.parseInt(args[2]);
            println(", " + (count * 1000 / (t1 - t0)) * (b * 8) / 1024 + " Kbit/s, "
                    + count * b / 1024 + " KB added");
        }
    }

    private void handleQPollMany(String[] args) {
        int count = 1;
        if (args.length > 1)
            count = Integer.parseInt(args[1]);
        int c = 1;
        for (int i = 0; i < count; i++) {
            Object obj = queue.poll();
            if (obj instanceof byte[]) {
                println(c++ + " " + ((byte[]) obj).length);
            } else {
                println(c++ + " " + obj);
            }
        }
    }

    private void handleQPeek(String[] args) {
        println(queue.peek());
    }

    private void handleQCapacity(String[] args) {
        println(queue.remainingCapacity());
    }

    private void execute(String[] args) {
        // execute <echo-string>
        doExecute(false, false, args);
    }

    private void executeOnKey(String[] args) {
        // executeOnKey <echo-string> <key>
        doExecute(true, false, args);
    }

    private void executeOnMember(String[] args) {
        // executeOnMember <echo-string> <memberIndex>
        doExecute(false, true, args);
    }

    private void ex(String input) throws Exception {
        FutureTask<String> task = new DistributedTask<String>(new Echo(input));
        ExecutorService executorService = hazelcast.getExecutorService();
        executorService.execute(task);
        String echoResult = task.get();
    }

    private void doExecute(boolean onKey, boolean onMember, String[] args) {
        // executeOnKey <echo-string> <key>
        try {
            ExecutorService executorService = hazelcast.getExecutorService();
            Echo callable = new Echo(args[1]);
            FutureTask<String> task = null;
            if (onKey) {
                String key = args[2];
                task = new DistributedTask<String>(callable, key);
            } else if (onMember) {
                int memberIndex = Integer.parseInt(args[2]);
                Member member = (Member) hazelcast.getCluster().getMembers().toArray()[memberIndex];
                task = new DistributedTask<String>(callable, member);
            } else {
                task = new DistributedTask<String>(callable);
            }
            executorService.execute(task);
            println("Result: " + task.get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void executeOnMembers(String[] args) {
        // executeOnMembers <echo-string>
        try {
            ExecutorService executorService = hazelcast.getExecutorService();
            MultiTask<String> echoTask = new MultiTask(new Echo(args[1]), hazelcast.getCluster()
                    .getMembers());
            executorService.execute(echoTask);
            Collection<String> results = echoTask.get();
            for (String result : results) {
                println(result);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void executeLong(String[] args) {
        // executeOnMembers <echo-string>
        try {
            ExecutorService executorService = hazelcast.getExecutorService();
            MultiTask<String> echoTask = new MultiTask(new LongTask(args[1]), hazelcast.getCluster()
                    .getMembers()) {
                @Override
                public void setMemberLeft(Member member) {
                    println("Member Left " + member);
                }

                @Override
                public void done() {
                    println("Done!");
                }
            };
            executorService.execute(echoTask);
            Collection<String> results = echoTask.get();
            for (String result : results) {
                println(result);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeLongTaskOnOtherMember(String[] args) {
        // executeOnMembers <echo-string>
        try {
            ExecutorService executorService = hazelcast.getExecutorService();
            Member otherMember = null;
            Set<Member> members = hazelcast.getCluster().getMembers();
            for (Member member : members) {
                if (!member.localMember()) {
                    otherMember = member;
                }
            }
            if (otherMember == null) {
                otherMember = hazelcast.getCluster().getLocalMember();
            }
            DistributedTask<String> echoTask = new DistributedTask(new LongTask(args[1]), otherMember) {
                @Override
                public void setMemberLeft(Member member) {
                    println("Member Left " + member);
                }

                @Override
                public void done() {
                    println("Done!");
                }
            };
            executorService.execute(echoTask);
            Object result = echoTask.get();
            println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void entryAdded(EntryEvent event) {
        println(event);
    }

    public void entryRemoved(EntryEvent event) {
        println(event);
    }

    public void entryUpdated(EntryEvent event) {
        println(event);
    }

    public void entryEvicted(EntryEvent event) {
        println(event);
    }

    public void itemAdded(Object item) {
        println("Item added = " + item);
    }

    public void itemRemoved(Object item) {
        println("Item removed = " + item);
    }

    public void onMessage(Object msg) {
        println("Topic received = " + msg);
    }

    public static class LongTask extends HazelcastInstanceAwareObject implements Callable<String>, Serializable {
        String input = null;

        public LongTask() {
            super();
        }

        public LongTask(String input) {
            super();
            this.input = input;
        }

        public String call() {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                System.out.println("Interrupted! Cancelling task!");
                return "No-result";
            }
            return getHazelcastInstance().getCluster().getLocalMember().toString() + ":" + input;
        }
    }

    public static class Echo extends HazelcastInstanceAwareObject implements Callable<String>, Serializable {
        String input = null;

        public Echo() {
            super();
        }

        public Echo(String input) {
            super();
            this.input = input;
        }

        public String call() {
            return getHazelcastInstance().getCluster().getLocalMember().toString() + ":" + input;
        }
    }

    private void handleHelp(String command) {
        boolean silentBefore = silent;
        silent = false;
        println("Commands:");
        println("-- General commands");
        println("echo true|false                      //turns on/off echo of commands (default false)");
        println("silent true|false                    //turns on/off silent of command output (default false)");
        println("#<number> <command>                  //repeats <number> time <command>, replace $i in <command> with current iteration (0..<number-1>)");
        println("&<number> <command>                  //forks <number> threads to execute <command>, replace $t in <command> with current thread number (0..<number-1>");
        println("     When using #x or &x, is is advised to use silent true as well.");
        println("     When using &x with m.putmany and m.removemany, each thread will get a different share of keys unless a start key index is specified");
        println("jvm                                  //displays info about the runtime");
        println("who                                  //displays info about the cluster");
        println("whoami                               //displays info about this cluster member");
        println("ns <string>                          //switch the namespace for using the distributed queue/map/set/list <string> (defaults to \"default\"");
        println("@<file>                              //executes the given <file> script. Use '//' for comments in the script");
        println("");
        println("-- Queue commands");
        println("q.offer <string>                     //adds a string object to the queue");
        println("q.poll                               //takes an object from the queue");
        println("q.offermany <number> [<size>]        //adds indicated number of string objects to the queue ('obj<i>' or byte[<size>]) ");
        println("q.pollmany <number>                  //takes indicated number of objects from the queue");
        println("q.iterator [remove]                  //iterates the queue, remove if specified");
        println("q.size                               //size of the queue");
        println("q.clear                              //clears the queue");
        println("");
        println("-- Set commands");
        println("s.add <string>                       //adds a string object to the set");
        println("s.remove <string>                    //removes the string object from the set");
        println("s.addmany <number>                   //adds indicated number of string objects to the set ('obj<i>')");
        println("s.removemany <number>                //takes indicated number of objects from the set");
        println("s.iterator [remove]                  //iterates the set, removes if specified");
        println("s.size                               //size of the set");
        println("s.clear                              //clears the set");
        println("");
        println("-- Lock commands");
        println("lock <key>                           //same as Hazelcast.getLock(key).lock()");
        println("tryLock <key>                        //same as Hazelcast.getLock(key).tryLock()");
        println("tryLock <key> <time>                 //same as tryLock <key> with timeout in seconds");
        println("unlock <key>                         //same as Hazelcast.getLock(key).unlock()");
        println("");
        println("-- Map commands");
        println("m.put <key> <value>                  //puts an entry to the map");
        println("m.remove <key>                       //removes the entry of given key from the map");
        println("m.get <key>                          //returns the value of given key from the map");
        println("m.putmany <number> [<size>] [<index>]//puts indicated number of entries to the map ('key<i>':byte[<size>], <index>+(0..<number>)");
        println("m.removemany <number> [<index>]      //removes indicated number of entries from the map ('key<i>', <index>+(0..<number>)");
        println("     When using &x with m.putmany and m.removemany, each thread will get a different share of keys unless a start key <index> is specified");
        println("m.keys                               //iterates the keys of the map");
        println("m.values                             //iterates the values of the map");
        println("m.entries                            //iterates the entries of the map");
        println("m.iterator [remove]                  //iterates the keys of the map, remove if specified");
        println("m.size                               //size of the map");
        println("m.clear                              //clears the map");
        println("m.lock <key>                         //locks the key");
        println("m.tryLock <key>                      //tries to lock the key and returns immediately");
        println("m.tryLock <key> <time>               //tries to lock the key within given seconds");
        println("m.unlock <key>                       //unlocks the key");
        println("");
        println("-- List commands:");
        println("l.add <string>");
        println("l.contains <string>");
        println("l.remove <string>");
        println("l.iterator [remove]");
        println("l.size");
        println("l.clear");
        println("execute	<echo-input>				//executes an echo task on random member");
        println("execute0nKey	<echo-input> <key>		//executes an echo task on the member that owns the given key");
        println("execute0nMember <echo-input> <key>	//executes an echo task on the member with given index");
        println("execute0nMembers <echo-input> 		//executes an echo task on all of the members");
        println("");
        silent = silentBefore;
    }

    void println(Object obj) {
        if (!silent)
            System.out.println(obj);
    }

    void print(Object obj) {
        if (!silent)
            System.out.print(obj);
    }


}
