package com.example.test;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.zlm.hp.libs.utils.ColorUtil;
import com.zlm.hp.libs.utils.ToastUtil;
import com.zlm.hp.lyrics.LyricsReader;
import com.zlm.hp.lyrics.utils.ColorUtils;
import com.zlm.hp.lyrics.utils.TimeUtils;
import com.zlm.hp.lyrics.widget.AbstractLrcView;
import com.zlm.hp.manager.AudioPlayerManager;
import com.zlm.hp.model.AudioInfo;
import com.zlm.hp.model.AudioMessage;
import com.zlm.hp.receiver.AudioBroadcastReceiver;
import com.zlm.hp.receiver.MobliePhoneReceiver;
import com.zlm.hp.receiver.OnLineAudioReceiver;
import com.zlm.hp.receiver.PhoneReceiver;
import com.zlm.hp.receiver.SystemReceiver;
import com.zlm.hp.service.AudioPlayerService;
import com.zlm.hp.widget.SwipeOutLayout;
import com.zlm.libs.widget.MusicSeekBar;

public class MainActivity extends AppCompatActivity {
    public HPApplication mHPApplication;
    /**
     * 音频广播
     */
    private AudioBroadcastReceiver mAudioBroadcastReceiver;
    /**
     * 广播监听
     */
    private AudioBroadcastReceiver.AudioReceiverListener mAudioReceiverListener = new AudioBroadcastReceiver.AudioReceiverListener() {
        @Override
        public void onReceive(Context context, Intent intent) {
            doAudioReceive(context, intent);
        }
    };

    /**
     * 在线音乐广播
     */
    private OnLineAudioReceiver mOnLineAudioReceiver;
    private OnLineAudioReceiver.OnlineAudioReceiverListener mOnlineAudioReceiverListener = new OnLineAudioReceiver.OnlineAudioReceiverListener() {
        @Override
        public void onReceive(Context context, Intent intent) {
//            doNetMusicReceive(context, intent);
        }
    };

