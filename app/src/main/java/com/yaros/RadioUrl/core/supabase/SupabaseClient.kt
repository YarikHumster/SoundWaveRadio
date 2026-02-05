package com.yaros.RadioUrl.core.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    private const val SUPABASE_URL = "https://emxgttrptqqaywszojxu.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_J9s-q-GiOq2g3v5EHmdYrA_IzVzFLVe"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
