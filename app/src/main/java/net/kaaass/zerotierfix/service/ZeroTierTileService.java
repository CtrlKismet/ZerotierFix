package net.kaaass.zerotierfix.service;

import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import net.kaaass.zerotierfix.events.IsServiceRunningReplyEvent;
import net.kaaass.zerotierfix.events.IsServiceRunningRequestEvent;
import net.kaaass.zerotierfix.events.NodeDestroyedEvent;
import net.kaaass.zerotierfix.events.NodeStatusEvent;
import net.kaaass.zerotierfix.events.StopEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * 快速设置磁贴服务，用于快速连接/断开 Zerotier 网络
 */
@RequiresApi(api = Build.VERSION_CODES.N)
public class ZeroTierTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        // 更新磁贴状态
        updateTile(false); // 默认为关闭，等待响应
        EventBus.getDefault().post(new IsServiceRunningRequestEvent());
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        Tile tile = getQsTile();
        if (tile == null) return;

        if (tile.getState() == Tile.STATE_ACTIVE) {
            // 正在运行，停止服务
            EventBus.getDefault().post(new StopEvent());
            Intent intent = new Intent(this, ZeroTierOneService.class);
            stopService(intent);
            updateTile(false);
        } else {
            // 未运行，启动服务
            if (VpnService.prepare(this) != null) {
                // 需要 VPN 权限，跳转到应用
                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        // Android 14+ 要求使用特定的启动方式或处理
                        startActivityAndCollapse(intent);
                    } else {
                        startActivityAndCollapse(intent);
                    }
                }
            } else {
                // 已有权限，直接启动
                Intent intent = new Intent(this, ZeroTierOneService.class);
                // ZeroTierOneService 将自动连接上一次激活的网络
                startService(intent);
                updateTile(true);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onIsServiceRunningReply(IsServiceRunningReplyEvent event) {
        updateTile(event.isRunning());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNodeStatus(NodeStatusEvent event) {
        updateTile(true);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNodeDestroyed(NodeDestroyedEvent event) {
        updateTile(false);
    }

    private void updateTile(boolean isRunning) {
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(isRunning ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.updateTile();
        }
    }
}
