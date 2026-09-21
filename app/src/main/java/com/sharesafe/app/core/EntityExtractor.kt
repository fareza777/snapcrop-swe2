package com.sharesafe.app.core

import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Entity Extraction pass — runs on joined OCR text and finds things
 * regex can't see well: postal addresses, date/time, tracking & flight
 * numbers, money amounts, IBANs. Bundled model, fully on-device.
 */
object EntityExtractor {

    private fun newClient() = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )

    /** Returns hits in joined-text coordinates; caller maps ranges to OCR spans. */
    suspend fun detect(joinedText: String): List<SensitiveHit> {
        if (joinedText.isBlank() || joinedText.length > 150_000) return emptyList()
        val client = newClient()
        return suspendCancellableCoroutine { cont ->
            client.annotate(EntityExtractionParams.Builder(joinedText).build())
                .addOnSuccessListener { annotations ->
                    if (cont.isActive) cont.resume(annotations.toHits())
                }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                .addOnCompleteListener { client.close() }
        }
    }

    private fun List<EntityAnnotation>.toHits(): List<SensitiveHit> {
        val hits = ArrayList<SensitiveHit>()
        forEach { ann ->
            val range = ann.start until ann.end
            val text = ann.annotatedText
            ann.entities.forEach { entity ->
                val spec = when (entity.type) {
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_ADDRESS ->
                        RegionKind.ADDRESS to "Address"
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_DATE_TIME ->
                        RegionKind.DATETIME to "Date/time"
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_TRACKING_NUMBER ->
                        RegionKind.TRACKING to "Tracking no."
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_FLIGHT_NUMBER ->
                        RegionKind.NUMBER to "Flight no."
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_MONEY ->
                        RegionKind.NUMBER to "Amount"
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_IBAN ->
                        RegionKind.CARD to "IBAN"
                    com.google.mlkit.nl.entityextraction.Entity.TYPE_PAYMENT_CARD ->
                        RegionKind.CARD to "Payment card"
                    else -> null
                }
                if (spec != null && text.isNotBlank()) {
                    // Entity extractor is assistive — medium confidence unless
                    // context boost or a regex pass already covered it.
                    hits += SensitiveHit(spec.first, range, spec.second, 0.85f, matched = text)
                }
            }
        }
        return hits
    }
}
