package com.bolt7.factory;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/** 程序化 8-bit 音效：启动时合成 PCM，全部离线，不加载任何音频资源。 */
public final class Sfx {

    private static final int RATE = 22050;
    private final AudioTrack[] tracks = new AudioTrack[Const.SFX_COUNT];
    private boolean enabled = true;
    private boolean ready;

    public Sfx(Context ctx) {
        int built = 0;
        built += add(Const.SFX_SWING, noise(0.09, 0.30, 6.0));
        built += add(Const.SFX_COLLECT, arp(new double[]{880, 1320}, 0.055, 0.32));
        built += add(Const.SFX_HURT, sweep(260, 90, 0.22, 0.42, 4.0));
        built += add(Const.SFX_DASH, noise(0.13, 0.26, 4.5));
        built += add(Const.SFX_KILL, sweep(520, 130, 0.16, 0.36, 4.0));
        built += add(Const.SFX_LAND, noise(0.05, 0.18, 8.0));
        built += add(Const.SFX_DOOR, arp(new double[]{523, 659, 784}, 0.08, 0.30));
        built += add(Const.SFX_CLEAR, arp(new double[]{523, 659, 784, 1047}, 0.10, 0.32));
        built += add(Const.SFX_DEATH, sweep(420, 60, 0.50, 0.40, 1.6));
        built += add(Const.SFX_SELECT, sweep(700, 940, 0.06, 0.26, 3.0));
        built += add(Const.SFX_EVOLVE, arp(new double[]{523, 659, 784, 1047, 1319}, 0.075, 0.34));
        built += add(Const.SFX_SHOOT, sweep(760, 340, 0.08, 0.22, 3.0));
        built += add(Const.SFX_DROP, arp(new double[]{620, 930}, 0.06, 0.28));
        built += add(Const.SFX_EQUIP, arp(new double[]{700, 880, 1175}, 0.06, 0.30));
        built += add(Const.SFX_GUARD, arp(new double[]{1568, 2093}, 0.045, 0.24));
        ready = built > 0;
    }

    private int add(int id, short[] pcm) {
        try {
            tracks[id] = build(pcm);
            return 1;
        } catch (Throwable t) {
            tracks[id] = null;
            return 0;
        }
    }

    public void setEnabled(boolean on) {
        enabled = on;
    }

    public void play(int id) {
        if (!enabled || !ready || id < 0 || id >= tracks.length) return;
        AudioTrack t = tracks[id];
        if (t == null) return;
        try {
            if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.stop();
        } catch (Throwable ignored) {
        }
        try {
            t.reloadStaticData();
        } catch (Throwable ignored) {
        }
        try {
            t.play();
        } catch (Throwable ignored) {
        }
    }

    public void release() {
        for (AudioTrack t : tracks) {
            if (t == null) continue;
            try {
                t.stop();
                t.release();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------ 合成

    private static AudioTrack build(short[] pcm) {
        AudioTrack t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                pcm.length * 2, AudioTrack.MODE_STATIC);
        t.write(pcm, 0, pcm.length);
        return t;
    }

    private static short[] sweep(double f0, double f1, double dur, double vol, double decay) {
        int n = (int) (RATE * dur);
        short[] s = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / RATE;
            double sq = Math.sin(phase) >= 0 ? 1.0 : -1.0;
            double env = Math.exp(-decay * t) * Math.min(1.0, i / 60.0);
            s[i] = clamp(sq * vol * env);
        }
        return s;
    }

    private static short[] noise(double dur, double vol, double decay) {
        int n = (int) (RATE * dur);
        short[] s = new short[n];
        Random r = new Random(1234);
        double last = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double white = r.nextDouble() * 2 - 1;
            last = last * 0.6 + white * 0.4;
            double env = Math.exp(-decay * t) * Math.min(1.0, i / 40.0);
            s[i] = clamp(last * vol * env);
        }
        return s;
    }

    private static short[] arp(double[] notes, double noteDur, double vol) {
        int per = (int) (RATE * noteDur);
        short[] s = new short[per * notes.length];
        int k = 0;
        for (double f : notes) {
            for (int i = 0; i < per; i++) {
                double t = (double) i / per;
                double sq = Math.sin(2 * Math.PI * f * i / RATE) >= 0 ? 1.0 : -1.0;
                double env = (1 - t) * Math.min(1.0, i / 40.0);
                s[k++] = clamp(sq * vol * env);
            }
        }
        return s;
    }

    private static short clamp(double v) {
        double x = v * 32000.0;
        if (x > 32000) x = 32000;
        if (x < -32000) x = -32000;
        return (short) x;
    }
}