    /**
     * 系统广播
     */
    private SystemReceiver mSystemReceiver;
    private SystemReceiver.SystemReceiverListener mSystemReceiverListener = new SystemReceiver.SystemReceiverListener() {
        @Override
        public void onReceive(Context context, Intent intent) {
            doSystemReceive(context, intent);
        }
    };
    /**
     * 耳机广播
     */
    private PhoneReceiver mPhoneReceiver;
    /**
     * 监听电话
     */
    private MobliePhoneReceiver mMobliePhoneReceiver;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHPApplication = (HPApplication) getApplication();
        //初始化底部播放器视图
        initPlayerViews();
        initService();
    }



    /**
     * 初始化服务
     */
    private void initService() {
        Intent playerServiceIntent = new Intent(this, AudioPlayerService.class);
        mHPApplication.startService(playerServiceIntent);


        //注册接收音频播放广播
        mAudioBroadcastReceiver = new AudioBroadcastReceiver(getApplicationContext(), mHPApplication);
        mAudioBroadcastReceiver.setAudioReceiverListener(mAudioReceiverListener);
        mAudioBroadcastReceiver.registerReceiver(getApplicationContext());

        //在线音乐广播
        mOnLineAudioReceiver = new OnLineAudioReceiver(getApplicationContext(), mHPApplication);
        mOnLineAudioReceiver.setOnlineAudioReceiverListener(mOnlineAudioReceiverListener);
        mOnLineAudioReceiver.registerReceiver(getApplicationContext());

        //系统广播
        mSystemReceiver = new SystemReceiver(getApplicationContext(), mHPApplication);
        mSystemReceiver.setSystemReceiverListener(mSystemReceiverListener);
        mSystemReceiver.registerReceiver(getApplicationContext());

        //耳机广播
        mPhoneReceiver = new PhoneReceiver();
        if (mHPApplication.isWire()) {
            mPhoneReceiver.registerReceiver(getApplicationContext());
        }

        //电话监听
        mMobliePhoneReceiver = new MobliePhoneReceiver(getApplicationContext(), mHPApplication);
        mMobliePhoneReceiver.registerReceiver(getApplicationContext());

//        //mFragment广播
//        mFragmentReceiver = new FragmentReceiver(getApplicationContext(), mHPApplication);
//        mFragmentReceiver.setFragmentReceiverListener(mFragmentReceiverListener);
//        mFragmentReceiver.registerReceiver(getApplicationContext());

        //锁屏广播
//        mLockLrcReceiver = new LockLrcReceiver(getApplicationContext());
//        mLockLrcReceiver.setLockLrcReceiverListener(mLockLrcReceiverListener);
//        mLockLrcReceiver.registerReceiver(getApplicationContext());

        //
//        mCheckServiceHandler.postDelayed(mCheckServiceRunnable, mCheckServiceTime);
    }


    /**
     * 处理系统广播
     *
     * @param context
     * @param intent
     */
    private void doSystemReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(SystemReceiver.ACTION_TOASTMESSAGE)) {
            //提示信息
            String message = intent.getStringExtra(ToastUtil.MESSAGEKEY);
//            ToastShowUtil.showTextToast(getApplicationContext(), message);
            Toast.makeText(getApplicationContext(),message,Toast.LENGTH_LONG).show();
        } else if (action.equals(SystemReceiver.ACTION_OPENWIREMESSAGE)) {
            //打开线控
            mPhoneReceiver.registerReceiver(getApplicationContext());
        } else if (action.equals(SystemReceiver.ACTION_CLOSEWIREMESSAGE)) {
            //关闭线控
            mPhoneReceiver.unregisterReceiver(getApplicationContext());
        } else if (action.equals("android.media.AUDIO_BECOMING_NOISY") || action.equals("android.provider.Telephony.SMS_RECEIVED")) {
// 耳机拔出  或者收到短信
            /**
             * 从硬件层面来看，直接监听耳机拔出事件不难，耳机的拔出和插入，会引起手机电平的变化，然后触发什么什么中断，
             *
             * 最终在stack overflow找到答案，监听Android的系统广播AudioManager.
             * ACTION_AUDIO_BECOMING_NOISY，
             * 但是这个广播只是针对有线耳机，或者无线耳机的手机断开连接的事件，监听不到有线耳机和蓝牙耳机的接入
             * ，但对于我的需求来说足够了，监听这个广播就没有延迟了，UI可以立即响应
             */
            int playStatus = mHPApplication.getPlayStatus();
            if (playStatus == AudioPlayerManager.PLAYING) {

                Intent resumeIntent = new Intent(AudioBroadcastReceiver.ACTION_PAUSEMUSIC);
                resumeIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(resumeIntent);

            }

        }
    }


    private LinearLayout mPlayerBarParentLinearLayout;
    /**
     * 底部播放器的布局
     */
    private SwipeOutLayout mSwipeOutLayout;
    /**
     * 初始化底部播放器视图
     */
    /**
     * 播放按钮
     */
    private ImageView mPlayImageView;
    /**
     * 暂停按钮
     */
    private ImageView mPauseImageView;
    /**
     * 下一首按钮
     */
    private ImageView mNextImageView;
    /**
     * 歌曲进度
     */
    private MusicSeekBar mMusicSeekBar;
    private void initPlayerViews() {

        //
        mPlayerBarParentLinearLayout = findViewById(R.id.playerBarParent);

        mSwipeOutLayout = findViewById(R.id.playerBar);
        mSwipeOutLayout.setBackgroundColor(ColorUtil.parserColor("#ffffff", 245));
        ViewGroup barContentView = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.layout_main_player_content, null);

        ViewGroup barMenuView = (ViewGroup) LayoutInflater.from(this).inflate(R.layout.layout_main_player_menu, null);
        //
//        mFloatLyricsView = barMenuView.findViewById(R.id.floatLyricsView);
        //默认颜色
//        int[] paintColors = new int[]{
//                ColorUtils.parserColor("#00348a"),
//                ColorUtils.parserColor("#0080c0"),
//                ColorUtils.parserColor("#03cafc")
//        };
//        mFloatLyricsView.setPaintColor(paintColors);

        //高亮颜色
//        int[] paintHLColors = new int[]{
//                ColorUtils.parserColor("#82f7fd"),
//                ColorUtils.parserColor("#ffffff"),
//                ColorUtils.parserColor("#03e9fc")
//        };
//        mFloatLyricsView.setPaintHLColor(paintHLColors);
        //设置字体文件
//        Typeface typeFace = Typeface.createFromAsset(getAssets(),
//                "fonts/weiruanyahei14M.ttf");
//        mFloatLyricsView.setTypeFace(typeFace, false);
//
//        //歌手头像
//        mSingerImg = barContentView.findViewById(R.id.play_bar_artist);
//        mSingerImg.setTag(null);
//        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.singer_def);
//        mSingerImg.setImageDrawable(new BitmapDrawable(bitmap));
//
//
//        mBarCloseFlagView = barContentView.findViewById(R.id.bar_dragflagClosed);
//        mBarOpenFlagView = barContentView.findViewById(R.id.bar_dragflagOpen);
        //
