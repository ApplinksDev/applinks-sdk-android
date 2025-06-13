package com.applinks.android.demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.applinks.android.Library

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Creating an instance of your library
        val library = Library()
        
        // Calling a method from your library
        val result = library.someLibraryMethod()
        
        // Showing the result
        Toast.makeText(this, "Library method returned: $result", Toast.LENGTH_SHORT).show()
    }
}