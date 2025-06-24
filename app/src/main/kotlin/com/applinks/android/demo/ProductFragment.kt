package com.applinks.android.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
class ProductFragment : Fragment() {
    
    private var productId: String = ""
    private var productName: String = ""
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_product, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        productId = arguments?.getString("productId") ?: ""
        productName = arguments?.getString("productName") ?: "Unknown Product"
        
        val productIdTextView = view.findViewById<TextView>(R.id.productIdTextView)
        val productNameTextView = view.findViewById<TextView>(R.id.productNameTextView)
        val sourceTextView = view.findViewById<TextView>(R.id.sourceTextView)
        
        // Display product information from navigation arguments
        productIdTextView.text = "Product ID: $productId"
        productNameTextView.text = "Product Name: $productName"
        
        // Check if we came from a deep link by checking if productId was passed
        if (productId.isNotEmpty()) {
            sourceTextView.text = "Arrived via Deep Link! ✨"
            sourceTextView.visibility = View.VISIBLE
        } else {
            sourceTextView.text = "Arrived via Navigation"
            sourceTextView.visibility = View.VISIBLE
        }
    }
}