//        if (mHPApplication.isBarMenuShow()) {
//            mSwipeOutLayout.initViewAndShowMenuView(barContentView, barMenuView, mSingerImg);
//        } else {
//            mSwipeOutLayout.initViewAndShowContentView(barContentView, barMenuView, mSingerImg);
//        }
        if (mHPApplication.isBarMenuShow()) {
            mSwipeOutLayout.initViewAndShowMenuView(barContentView, barMenuView, null);
        } else {
            mSwipeOutLayout.initViewAndShowContentView(barContentView, barMenuView, null);
        }

//        playerBarLinearLayout.setDragViewOnClickListener(new PlayerBarLinearLayout.DragViewOnClickListener() {
//            @Override
//            public void onClick() {
//
//                if(playerBarLinearLayout.isMenuViewShow()){
//
//                    //隐藏菜单
//                    playerBarLinearLayout.hideMenuView();
//
//                }else{
//                    logger.e("点击了专辑图片");
//                }
//            }
//        });
//        mSwipeOutLayout.setPlayerBarListener(new SwipeOutLayout.PlayerBarListener() {
//            @Override
//            public void onClose() {
////                if (mBarCloseFlagView.getVisibility() != View.VISIBLE) {
////                    mBarCloseFlagView.setVisibility(View.VISIBLE);
////                }
//
//                if (mBarOpenFlagView.getVisibility() != View.INVISIBLE) {
//                    mBarOpenFlagView.setVisibility(View.INVISIBLE);
//                }
//
//                //
//                mHPApplication.setBarMenuShow(false);
//            }
//
//
//            @Override
//            public void onOpen() {
////                if (mBarCloseFlagView.getVisibility() != View.INVISIBLE) {
////                    mBarCloseFlagView.setVisibility(View.INVISIBLE);
////                }
//
//                if (mBarOpenFlagView.getVisibility() != View.VISIBLE) {
//                    mBarOpenFlagView.setVisibility(View.VISIBLE);
//                }
//
//                //
//                mHPApplication.setBarMenuShow(true);
//            }
//        });
//        mSwipeOutLayout.setPlayerBarOnClickListener(new SwipeOutLayout.PlayerBarOnClickListener() {
//            @Override
//            public void onClick() {
//
//                if (isPopViewShow) {
//                    hidePopView();
//                    return;
//                }
//                if (mSwipeOutLayout.isMenuViewShow() && mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                    if (mFloatLyricsView.getExtraLrcType() != AbstractLrcView.EXTRALRCTYPE_NOLRC) {
//
//                        if (mFloatLyricsView.getExtraLrcType() == AbstractLrcView.EXTRALRCTYPE_BOTH) {
//                            //有两种歌词
//                            if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC) {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC);
//                            } else if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC);
//                            } else if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC);
//                            }
//                        } else if (mFloatLyricsView.getExtraLrcType() == AbstractLrcView.EXTRALRCTYPE_TRANSLITERATIONLRC) {
//                            if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC) {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC);
//                            } else {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLITERATIONLRC);
//                            }
//                        } else {
//                            if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC) {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC);
//                            } else {
//                                mFloatLyricsView.setExtraLrcStatus(AbstractLrcView.EXTRALRCSTATUS_SHOWTRANSLATELRC);
//                            }
//                        }
//
//                        return;
//                    }
//                }
//                //设置底部点击后，下沉动画
//                TranslateAnimation transAnim = new TranslateAnimation(0, 0, 0, mPlayerBarParentLinearLayout.getHeight());
//                transAnim.setDuration(150);
//                transAnim.setFillAfter(true);
//                mPlayerBarParentLinearLayout.startAnimation(transAnim);
//
//
//                //
//                Intent intent = new Intent(MainActivity.this, LrcActivity.class);
//                startActivityForResult(intent, MAINTOLRCRESULTCODE);
//                //去掉动画
//                overridePendingTransition(0, 0);
//            }
//        });

        //
