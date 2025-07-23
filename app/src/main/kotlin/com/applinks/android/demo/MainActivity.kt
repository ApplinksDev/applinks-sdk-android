package com.applinks.android.demo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.applinks.android.AppLinksSDK
import com.applinks.android.middleware.LinkHandlingResult

class MainActivity : AppCompatActivity(), AppLinksSDK.AppLinksListener {
    
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
        
        // Setup AppLinks listener
        AppLinksSDK.getInstance().addLinkListener(this)
        
        // Handle intent if activity was launched with a deep link
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    
    
    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            Log.d("MainActivity", "Handling intent with URI: $uri")
            AppLinksSDK.getInstance().handleLink(uri)
        }
    }
    
    override fun onLinkReceived(result: LinkHandlingResult) {
        Log.d("MainActivity", "Link received: ${result.originalUrl}")
        Log.d("MainActivity", "  Handled: ${result.handled}")
        Log.d("MainActivity", "  Path: ${result.path}")
        Log.d("MainActivity", "  SchemeUrl: ${result.schemeUrl}")
        
        result.params.forEach { (key, value) ->
            Log.d("MainActivity", "  Param $key: $value")
        }
        
        result.metadata.forEach { (key, value) ->
            Log.d("MainActivity", "  Metadata $key: $value")
        }
        
        // Handle the deep link - navigate based on the result
        handleLinkNavigation(result)
    }
    
    override fun onError(error: String) {
        Log.e("MainActivity", "AppLinks error: $error")
        // Navigate to home as fallback
        navController.navigate(R.id.homeFragment)
    }
    
    private fun handleLinkNavigation(result: LinkHandlingResult) {
        try {
            val internalUri = result.schemeUrl
            Log.d("MainActivity", "Navigating with URI: $internalUri")

            try {
                // Let Android Navigation Component handle the navigation automatically
                if (internalUri != null) {
                    navController.navigate(internalUri)
                }
                Log.d("MainActivity", "Successfully navigated to: $internalUri")
            } catch (e: IllegalArgumentException) {
                Log.e("MainActivity", "Invalid deep link URI: $internalUri", e)
                navController.navigate(R.id.homeFragment)
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation error for URI: $internalUri", e)
                navController.navigate(R.id.homeFragment)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling deep link navigation", e)
            // Fallback to home on any navigation error
            navController.navigate(R.id.homeFragment)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLinksSDK.getInstance().removeLinkListener(this)
    }
    
}