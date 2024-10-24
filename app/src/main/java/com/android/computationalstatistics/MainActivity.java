package com.example.com;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.net.http.SslError;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import android.os.PersistableBundle;
import androidx.palette.graphics.Palette;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.provider.Settings;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    String websiteURL = "https://sites.google.com/view/comp-stat/home";
    private WebView webView;
    private SwipeRefreshLayout mySwipeRefreshLayout;
    private ConnectivityReceiver connectivityReceiver;
    private ProgressBar progressBar;
    private View fullScreenOverlay;
    private Animation fadeIn;
    private Animation fadeOut;
    private String selectedColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mySwipeRefreshLayout = findViewById(R.id.swipeContainer);
        mySwipeRefreshLayout.setProgressViewOffset(false, 100, 300);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        fullScreenOverlay = findViewById(R.id.fullScreenOverlay);
        fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        selectedColor = getColors();

        setupWebView();
        setupCookieManager();
        connectivityReceiver = new ConnectivityReceiver();
        registerReceiver(connectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mySwipeRefreshLayout.setOnRefreshListener(() -> {
            if (CheckNetwork.isInternetAvailable(MainActivity.this)) {
                webView.reload();
            } else {
                webView.loadUrl("file:///android_asset/offline-message.html");
            }
            mySwipeRefreshLayout.setRefreshing(false);
        });

        if (!CheckNetwork.isInternetAvailable(MainActivity.this)) {
            webView.loadUrl("file:///android_asset/offline-startup.html");
        } else {
            fullScreenOverlay.setVisibility(View.VISIBLE);
            loadWebPage();
        }

        disableSwipeWhenDivScrollActive();
    }

    private String getColors() {
        String[] colors = {"#3484FF", "#17C5B3", "#F40C69", "#F40CCD", "#DC0D66", "#A911BA", "#E65D13"};
        int rand = (int) (Math.random() * colors.length);
        return colors[rand];
    }

    private void setColors(String colors) {
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(Color.parseColor(colors)));
        mySwipeRefreshLayout.setColorSchemeColors(Color.parseColor(colors));
    }

    private void loadWebPage() {
        webView.loadUrl(websiteURL);
    }

    private void setupWebView() {
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new WebAppInterface(),"Android");
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setupDownloadListener();
    }

    private void setupCookieManager() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        CookieManager.setAcceptFileSchemeCookies(true);
    }


    private void disableSwipeWhenDivScrollActive() {
        webView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                webView.evaluateJavascript(
                        "(function() { return document.querySelector('div[style*=\"overflow\"]') !== null; })();",
                        value -> {
                            if ("true".equals(value)) {
                                mySwipeRefreshLayout.setEnabled(false);
                                setStatusBarAndNavBarColor("#000000", "#FFFFFFFF");
                            } else {
                                mySwipeRefreshLayout.setEnabled(true);
                            }
                        });
            }
            return false;
        });
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.contains("docs.google.com") || url.contains("drive.google.com") ||
                    url.contains("sheets.google.com") || url.contains("slides.google.com") ||
                    url.startsWith("https://www.youtube.com/") || url.contains("m.youtube.com")) {

                mySwipeRefreshLayout.setEnabled(false);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            handleColors(url);
            progressBar.setVisibility(View.VISIBLE);
            fullScreenOverlay.setVisibility(View.VISIBLE);
            progressBar.startAnimation(fadeIn);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            handleUrlSpecificSettings(url);

            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    progressBar.setVisibility(View.GONE);
                    fullScreenOverlay.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

           progressBar.startAnimation(fadeOut);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            Log.e("WebView Error", "Error: " + error.getDescription() + " Code: " + error.getErrorCode());
            if (error.getErrorCode() != -1) {
                webView.loadUrl("file:///android_asset/offline-message.html");
            } else if (error.getDescription().toString().contains("net::ERR_NETWORK_ACCESS_DENIED")) {
                webView.loadUrl("file:///android_asset/offline.html");
            }
            mySwipeRefreshLayout.setRefreshing(false);
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            Log.e("WebView SSL Error", "SSL Error: " + error.toString());
            handler.cancel();
            webView.loadUrl("file:///android_asset/offline-message.html");
            mySwipeRefreshLayout.setRefreshing(false);
        }
    }


    private void setupDownloadListener() {
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    startDownload(url, userAgent, contentDisposition, mimeType);
                }
            } else {
                startDownload(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimeType);
        String cookies = CookieManager.getInstance().getCookie(url);
        request.addRequestHeader("cookie", cookies);
        request.addRequestHeader("User-Agent", userAgent);
        request.setDescription("Downloading file...");
        request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimeType));

        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(request);
        Toast.makeText(getApplicationContext(), "Downloading File", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Permission denied to write to storage", Toast.LENGTH_SHORT).show();
        }
    }

    static class CheckNetwork {
        public static boolean isInternetAvailable(Context context) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            AlertDialog alertDialog = new AlertDialog.Builder(this)
                    .setTitle("!Exit CompStat")
                    .setMessage("Do you want to exit the app? Please confirm your action.")
                    .setPositiveButton("STAY", (dialog, which) -> dialog.dismiss())
                    .setNegativeButton("EXIT", (dialog, which) -> finish())
                    .create();

            alertDialog.setOnShowListener(dialog -> {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor(selectedColor));
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor(selectedColor));
            });

            alertDialog.show();
        }
    }



    public class ConnectivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CheckNetwork.isInternetAvailable(context)) {
                webView.reload();
            }
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void openNetworkSettings() {
            Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
            startActivity(intent);
        }

        @JavascriptInterface
        public void openAppInfo() {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(getApplicationContext(), "Enable app network permission and try again", Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void visitUrl(String url) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        }
    }

    private void handleColors(String url) {
        Log.d("URL VISIT STARTED", url);
        selectedColor = getColors();
        setColors(selectedColor);
    }

    private void handleUrlSpecificSettings(String url) {

        String statusBarColor = selectedColor;
        String navBarColor = "#FFFFFFFF";

        setStatusBarAndNavBarColor(statusBarColor, navBarColor);
    }


    private void setStatusBarAndNavBarColor(String statusBarColor, String navBarColor) {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        window.setStatusBarColor(Color.parseColor(statusBarColor));
        window.setNavigationBarColor(Color.parseColor(navBarColor));
    }

}

