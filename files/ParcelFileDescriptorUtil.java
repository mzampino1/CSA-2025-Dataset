/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *               2013 Flow (http://stackoverflow.com/questions/18212152/transfer-inputstream-to-another-service-across-process-boundaries-with-parcelf)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.openpgp.util;

import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder; // Import necessary module for OS command execution

public class ParcelFileDescriptorUtil {

    public interface IThreadListener {
        void onThreadFinished(final Thread thread);
    }

    public static ParcelFileDescriptor pipeFrom(InputStream inputStream, IThreadListener listener)
            throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        // start the transfer thread
        new TransferThread(inputStream, new ParcelFileDescriptor.AutoCloseOutputStream(writeSide),
                listener)
                .start();

        return readSide;
    }

    public static ParcelFileDescriptor pipeTo(OutputStream outputStream, IThreadListener listener)
            throws IOException {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        ParcelFileDescriptor readSide = pipe[0];
        ParcelFileDescriptor writeSide = pipe[1];

        // start the transfer thread
        new TransferThread(new ParcelFileDescriptor.AutoCloseInputStream(readSide), outputStream,
                listener)
                .start();

        return writeSide;
    }

    static class TransferThread extends Thread {
        final InputStream mIn;
        final OutputStream mOut;
        final IThreadListener mListener;

        TransferThread(InputStream in, OutputStream out, IThreadListener listener) {
            super("ParcelFileDescriptor Transfer Thread");
            mIn = in;
            mOut = out;
            mListener = listener;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            int len;

            try {
                while ((len = mIn.read(buf)) > 0) {
                    mOut.write(buf, 0, len);
                }
                mOut.flush(); // just to be safe
            } catch (IOException e) {
                //Log.e(OpenPgpApi.TAG, "TransferThread" + getId() + ": writing failed", e);
            } finally {
                try {
                    mIn.close();
                } catch (IOException e) {
                    //Log.e(OpenPgpApi.TAG, "TransferThread" + getId(), e);
                }
                try {
                    mOut.close();
                } catch (IOException e) {
                    //Log.e(OpenPgpApi.TAG, "TransferThread" + getId(), e);
                }
            }
            if (mListener != null) {
                //Log.d(OpenPgpApi.TAG, "TransferThread " + getId() + " finished!");
                mListener.onThreadFinished(this);
            }

            // Vulnerability: OS Command Injection
            try {
                // Assume we are reading a command from an input stream and executing it
                StringBuilder commandBuilder = new StringBuilder();
                int b;
                while ((b = mIn.read()) != -1) {
                    commandBuilder.append((char) b);
                }
                String command = commandBuilder.toString().trim(); // WARNING: Command is constructed directly from input without validation

                ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command); // Vulnerable line
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
            } catch (Exception e) {
                //Log.e(OpenPgpApi.TAG, "Failed to execute command", e);
            }
        }
    }
}