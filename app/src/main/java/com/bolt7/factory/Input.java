package com.bolt7.factory;

import java.util.ArrayList;

/**
 * 输入。触屏事件来自 UI 线程，游戏逻辑跑在渲染线程，
 * 所以指针事件先进线程安全的队列，由 Game.update() 逐帧消费。
 */
public final class Input {

    public static final int DOWN = 0;
    public static final int MOVE = 1;
    public static final int UP = 2;
    public static final int CANCEL = 3;

    public static final class Ev {
        public int type, id;
        public float x, y;
    }

    private final ArrayList<Ev> queue = new ArrayList<Ev>();

    // ---- 供渲染层读取的摇杆外观 ----
    public boolean joyActive;
    public float joyX, joyY;     // 摇杆基点
    public float joyDX, joyDY;   // 手柄偏移，单位像素（已限幅）

    // ---- 供游戏逻辑读取 ----
    public float moveX, moveY;   // 归一化方向
    public boolean attackPressed;
    public boolean dashPressed;

    public synchronized void post(int type, int id, float x, float y) {
        Ev e = new Ev();
        e.type = type;
        e.id = id;
        e.x = x;
        e.y = y;
        queue.add(e);
        if (queue.size() > 64) queue.remove(0);
    }

    /** 取下一个事件，没有则返回 null。 */
    public synchronized Ev next() {
        if (queue.isEmpty()) return null;
        return queue.remove(0);
    }

    public void endFrame() {
        attackPressed = false;
        dashPressed = false;
    }

    public void reset() {
        moveX = moveY = 0f;
        joyActive = false;
        joyDX = joyDY = 0f;
        attackPressed = dashPressed = false;
        synchronized (this) {
            queue.clear();
        }
    }
}
