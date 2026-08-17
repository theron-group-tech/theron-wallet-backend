package com.theron.wallet.notification;

import com.theron.wallet.entity.Notification;
import com.theron.wallet.enums.NotificationChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PushNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void deliver(Notification notification) {
        log.debug("Push channel stub skipped: id={}", notification.getId());
    }
}
