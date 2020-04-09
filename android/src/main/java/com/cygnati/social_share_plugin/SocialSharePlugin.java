package com.cygnati.social_share_plugin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.share.Sharer;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.twitter.sdk.android.tweetcomposer.TweetComposer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;

/**
 * SocialSharePlugin
 */
public class SocialSharePlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener {
    private final static String INSTAGRAM_PACKAGE_NAME = "com.instagram.android";
    private final static String FACEBOOK_PACKAGE_NAME = "com.facebook.katana";
    private final static String TWITTER_PACKAGE_NAME = "com.twitter.android";

    private final static int TWITTER_REQUEST_CODE = 0xc0ce;

    private final Registrar registrar;
    private final MethodChannel channel;
    private final CallbackManager callbackManager;

    private SocialSharePlugin(final Registrar registrar, final MethodChannel channel) {
        this.channel = channel;
        this.registrar = registrar;
        this.callbackManager = CallbackManager.Factory.create();
        this.registrar.addActivityResultListener(this);
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "social_share_plugin");
        channel.setMethodCallHandler(new SocialSharePlugin(registrar, channel));
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("SocialSharePlugin", "onActivityResult with request code = " +requestCode);
        if (requestCode == TWITTER_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("SocialSharePlugin", "Twitter done.");
                channel.invokeMethod("onSuccess", null);
            } else if (resultCode == RESULT_CANCELED) {
                Log.d("SocialSharePlugin", "Twitter cancelled.");
                channel.invokeMethod("onCancel", null);

            }

            return true;
        }

        return callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        final PackageManager pm = registrar.activeContext().getPackageManager();
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "shareToFeedInstagram":
                try {
                    pm.getPackageInfo(INSTAGRAM_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
                    instagramShare(call.<String>argument("type"), call.<String>argument("path"));
                    result.success(true);
                } catch (PackageManager.NameNotFoundException e) {
                    openPlayStore(INSTAGRAM_PACKAGE_NAME);
                    result.success(false);
                }

                result.success(null);
                break;
            case "shareToFeedFacebook":
                try {
                    pm.getPackageInfo(FACEBOOK_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
                    facebookShare(call.<String>argument("caption"), call.<String>argument("path"));
                    result.success(true);
                } catch (PackageManager.NameNotFoundException e) {
                    openPlayStore(FACEBOOK_PACKAGE_NAME);
                    result.success(false);
                }

                result.success(null);
                break;
            case "shareToFeedFacebookLink":
                try {
                    pm.getPackageInfo(FACEBOOK_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
                    facebookShareLink(call.<String>argument("quote"), call.<String>argument("url"));
                    result.success(true);
                } catch (PackageManager.NameNotFoundException e) {
                    openPlayStore(FACEBOOK_PACKAGE_NAME);
                    result.success(false);
                }
                break;
            case "shareToTwitterLink":
                try {
                    pm.getPackageInfo(TWITTER_PACKAGE_NAME, PackageManager.GET_ACTIVITIES);
                    twitterShareLink(call.<String>argument("text"), call.<String>argument("url"));
                    result.success(true);
                } catch (PackageManager.NameNotFoundException e) {
                    openPlayStore(TWITTER_PACKAGE_NAME);
                    result.success(false);
                }
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void openPlayStore(String packageName) {
        final Context context = registrar.activeContext();
        try {
            final Uri playStoreUri = Uri.parse("market://details?id=" + packageName);
            final Intent intent = new Intent(Intent.ACTION_VIEW, playStoreUri);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            final Uri playStoreUri = Uri.parse("https://play.google.com/store/apps/details?id=" + packageName);
            final Intent intent = new Intent(Intent.ACTION_VIEW, playStoreUri);
            context.startActivity(intent);
        }
    }

    private void instagramShare(String type, String imagePath) {
        final Context context = registrar.activeContext();
        final File image = new File(imagePath);
        final Uri uri = FileProvider.getUriForFile(context,
                context.getApplicationContext().getPackageName() + ".social.share.fileprovider", image);
        final Intent share = new Intent(Intent.ACTION_SEND);
        share.setType(type);
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.setPackage(INSTAGRAM_PACKAGE_NAME);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(share, "Share to"));
    }

    private void facebookShare(String caption, String mediaPath) {
        final Context context = registrar.activeContext();
        final File media = new File(mediaPath);
        final Uri uri = FileProvider.getUriForFile(context,
                context.getApplicationContext().getPackageName() + ".social.share.fileprovider", media);
        final SharePhoto photo = new SharePhoto.Builder().setImageUrl(uri).setCaption(caption).build();
        final SharePhotoContent content = new SharePhotoContent.Builder().addPhoto(photo).build();
        final ShareDialog shareDialog = new ShareDialog(registrar.activity());
        shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                Log.d("SocialSharePlugin", "Sharing successfully done.");
                channel.invokeMethod("onSuccess", null);
            }

            @Override
            public void onCancel() {
                Log.d("SocialSharePlugin", "Sharing cancelled.");
                channel.invokeMethod("onCancel", null);
            }

            @Override
            public void onError(FacebookException error) {
                Log.d("SocialSharePlugin", "Sharing error occurred.");
                channel.invokeMethod("onError", error.getMessage());
            }
        });

        if (ShareDialog.canShow(SharePhotoContent.class)) {
            shareDialog.show(content);
        }
    }

    private void facebookShareLink(String quote, String url) {
        Log.d("SocialSharePlugin", "facebookShareLink - quote: " +quote+ ", url: " +url);
        final Uri uri = Uri.parse(url);
        final ShareLinkContent content = new ShareLinkContent.Builder()
                .setContentUrl(uri).setQuote(quote).build();
        final ShareDialog shareDialog = new ShareDialog(registrar.activity());
        shareDialog.registerCallback(callbackManager, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                channel.invokeMethod("onSuccess", null);
                Log.d("SocialSharePlugin", "Sharing successfully done.");
            }

            @Override
            public void onCancel() {
                channel.invokeMethod("onCancel", null);
                Log.d("SocialSharePlugin", "Sharing cancelled.");
            }

            @Override
            public void onError(FacebookException error) {
                channel.invokeMethod("onError", error.getMessage());
                Log.d("SocialSharePlugin", "Sharing error occurred.");
            }
        }, 123459);

        if (ShareDialog.canShow(ShareLinkContent.class)) {
            shareDialog.show(content);
        }
    }

    private void twitterShareLink(String text, String url) {
        try {
            TweetComposer.Builder builder = new TweetComposer
                    .Builder(registrar.activity()).text(text);
            if (url != null && url.length() > 0) {
                builder.url(new URL(url));
            }

            final Intent intent = builder.createIntent();
            registrar.activity().startActivityForResult(intent, TWITTER_REQUEST_CODE);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
}
