package app.motonav.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

object Geo {
    private const val R = 6_371_008.8

    /** Απόσταση σε μέτρα (haversine). */
    fun distance(a: LatLon, b: LatLon): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val s = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(min(1.0, s)))
    }

    /** Κατεύθυνση από a προς b, μοίρες 0..360 (0 = βορράς). */
    fun bearing(a: LatLon, b: LatLon): Double {
        val la = Math.toRadians(a.lat)
        val lb = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(lb)
        val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Προβολή του p πάνω στο τμήμα ab.
     * Επιστρέφει (t, απόσταση σε μέτρα), με t στο [0,1] τη θέση πάνω στο τμήμα.
     * Τοπική επίπεδη προσέγγιση: αρκετά ακριβής για τμήματα λίγων εκατοντάδων μέτρων.
     */
    fun project(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
        val k = cos(Math.toRadians(p.lat))
        val ax = (a.lon - p.lon) * k
        val ay = a.lat - p.lat
        val bx = (b.lon - p.lon) * k
        val by = b.lat - p.lat
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((-ax) * dx + (-ay) * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        val meters = sqrt(cx * cx + cy * cy) * (PI / 180.0) * R
        return t to meters
    }
}
