package com.aditya.passwordmanager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;

import java.util.concurrent.Executor;
import androidx.biometric.BiometricPrompt;
public class VaultFragment extends Fragment {
    TextView t1, t2;
    FloatingActionButton add_new;
    RecyclerView recyclerView;
    LinearLayoutManager linearLayoutManager;
    ArrayList<UserData> userDataList;
    Adapter adapter;
    Button importButton;
    public static final int FILE_PICKER_REQUEST_CODE = 101;
    private static final int STORAGE_PERMISSION_CODE = 100;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_vault, container, false);

        checkStoragePermission();

        t1 = view.findViewById(R.id.textView4);
        t2 = view.findViewById(R.id.textView8);
        add_new = view.findViewById(R.id.add_more);
        recyclerView = view.findViewById(R.id.recycler_view);
        importButton = view.findViewById(R.id.importButton);
        userDataList = new ArrayList<>();

        loadData();
        setupRecyclerView();

        add_new.setOnClickListener(v -> showAddDataDialog());
        importButton.setOnClickListener(v -> importFun());

        return view;
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) needs MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            } else {
                onStoragePermissionGranted();
            }
        } else {
            // For Android 10 and below
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {

                requestPermissions(new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, STORAGE_PERMISSION_CODE);
            } else {
                onStoragePermissionGranted();
            }
        }
    }

    private void onStoragePermissionGranted() {
        Toast.makeText(requireContext(), "Storage Permission Granted", Toast.LENGTH_SHORT).show();
        // Access your files or start file operations here
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                onStoragePermissionGranted();
            } else {
                Toast.makeText(requireContext(), "Storage Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void importFun() {
        Executor executor = ContextCompat.getMainExecutor(requireContext());
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(requireContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                Toast.makeText(requireContext(), "Authentication successful!", Toast.LENGTH_SHORT).show();

                // Open file picker only after successful authentication
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/json"); // Only allow JSON files
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(requireContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to import backup")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importBackupFile(uri);
            }
        }
    }

    private void importBackupFile(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            reader.close();

            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<UserData>>() {}.getType();
            ArrayList<UserData> importedData = gson.fromJson(jsonString.toString(), type);

            if (importedData != null) {
                userDataList.clear();  // Clear existing list
                userDataList.addAll(importedData); // Add new data

                saveImportedData(); // Save imported data to SharedPreferences
                adapter.notifyDataSetChanged(); // Notify adapter of data change

                t1.setVisibility(View.GONE);
                t2.setVisibility(View.GONE);

                Toast.makeText(getContext(), "Backup imported successfully!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Error: Invalid backup file.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Failed to import backup.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveImportedData() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(userDataList);
        editor.putString("courses", json);
        editor.apply();
    }


    private void showAddDataDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.custom_dialog, null);
        alert.setView(dialogView);

        final AlertDialog alertDialog = alert.create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();

        EditText type_of_data = dialogView.findViewById(R.id.data_type);
        EditText user_name_field = dialogView.findViewById(R.id.user_name_box);
        EditText user_credentials = dialogView.findViewById(R.id.user_credentials_field);
        Button submit = dialogView.findViewById(R.id.submit);
        Button generate = dialogView.findViewById(R.id.generatorButton);

        submit.setOnClickListener(v -> {
            if (type_of_data.getText().toString().isEmpty() ||
                    user_name_field.getText().toString().isEmpty() ||
                    user_credentials.getText().toString().isEmpty()) {

                Toast.makeText(requireContext(), "Enter your data", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    String data_type_enter = AESUtils.encrypt(type_of_data.getText().toString());
                    String user_name_enter = AESUtils.encrypt(user_name_field.getText().toString());
                    String credentials_enter = AESUtils.encrypt(user_credentials.getText().toString());

                    userDataList.add(new UserData(data_type_enter, user_name_enter, credentials_enter));
                    adapter.notifyItemInserted(userDataList.size());
                    alertDialog.dismiss();
                    saveData();

                    t1.setVisibility(View.GONE);
                    t2.setVisibility(View.GONE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        generate.setOnClickListener(v -> user_credentials.setText(generateSecurePassword()));
    }
    private void setupRecyclerView() {
        linearLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setHasFixedSize(true);
        adapter = new Adapter(userDataList, requireContext());
        recyclerView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    private void loadData() {
        // Load data from SharedPreferences
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        Gson gson = new Gson();
        String json = sharedPreferences.getString("courses", null);
        Type type = new TypeToken<ArrayList<UserData>>() {}.getType();
        userDataList = gson.fromJson(json, type);

        if (userDataList == null) {
            userDataList = new ArrayList<>();
        }
    }

    private void saveData() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(userDataList);
        String jsonData = gson.toJson(userDataList);
        editor.putString("courses", json);
        editor.apply();
        try {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Backup");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, "backup.json");

            FileWriter writer = new FileWriter(file);
            writer.write(jsonData);
            writer.flush();
            writer.close();

            Toast.makeText(requireContext(), "Backup saved!", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Failed to save backup!", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() != 0) {
            t1.setVisibility(View.GONE);
            t2.setVisibility(View.GONE);
        } else {
            t1.setVisibility(View.VISIBLE);
            t2.setVisibility(View.VISIBLE);
        }
    }
    private String generateSecurePassword() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$₹%^&*()-_=+~";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 16; i++) {
            password.append(characters.charAt(random.nextInt(characters.length())));
        }

        return password.toString();
    }
}