//        mSongNameTextView = findViewById(R.id.songName);
//        mSingerNameTextView = findViewById(R.id.singerName);
        //播放
        mPlayImageView = findViewById(R.id.bar_play);
        mPlayImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int playStatus = mHPApplication.getPlayStatus();
                if (playStatus == AudioPlayerManager.PAUSE) {

                    AudioInfo audioInfo = mHPApplication.getCurAudioInfo();
                    if (audioInfo != null) {

                        AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
                        Intent resumeIntent = new Intent(AudioBroadcastReceiver.ACTION_RESUMEMUSIC);
                        resumeIntent.putExtra(AudioMessage.KEY, audioMessage);
                        resumeIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                        sendBroadcast(resumeIntent);

                    }

                } else {
                    if (mHPApplication.getCurAudioMessage() != null) {
                        AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
                        AudioInfo audioInfo = mHPApplication.getCurAudioInfo();
                        if (audioInfo != null) {
                            audioMessage.setAudioInfo(audioInfo);
                            Intent playIntent = new Intent(AudioBroadcastReceiver.ACTION_PLAYMUSIC);
                            playIntent.putExtra(AudioMessage.KEY, audioMessage);
                            playIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            sendBroadcast(playIntent);
                        }
                    }
                }
            }
        });
        //暂停
        mPauseImageView = findViewById(R.id.bar_pause);
        mPauseImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int playStatus = mHPApplication.getPlayStatus();
                if (playStatus == AudioPlayerManager.PLAYING) {

                    Intent resumeIntent = new Intent(AudioBroadcastReceiver.ACTION_PAUSEMUSIC);
                    resumeIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    sendBroadcast(resumeIntent);

                }
            }
        });
        //下一首
        mNextImageView = findViewById(R.id.bar_next);
        mNextImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //
                Intent nextIntent = new Intent(AudioBroadcastReceiver.ACTION_NEXTMUSIC);
                nextIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                sendBroadcast(nextIntent);
            }
        });

        mMusicSeekBar = findViewById(R.id.seekBar);
        mMusicSeekBar.setOnMusicListener(new MusicSeekBar.OnMusicListener() {
            @Override
            public String getTimeText() {
//                if (mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                    if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC)
//                        //不显示额外歌词
//                        return TimeUtils.parseMMSSString(Math.max(0, mFloatLyricsView.getSplitLineLrcStartTime(mMusicSeekBar.getProgress())));
//                    else
//                        return TimeUtils.parseMMSSString(Math.max(0, mFloatLyricsView.getLineLrcStartTime(mMusicSeekBar.getProgress())));
//                }
                return TimeUtils.parseMMSSString(mMusicSeekBar.getProgress());
            }

            @Override
            public String getLrcText() {
//                if (mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                    if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC)
//                        //不显示额外歌词
//                        return mFloatLyricsView.getSplitLineLrc(mMusicSeekBar.getProgress());
//                    else
//                        return mFloatLyricsView.getLineLrc(mMusicSeekBar.getProgress());
//                }
                return null;
            }

            @Override
            public void onProgressChanged(MusicSeekBar musicSeekBar) {

            }

            @Override
            public void onTrackingTouchStart(MusicSeekBar musicSeekBar) {

            }

            @Override
            public void onTrackingTouchFinish(MusicSeekBar musicSeekBar) {
                int seekToTime = mMusicSeekBar.getProgress();
                mMusicSeekBar.setTrackingTouchSleepTime(1000);
//                if (mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//
//                    if (mFloatLyricsView.getExtraLrcStatus() == AbstractLrcView.EXTRALRCSTATUS_NOSHOWEXTRALRC)
//                        //不显示额外歌词
//                        seekToTime = mFloatLyricsView.getSplitLineLrcStartTime(mMusicSeekBar.getProgress());
//                    else
//                        seekToTime = mFloatLyricsView.getLineLrcStartTime(mMusicSeekBar.getProgress());
//
//                    mMusicSeekBar.setTrackingTouchSleepTime(0);
//                }


                int playStatus = mHPApplication.getPlayStatus();
                if (playStatus == AudioPlayerManager.PLAYING) {
                    //正在播放
                    if (mHPApplication.getCurAudioMessage() != null) {
                        AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
                        // AudioInfo audioInfo = mHPApplication.getCurAudioInfo();
                        //if (audioInfo != null) {
                        //  audioMessage.setAudioInfo(audioInfo);
                        if (audioMessage != null) {
                            audioMessage.setPlayProgress(seekToTime);
                            Intent resumeIntent = new Intent(AudioBroadcastReceiver.ACTION_SEEKTOMUSIC);
                            resumeIntent.putExtra(AudioMessage.KEY, audioMessage);
                            resumeIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            sendBroadcast(resumeIntent);
                        }
                    }
                } else {

                    if (mHPApplication.getCurAudioMessage() != null)
                        mHPApplication.getCurAudioMessage().setPlayProgress(seekToTime);

                    //歌词快进
                    Intent lrcSeektoIntent = new Intent(AudioBroadcastReceiver.ACTION_LRCSEEKTO);
                    lrcSeektoIntent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                    sendBroadcast(lrcSeektoIntent);


                }
            }
        });

        //
        ImageView listMenuImg = findViewById(R.id.list_menu);
        listMenuImg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                if (isPopViewShow) {
//                    hidePopView();
//                    return;
//                }
//
//                showPopView();
            }
        });
    }

    /**
     * 处理音频广播事件
     *
     * @param context
     * @param intent
     */
    private void doAudioReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(AudioBroadcastReceiver.ACTION_NULLMUSIC)) {
            //空数据
//            mSongNameTextView.setText(R.string.def_songName);
//            mSingerNameTextView.setText(R.string.def_artist);
            mPauseImageView.setVisibility(View.INVISIBLE);
            mPlayImageView.setVisibility(View.VISIBLE);

            //
            mMusicSeekBar.setEnabled(false);
            mMusicSeekBar.setProgress(0);
            mMusicSeekBar.setSecondaryProgress(0);
            mMusicSeekBar.setMax(0);
            //隐藏
//            mSingerImg.setTag(null);

            //
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.singer_def);
//            mSingerImg.setImageDrawable(new BitmapDrawable(bitmap));

            //
