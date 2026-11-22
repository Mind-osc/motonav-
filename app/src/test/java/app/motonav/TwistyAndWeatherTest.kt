package app.motonav

import app.motonav.geo.Geo
import app.motonav.geo.LatLon
import app.motonav.routing.Route
import app.motonav.routing.Twistiness
import app.motonav.routing.TwistyPicker
import app.motonav.weather.OpenMeteoParser
import app.motonav.weather.RouteSampler
import app.motonav.weather.Sky
import app.motonav.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TwistyAndWeatherTest {

    private fun route(points: List<LatLon>, durationS: Double): Route {
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) cum[i] = cum[i - 1] + Geo.distance(points[i - 1], points[i])
        return Route(points, points.map { null }, cum, emptyList(), durationS)
    }

    /** Ευθεία ~5 km ανατολικά, σημείο ανά ~10 m. */
    private val straight = (0..500).map { LatLon(39.0, 22.0 + it * 0.000115) }

    /** Φιδωτός δρόμος: ημιτονοειδής, ~5 km, στροφή κάθε ~250 m. */
    private val snake = (0..500).map {
        val x = it * 0.000115
        LatLon(39.0 + 0.0012 * sin(it / 500.0 * 2 * Math.PI * 10), 22.0 + x)
    }

    @Test
    fun straightRoadHasNoTwist() {
        assertTrue(Twistiness.degPerKm(straight) < 5)
        assertEquals(1, Twistiness.stars(Twistiness.degPerKm(straight)))
    }

    @Test
    fun snakeIsTwisty() {
        val t = Twistiness.degPerKm(snake)
        assertTrue("$t", t > 120)
        assertTrue(Twistiness.stars(t) >= 3)
    }

    @Test
    fun pickerTakesTwistiestWithinTimeBudget() {
        val fast = route(straight, 300.0)
        val twisty = route(snake, 420.0) // +40%: μέσα στο όριο
        val tooSlow = route(snake.map { LatLon(it.lat + 0.001 * cos(it.lon * 5000), it.lon) }, 600.0) // +100%: έξω
        assertSame(twisty, TwistyPicker.pick(listOf(fast, twisty, tooSlow)))
        assertSame(fast, TwistyPicker.pick(listOf(fast)))
    }

    @Test
    fun samplesAlongRouteIncludeStartAndEnd() {
        val r = route((0..1000).map { LatLon(39.0, 22.0 + it * 0.0006) }, 3600.0) // ~52 km, 1 ώρα
        val s = RouteSampler.sample(r, startEpochS = 1_000_000L)
        assertEquals(0.0, s.first().second, 1e-6)
        assertEquals(r.lengthM, s.last().second, 1e-6)
        assertEquals(1_000_000L, s.first().third)
        assertEquals(1_000_000L + 3600L, s.last().third)
        assertTrue("${s.size}", s.size in 4..12)
        // μεγάλη διαδρομή: όχι πάνω από 12 σημεία
        val long = route((0..1000).map { LatLon(39.0, 20.0 + it * 0.005) }, 20_000.0)
        assertTrue(RouteSampler.sample(long, 0L).size <= 12)
    }

    @Test
    fun parsesOpenMeteoListAndPicksNearestHour() {
        val json = """
            [
              {"latitude":39.0,"longitude":22.0,"hourly":{"time":[3600,7200,10800],
                "temperature_2m":[10.0,12.5,15.0],"precipitation_probability":[0,10,80],
                "weather_code":[0,2,63],"wind_speed_10m":[5.0,8.0,30.0]}},
              {"latitude":39.1,"longitude":22.1,"hourly":{"time":[3600,7200,10800],
                "temperature_2m":[1.0,2.0,3.0],"precipitation_probability":[0,0,0],
                "weather_code":[71,71,71],"wind_speed_10m":[1.0,1.0,1.0]}}
            ]
        """.trimIndent()
        val samples = listOf(
            Triple(LatLon(39.0, 22.0), 0.0, 7000L),
            Triple(LatLon(39.1, 22.1), 15_000.0, 11_000L),
        )
        val w = OpenMeteoParser.parse(json, samples)
        assertEquals(2, w.size)
        assertEquals(12.5, w[0].tempC, 1e-9)
        assertEquals(Sky.PARTLY, w[0].sky)
        assertEquals(3.0, w[1].tempC, 1e-9)
        assertEquals(Sky.SNOW, w[1].sky)
        assertTrue(w[1].isBad)
    }

    @Test
    fun parsesOpenMeteoSingleObject() {
        val json = """{"hourly":{"time":[100],"temperature_2m":[20.0],"precipitation_probability":[60],"weather_code":[61],"wind_speed_10m":[10.0]}}"""
        val w = OpenMeteoParser.parse(json, listOf(Triple(LatLon(1.0, 2.0), 0.0, 100L)))
        assertEquals(1, w.size)
        assertEquals(Sky.RAIN, w[0].sky)
        assertTrue(w[0].isBad)
    }

    @Test
    fun weatherCodes() {
        assertEquals(Sky.CLEAR, WeatherCodes.sky(0))
        assertEquals(Sky.FOG, WeatherCodes.sky(45))
        assertEquals(Sky.RAIN, WeatherCodes.sky(81))
        assertEquals(Sky.STORM, WeatherCodes.sky(95))
        assertEquals("βροχή", WeatherCodes.label(Sky.RAIN))
    }
}
