package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

public class WanderingFlightTests {

    private static final WanderingFlight.Settings SETTINGS = new WanderingFlight.Settings(
            0.012F, 0.03F, 0.025F, 0.9F, 0.12F, 0.018F, 0.004F, 0.2F, 6);
    private static final WanderingFlight.Obstacles OPEN_AIR = (x, y, z) -> false;

    private static double horizontalSpeed(WanderingFlight flight) {
        return Math.hypot(flight.xd(), flight.zd());
    }

    @Test
    void startsUnderWayAtCruisingSpeed() {
        for (long seed = 0; seed < 100; seed++) {
            var flight = new WanderingFlight(SETTINGS, Randomizer.create(seed));
            var speed = horizontalSpeed(flight);
            assertTrue(speed >= SETTINGS.minSpeed() - 1e-6 && speed <= SETTINGS.maxSpeed() + 1e-6, "speed " + speed);
            assertEquals(0D, flight.yd());
        }
    }

    @Test
    void neverFasterThanItsTopSpeed() {
        for (long seed = 0; seed < 50; seed++) {
            var flight = new WanderingFlight(SETTINGS, Randomizer.create(seed));
            for (int age = 0; age < 200; age++) {
                flight.tick(0, 0, 0, age, 0F, OPEN_AIR);
                assertTrue(horizontalSpeed(flight) <= SETTINGS.maxSpeed() + 1e-6);
            }
        }
    }

    @Test
    void turnsSmoothly() {
        // The velocity only eases toward its target, so it never changes by much in a tick
        var limit = SETTINGS.steer() * 2 * (SETTINGS.maxSpeed() + SETTINGS.lift() + SETTINGS.bob());
        for (long seed = 0; seed < 50; seed++) {
            var flight = new WanderingFlight(SETTINGS, Randomizer.create(seed));
            for (int age = 0; age < 200; age++) {
                double xd = flight.xd(), yd = flight.yd(), zd = flight.zd();
                flight.tick(0, 0, 0, age, 1F, OPEN_AIR);
                var change = Math.sqrt(Math.pow(flight.xd() - xd, 2) + Math.pow(flight.yd() - yd, 2) + Math.pow(flight.zd() - zd, 2));
                assertTrue(change <= limit, "changed by " + change);
            }
        }
    }

    @Test
    void wandersRatherThanFlyingStraight() {
        // Over a long flight it ends up well short of how far it flew, since its path bends
        int bent = 0;
        for (long seed = 0; seed < 50; seed++) {
            var flight = new WanderingFlight(SETTINGS, Randomizer.create(seed));
            double x = 0, z = 0, travelled = 0;
            for (int age = 0; age < 200; age++) {
                flight.tick(x, 0, z, age, 0F, OPEN_AIR);
                x += flight.xd();
                z += flight.zd();
                travelled += horizontalSpeed(flight);
            }
            if (Math.hypot(x, z) < travelled * 0.9)
                bent++;
        }
        assertTrue(bent >= 40, "only " + bent + " of 50 flights bent");
    }

    @Test
    void climbsAndSinksAsAsked() {
        for (var climb : new float[]{1F, -1F}) {
            var flight = new WanderingFlight(SETTINGS, Randomizer.create(7));
            double y = 0;
            for (int age = 0; age < 100; age++) {
                flight.tick(0, y, 0, age, climb, OPEN_AIR);
                y += flight.yd();
            }
            assertEquals(Math.signum(climb), Math.signum(y), "climb " + climb + " ended at " + y);
        }
    }

    @Test
    void turnsAsideAndLiftsWhenBlocked() {
        for (long seed = 0; seed < 50; seed++) {
            var flight = new WanderingFlight(SETTINGS, Randomizer.create(seed));
            double xd = flight.xd(), zd = flight.zd();
            flight.tick(0, 0, 0, 0, -1F, (x, y, z) -> true);
            // Steering away: the change in velocity points back against the way it was going, and up
            var dx = flight.xd() - xd;
            var dz = flight.zd() - zd;
            assertTrue(dx * xd + dz * zd < 0, "kept heading into the obstacle");
            assertTrue(flight.yd() > 0, "didn't lift");
        }
    }

    @Test
    void looksAheadAlongItsPath() {
        // Only the point it is heading for is blocked: a wall ahead, nothing to the sides or behind
        var flight = new WanderingFlight(SETTINGS, Randomizer.create(3));
        var asked = new double[3];
        flight.tick(10, 20, 30, 0, 0F, (x, y, z) -> {
            asked[0] = x;
            asked[1] = y;
            asked[2] = z;
            return false;
        });
        var reach = Math.hypot(asked[0] - 10, asked[2] - 30);
        assertTrue(reach >= SETTINGS.minSpeed() * 0.75 * SETTINGS.lookAhead() - 1e-6);
        assertTrue(reach <= SETTINGS.maxSpeed() * SETTINGS.lookAhead() + 1e-6);
    }

    @Test
    void sameSeedSameFlight() {
        var a = new WanderingFlight(SETTINGS, Randomizer.create(42));
        var b = new WanderingFlight(SETTINGS, Randomizer.create(42));
        for (int age = 0; age < 100; age++) {
            a.tick(0, 0, 0, age, 0.5F, OPEN_AIR);
            b.tick(0, 0, 0, age, 0.5F, OPEN_AIR);
            assertEquals(a.xd(), b.xd());
            assertEquals(a.yd(), b.yd());
            assertEquals(a.zd(), b.zd());
        }
    }
}
