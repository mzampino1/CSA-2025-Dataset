java
package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.Scanner;

public class MainActivity extends AppCompatActivity {
    private TextView textView;
    private Button button;

// 🔥 NEW VULNERABILITY ADDED HERE 🔥

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        button = findViewById(R.id.button);

// 🔥 NEW VULNERABILITY ADDED HERE 🔥

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textView.setText("Hello, World!");
            }
        });
    }
}