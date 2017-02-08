package com.download;

import android.os.Debug;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by liuguangli on 15/9/11.
 */
public class TaskCreatorThread extends Thread {
    public static final String TAG =  "TaskCreatorThread";
    private FileDownloader mdownLoader;
    private TaskList mTaskList;
    private String folder;
    private String extend;
    private ExecutorService executor;
    public TaskCreatorThread(FileDownloader downloader,TaskList taskList){
        mTaskList = taskList;
        mdownLoader = downloader;
        executor = Executors.newFixedThreadPool(FileDownloader.THREAD_COUNT);
    }

    @Override
    public void run() {
        while (mdownLoader.isRunning()){
            String url = mTaskList.removeReturnRul();
            if (url == null){
                try {
                    synchronized (mTaskList){
                        mTaskList.wait();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                create(url);
            }
        }
        stopExcutor();
    }



    private void create(String url){
        Debug.startMethodTracing("callc");
        //之前没有下载
        FileInfo fileInfo = mTaskList.getFileInfoByUrl(url);
        if (fileInfo == null) {
            String apkFilePath = folder + MD5Util.getUpperMD5Str(url) + extend;
            LogUtil.d(TAG,"file:"+apkFilePath);
            fileInfo = new FileInfo(url, 0, 0, apkFilePath);
            fileInfo._id = System.currentTimeMillis();
        }
        fileInfo.fileSize = getFileSize(fileInfo);
        LogUtil.d(TAG,"fileSize:"+ fileInfo.fileSize);
        if (fileInfo.fileSize <= 0) {
            fileInfo.state = FileInfo.ERROR;

            mdownLoader.error(url, DownloadListener.GET_APK_SIZE_FAIL);//通知重新下载
            return;

        }
        boolean isSuccess = createApkFile(fileInfo);
        if (!isSuccess) {
            fileInfo.state = FileInfo.ERROR;
            mdownLoader.error(url, DownloadListener.CREATE_APK_FILE_FAIL);//通知重新下载
            return ;
        }
        createSubsection(fileInfo);
        fileInfo.state = FileInfo.WAITING;
        mdownLoader.update(url, fileInfo.filePath, 0);
        mTaskList.add(fileInfo);
        Debug.stopMethodTracing();

    }

    private void createSubsection(FileInfo fileInfo) {
        if (fileInfo.fileSize > 0) {
            LogUtil.d(TAG, "(4)fileInfo._id > 0 && fileInfo.fileSize > 0");
            fileInfo.fileItemList = new ArrayList<FileInfo.FileItem>();
            int itemtLen = getSuitableSize();
            int count = fileInfo.fileSize/itemtLen;
            if (count < 1){
                FileInfo.FileItem fileItem = new FileInfo.FileItem();
                fileItem.info = fileInfo;
                fileItem.infoId = fileInfo._id;
                fileItem.startPos = 0;
                fileItem._id = 0;
                fileItem.endPos = fileItem.startPos + fileInfo.fileSize;
                fileInfo.fileItemList.add(fileItem);
                executor.submit(new DownloadWorker(mdownLoader, fileItem));
            } else {
                for (int i = 0; i < count; i++) {
                    FileInfo.FileItem fileItem = new FileInfo.FileItem();
                    fileItem.info = fileInfo;
                    fileItem.infoId = fileInfo._id;
                    fileItem.startPos = i * itemtLen;
                    fileItem._id = i;
                    fileItem.endPos = fileItem.startPos + itemtLen - 1;
                    fileInfo.fileItemList.add(fileItem);
                    executor.submit(new DownloadWorker(mdownLoader, fileItem));
                }
                int length = fileInfo.fileSize % itemtLen;
                if (length > 0) {
                    FileInfo.FileItem fileItem = new FileInfo.FileItem();
                    fileItem.info = fileInfo;
                    fileItem.infoId = fileInfo._id;
                    fileItem.startPos = count * itemtLen;
                    fileItem.endPos = fileItem.startPos + length - 1;
                    fileInfo.fileItemList.add(fileItem);
                    executor.submit(new DownloadWorker(mdownLoader,fileItem));

                }
            }

        }
    }




    /**
     * 获取要下载的包大小
     */
    private int getFileSize(FileInfo fileInfo) {
        HttpURLConnection connection = null;
        int size = -1;
        try {
            URL url = new URL(fileInfo.fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setRequestMethod("GET");
            size = connection.getContentLength();
        } catch (Exception e) {
            e.printStackTrace();
            size = 0;
        } finally {
            if (connection != null) connection.disconnect();
        }
        return size;
    }
    private boolean createApkFile(FileInfo fileInfo) {
        boolean result = true;
        RandomAccessFile accessFile = null;
        try {
            if (fileInfo.fileSize > 0) {
                File file = new File(fileInfo.filePath);
                if (!file.exists()) {
                    file.createNewFile();
                }
                accessFile = new RandomAccessFile(file, "rwd");
                accessFile.setLength(fileInfo.fileSize);
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = false;
        } finally {
            if (accessFile != null) {
                try {
                    accessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }




    public void setFolder(String folder) {
        this.folder = folder;
    }
    public void setExtend(String extend){
        this.extend = extend;
    }

    public int getSuitableSize() {
        //这个大小的设置可以根据网络、服务器负责等信息综合考虑来确定。
        return 1024 * 1024;
    }

    public void stopExcutor() {
        if (!executor.isShutdown()){
            executor.shutdown();
        }
    }
}


