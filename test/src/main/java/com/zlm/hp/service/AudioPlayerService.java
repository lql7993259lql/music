package com.zlm.hp.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import com.example.test.HPApplication;
import com.example.test.R;
import com.zlm.hp.constants.ResourceConstants;
import com.zlm.hp.db.DownloadThreadDB;
import com.zlm.hp.libs.utils.ToastUtil;
import com.zlm.hp.manager.AudioPlayerManager;
import com.zlm.hp.manager.OnLineAudioManager;
import com.zlm.hp.model.AudioInfo;
import com.zlm.hp.model.AudioMessage;
import com.zlm.hp.receiver.AudioBroadcastReceiver;
import com.zlm.hp.utils.ResourceFileUtil;

import java.io.File;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * @Description:播放服务
 * @Param:
 * @Return:
 * @Author: zhangliangming
 * @Date: 2017/8/6 12:08
 * @Throws:
 */
public class AudioPlayerService extends Service {


    /**
     * 播放器
     */
    private IjkMediaPlayer mMediaPlayer;

    /**
     * 播放线程
     */
    private Thread mPlayerThread = null;

    private HPApplication mHPApplication;

    /**
     * 音频广播
     */
    private AudioBroadcastReceiver mAudioBroadcastReceiver;

    /**
     * 广播监听
     */
    private AudioBroadcastReceiver.AudioReceiverListener mAudioReceiverListener = new AudioBroadcastReceiver.AudioReceiverListener() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            new AsyncTask<String, Integer, String>() {
                @Override
                protected String doInBackground(String... strings) {
                    doAudioReceive(context, intent);
                    return null;
                }
            }.execute("");
        }
    };
    /**
     * 是否正在快进
     */
    private boolean isSeekTo = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mHPApplication = HPApplication.getInstance();
        //注册接收音频播放广播
        mAudioBroadcastReceiver = new AudioBroadcastReceiver(getApplicationContext(), mHPApplication);
        mAudioBroadcastReceiver.setAudioReceiverListener(mAudioReceiverListener);
        mAudioBroadcastReceiver.registerReceiver(getApplicationContext());
        Log.i("","音频播放服务启动");
    }




    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("WrongConstant")
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onDestroy() {
        mAudioBroadcastReceiver.unregisterReceiver(getApplicationContext());
        releasePlayer();
        Log.i("","音频播放服务销毁");
        super.onDestroy();
    }

    /**
     * 广播处理
     *
     * @param context
     * @param intent
     */
    private void doAudioReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(AudioBroadcastReceiver.ACTION_NULLMUSIC)) {
            releasePlayer();
            resetPlayData();

        } else if (action.equals(AudioBroadcastReceiver.ACTION_PLAYMUSIC)) {
            //播放歌曲
            playMusic((AudioMessage) intent.getSerializableExtra(AudioMessage.KEY));

        } else if (action.equals(AudioBroadcastReceiver.ACTION_PAUSEMUSIC)) {
            //暂停歌曲
            pauseMusic();
        } else if (action.equals(AudioBroadcastReceiver.ACTION_RESUMEMUSIC)) {
            //唤醒歌曲
            resumeMusic((AudioMessage) intent.getSerializableExtra(AudioMessage.KEY));
        } else if (action.equals(AudioBroadcastReceiver.ACTION_SEEKTOMUSIC)) {
            //歌曲快进
            seekToMusic((AudioMessage) intent.getSerializableExtra(AudioMessage.KEY));
        } else if (action.equals(AudioBroadcastReceiver.ACTION_NEXTMUSIC)) {
            //下一首
            nextMusic();
        } else if (action.equals(AudioBroadcastReceiver.ACTION_PREMUSIC)) {
            //上一首
            preMusic();
        }
    }

    /**
     * 上一首
     */
    private void preMusic() {

        Log.e("","准备播放上一首");
        int playModel = mHPApplication.getPlayModel();
        AudioInfo audioInfo = AudioPlayerManager.getAudioPlayerManager(getApplicationContext(), mHPApplication).preMusic(playModel);
        if (audioInfo == null) {
            releasePlayer();
            resetPlayData();

            //发送空数据广播
            Intent nullIntent = new Intent(AudioBroadcastReceiver.ACTION_NULLMUSIC);
            nullIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(nullIntent);

            return;
        }

        Log.e("","上一首歌曲为：" + audioInfo.getSongName());
        //
        AudioMessage audioMessage = new AudioMessage();
        audioMessage.setAudioInfo(audioInfo);
        playMusic(audioMessage);
    }

    /**
     * 下一首
     */
    private void nextMusic() {
        Log.e("","准备播放下一首");
        int playModel = mHPApplication.getPlayModel();
        AudioInfo audioInfo = AudioPlayerManager.getAudioPlayerManager(getApplicationContext(), mHPApplication).nextMusic(playModel);
        if (audioInfo == null) {
            releasePlayer();
            resetPlayData();

            //发送空数据广播
            Intent nullIntent = new Intent(AudioBroadcastReceiver.ACTION_NULLMUSIC);
            nullIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(nullIntent);

            return;
        }
        Log.e("","下一首歌曲为：" + audioInfo.getSongName());
        //
        AudioMessage audioMessage = new AudioMessage();
        audioMessage.setAudioInfo(audioInfo);
        playMusic(audioMessage);
    }


    /**
     * 快进
     *
     * @param audioMessage
     */
    private void seekToMusic(AudioMessage audioMessage) {

        if (mMediaPlayer != null) {
            isSeekTo = true;
            mMediaPlayer.seekTo(audioMessage.getPlayProgress());
        }

    }

    /**
     * 唤醒播放
     */
    private void resumeMusic(AudioMessage audioMessage) {

        //如果是网络歌曲，先进行下载，再进行播放
        if (mHPApplication.getCurAudioInfo() != null && mHPApplication.getCurAudioInfo().getType() == AudioInfo.NET) {
            //如果进度为0，表示上一次下载直接错误。
            int downloadedSize = DownloadThreadDB.getDownloadThreadDB(getApplicationContext()).getDownloadedSize(mHPApplication.getPlayIndexHashID(), OnLineAudioManager.threadNum);
            if (downloadedSize == 0) {
                //发送init的广播
                Intent initIntent = new Intent(AudioBroadcastReceiver.ACTION_INITMUSIC);
                //initIntent.putExtra(AudioMessage.KEY, audioMessage);
                initIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(initIntent);
            }
            doNetMusic();
        } else {
            if (mMediaPlayer != null) {
                isSeekTo = true;
                mMediaPlayer.seekTo(audioMessage.getPlayProgress());
            }
            mHPApplication.setPlayStatus(AudioPlayerManager.PLAYING);
        }

        Intent nextIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_RESUMEMUSIC);
        nextIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(nextIntent);
    }

    /**
     * 暂停播放
     */
    private void pauseMusic() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
            }
        }
        mHPApplication.setPlayStatus(AudioPlayerManager.PAUSE);
        Intent pauseIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PAUSEMUSIC);
        pauseIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        sendBroadcast(pauseIntent);
    }

    /**
     * 播放歌曲
     *
     * @param audioMessage
     */
    private void playMusic(AudioMessage audioMessage) {
        releasePlayer();
        // resetPlayData();

        AudioInfo audioInfo = audioMessage.getAudioInfo();
        if (mHPApplication.getCurAudioInfo() != null) {
            if (!mHPApplication.getCurAudioInfo().getHash().equals(audioInfo.getHash())) {
                //设置当前播放数据
                mHPApplication.setCurAudioMessage(audioMessage);
                //设置当前正在播放的歌曲数据
                mHPApplication.setCurAudioInfo(audioInfo);
                //设置当前的播放索引
                mHPApplication.setPlayIndexHashID(audioInfo.getHash());

                //发送init的广播
                Intent initIntent = new Intent(AudioBroadcastReceiver.ACTION_INITMUSIC);
                //initIntent.putExtra(AudioMessage.KEY, audioMessage);
                initIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(initIntent);
            }

        } else {

            //设置当前播放数据
            mHPApplication.setCurAudioMessage(audioMessage);
            //设置当前正在播放的歌曲数据
            mHPApplication.setCurAudioInfo(audioInfo);
            //设置当前的播放索引
            mHPApplication.setPlayIndexHashID(audioInfo.getHash());

            //发送init的广播
            Intent initIntent = new Intent(AudioBroadcastReceiver.ACTION_INITMUSIC);
            //initIntent.putExtra(AudioMessage.KEY, audioMessage);
            initIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(initIntent);
        }


        if (audioInfo.getType() == AudioInfo.LOCAL) {
            //播放本地歌曲
            playLocalMusic(audioMessage);
        } else {
            String fileName = audioInfo.getSingerName() + " - " + audioInfo.getSongName();
            String filePath = ResourceFileUtil.getFilePath(getApplicationContext(), ResourceConstants.PATH_AUDIO, fileName + "." + audioInfo.getFileExt());
            //设置文件路径
            audioInfo.setFilePath(filePath);
            File audioFile = new File(filePath);
            if (audioFile.exists()) {
                //播放本地歌曲
                playLocalMusic(audioMessage);
            } else {
                //播放网络歌曲
                doNetMusic();
            }
        }
    }

    /**
     * 下载线程
     */
    private Handler mDownloadHandler = new Handler();

    /**
     *
     */
    private Runnable mDownloadCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (mHPApplication.getPlayStatus() == AudioPlayerManager.PLAYNET) {

                int downloadedSize = DownloadThreadDB.getDownloadThreadDB(getApplicationContext()).getDownloadedSize(mHPApplication.getPlayIndexHashID(), OnLineAudioManager.threadNum);
                Log.e("","在线播放任务名称：" + mHPApplication.getCurAudioInfo().getSongName() + "  缓存播放时，监听已下载大小：" + downloadedSize);

                mDownloadHandler.removeCallbacks(mDownloadCheckRunnable);
                if (downloadedSize > 1024 * 200) {

                    if (mHPApplication.getPlayStatus() != AudioPlayerManager.PAUSE) {
                        playNetMusic();
                    }

                } else {
                    mDownloadHandler.postDelayed(mDownloadCheckRunnable, 1000);
                }
            }
        }
    };

    /**
     * 播放网络歌曲
     */
    private void playNetMusic() {
        if (mHPApplication.getCurAudioMessage() != null && mHPApplication.getCurAudioInfo() != null) {
            String filePath = ResourceFileUtil.getFilePath(getApplicationContext(), ResourceConstants.PATH_CACHE_AUDIO, mHPApplication.getCurAudioInfo().getHash() + ".temp");
            try {
                mMediaPlayer = new IjkMediaPlayer();
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setDataSource(filePath);
                mMediaPlayer.prepareAsync();
                //
                mMediaPlayer.setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(IMediaPlayer mp) {
                        mMediaPlayer.start();

                        AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
                        //设置当前播放的状态
                        mHPApplication.setPlayStatus(AudioPlayerManager.PLAYING);
                        audioMessage.setPlayProgress(mMediaPlayer.getCurrentPosition());
                        Intent seekToIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_SEEKTOMUSIC);
                        seekToIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                        sendBroadcast(seekToIntent);

                        if (mHPApplication.isLrcSeekTo()) {

                            try {
                                Thread.sleep(300);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            mHPApplication.setLrcSeekTo(false);
                        }
                        isSeekTo = false;

                    }
                });
                mMediaPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(IMediaPlayer mp) {

                        if (mMediaPlayer.getCurrentPosition() < (mHPApplication.getCurAudioInfo().getDuration() - 2 * 1000)) {
                            playNetMusic();
                        } else {
                            //播放完成，执行下一首操作
                            nextMusic();
                        }

                    }
                });
                mMediaPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(IMediaPlayer mp, int what, int extra) {

                        //发送播放错误广播
                        Intent errorIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYERRORMUSIC);
                        errorIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                        sendBroadcast(errorIntent);

                        ToastUtil.showTextToast(getApplicationContext(), "播放歌曲出错，1秒后播放下一首");


                        new Thread() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(1000);
                                    //播放下一首
                                    nextMusic();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }.start();

                        return false;
                    }
                });
                mMediaPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(IMediaPlayer mp) {

                        if (mHPApplication.getCurAudioMessage() != null) {
                            AudioMessage audioMessage = mHPApplication.getCurAudioMessage();

                            if (audioMessage.getPlayProgress() > 0) {
                                isSeekTo = true;
                                mMediaPlayer.seekTo(audioMessage.getPlayProgress());
                            } else {
                                mMediaPlayer.start();


                                //设置当前播放的状态
                                mHPApplication.setPlayStatus(AudioPlayerManager.PLAYING);
                                audioMessage.setPlayProgress(mMediaPlayer.getCurrentPosition());
                                //发送play的广播
                                Intent playIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYMUSIC);
                                playIntent.putExtra(AudioMessage.KEY, audioMessage);
                                playIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                                sendBroadcast(playIntent);

                            }


                        }
                    }
                });

                if (mPlayerThread == null) {
                    mPlayerThread = new Thread(new PlayerRunable());
                    mPlayerThread.start();
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("",e.getMessage());

                //发送播放错误广播
                Intent errorIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYERRORMUSIC);
                mHPApplication.getCurAudioMessage().setErrorMsg(e.getMessage());
                errorIntent.putExtra(AudioMessage.KEY, mHPApplication.getCurAudioMessage());
                errorIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(errorIntent);

                ToastUtil.showTextToast(getApplicationContext(), "播放歌曲出错，1秒后播放下一首");
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(1000);
                            //播放下一首
                            nextMusic();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
            }


        }
    }

    /**
     * 播放网络歌曲
     */
    private void doNetMusic() {
        AudioInfo audioInfo = mHPApplication.getCurAudioInfo();
        mDownloadHandler.removeCallbacks(mDownloadCheckRunnable);
        //设置当前的播放状态
        mHPApplication.setPlayStatus(AudioPlayerManager.PLAYNET);

        //下载
        if (!OnLineAudioManager.getOnLineAudioManager(mHPApplication, getApplicationContext()).taskIsExists(audioInfo.getHash())) {
            OnLineAudioManager.getOnLineAudioManager(mHPApplication, getApplicationContext()).addTask(audioInfo);
            mDownloadHandler.postAtTime(mDownloadCheckRunnable, 1000);
            Log.e("","准备播放在线歌曲：" + audioInfo.getSongName());
        }

    }

    /**
     * 播放本地歌曲
     *
     * @param audioMessage
     */
    private void playLocalMusic(AudioMessage audioMessage) {

        try {
            mMediaPlayer = new IjkMediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setDataSource(audioMessage.getAudioInfo().getFilePath());
            mMediaPlayer.prepareAsync();
            //
            mMediaPlayer.setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(IMediaPlayer mp) {
                    mMediaPlayer.start();

                    AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
                    //设置当前播放的状态
                    mHPApplication.setPlayStatus(AudioPlayerManager.PLAYING);
                    audioMessage.setPlayProgress(mMediaPlayer.getCurrentPosition());
                    Intent seekToIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_SEEKTOMUSIC);
                    seekToIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    sendBroadcast(seekToIntent);

                    if (mHPApplication.isLrcSeekTo()) {

                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mHPApplication.setLrcSeekTo(false);
                    }
                    isSeekTo = false;

                }
            });
            mMediaPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(IMediaPlayer mp) {

                    //播放完成，执行下一首操作
                    nextMusic();

                }
            });
            mMediaPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(IMediaPlayer mp, int what, int extra) {

                    //发送播放错误广播
                    Intent errorIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYERRORMUSIC);
                    errorIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    sendBroadcast(errorIntent);

                    ToastUtil.showTextToast(getApplicationContext(), "播放歌曲出错，1秒后播放下一首");


                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                                //播放下一首
                                nextMusic();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();

                    return false;
                }
            });
            mMediaPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(IMediaPlayer mp) {

                    if (mHPApplication.getCurAudioMessage() != null) {
                        AudioMessage audioMessage = mHPApplication.getCurAudioMessage();

                        if (audioMessage.getPlayProgress() > 0) {
                            isSeekTo = true;
                            mMediaPlayer.seekTo(audioMessage.getPlayProgress());
                        } else {
                            mMediaPlayer.start();

                            //设置当前播放的状态
                            mHPApplication.setPlayStatus(AudioPlayerManager.PLAYING);
                            audioMessage.setPlayProgress(mMediaPlayer.getCurrentPosition());
                            //发送play的广播
                            Intent playIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYMUSIC);
                            playIntent.putExtra(AudioMessage.KEY, audioMessage);
                            playIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            sendBroadcast(playIntent);
                        }


                    }
                }
            });

            if (mPlayerThread == null) {
                mPlayerThread = new Thread(new PlayerRunable());
                mPlayerThread.start();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Log.e("",e.getMessage());

            //发送播放错误广播
            Intent errorIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYERRORMUSIC);
            audioMessage.setErrorMsg(e.getMessage());
            errorIntent.putExtra(AudioMessage.KEY, audioMessage);
            errorIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            sendBroadcast(errorIntent);

            ToastUtil.showTextToast(getApplicationContext(), "播放歌曲出错，1秒后播放下一首");
            new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                        //播放下一首
                        nextMusic();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    /**
     * 播放线程
     */

    private class PlayerRunable implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {

                    if (!isSeekTo && mMediaPlayer != null && mMediaPlayer.isPlaying()) {

                        if (mHPApplication.getCurAudioMessage() != null) {
                            mHPApplication.getCurAudioMessage().setPlayProgress(mMediaPlayer.getCurrentPosition());


                            //发送正在播放中的广播
                            Intent playingIntent = new Intent(AudioBroadcastReceiver.ACTION_SERVICE_PLAYINGMUSIC);
                            //playingIntent.putExtra(AudioMessage.KEY, mHPApplication.getCurAudioMessage());
                            playingIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            sendBroadcast(playingIntent);

                        }
                    }

                    Thread.sleep(1000);//
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 释放播放器
     */
    private void releasePlayer() {
        mHPApplication.setPlayStatus(AudioPlayerManager.STOP);
        if (mPlayerThread != null) {
            mPlayerThread = null;
        }
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            //mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        System.gc();
    }

    /**
     * 重置播放数据
     */
    private void resetPlayData() {
        mHPApplication.setCurAudioMessage(null);
        //设置当前播放的状态
        mHPApplication.setCurAudioInfo(null);
        mHPApplication.setPlayIndexHashID("");
    }
}