//            mFloatLyricsView.initLrcData();

            //重置弹出窗口播放列表
//            if (isPopViewShow) {
//                if (mPopPlayListAdapter != null) {
//                    mPopPlayListAdapter.reshViewHolder(null);
//                }
//            }

        } else if (action.equals(AudioBroadcastReceiver.ACTION_INITMUSIC)) {


            //初始化
            AudioMessage audioMessage = mHPApplication.getCurAudioMessage();//(AudioMessage) intent.getSerializableExtra(AudioMessage.KEY);
            AudioInfo audioInfo = mHPApplication.getCurAudioInfo();

//            mCurPlayIndexHash = audioInfo.getHash();
//
//            mSongNameTextView.setText(audioInfo.getSongName());
//            mSingerNameTextView.setText(audioInfo.getSingerName());
            mPauseImageView.setVisibility(View.INVISIBLE);
            mPlayImageView.setVisibility(View.VISIBLE);

            //
            mMusicSeekBar.setEnabled(true);
            mMusicSeekBar.setMax((int) audioInfo.getDuration());
            mMusicSeekBar.setProgress((int) audioMessage.getPlayProgress());
            mMusicSeekBar.setSecondaryProgress(0);
            //加载歌手图片
//            ImageUtil.loadSingerImage(mHPApplication, getApplicationContext(), mSingerImg, audioInfo.getSingerName());

            //加载歌词
            String keyWords = "";
            if (audioInfo.getSingerName().equals("未知")) {
                keyWords = audioInfo.getSongName();
            } else {
                keyWords = audioInfo.getSingerName() + " - " + audioInfo.getSongName();
            }
//            LyricsManager.getLyricsManager(mHPApplication, getApplicationContext()).loadLyricsUtil(keyWords, keyWords, audioInfo.getDuration() + "", audioInfo.getHash());

            //
//            mFloatLyricsView.initLrcData();
            //加载中
//            mFloatLyricsView.setLrcStatus(AbstractLrcView.LRCSTATUS_LOADING);

            //设置弹出窗口播放列表
//            if (isPopViewShow) {
//                if (mPopPlayListAdapter != null) {
//                    mPopPlayListAdapter.reshViewHolder(audioInfo);
//                }
//            }

        } else if (action.equals(AudioBroadcastReceiver.ACTION_SERVICE_PLAYMUSIC)) {
            //播放

            AudioMessage audioMessage = mHPApplication.getCurAudioMessage();//(AudioMessage) intent.getSerializableExtra(AudioMessage.KEY);

            mPauseImageView.setVisibility(View.VISIBLE);
            mPlayImageView.setVisibility(View.INVISIBLE);

            //
            mMusicSeekBar.setProgress((int) audioMessage.getPlayProgress());

            if (audioMessage != null) {
                mMusicSeekBar.setProgress((int) audioMessage.getPlayProgress());
                AudioInfo audioInfo = mHPApplication.getCurAudioInfo();
                if (audioInfo != null) {
                    //更新歌词


//                    if (mFloatLyricsView.getLyricsReader() != null && mFloatLyricsView.getLyricsReader().getHash().equals(audioInfo.getHash()) && mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC && mFloatLyricsView.getLrcPlayerStatus() != AbstractLrcView.LRCPLAYERSTATUS_PLAY) {
//                        mFloatLyricsView.play((int) audioMessage.getPlayProgress());
//                    }
                }

            }

        } else if (action.equals(AudioBroadcastReceiver.ACTION_SERVICE_PAUSEMUSIC)) {

//            if (mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                mFloatLyricsView.pause();
//            }

            //暂停完成
            mPauseImageView.setVisibility(View.INVISIBLE);
            mPlayImageView.setVisibility(View.VISIBLE);


        } else if (action.equals(AudioBroadcastReceiver.ACTION_SERVICE_RESUMEMUSIC)) {
            AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
//            if (audioMessage != null) {
//                if (mFloatLyricsView != null && mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                    mFloatLyricsView.play((int) audioMessage.getPlayProgress());
//                }
//            }

            //唤醒完成
            mPauseImageView.setVisibility(View.VISIBLE);
            mPlayImageView.setVisibility(View.INVISIBLE);


        } else if (action.equals(AudioBroadcastReceiver.ACTION_SERVICE_SEEKTOMUSIC)) {
            //唤醒完成
            mPauseImageView.setVisibility(View.VISIBLE);
            mPlayImageView.setVisibility(View.INVISIBLE);

            AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
//            if (audioMessage != null) {
//                if (mFloatLyricsView != null && mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                    mFloatLyricsView.play((int) audioMessage.getPlayProgress());
//                }
//            }
        } else if (action.equals(AudioBroadcastReceiver.ACTION_SERVICE_PLAYINGMUSIC)) {

            //播放中
            AudioMessage audioMessage = mHPApplication.getCurAudioMessage();
            if (audioMessage != null) {
                mMusicSeekBar.setProgress((int) audioMessage.getPlayProgress());

            }

        } else if (action.equals(AudioBroadcastReceiver.ACTION_LRCLOADED)) {
            if (mHPApplication.getCurAudioMessage() != null && mHPApplication.getCurAudioInfo() != null) {
                //歌词加载完成
                AudioMessage curAudioMessage = mHPApplication.getCurAudioMessage();
                AudioMessage audioMessage = (AudioMessage) intent.getSerializableExtra(AudioMessage.KEY);
                String hash = audioMessage.getHash();
                if (hash.equals(mHPApplication.getCurAudioInfo().getHash())) {
                    //
//                    LyricsReader lyricsReader = LyricsManager.getLyricsManager(mHPApplication, getApplicationContext()).getLyricsUtil(hash);
//                    if (lyricsReader != null) {
//                        if (lyricsReader.getHash() != null && lyricsReader.getHash().equals(hash) && mFloatLyricsView.getLyricsReader() != null) {
//                            //已加载歌词，不用重新加载
//                        } else {
//                            lyricsReader.setHash(hash);
//                            mFloatLyricsView.setLyricsReader(lyricsReader);
//                            if (mHPApplication.getPlayStatus() == AudioPlayerManager.PLAYING && mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC && mFloatLyricsView.getLrcPlayerStatus() != AbstractLrcView.LRCPLAYERSTATUS_PLAY)
//                                mFloatLyricsView.play((int) curAudioMessage.getPlayProgress());
//                        }
//                    }
                }
            }
        } else if (action.equals(AudioBroadcastReceiver.ACTION_LRCSEEKTO)) {
            if (mHPApplication.getCurAudioMessage() != null) {
                mMusicSeekBar.setProgress((int) mHPApplication.getCurAudioMessage().getPlayProgress());
//                if (mHPApplication.getCurAudioInfo() != null) {
//                    if (mFloatLyricsView.getLyricsReader() != null && mFloatLyricsView.getLyricsReader().getHash().equals(mHPApplication.getCurAudioInfo().getHash()) && mFloatLyricsView.getLrcStatus() == AbstractLrcView.LRCSTATUS_LRC) {
//                        mFloatLyricsView.seekto((int) mHPApplication.getCurAudioMessage().getPlayProgress());
//                    }
//                }
            }

        }
    }

}
