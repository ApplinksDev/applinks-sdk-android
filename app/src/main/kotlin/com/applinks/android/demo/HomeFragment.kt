package com.applinks.android.demo

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applinks.android.AppLinksSDK
import com.applinks.android.PathType
import java.time.Instant
import java.time.temporal.ChronoUnit

class HomeFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    
    private lateinit var createdLinkTextView: TextView
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val statusTextView = view.findViewById<TextView>(R.id.statusTextView)
        val productButton = view.findViewById<Button>(R.id.productButton)
        val promoButton = view.findViewById<Button>(R.id.promoButton)
        val checkDeferredButton = view.findViewById<Button>(R.id.checkDeferredButton)
        val createProductLinkButton = view.findViewById<Button>(R.id.createProductLinkButton)
        val createPromoLinkButton = view.findViewById<Button>(R.id.createPromoLinkButton)
        createdLinkTextView = view.findViewById<TextView>(R.id.createdLinkTextView)
        
        // Navigate to product
        productButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString("productId", "123")
                putString("productName", "Demo Product")
            }
            findNavController().navigate(R.id.productFragment, bundle)
        }
        
        // Navigate to promo
        promoButton.setOnClickListener {
            val bundle = Bundle().apply {
                putString("promoCode", "WELCOME20")
                putInt("discount", 20)
            }
            findNavController().navigate(R.id.promoFragment, bundle)
        }
        
        // Check for deferred deep link
        checkDeferredButton.setOnClickListener {
            statusTextView.text = "Checking for deferred deep link..."
        }
        
        // Create product link
        createProductLinkButton.setOnClickListener {
            createProductLink()
        }
        
        // Create promo link
        createPromoLinkButton.setOnClickListener {
            createPromoLink()
        }
        
        // Show initial status
        statusTextView.text = "Welcome to AppLinks Demo!\n\nTry the buttons below or use these deep links:\n\n" +
                "• https://example.com/product/456\n" +
                "• applinks://promo/SPECIAL50\n"
    }
    
    private fun createProductLink() {
        AppLinksSDK.getInstance().linkShortener.createLinkAsync {
            webLink = Uri.parse("https://example.com/product/456")
            domain = "example.onapp.link"
            title = "Demo Product - Special Edition"
            deepLinkPath = "/product/456"
            deepLinkParams = mapOf(
                "productId" to "456",
                "source" to "app_share",
                "campaign" to "product_demo"
            )
            pathType = PathType.UNGUESSABLE
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        }.addOnSuccessListener { (shortLink) ->
            createdLinkTextView.visibility = View.VISIBLE
            createdLinkTextView.text = "✅ Product link created:\n$shortLink"
            Toast.makeText(context, "Product link created!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { exception ->
            createdLinkTextView.visibility = View.VISIBLE
            createdLinkTextView.text = "❌ Error creating product link:\n${exception.message}"
            Toast.makeText(context, "Failed: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun createPromoLink() {
        AppLinksSDK.getInstance().linkShortener.createLinkAsync {
            webLink = Uri.parse("https://example.com/promo/SPECIAL50")
            domain = "example.onapp.link"
            title = "Special Promotion - 50% Off"
            deepLinkPath = "/promo/SPECIAL50"
            deepLinkParams = mapOf(
                "promoCode" to "SPECIAL50",
                "discount" to "50",
                "source" to "app_share",
                "campaign" to "summer_promo"
            )
            pathType = PathType.SHORT
            expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)
        }.addOnSuccessListener { result ->
            val (shortLink) = result
            createdLinkTextView.visibility = View.VISIBLE
            createdLinkTextView.text = "✅ Promo link created:\n$shortLink"
            Toast.makeText(context, "Promo link created!", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { exception ->
            createdLinkTextView.visibility = View.VISIBLE
            createdLinkTextView.text = "❌ Error creating promo link:\n${exception.message}"
            Toast.makeText(context, "Failed: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }
}