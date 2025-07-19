java
package com.example.myapplication;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ContactsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        RecyclerView recyclerView = binding.recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactsAdapter();
        recyclerView.setAdapter(adapter);

        // Load the contacts using a CursorLoader
        LoaderManager loaderManager = getSupportLoaderManager();
        loaderManager.initLoader(0, null, new LoaderManager.LoaderCallbacks<Cursor>() {
            @Override
            public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                String[] projection = {
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.PHOTO_URI
                };
                return new CursorLoader(MainActivity.this, ContactsContract.Contacts.CONTENT_URI, projection, null, null, null);
            }

            @Override
            public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
                List<Contact> contacts = new ArrayList<>();
                if (cursor != null && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        long id = cursor.getLong(0);
                        String name = cursor.getString(1);
                        Uri photoUri = Uri.parse(cursor.getString(2));
                        Contact contact = new Contact(id, name, photoUri);
                        contacts.add(contact);
                        cursor.moveToNext();
                    }
                }
                adapter.setContacts(contacts);
            }

            @Override
            public void onLoaderReset(Loader<Cursor> loader) {
                adapter.setContacts(null);
            }
        });
    }
}