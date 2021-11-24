package com.beemdevelopment.aegis.services;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.beemdevelopment.aegis.ui.MainActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebaseNotificationService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d("BrowserLink", "From: " + remoteMessage.getFrom());

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MainActivity.NOTIFY_ACTIVITY_CHECK_REQUESTS);
        sendBroadcast(broadcastIntent);
    }
}
