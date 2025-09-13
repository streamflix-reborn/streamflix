package com.streamflixreborn.streamflix.adapters.viewholders

import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.databinding.ItemProviderMobileBinding
import com.streamflixreborn.streamflix.databinding.ItemProviderTvBinding
import com.streamflixreborn.streamflix.models.Provider
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.streamflixreborn.streamflix.utils.toActivity
import java.util.Locale

class ProviderViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private lateinit var provider: Provider

    fun bind(provider: Provider) {
        this.provider = provider

        when (_binding) {
            is ItemProviderMobileBinding -> displayMobileItem(_binding)
            is ItemProviderTvBinding -> displayTvItem(_binding)
        }
    }


    private fun displayMobileItem(binding: ItemProviderMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                UserPreferences.currentProvider = provider.provider
                context.toActivity()?.apply {
                    finish()
                    startActivity(Intent(this, this::class.java))
                }
            }
        }

        Glide.with(context)
            .load(provider.logo.takeIf { it.isNotEmpty() }
                ?: R.drawable.ic_provider_default_logo)
            .fitCenter()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivProviderLogo)

        binding.tvProviderName.text = provider.name

        binding.tvProviderLanguage.text = Locale(provider.language)
            .let { it.getDisplayLanguage(it) }
            .replaceFirstChar { it.titlecase() }
    }

    private fun displayTvItem(binding: ItemProviderTvBinding) {
        binding.root.apply {
            setOnClickListener {
                UserPreferences.currentProvider = provider.provider
                context.toActivity()?.apply {
                    finish()
                    startActivity(Intent(this, this::class.java))
                }
            }
        }

        Glide.with(context)
            .load(provider.logo.takeIf { it.isNotEmpty() }
                ?: R.drawable.ic_provider_default_logo)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivProviderLogo)

        binding.tvProviderName.text = provider.name

        binding.tvProviderLanguage.text = Locale(provider.language)
            .let { it.getDisplayLanguage(it) }
            .replaceFirstChar { it.titlecase() }
    }
}