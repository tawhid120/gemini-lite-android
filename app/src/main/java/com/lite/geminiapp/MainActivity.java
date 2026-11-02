package com.lite.geminiapp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "gemini_prefs";
    private static final String PREF_API_KEY = "api_key";

    private DrawerLayout drawerLayout;
    private RecyclerView rvChat;
    private RecyclerView rvConversations;
    private EditText etMessage;
    private ImageButton btnAddAttachment;
    private ImageButton btnVoice;
    private ImageButton btnSend;
    private ImageButton btnMenu;
    private ImageButton btnNewChatTop;
    private ImageButton btnSettings;
    private Button btnDrawerNewChat;
    private View drawerSettingsItem;

    // Attachment Preview
    private LinearLayout attachmentPreviewBar;
    private ImageView ivAttachmentThumb;
    private TextView tvAttachmentName;
    private ImageButton btnRemoveAttachment;

    // Data & Adapters
    private ChatDatabaseHelper dbHelper;
    private GeminiApiService geminiService;
    private SharedPreferences prefs;

    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<Conversation> conversationList = new ArrayList<>();
    private ChatMessageAdapter chatAdapter;
    private ConversationAdapter conversationAdapter;

    private long currentConversationId = -1;
    private String currentAttachmentPath = null;
    private String currentAttachmentBase64 = null;
    private String currentAttachmentMime = null;
    private Uri cameraTempFileUri;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Intent> documentLauncher;
    private ActivityResultLauncher<Intent> voiceLauncher;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new ChatDatabaseHelper(this);
        geminiService = new GeminiApiService();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        initViews();
        setupLaunchers();
        setupChatRecycler();
        setupConversationsRecycler();
        setupListeners();
        setupBackNavigation();

        // Load most recent conversation or start fresh
        loadInitialConversation();
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        rvChat = findViewById(R.id.rvChat);
        rvConversations = findViewById(R.id.rvConversations);
        etMessage = findViewById(R.id.etMessage);
        btnAddAttachment = findViewById(R.id.btnAddAttachment);
        btnVoice = findViewById(R.id.btnVoice);
        btnSend = findViewById(R.id.btnSend);
        btnMenu = findViewById(R.id.btnMenu);
        btnNewChatTop = findViewById(R.id.btnNewChatTop);
        btnSettings = findViewById(R.id.btnSettings);
        btnDrawerNewChat = findViewById(R.id.btnDrawerNewChat);
        drawerSettingsItem = findViewById(R.id.drawerSettingsItem);

        attachmentPreviewBar = findViewById(R.id.attachmentPreviewBar);
        ivAttachmentThumb = findViewById(R.id.ivAttachmentThumb);
        tvAttachmentName = findViewById(R.id.tvAttachmentName);
        btnRemoveAttachment = findViewById(R.id.btnRemoveAttachment);
    }

    private void setupChatRecycler() {
        chatAdapter = new ChatMessageAdapter(this, messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChat.setLayoutManager(layoutManager);
        rvChat.setAdapter(chatAdapter);
    }

    private void setupConversationsRecycler() {
        conversationAdapter = new ConversationAdapter(this, conversationList, new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onConversationClick(Conversation conversation) {
                loadConversation(conversation.getId());
                drawerLayout.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onConversationDelete(Conversation conversation) {
                dbHelper.deleteConversation(conversation.getId());
                loadConversationsList();
                if (currentConversationId == conversation.getId()) {
                    startNewChat();
                }
            }
        });
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(conversationAdapter);
        loadConversationsList();
    }

    private void setupListeners() {
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        btnNewChatTop.setOnClickListener(v -> startNewChat());
        btnDrawerNewChat.setOnClickListener(v -> {
            startNewChat();
            drawerLayout.closeDrawer(GravityCompat.START);
        });

        btnSettings.setOnClickListener(v -> showApiKeyDialog());
        drawerSettingsItem.setOnClickListener(v -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            showApiKeyDialog();
        });

        btnAddAttachment.setOnClickListener(v -> showUploadBottomSheet());
        btnVoice.setOnClickListener(v -> startVoiceRecognition());
        btnSend.setOnClickListener(v -> sendMessage());
        btnRemoveAttachment.setOnClickListener(v -> clearAttachment());
    }

    private void setupLaunchers() {
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && cameraTempFileUri != null) {
                        processAttachmentUri(cameraTempFileUri, "camera_photo.jpg", "image/jpeg");
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            String name = getFileNameFromUri(uri);
                            String mime = getContentResolver().getType(uri);
                            if (mime == null) mime = "image/jpeg";
                            processAttachmentUri(uri, name, mime);
                        }
                    }
                }
        );

        documentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            String name = getFileNameFromUri(uri);
                            String mime = getContentResolver().getType(uri);
                            if (mime == null) mime = "application/octet-stream";
                            processAttachmentUri(uri, name, mime);
                        }
                    }
                }
        );

        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String current = etMessage.getText().toString();
                            if (current.isEmpty()) {
                                etMessage.setText(matches.get(0));
                            } else {
                                etMessage.setText(current + " " + matches.get(0));
                            }
                            etMessage.setSelection(etMessage.getText().length());
                        }
                    }
                }
        );
    }

    private void showUploadBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.layout_upload_sheet, null);

        sheet.findViewById(R.id.optionCamera).setOnClickListener(v -> {
            dialog.dismiss();
            launchCamera();
        });

        sheet.findViewById(R.id.optionGallery).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            galleryLauncher.launch(Intent.createChooser(intent, "Select Image"));
        });

        sheet.findViewById(R.id.optionDocument).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            documentLauncher.launch(Intent.createChooser(intent, "Select Document"));
        });

        dialog.setContentView(sheet);
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
                currentAttachmentPath = photoFile.getAbsolutePath();
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraTempFileUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cameraLauncher.launch(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void processAttachmentUri(Uri uri, String fileName, String mimeType) {
        attachmentPreviewBar.setVisibility(View.VISIBLE);
        tvAttachmentName.setText(fileName);
        currentAttachmentMime = mimeType;

        backgroundExecutor.execute(() -> {
            Bitmap thumb = null;
            String base64 = null;
            String localPath = null;
            try {
                // Save locally in app storage for persistent chat history
                File localFile = saveUriToLocalCache(uri, fileName);
                if (localFile != null) {
                    localPath = localFile.getAbsolutePath();
                }

                if (mimeType.startsWith("image/")) {
                    thumb = createThumbnail(uri);
                    base64 = convertImageToBase64(uri);
                } else {
                    base64 = convertFileToBase64(uri);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Bitmap finalThumb = thumb;
            final String finalBase64 = base64;
            final String finalPath = localPath;

            mainHandler.post(() -> {
                if (finalThumb != null) {
                    ivAttachmentThumb.setImageBitmap(finalThumb);
                } else {
                    ivAttachmentThumb.setImageResource(R.drawable.ic_document);
                }
                currentAttachmentBase64 = finalBase64;
                currentAttachmentPath = finalPath;
            });
        });
    }

    private void sendMessage() {
        String prompt = etMessage.getText().toString().trim();
        String apiKey = prefs.getString(PREF_API_KEY, "");

        if (prompt.isEmpty() && currentAttachmentBase64 == null) {
            Toast.makeText(this, "Please enter a message or attach an image", Toast.LENGTH_SHORT).show();
            return;
        }

        if (apiKey.isEmpty()) {
            showApiKeyDialog();
            return;
        }

        // Create conversation in DB if not started
        if (currentConversationId == -1) {
            String title = prompt.isEmpty() ? "Image Query" : (prompt.length() > 28 ? prompt.substring(0, 28) + "..." : prompt);
            currentConversationId = dbHelper.createConversation(title);
            loadConversationsList();
        }

        // Add user message to UI & DB
        ChatMessage userMsg = new ChatMessage(currentConversationId, "user", prompt, currentAttachmentPath, System.currentTimeMillis());
        dbHelper.saveMessage(currentConversationId, "user", prompt, currentAttachmentPath);
        messageList.add(userMsg);

        // Add loading message to UI
        ChatMessage loadingMsg = ChatMessage.createLoadingMessage(currentConversationId);
        messageList.add(loadingMsg);
        int loadingPos = messageList.size() - 1;
        chatAdapter.notifyItemRangeInserted(messageList.size() - 2, 2);
        rvChat.smoothScrollToPosition(messageList.size() - 1);

        // Capture attachments for request
        String reqBase64 = currentAttachmentBase64;
        String reqMime = currentAttachmentMime;

        // Reset UI inputs
        etMessage.setText("");
        clearAttachment();

        // Dispatch directly to Gemini API
        geminiService.generateResponse(apiKey, prompt, reqBase64, reqMime, new GeminiApiService.GeminiCallback() {
            @Override
            public void onSuccess(String responseText) {
                loadingMsg.setText(responseText);
                loadingMsg.setLoading(false);
                dbHelper.saveMessage(currentConversationId, "gemini", responseText, null);
                chatAdapter.notifyItemChanged(loadingPos);
                rvChat.smoothScrollToPosition(messageList.size() - 1);
            }

            @Override
            public void onError(String errorMessage) {
                loadingMsg.setText("⚠️ " + errorMessage);
                loadingMsg.setLoading(false);
                chatAdapter.notifyItemChanged(loadingPos);
                rvChat.smoothScrollToPosition(messageList.size() - 1);
            }
        });
    }

    private void startNewChat() {
        currentConversationId = -1;
        messageList.clear();
        chatAdapter.notifyDataSetChanged();
        clearAttachment();
        etMessage.setText("");
    }

    private void loadConversation(long conversationId) {
        currentConversationId = conversationId;
        messageList.clear();
        messageList.addAll(dbHelper.getMessages(conversationId));
        chatAdapter.notifyDataSetChanged();
        if (!messageList.isEmpty()) {
            rvChat.scrollToPosition(messageList.size() - 1);
        }
        clearAttachment();
    }

    private void loadConversationsList() {
        conversationList.clear();
        conversationList.addAll(dbHelper.getAllConversations());
        conversationAdapter.notifyDataSetChanged();
    }

    private void loadInitialConversation() {
        List<Conversation> list = dbHelper.getAllConversations();
        if (!list.isEmpty()) {
            loadConversation(list.get(0).getId());
        } else {
            startNewChat();
        }
    }

    private void clearAttachment() {
        currentAttachmentPath = null;
        currentAttachmentBase64 = null;
        currentAttachmentMime = null;
        attachmentPreviewBar.setVisibility(View.GONE);
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_api_key, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etApiKey = dialogView.findViewById(R.id.etApiKey);
        String currentKey = prefs.getString(PREF_API_KEY, "");
        etApiKey.setText(currentKey);

        dialogView.findViewById(R.id.btnCancelApiKey).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSaveApiKey).setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if (!key.isEmpty()) {
                prefs.edit().putString(PREF_API_KEY, key).apply();
                Toast.makeText(this, "Gemini Engine connected!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please enter an API key", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
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

    private File createImageFile() throws Exception {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File storageDir = getExternalCacheDir();
        if (storageDir == null) storageDir = getCacheDir();
        return File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
    }

    private File saveUriToLocalCache(Uri uri, String fileName) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            File cacheDir = new File(getFilesDir(), "chat_images");
            if (!cacheDir.exists()) cacheDir.mkdirs();
            File dest = new File(cacheDir, System.currentTimeMillis() + "_" + fileName);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
            return dest;
        } catch (Exception e) {
            return null;
        }
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

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_MODERATE) {
            System.gc();
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdown();
        super.onDestroy();
    }
}
