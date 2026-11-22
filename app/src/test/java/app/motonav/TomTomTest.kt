package app.motonav

import app.motonav.geo.LatLon
import app.motonav.routing.Turn
import app.motonav.traffic.TomTomClient
import app.motonav.traffic.TomTomParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TomTomTest {

    // Μορφή απάντησης calculateRoute (συντομευμένη) με instructionsType=coded.
    private val routeJson = """
        {"formatVersion":"0.0.12","routes":[{
          "summary":{"lengthInMeters":1150,"travelTimeInSeconds":240,"trafficDelayInSeconds":60,
                     "trafficLengthInMeters":300,"noTrafficTravelTimeInSeconds":180},
          "legs":[{"summary":{"lengthInMeters":1150},"points":[
            {"latitude":37.98000,"longitude":23.72000},
            {"latitude":37.98000,"longitude":23.72500},
            {"latitude":37.98000,"longitude":23.73000},
            {"latitude":37.97500,"longitude":23.73000}
          ]}],
          "guidance":{"instructions":[
            {"routeOffsetInMeters":0,"pointIndex":0,"maneuver":"DEPART"},
            {"routeOffsetInMeters":878,"pointIndex":2,"maneuver":"TURN_RIGHT","street":"Αθηνάς"},
            {"routeOffsetInMeters":1000,"pointIndex":3,"maneuver":"ROUNDABOUT_RIGHT","roundaboutExitNumber":2},
            {"routeOffsetInMeters":1150,"pointIndex":3,"maneuver":"ARRIVE"}
          ]}
        }]}
    """.trimIndent()

    @Test
    fun parsesRouteWithTrafficAndGuidance() {
        val r = TomTomParser.route(routeJson)
        assertEquals(4, r.points.size)
        assertEquals(240.0, r.durationS, 1e-9)
        assertEquals(60.0, r.trafficDelayS, 1e-9)
        assertEquals("TomTom", r.source)
        // TURN_RIGHT, ROUNDABOUT (έξοδος 2), δικό μας ARRIVE· το DEPART και το ARRIVE του TomTom δεν μετράνε
        assertEquals(3, r.maneuvers.size)
        assertEquals(Turn.RIGHT, r.maneuvers[0].turn)
        assertEquals(878.0, r.maneuvers[0].atMeters, 1e-9)
        assertEquals(Turn.ROUNDABOUT, r.maneuvers[1].turn)
        assertEquals(2, r.maneuvers[1].exit)
        assertEquals(Turn.ARRIVE, r.maneuvers[2].turn)
    }

    @Test
    fun parsesEta() {
        val eta = TomTomParser.eta("""{"routes":[{"summary":{"lengthInMeters":5000,"travelTimeInSeconds":900,"trafficDelayInSeconds":120}}]}""")
        assertEquals(900.0, eta.durationS, 1e-9)
        assertEquals(120.0, eta.delayS, 1e-9)
    }

    @Test
    fun maneuverMapping() {
        assertEquals(Turn.SLIGHT_LEFT, TomTomParser.turnFor("BEAR_LEFT"))
        assertEquals(Turn.UTURN, TomTomParser.turnFor("TRY_MAKE_UTURN"))
        assertEquals(Turn.EXIT_RIGHT, TomTomParser.turnFor("TAKE_EXIT"))
        assertEquals(Turn.KEEP_LEFT, TomTomParser.turnFor("SOMETHING_NEW_LEFT"))
        assertEquals(null, TomTomParser.turnFor("DEPART"))
    }

    @Test
    fun downsamplesKeepsEndsAndCaps() {
        val pts = (0..5000).map { LatLon(38.0, 23.0 + it * 0.0001) } // ~44 km, σημείο ανά ~9 m
        val d = TomTomParser.downsample(pts)
        assertEquals(pts.first(), d.first())
        assertEquals(pts.last(), d.last())
        assertTrue("${d.size}", d.size in 250..400)
    }

    @Test
    fun vectorTileUrl() {
        val u = TomTomClient.flowVectorTileUrl(" k1 ")
        assertTrue(u.contains("/flow/relative/{z}/{x}/{y}.pbf"))
        assertTrue(u.endsWith("key=k1"))
    }

    @Test
    fun flowTileUrlKeepsPlaceholders() {
        val u = TomTomClient.flowTileUrl("abc 123", dark = true)
        assertTrue(u.contains("/relative-delay/{z}/{x}/{y}.png"))
        assertTrue(u.endsWith("key=abc+123"))
    }

    @Test
    fun numberedRoadsGetBlueSign() {
        // 3 σημεία σε ευθεία ~1,1 km το καθένα. Στροφή στα ~1,1 km σε δρόμο με αριθμό («8Α»).
        val json = """{"routes":[{"summary":{"travelTimeInSeconds":200},
          "legs":[{"points":[{"latitude":38.0,"longitude":23.0},{"latitude":38.01,"longitude":23.0},{"latitude":38.02,"longitude":23.0}]}],
          "guidance":{"instructions":[
            {"maneuver":"DEPART","routeOffsetInMeters":0,"pointIndex":0,"street":"Λιοσίων"},
            {"maneuver":"TURN_RIGHT","routeOffsetInMeters":1112,"pointIndex":1,"roadNumbers":["8Α"]},
            {"maneuver":"ARRIVE","routeOffsetInMeters":2224,"pointIndex":2}
          ]},
          "sections":[]}]}"""
        val r = TomTomParser.routes(json).first()
        assertEquals(app.motonav.routing.RoadKind.OTHER, r.roadKindAt(500.0))
        assertEquals(app.motonav.routing.RoadKind.MAIN, r.roadKindAt(1500.0))
    }
}
