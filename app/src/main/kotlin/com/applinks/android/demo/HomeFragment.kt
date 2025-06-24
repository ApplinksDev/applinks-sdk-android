package com.applinks.android.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applinks.android.AppLinksSDK

class HomeFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val statusTextView = view.findViewById<TextView>(R.id.statusTextView)
        val productButton = view.findViewById<Button>(R.id.productButton)
        val promoButton = view.findViewById<Button>(R.id.promoButton)
        val checkDeferredButton = view.findViewById<Button>(R.id.checkDeferredButton)
        
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
        
        // Show initial status
        statusTextView.text = "Welcome to AppLinks Demo!\n\nTry the buttons below or use these deep links:\n\n" +
                "• https://example.com/product/456\n" +
                "• applinks://promo/SPECIAL50\n"
    }
}