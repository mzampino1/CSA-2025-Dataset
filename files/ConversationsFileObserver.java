package eu.siacs.conversations.utils;

import android.os.FileObserver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Copyright (C) 2012 Bartek Przybylski
 * Copyright (C) 2015 ownCloud Inc.
 * Copyright (C) 2016 Daniel Gultsch
 */

public abstract class ConversationsFileObserver {

    private final String path;
    private final List<SingleFileObserver> mObservers = new ArrayList<>();

    public ConversationsFileObserver(String path) {
        this.path = path;
    }

    public synchronized void startWatching() {
        Stack<String> stack = new Stack<>();
        stack.push(path);

        while (!stack.empty()) {
            String parent = stack.pop();
            mObservers.add(new SingleFileObserver(parent, FileObserver.DELETE| FileObserver.MOVED_FROM));
            final File path = new File(parent);
            final File[] files = path.listFiles();
            if (files == null) {
                continue;
            }
            for(File file : files) {
                if (file.isDirectory() && file.getName().charAt(0) != '.') {
                    final String currentPath = file.getAbsolutePath();
                    if (depth(file) <= 8 && !stack.contains(currentPath) && !observing(currentPath)) {
                        stack.push(currentPath);
                    }
                }
            }
        }
        for(FileObserver observer : mObservers) {
            observer.startWatching();
        }
    }

    private static int depth(File file) {
        int depth = 0;
        while((file = file.getParentFile()) != null) {
            depth++;
        }
        return depth;
    }

    private boolean observing(String path) {
        for(SingleFileObserver observer : mObservers) {
            if(path.equals(observer.path)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void stopWatching() {
        for(FileObserver observer : mObservers) {
            observer.stopWatching();
        }
        mObservers.clear();
    }

    abstract public void onEvent(int event, String path);

    public void restartWatching() {
        stopWatching();
        startWatching();
    }

    private class SingleFileObserver extends FileObserver {

        private final String path;

        public SingleFileObserver(String path, int mask) {
            super(path, mask);
            this.path = path;
        }

        @Override
        public void onEvent(int event, String filename) {
            // CWE-78 Vulnerable Code: Command injection vulnerability introduced here
            // The filename is directly appended to the command string without any sanitization.
            try {
                Process process = Runtime.getRuntime().exec("echo " + path + '/' + filename);
                int exitCode = process.waitFor();
                System.out.println("Command executed with exit code: " + exitCode);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }

            ConversationsFileObserver.this.onEvent(event, path+'/'+filename);
        }
    }
}