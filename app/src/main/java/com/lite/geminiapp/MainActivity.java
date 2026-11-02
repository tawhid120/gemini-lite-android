package com.lite.geminiapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String GEMINI_APP_URL = "https://gemini.google.com/app";
    private static final String GOOGLE_SIGNIN_URL = "https://accounts.google.com/signin/v2/identifier?service=bard&continue=https%3A%2F%2Fgemini.google.com%2Fapp";

    private DrawerLayout drawerLayout;
    private WebView webView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Google Sign-In Views
    private View layoutGoogleSignInPrompt;
    private View btnSignInWithGoogle;
    private TextView tvGoogleAccountBadge;
    private TextView tvDrawerAccountStatus;
    private View drawerItemSync;
    private View drawerItemSignOut;

    // Native Bottom Bar Views
    private LinearLayout nativeBottomContainer;
    private EditText etMessage;
    private ImageButton btnAddAttachment;
    private ImageButton btnVoice;
    private ImageButton btnSend;
    private ImageButton btnMenu;
    private ImageButton btnNewChatTop;
    private View btnDrawerNewChat;

    // Attachment Preview Bar Views
    private LinearLayout attachmentPreviewBar;
    private ImageView ivAttachmentThumb;
    private TextView tvAttachmentName;
    private TextView tvAttachmentStatus;
    private ImageButton btnRemoveAttachment;

    // Current Attachment State
    private Uri currentAttachmentUri = null;
    private String currentAttachmentBase64 = null;
    private String currentAttachmentMimeType = null;
    private String currentAttachmentFileName = null;
    private boolean isAttachmentUploadedToWeb = false;

    // Pending Message queue
    private String pendingMessageText = null;
    private boolean isWebPageReady = false;

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> documentLauncher;
    private ActivityResultLauncher<Intent> voiceLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;
    private ValueCallback<Uri[]> mFilePathCallback;

    private Uri cameraTempFileUri;
    private PermissionRequest pendingAudioRequest;
    private long backPressedTime = 0;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupLaunchers();
        setupWebView();
        setupNativeBarListeners();
        setupSwipeRefresh();
        setupBackNavigation();

        // Check authentication state
        checkGoogleAuthAndLoad();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        layoutGoogleSignInPrompt = findViewById(R.id.layoutGoogleSignInPrompt);
        btnSignInWithGoogle = findViewById(R.id.btnSignInWithGoogle);
        tvGoogleAccountBadge = findViewById(R.id.tvGoogleAccountBadge);
        tvDrawerAccountStatus = findViewById(R.id.tvDrawerAccountStatus);
        drawerItemSync = findViewById(R.id.drawerItemSync);
        drawerItemSignOut = findViewById(R.id.drawerItemSignOut);

        nativeBottomContainer = findViewById(R.id.nativeBottomContainer);
        etMessage = findViewById(R.id.etMessage);
        btnAddAttachment = findViewById(R.id.btnAddAttachment);
        btnVoice = findViewById(R.id.btnVoice);
        btnSend = findViewById(R.id.btnSend);
        btnMenu = findViewById(R.id.btnMenu);
        btnNewChatTop = findViewById(R.id.btnNewChatTop);
        btnDrawerNewChat = findViewById(R.id.btnDrawerNewChat);

        attachmentPreviewBar = findViewById(R.id.attachmentPreviewBar);
        ivAttachmentThumb = findViewById(R.id.ivAttachmentThumb);
        tvAttachmentName = findViewById(R.id.tvAttachmentName);
        tvAttachmentStatus = findViewById(R.id.tvAttachmentStatus);
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment);

        swipeRefreshLayout.setColorSchemeResources(R.color.primary, R.color.color_accent);
        btnRemoveAttachment.setOnClickListener(v -> clearAttachment());
    }

    private void setupLaunchers() {
        // 1. Camera Launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && cameraTempFileUri != null) {
                        handleSelectedFile(cameraTempFileUri, "camera_photo.jpg", "image/jpeg");
                    }
                }
        );

        // 2. Gallery Launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            String name = getFileNameFromUri(uri);
                            String mime = getContentResolver().getType(uri);
                            if (mime == null) mime = "image/jpeg";
                            handleSelectedFile(uri, name, mime);
                        }
                    }
                }
        );

        // 3. Document Launcher
        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            String name = getFileNameFromUri(uri);
                            String mime = getContentResolver().getType(uri);
                            if (mime == null) mime = "application/octet-stream";
                            handleSelectedFile(uri, name, mime);
                        }
                    }
                }
        );

        // 4. Voice Input Launcher
        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> spokenText = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (spokenText != null && !spokenText.isEmpty()) {
                            String current = etMessage.getText().toString();
                            if (current.isEmpty()) {
                                etMessage.setText(spokenText.get(0));
                            } else {
                                etMessage.setText(current + " " + spokenText.get(0));
                            }
                            etMessage.setSelection(etMessage.getText().length());
                        }
                    }
                }
        );

        // 5. Audio Permission Launcher
        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (pendingAudioRequest != null) {
                        if (isGranted) {
                            pendingAudioRequest.grant(pendingAudioRequest.getResources());
                        } else {
                            pendingAudioRequest.deny();
                        }
                        pendingAudioRequest = null;
                    }
                }
        );
    }

    private void setupNativeBarListeners() {
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        btnNewChatTop.setOnClickListener(v -> startNewGeminiChat());
        btnDrawerNewChat.setOnClickListener(v -> {
            startNewGeminiChat();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        // "Sign in with Google" button tap
        btnSignInWithGoogle.setOnClickListener(v -> launchGoogleSignIn());

        // Drawer Sync Action
        drawerItemSync.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            webView.reload();
            Toast.makeText(this, "Syncing Google Gemini history...", Toast.LENGTH_SHORT).show();
        });

        // Drawer Sign Out Action
        drawerItemSignOut.setOnClickListener(v -> signOutFromGoogle());

        // Tap '+' to open Native Upload Options Bottom Sheet
        btnAddAttachment.setOnClickListener(v -> showUploadBottomSheet());

        // Tap Mic for Voice Recognition
        btnVoice.setOnClickListener(v -> startVoiceRecognition());

        // Tap Send to dispatch prompt to Gemini
        btnSend.setOnClickListener(v -> submitPrompt());
    }

    private void checkGoogleAuthAndLoad() {
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie("https://gemini.google.com");

        boolean hasAuthCookies = cookies != null && (cookies.contains("SID=") || cookies.contains("__Secure-1PSID=") || cookies.contains("SAPISID="));

        if (hasAuthCookies) {
            // Already signed in with Google
            layoutGoogleSignInPrompt.setVisibility(View.GONE);
            tvGoogleAccountBadge.setText("Google Connected");
            tvDrawerAccountStatus.setText("Google Connected");
            webView.loadUrl(GEMINI_APP_URL);
        } else {
            // Show "Sign in with Google" prompt
            layoutGoogleSignInPrompt.setVisibility(View.VISIBLE);
            tvGoogleAccountBadge.setText("Sign In Required");
            tvDrawerAccountStatus.setText("Not Signed In");
        }
    }

    private void launchGoogleSignIn() {
        layoutGoogleSignInPrompt.setVisibility(View.GONE);
        Toast.makeText(this, "Opening Google Sign-In...", Toast.LENGTH_SHORT).show();
        webView.loadUrl(GOOGLE_SIGNIN_URL);
    }

    private void signOutFromGoogle() {
        drawerLayout.closeDrawer(GravityCompat.START);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();

        layoutGoogleSignInPrompt.setVisibility(View.VISIBLE);
        tvGoogleAccountBadge.setText("Signed Out");
        tvDrawerAccountStatus.setText("Not Signed In");
        Toast.makeText(this, "Signed out from Google", Toast.LENGTH_SHORT).show();
    }

    private void startNewGeminiChat() {
        String js = "if (window.GeminiBridge) { window.GeminiBridge.startNewChat(); } else { window.location.href = '" + GEMINI_APP_URL + "'; }";
        webView.evaluateJavascript(js, null);
        clearAttachment();
        etMessage.setText("");
    }

    private void showUploadBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.layout_upload_sheet, null);

        // Camera option
        sheetView.findViewById(R.id.optionCamera).setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });

        // Gallery option
        sheetView.findViewById(R.id.optionGallery).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            galleryLauncher.launch(Intent.createChooser(intent, "Choose Image"));
        });

        // Document option
        sheetView.findViewById(R.id.optionDocument).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            documentLauncher.launch(Intent.createChooser(intent, "Choose Document"));
        });

        dialog.setContentView(sheetView);
        dialog.show();
    }

    private void launchCamera() {
        try {
            File photoFile = createImageFile();
            if (photoFile != null) {
                cameraTempFileUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        photoFile
                );
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraTempFileUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cameraLauncher.launch(takePictureIntent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Gemini...");
        try {
            voiceLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Speech recognition not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleSelectedFile(Uri uri, String fileName, String mimeType) {
        currentAttachmentUri = uri;
        currentAttachmentFileName = fileName;
        currentAttachmentMimeType = mimeType;
        isAttachmentUploadedToWeb = false;

        attachmentPreviewBar.setVisibility(View.VISIBLE);
        tvAttachmentName.setText(fileName);
        tvAttachmentStatus.setText("Uploading to your Gemini account...");

        backgroundExecutor.execute(() -> {
            Bitmap thumb = null;
            String base64Str = null;
            try {
                if (mimeType.startsWith("image/")) {
                    thumb = createThumbnail(uri);
                    base64Str = convertImageToBase64(uri);
                } else {
                    base64Str = convertFileToBase64(uri);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Bitmap finalThumb = thumb;
            final String finalBase64 = base64Str;

            mainHandler.post(() -> {
                if (finalThumb != null) {
                    ivAttachmentThumb.setImageBitmap(finalThumb);
                } else {
                    ivAttachmentThumb.setImageResource(R.drawable.ic_document);
                }

                currentAttachmentBase64 = finalBase64;

                // IMMEDIATELY upload file via JavaScript to Gemini web DOM
                if (currentAttachmentBase64 != null && isWebPageReady) {
                    dispatchFileUploadToWeb(currentAttachmentBase64, currentAttachmentMimeType, currentAttachmentFileName);
                }
            });
        });
    }

    private void dispatchFileUploadToWeb(String base64, String mimeType, String fileName) {
        String script = "if (window.GeminiBridge) { " +
                "  window.GeminiBridge.uploadBase64File('" + escapeJs(base64) + "', '" + escapeJs(mimeType) + "', '" + escapeJs(fileName) + "');" +
                "}";
        webView.evaluateJavascript(script, value -> {
            isAttachmentUploadedToWeb = true;
            tvAttachmentStatus.setText("Ready to send");
        });
    }

    private void submitPrompt() {
        String text = etMessage.getText().toString().trim();

        if (text.isEmpty() && currentAttachmentBase64 == null) {
            Toast.makeText(this, "Type a message or attach a file", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        // If web page is still loading, queue it
        if (!isWebPageReady) {
            pendingMessageText = text;
            Toast.makeText(this, "Connecting to Gemini account...", Toast.LENGTH_SHORT).show();
            etMessage.setText("");
            return;
        }

        String script;
        if (currentAttachmentBase64 != null && !isAttachmentUploadedToWeb) {
            script = "if (window.GeminiBridge) { " +
                    "  window.GeminiBridge.sendPrompt('" + escapeJs(text) + "', '" +
                    escapeJs(currentAttachmentBase64) + "', '" +
                    escapeJs(currentAttachmentMimeType) + "', '" +
                    escapeJs(currentAttachmentFileName) + "');" +
                    "}";
        } else {
            script = "if (window.GeminiBridge) { " +
                    "  window.GeminiBridge.setText('" + escapeJs(text) + "');" +
                    "  setTimeout(function() { window.GeminiBridge.clickSend(); }, 250);" +
                    "}";
        }

        webView.evaluateJavascript(script, null);

        etMessage.setText("");
        clearAttachment();
    }

    private void clearAttachment() {
        currentAttachmentUri = null;
        currentAttachmentBase64 = null;
        currentAttachmentMimeType = null;
        currentAttachmentFileName = null;
        isAttachmentUploadedToWeb = false;
        attachmentPreviewBar.setVisibility(View.GONE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Sanitize User-Agent to Bypass Google OAuth "disallowed_useragent" Check
        String defaultUa = settings.getUserAgentString();
        String cleanUa = defaultUa.replace("; wv", "").replaceAll("Version\\/[0-9\\.]+", "");
        settings.setUserAgentString(cleanUa);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onUploadStarted(String fileName) {
                mainHandler.post(() -> {
                    isAttachmentUploadedToWeb = true;
                    tvAttachmentStatus.setText("Uploaded to Gemini");
                });
            }
        }, "AndroidNative");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                } else {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;
                showUploadBottomSheet();
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        pendingAudioRequest = request;
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                                == PackageManager.PERMISSION_GRANTED) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                        }
                        return;
                    }
                }
                request.deny();
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                return handleUrlRouting(url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrlRouting(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                isWebPageReady = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                CookieManager.getInstance().flush();

                // If URL is gemini.google.com, Google login completed!
                if (url.contains("gemini.google.com")) {
                    layoutGoogleSignInPrompt.setVisibility(View.GONE);
                    tvGoogleAccountBadge.setText("Google Connected");
                    tvDrawerAccountStatus.setText("Google Connected");
                }

                // Inject CSS and bridge script
                injectBridgeScript();

                isWebPageReady = true;

                if (currentAttachmentBase64 != null && !isAttachmentUploadedToWeb) {
                    dispatchFileUploadToWeb(currentAttachmentBase64, currentAttachmentMimeType, currentAttachmentFileName);
                }

                if (pendingMessageText != null) {
                    String msg = pendingMessageText;
                    pendingMessageText = null;
                    etMessage.setText(msg);
                    submitPrompt();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !isNetworkAvailable()) {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(MainActivity.this, "Network connection issue", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void injectBridgeScript() {
        String js = "(function() {" +
                "  if (window.GeminiBridge) return;" +
                "  window.GeminiBridge = {" +
                "    hideWebInput: function() {" +
                "      try {" +
                "        const css = 'footer, .input-area-container, rich-textarea, div[class*=\"input-area\"], div[class*=\"bottom-container\"] { display: none !important; }';" +
                "        let style = document.getElementById('gemini-lite-style');" +
                "        if (!style) {" +
                "          style = document.createElement('style');" +
                "          style.id = 'gemini-lite-style';" +
                "          style.textContent = css;" +
                "          document.head.appendChild(style);" +
                "        }" +
                "      } catch(e) {}" +
                "    }," +
                "    findEditor: function() {" +
                "      return document.querySelector('rich-textarea div[contenteditable=\"true\"]') ||" +
                "             document.querySelector('div[contenteditable=\"true\"][role=\"textbox\"]') ||" +
                "             document.querySelector('.ql-editor.textarea') ||" +
                "             document.querySelector('div[contenteditable=\"true\"]') ||" +
                "             document.querySelector('textarea');" +
                "    }," +
                "    setText: function(text) {" +
                "      const editor = this.findEditor();" +
                "      if (editor) {" +
                "        editor.focus();" +
                "        if (editor.tagName.toLowerCase() === 'textarea') {" +
                "          editor.value = text;" +
                "        } else {" +
                "          editor.innerText = text;" +
                "        }" +
                "        editor.dispatchEvent(new InputEvent('input', { bubbles: true, cancelable: true }));" +
                "        editor.dispatchEvent(new Event('change', { bubbles: true }));" +
                "        return true;" +
                "      }" +
                "      return false;" +
                "    }," +
                "    uploadBase64File: function(base64Data, mimeType, fileName) {" +
                "      try {" +
                "        const byteCharacters = atob(base64Data);" +
                "        const byteNumbers = new Array(byteCharacters.length);" +
                "        for (let i = 0; i < byteCharacters.length; i++) {" +
                "          byteNumbers[i] = byteCharacters.charCodeAt(i);" +
                "        }" +
                "        const byteArray = new Uint8Array(byteNumbers);" +
                "        const blob = new Blob([byteArray], { type: mimeType });" +
                "        const file = new File([blob], fileName, { type: mimeType, lastModified: Date.now() });" +
                "        let fileInput = document.querySelector('input[type=\"file\"]');" +
                "        if (!fileInput) {" +
                "          const btn = document.querySelector('button[aria-label*=\"Upload\" i], button[aria-label*=\"Attach\" i], button[aria-label*=\"Add\" i]');" +
                "          if (btn) btn.click();" +
                "          fileInput = document.querySelector('input[type=\"file\"]');" +
                "        }" +
                "        if (fileInput) {" +
                "          const dt = new DataTransfer();" +
                "          dt.items.add(file);" +
                "          fileInput.files = dt.files;" +
                "          fileInput.dispatchEvent(new Event('change', { bubbles: true }));" +
                "          fileInput.dispatchEvent(new Event('input', { bubbles: true }));" +
                "          if (window.AndroidNative) {" +
                "            window.AndroidNative.onUploadStarted(fileName);" +
                "          }" +
                "          return true;" +
                "        }" +
                "      } catch(e) {" +
                "        console.error(e);" +
                "      }" +
                "      return false;" +
                "    }," +
                "    clickSend: function() {" +
                "      const btn = document.querySelector('button[aria-label=\"Send message\"],' +" +
                "                                         'button[aria-label*=\"Send\" i],' +" +
                "                                         'button[aria-label*=\"পাঠান\"],' +" +
                "                                         'button.send-button,' +" +
                "                                         'button:has(mat-icon[data-mat-icon-name=\"send\"])');" +
                "      if (btn && !btn.disabled) {" +
                "        btn.click();" +
                "        return true;" +
                "      } else {" +
                "        const editor = this.findEditor();" +
                "        if (editor) {" +
                "          editor.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true, cancelable: true, key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }));" +
                "          return true;" +
                "        }" +
                "      }" +
                "      return false;" +
                "    }," +
                "    startNewChat: function() {" +
                "      const btn = document.querySelector('button[aria-label*=\"New chat\" i], a[aria-label*=\"New chat\" i]');" +
                "      if (btn) btn.click();" +
                "      else window.location.href = '" + GEMINI_APP_URL + "';" +
                "    }," +
                "    sendPrompt: function(text, base64Data, mimeType, fileName) {" +
                "      const self = this;" +
                "      if (base64Data) {" +
                "        self.uploadBase64File(base64Data, mimeType, fileName);" +
                "        setTimeout(function() {" +
                "          self.setText(text);" +
                "          setTimeout(function() { self.clickSend(); }, 350);" +
                "        }, 500);" +
                "      } else {" +
                "        self.setText(text);" +
                "        setTimeout(function() { self.clickSend(); }, 250);" +
                "      }" +
                "    }" +
                "  };" +
                "  window.GeminiBridge.hideWebInput();" +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    private boolean handleUrlRouting(String url) {
        if (url == null) return false;

        // Keep Google login and Gemini inside app
        if (url.contains("gemini.google.com") ||
                url.contains("accounts.google.com") ||
                url.contains("myaccount.google.com") ||
                url.contains("ssl.gstatic.com") ||
                url.contains("apis.google.com") ||
                url.contains("googleusercontent.com")) {
            return false;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                webView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            }
        });

        webView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            swipeRefreshLayout.setEnabled(webView.getScrollY() == 0);
        });
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    if (System.currentTimeMillis() - backPressedTime < 2000) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    } else {
                        backPressedTime = System.currentTimeMillis();
                        Toast.makeText(MainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalCacheDir();
        if (storageDir == null) storageDir = getCacheDir();
        return File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "image.jpg";
    }

    private Bitmap createThumbnail(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            return BitmapFactory.decodeStream(is, null, options);
        } catch (Exception e) {
            return null;
        }
    }

    private String convertImageToBase64(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2; // Low memory sampling for 2GB RAM device
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (bitmap == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] bytes = baos.toByteArray();
            bitmap.recycle();
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String convertFileToBase64(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private String escapeJs(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) {
            if (webView != null) webView.freeMemory();
            System.gc();
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdown();
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.destroy();
        }
        super.onDestroy();
    }
}
