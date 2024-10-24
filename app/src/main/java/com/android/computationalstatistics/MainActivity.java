package com.android.computationalstatistics;

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
                    .setTitle("Exit CompStat")
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

        if (url.equals("https://sites.google.com/view/comp-stat/sample-questions") ||
                url.equals("https://sites.google.com/view/comp-stat/assignments-previous-year-question-papers") ||
                url.equals("https://sites.google.com/view/comp-stat/books-and-study-notes") ||
                url.equals("https://sites.google.com/view/comp-stat/lecture-ppts")) {
            selectedColor = "#000000";
        } else if (url.equals("https://sites.google.com/view/comp-stat/nptel-videos") ||
                url.equals("file:///android_asset/offline.html")) {
            selectedColor = "#FF0000";
        } else if(url.contains("https://z-table.onrender.com/")){
            selectedColor = "#000000";
        } else if (url.equals("file:///android_asset/offline-message.html") ||
                url.equals("file:///android_asset/offline-startup.html")) {
            if (!CheckNetwork.isInternetAvailable(MainActivity.this)) {
                selectedColor = "#FF0000";
            } else {
                selectedColor = "#008000";
            }
        }

        setColors(selectedColor);
    }

    private void handleUrlSpecificSettings(String url) {

        String statusBarColor = selectedColor;
        String navBarColor = "#FFFFFFFF";

        String logo = "https://kstatic.googleusercontent.com/files/" +
                "97ecc831526fbe8c60fe88ef0d7a6cbf06361809f0acf85732668" +
                "1f6a1f35740d3bd7d69bf4a5381f5c31a863bccace4d9d1660379" +
                "182901f73d24ef137f6fb4";

        Log.d("URL VISIT", url);

        webView.loadUrl("javascript:(function() { \n" +
                "document.querySelectorAll('*').forEach(function(e) { \n" +
                "    e.style.webkitTapHighlightColor = 'transparent';\n" +
                "    e.style.touchAction = 'manipulation';\n" +
                "});\n" +

                "if (document.querySelector('.zDUgLc')) { \n" +
                "    document.querySelector('.zDUgLc').style.backgroundColor = '"+ statusBarColor +"';\n" +
                "}\n" +

                "if (document.querySelector('.VLoccc')) { \n" +
                "    document.querySelector('.VLoccc').style.backgroundColor = '"+ statusBarColor +"';\n" +
                "}\n" +

                "if (document.querySelector('.vu8Pwe')) { \n" +
                "    document.querySelector('.vu8Pwe').style.color = '#FFFFFFFF';\n" +
                "}\n" +

                "if (document.querySelector('.tCHXDc')) { \n" +
                "    document.querySelector('.tCHXDc').style.color = '#FFFFFFFF';\n" +
                "}\n" +

                "if (document.querySelector('.DXsoRd')) { \n" +
                "    document.querySelector('.DXsoRd').style.color = '#FFFFFFFF';\n" +
                "}\n" +

                "if (document.querySelector('.QTKDff')) { \n" +
                "    document.querySelector('.QTKDff').style.color = '#FFFFFFFF';\n" +
                "    document.querySelector('.QTKDff').style.fontWeight = '900';\n" +
                "}\n" +

                "document.querySelectorAll('.lhZOrc').forEach(function(e) { \n" +
                "    e.style.color = '"+ statusBarColor +"';\n" +
                "    e.style.fontWeight = '900';\n" +
                "});\n" +

                "if (document.querySelector('.JzO0Vc')) {\n" +
                "   const appVersion = document.createElement('div');\n" +
                "   appVersion.innerText = 'App version: v1.5.81';\n" +
                "   appVersion.style.cssText = 'color: #f5f5f5; position: absolute; bottom: 30px; left: 45px; line-height: 25px; font-size: 12px; font-weight: lighter;';\n" +
                "   document.querySelector('.JzO0Vc').appendChild(appVersion);\n" +
                "}\n" +

                "if (document.querySelector('.JzO0Vc')) {\n" +
                "   const dev = document.createElement('div');\n" +
                "   dev.innerText = 'Made By Abhishek';\n" +
                "   dev.style.cssText = 'color: #f5f5f5; background: "+ selectedColor +"; position: absolute; bottom: 0px; width:100%; text-align: center; padding: 3px 0; letter-spacing: 0.02rem; font-size: 10px; font-weight: lighter;';\n" +
                "   document.querySelector('.JzO0Vc').appendChild(dev);\n" +
                "}\n" +

                "if (document.querySelector('.JzO0Vc')) {\n" +
                "   const zTable = document.createElement('a');\n" +
                "   zTable.innerHTML = 'Z Table';\n" +
                "   zTable.onclick = function() { \n" +
                "      window.Android.visitUrl('https://z-table.onrender.com');\n" +
                "   };\n" +
                "   zTable.style.cssText = 'font-size: 15px; padding: 12px 5px; position: absolute;';\n" +
                "   document.querySelectorAll('.I35ICb').forEach(function(e) { \n" +
                "     e.append(zTable);\n" +
                "   });\n" +
                "}\n" +

                "document.querySelectorAll('a').forEach(function(e) { \n" +
                "    e.style.userSelect = 'none';\n" +
                "    e.onmouseover = function() { \n" +
                "        e.style.color = '" + statusBarColor + "';\n" +
                "    };\n" +
                "    e.onmouseout = function() { \n" +
                "        e.style.color = '';\n" +
                "    };\n" +
                "});\n" +

                "if (document.querySelector('.dZA9kd')) {\n" +
                "    document.querySelector('.dZA9kd').remove();\n" +
                "}\n" +

                "if (document.querySelector('.DXsoRd')) {\n" +
                "   document.querySelector('.DXsoRd').onclick = function() {;\n" +
                "      if(document.querySelector('.DXsoRd').title === 'Show sidebar') {\n" +
                "         console.log('open');\n" +
                "         document.querySelector('.JzO0Vc').style.cssText = 'backdrop-filter: blur(50px); background: rgba(0,0,0,0); width: 100%; max-width: 80%;';\n"+
                "      } else {\n" +
                "         console.log('close');\n" +
                "      };\n" +
                "   };\n" +
                "}\n" +

                "document.querySelectorAll('.lhZOrc').forEach(function(e) { \n" +
                "    e.style.color = '"+ statusBarColor +"';\n" +
                "    e.style.fontWeight = 'bold';\n" +
                "});\n" +
                "})()");

        if(url.equals("https://sites.google.com/view/comp-stat/home")){
            webView.loadUrl("javascript:(function() { \n" +
                    "if (document.querySelector('.JNdkSc-SmKAyb')) { \n" +
                    "    document.querySelector('.JNdkSc-SmKAyb').style.padding = '0';\n" +
                    "}\n" +

                    "document.querySelectorAll('.GJytX').forEach((e, i) => {\n" +
                    "   e.innerText = '';\n" +
                    "   if (i === 0) {\n" +
                    "      e.style.backgroundColor = '" + statusBarColor + "';\n" +
                    "      e.style.fontSize = '25px';\n" +
                    "      e.style.borderRadius = '10px';\n" +
                    "      e.style.padding = '17px 0 10px 0';\n" +
                    "      e.style.letterSpacing = '0.02rem';\n" +
                    "      e.style.userSelect = 'none';\n" +
                    "      e.style.width = '100%';\n" +

                    "      function createMarquee(text) {\n" +
                    "          const marquee = document.createElement('marquee');\n" +
                    "          marquee.setAttribute('behavior', 'scroll');\n" +
                    "          marquee.setAttribute('direction', 'left');\n" +
                    "          marquee.setAttribute('scrollamount', '10');\n" +
                    "          marquee.setAttribute('scrolldelay', '200');\n" +
                    "          marquee.innerText = text;\n" +

                    "          let clearTime = 0;\n" +
                    "          marquee.addEventListener('click', (e) => {\n" +
                    "              clearTime++;\n" +
                    "              if(clearTime > 1){\n" +
                    "                  return;\n" +
                    "              }\n" +
                    "              e.preventDefault();\n" +
                    "              marquee.stop();\n" +
                    "              setTimeout(() => {\n" +
                    "                  marquee.start();\n" +
                    "                  clearTime = 0;\n" +
                    "              }, 2000);\n" +
                    "          });\n" +
                    "         return marquee;\n" +
                    "      }\n" +

                    "const text = 'Welcome to Computational Statitics Classes';\n" +
                    "const marqueeElement = createMarquee(text);\n" +
                    "e.appendChild(marqueeElement);\n" +

                    "   }\n" +
                    "   if (i === 1 || i === 2 || i === 3 || i === 4) {\n" +
                    "      e.remove();\n" +
                    "   }\n" +
                    "});\n" +

//                    "document.querySelectorAll('.GJytX').forEach((e, i) => {\n" +
//                    "   e.innerText = '';\n" +
//                    "   e.style.backgroundColor = '" + statusBarColor + "';\n" +
//                    "   e.style.fontSize = '25px';\n" +
//                    "   e.style.borderRadius = '10px';\n" +
//                    "   e.style.display = 'inline-block';\n" +
//                    "   e.style.padding = '2px 5px 5px 7px';\n" +
//                    "   if (i === 0) {\n" +
//                    "      e.innerText = 'Welcome to';\n" +
//                    "   }\n" +
//                    "   if (i === 1) {\n" +
//                    "      e.innerText = 'Computational Statitics';\n" +
//                    "   }\n" +
//                    "   if (i === 2) {\n" +
//                    "      e.innerText = 'Classes';\n" +
//                    "   }\n" +
//                    "   if (i === 3 || i === 4) {\n" +
//                    "      e.remove();\n" +
//                    "   }\n" +
//                    "});\n" +

                    "if (document.querySelector('.jXK9ad-SmKAyb')) { \n" +
                    "    document.querySelector('.jXK9ad-SmKAyb').style.margin = '12px 0 -38px';\n" +
                    "}\n" +

                    "if (document.querySelector('.zfr3Q')) { \n" +
                    "    document.querySelector('.zfr3Q').style.marginBottom = '25px';\n" +
                    "}\n" +

                    "if (document.querySelector('.fktJzd')) { \n" +
                    "    document.querySelector('.fktJzd').classList.remove('fOU46b');\n" +
                    "}\n" +
                    "})()");

        } else if (url.equals("https://sites.google.com/view/comp-stat/sample-questions") ||
                url.equals("https://sites.google.com/view/comp-stat/assignments-previous-year-question-papers") ||
                url.equals("https://sites.google.com/view/comp-stat/books-and-study-notes") ||
                url.equals("https://sites.google.com/view/comp-stat/lecture-ppts")) {

            logo = "https://imgs.search.brave.com/2hwEu99nVHp0skBiwMA2Ye_EKuGvDqZGnXMOC8hKkIw/" +
                    "rs:fit:860:0:0:0/g:ce/aHR0cHM6Ly9rc3Rh/dGljLmdvb2dsZXVz/ZXJjb250ZW50LmNv/" +
                    "bS9maWxlcy9mNjgw/MjAwMTNhOTM1MzYx/N2EyZmNhMjhiMTk3/YzQ2YjM5ODNhYWYw/N2IwM" +
                    "mFlYWQwMDM1/ZDQ5ZWEyZTFiYmUx/ZmVjOTRiNWI0NzNh/ZTdmYmI5MGRmMjBk/NTljYzkwMW" +
                    "RhNDRh/MDUwOTcyMjBjN2Y5/YzY0YjQyZTYzOGM1/MTU4Yw";

            statusBarColor = "#000000";
            navBarColor = "#FFFFFFFF";

            webView.loadUrl("javascript:(function() { \n" +
                    "document.querySelectorAll('.TZchLb').forEach(function(e) { \n" +
                    "    e.style.background = '';\n" +
                    "    e.style.backgroundColor = '#000000';\n" +
                    "    e.style.backgroundSize = 'contain';\n" +
                    "    e.style.backgroundImage = 'url(\""+ logo +"\")';\n" +
                    "});\n" +

                    "document.querySelectorAll('.C9DxTc').forEach(function(e, i) { \n" +
                    "    if (i === 0) {\n" +
                    "        e.style.color = '"+ statusBarColor +"';\n" +
                    "    }\n" +
                    "});\n" +

                    "document.querySelectorAll('.J1oeHf').forEach(function(e) { \n" +
                    "    e.style.display = 'none';\n" +
                    "});\n" +

                    "if (document.querySelector('.zDUgLc')) { \n" +
                    "    document.querySelector('.zDUgLc').style.backgroundColor = '"+ statusBarColor +"';\n" +
                    "}\n" +

                    "if (document.querySelector('.VLoccc')) { \n" +
                    "    document.querySelector('.VLoccc').style.backgroundColor = '"+ statusBarColor +"';\n" +
                    "}\n" +

                    "const colors = ['#3484FF', '#FF0', '#0DA861'];\n" +
                    "const rand = Math.floor(Math.random() * colors.length);\n" +

                    "document.querySelectorAll('.lhZOrc').forEach(function(e) { \n" +
                    "    e.style.color = colors[rand];\n" +
                    "    e.style.fontWeight = 'bolder';\n" +
                    "});\n" +

                    "document.querySelectorAll('.JmGwdf').forEach(function(e) { \n" +
                    "    e.style.borderRadius = '10px';\n" +
                    "});\n" +

                    "document.querySelectorAll('.pHBU0d').forEach(function(e) { \n" +
                    "    e.style.borderRadius = '0 0 10px 10px';\n" +
                    "});\n" +

                    "document.querySelectorAll('a').forEach(function(e) { \n" +
                    "    e.onmouseover = function() { \n" +
                    "        e.style.color = colors[rand];\n" +
                    "    };\n" +
                    "    e.onmouseout = function() { \n" +
                    "        e.style.color = '';\n" +
                    "    };\n" +
                    "});\n" +

//                    "document.querySelectorAll('.t0pVcb').forEach((e, i) => {\n" +
//                    "   e.onclick = function() {\n" +
//                    "     setTimeout(() => Android.blackStatusBar('#000000', '#FFFFFFFF'), 2000);\n" +
//                    "     const element = document.querySelector('.ndfHFb-c4YZDc-TvD9Pc-LgbsSe');\n" +
//                    "     if (element) {\n" +
//                    "       element.onclick = function() {\n" +
//                    "           console.log('working');\n" +
//                    "           setTimeout(() => window.location.reload(), 2000);\n" +
//                    "             Android.defaultStatusBar('#3484FF', '#FFFFFFFF');\n" +
//                    "       };\n" +
//                    "     }\n" +
//                    "   };\n" +
//                    "});\n" +
                    "})()");

        } else if (url.equals("https://sites.google.com/view/comp-stat/nptel-videos")) {

            statusBarColor = "#FF0000";
            navBarColor = "#FFFFFFFF";
            webView.loadUrl("javascript:(function() { \n" +
                    "if (document.querySelector('.zDUgLc')) { \n" +
                    "    document.querySelector('.zDUgLc').style.backgroundColor = '"+ statusBarColor +"';\n" +
                    "}\n" +

                    "if (document.querySelector('.VLoccc')) { \n" +
                    "    document.querySelector('.VLoccc').style.backgroundColor = '"+ statusBarColor +"';\n" +
                    "}\n" +

                    "document.querySelectorAll('div[class=\"WIdY2d M1aSXe\"]').forEach(function(e) { \n" +
                    "   if (e) { \n" +
                    "      e.style.height = '260px';\n" +
                    "   }\n" +
                    "});\n" +

                    "document.querySelectorAll('.YMEQtf').forEach(function(e) { \n" +
                    "   if (e) { \n" +
                    "      e.style.borderRadius = '7px';\n" +
                    "   }\n" +
                    "});\n" +

                    "document.querySelectorAll('.hJDwNd-AhqUyc-c5RTEf').forEach(function(e) { \n" +
                    "   if (e) { \n" +
                    "      e.style.width = '100%';\n" +
                    "   }\n" +
                    "});\n" +

                    "document.querySelectorAll('.C9DxTc').forEach(function(e, i) { \n" +
                    "    if (i === 0) {\n" +
                    "        e.style.color = '"+ statusBarColor +"';\n" +
                    "    }\n" +
                    "});\n" +

                    "document.querySelectorAll('.ytp-youtube-button').forEach(function(e) { \n" +
                    "   if (e) { \n" +
                    "      e.style.display = 'none';\n" +
                    "   }\n" +
                    "});\n" +

                    "document.querySelectorAll('.lhZOrc').forEach(function(e) { \n" +
                    "    e.style.color = '"+ statusBarColor +"';\n" +
                    "    e.style.fontWeight = 'bolder';\n" +
                    "});\n" +

                    "document.querySelectorAll('a').forEach(function(e) { \n" +
                    "    e.onmouseover = function() { \n" +
                    "        e.style.color = '"+ statusBarColor +"';\n" +
                    "    };\n" +
                    "    e.onmouseout = function() { \n" +
                    "        e.style.color = '';\n" +
                    "    };\n" +
                    "});\n" +
                    "})()");


        } else if(url.equals("https://sites.google.com/view/comp-stat/topic-wise-notes/multivariate-normal-distribution")){

            webView.loadUrl("javascript:(function() { \n" +
                    "document.querySelectorAll('.GJytX').forEach(function(e) {\n" +
                    "    e.style.backgroundColor = '" + statusBarColor + "';\n" +
                    "});\n" +

                    "document.querySelectorAll('.C9DxTc').forEach(function(e, i) { \n" +
                    "    if (i === 0) {\n" +
                    "        e.style.color = '"+ statusBarColor +"';\n" +
                    "    }\n" +
                    "});\n" +

                    "document.querySelectorAll('.J1oeHf').forEach(function(e) { \n" +
                    "    e.style.display = 'none';\n" +
                    "});\n" +

                    "})()");

        } else if(url.contains("https://sites.google.com/view/comp-stat")){

            webView.loadUrl("javascript:(function() { \n" +
                    "document.querySelectorAll('.GJytX').forEach(function(e) {\n" +
                    "    e.style.backgroundColor = '" + statusBarColor + "';\n" +
                    "});\n" +

                    "document.querySelectorAll('.C9DxTc').forEach(function(e, i) { \n" +
                    "    if (i === 0) {\n" +
                    "        e.style.color = '"+ statusBarColor +"';\n" +
                    "    }\n" +
                    "});\n" +

                    "document.querySelectorAll('p[role=\"presentation\"]').forEach(function(e, i) { \n" +
                    "   e.children[0].style.color = '"+ statusBarColor +"';\n" +
                    "});\n" +

                    "document.querySelectorAll('.J1oeHf').forEach(function(e) { \n" +
                    "    e.style.display = 'none';\n" +
                    "});\n" +

                    "})()");

        } else if (url.equals("file:///android_asset/offline.html")) {
            statusBarColor = "#FF0000";
            navBarColor = "#F0F0F0";
        } else if (url.equals("file:///android_asset/offline-message.html") ||
                url.equals("file:///android_asset/offline-startup.html")) {
            if (!CheckNetwork.isInternetAvailable(MainActivity.this)) {
                statusBarColor = "#FF0000";
                navBarColor = "#F0F0F0";
            } else {
                statusBarColor = "#008000";
                navBarColor = "#F5F5F5";
            }
        }

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

