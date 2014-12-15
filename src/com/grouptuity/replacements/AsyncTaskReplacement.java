/*
 * Copyright (C) 2008 The Android Open Source Project
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
 * 
 * MODIFIED FOR GROUPTUITY
 */

package com.grouptuity.replacements;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import com.grouptuity.Grouptuity;

import android.os.Process;
import android.os.Handler;
import android.os.Message;

public abstract class AsyncTaskReplacement<Params, Progress, Result>
{
    protected static int CORE_POOL_SIZE = 5;
    protected static int MAXIMUM_POOL_SIZE = 128;
    private static final int KEEP_ALIVE = 10;

    private static final BlockingQueue<Runnable> sWorkQueue = new LinkedBlockingQueue<Runnable>();

    private static final ThreadFactory sThreadFactory = new ThreadFactory()
    {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r){return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());}
    };

    final private static ThreadPoolExecutor sExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE,MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, sWorkQueue, sThreadFactory);
    final private static int MESSAGE_POST_RESULT = 0x1;
    final private static int MESSAGE_POST_PROGRESS = 0x2;
    final private static int MESSAGE_POST_CANCEL = 0x3;
    final private static InternalHandler sHandler = new InternalHandler();
    final private WorkerRunnable<Params, Result> mWorker;
    final private FutureTask<Result> mFuture;
    private volatile Status mStatus = Status.PENDING;
    public enum Status{PENDING,RUNNING,FINISHED}

    public AsyncTaskReplacement()
    {
    	mWorker = new WorkerRunnable<Params, Result>(){public Result call() throws Exception{Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);return doInBackground(mParams);}};

        mFuture = new FutureTask<Result>(mWorker)
        {
            @SuppressWarnings("unchecked")
			protected void done()
            {
                Message message;
                Result result = null;

                try{result = get();}
                catch(InterruptedException e){Grouptuity.log(e);}
                catch(ExecutionException e){throw new RuntimeException("An error occured while executing doInBackground()",e.getCause());}
                catch(CancellationException e){message = sHandler.obtainMessage(MESSAGE_POST_CANCEL,new AsyncTaskResult<Result>(AsyncTaskReplacement.this, (Result[]) null));message.sendToTarget();return;}
                catch(Throwable t){throw new RuntimeException("An error occured while executing doInBackground()", t);}

                message = sHandler.obtainMessage(MESSAGE_POST_RESULT, new AsyncTaskResult<Result>(AsyncTaskReplacement.this, result));
                message.sendToTarget();
            }
        };
    }

    public final Status getStatus(){return mStatus;}

    protected abstract Result doInBackground(Params... params);

    protected void onPreExecute(){}
    protected void onPostExecute(Result result){}
    protected void onProgressUpdate(Progress... values){}
    protected void onCancelled(){}
    public final boolean isCancelled(){return mFuture.isCancelled();}
    public final boolean cancel(boolean mayInterruptIfRunning){return mFuture.cancel(mayInterruptIfRunning);}
    public final Result get() throws InterruptedException, ExecutionException{return mFuture.get();}
    public final Result get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {return mFuture.get(timeout, unit);}

    public final AsyncTaskReplacement<Params, Progress, Result> execute(Params... params)
    {
    	if (mStatus != Status.PENDING)
            switch (mStatus)
            {
                case RUNNING:	throw new IllegalStateException("Cannot execute task: the task is already running.");
                case FINISHED:	throw new IllegalStateException("Cannot execute task: the task has already been executed (a task can be executed only once)");
            }

        mStatus = Status.RUNNING;

        onPreExecute();

        mWorker.mParams = params;
        sExecutor.execute(mFuture);

        return this;
    }

    protected final void publishProgress(Progress... values){sHandler.obtainMessage(MESSAGE_POST_PROGRESS,new AsyncTaskResult<Progress>(this, values)).sendToTarget();}

    private void finish(Result result){if (isCancelled()) result = null;  onPostExecute(result);mStatus = Status.FINISHED;}

    private static class InternalHandler extends Handler {
        @SuppressWarnings({"unchecked", "RawUseOfParameterizedType"})
        @Override
        public void handleMessage(Message msg) {
            AsyncTaskResult result = (AsyncTaskResult) msg.obj;
            switch (msg.what) {
                case MESSAGE_POST_RESULT:
                    // There is only one result
                    result.mTask.finish(result.mData[0]);
                    break;
                case MESSAGE_POST_PROGRESS:
                    result.mTask.onProgressUpdate(result.mData);
                    break;
                case MESSAGE_POST_CANCEL:
                    result.mTask.onCancelled();
                    break;
            }
        }
    }

    private static abstract class WorkerRunnable<Params, Result> implements Callable<Result>{Params[] mParams;}

    private static class AsyncTaskResult<Data> 
    {
        final AsyncTaskReplacement<?, ?, ?> mTask;
        final Data[] mData;

        AsyncTaskResult(AsyncTaskReplacement<?, ?, ?> task, Data... data){mTask = task;mData = data;}
    }
}