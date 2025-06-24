package com.applinks.android.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
class PromoFragment : Fragment() {
    
    private var promoCode: String = ""
    private var discount: Int = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_promo, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get arguments
        promoCode = arguments?.getString("promoCode") ?: "DEFAULT"
        discount = arguments?.getInt("discount") ?: 10
        
        val promoCodeTextView = view.findViewById<TextView>(R.id.promoCodeTextView)
        val discountTextView = view.findViewById<TextView>(R.id.discountTextView)
        val messageTextView = view.findViewById<TextView>(R.id.messageTextView)
        
        promoCodeTextView.text = "Promo Code: $promoCode"
        discountTextView.text = "Discount: $discount% OFF"
        
        // Show special message based on discount
        val message = when {
            discount >= 50 -> "🎉 WOW! Incredible deal!"
            discount >= 20 -> "💰 Great savings!"
            else -> "✨ Nice discount!"
        }
        messageTextView.text = message
    }
}