package com.applinks.android.demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.applinks.android.AppLinksSDK
import com.applinks.android.handlers.NavigationLinkHandler

class MainActivity : AppCompatActivity() {
    
    private lateinit var navController: NavController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Setup Navigation Component
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Setup Action Bar with Navigation
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        
        // Add Navigation handler to AppLinks SDK
        setupAppLinksNavigation()
        
        // Handle intent if activity was launched with a deep link
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    
    private fun setupAppLinksNavigation() {
        // Create Navigation handler
        val navigationHandler = NavigationLinkHandler(
            navControllerProvider = { navController },
            navGraphId = R.navigation.main_navigation,
            enableLogging = true
        )
        
        // Add it to AppLinks SDK
        AppLinksSDK.getInstance().addCustomHandler(navigationHandler)
        
        Log.d("MainActivity", "Navigation handler added to AppLinks SDK")
    }
    
    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            Log.d("MainActivity", "Handling intent with URI: $uri")
            
            AppLinksSDK.getInstance().handleLink(uri, object : AppLinksSDK.LinkCallback {
                override fun onSuccess(link: String, metadata: Map<String, String>) {
                    Log.d("MainActivity", "Successfully handled link: $link")
                    metadata.forEach { (key, value) ->
                        Log.d("MainActivity", "  $key: $value")
                    }
                }
                
                override fun onError(error: String) {
                    Log.e("MainActivity", "Failed to handle link: $error")
                    // Navigation Component couldn't handle it, try manual handling
                    handleLinkManually(uri)
                }
            })
        }
    }
    
    private fun handleLinkManually(uri: Uri) {
        // Fallback for links not defined in navigation graph
        Log.d("MainActivity", "Handling link manually: $uri")
        
        // Navigate to home as fallback
        navController.navigate(R.id.homeFragment)
    }
    
}