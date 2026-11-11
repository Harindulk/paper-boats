package com.starfungames.paperboats.ui.fx

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A capped pool of short-lived particles in race units (x across 0..1, y up the river).
 * When full, the oldest particles make room, so a burst-heavy frame never grows the pool.
 */
class Particles(val cap: Int = 600) {
    enum class Kind { Wake, Spark, Splash, Lantern, Confetti }

    /** One particle; [colour] is a player colour index or a small colour code. */
    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val colour: Int,
        val size: Float,
        val kind: Kind,
    )

    private val particles = ArrayDeque<Particle>()

    /** Live particles, oldest first. */
    val live: List<Particle> get() = particles

    /** Spawns [count] particles of [kind] at (x, y), dropping the oldest past [cap]. */
    fun emit(kind: Kind, x: Float, y: Float, colour: Int, count: Int, random: Random) {
        repeat(count) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val speed = random.nextFloat()
            val dx = cos(angle)
            val dy = sin(angle)
            val burst = 0.15f + speed * 0.25f
            val p = when (kind) {
                Kind.Wake -> Particle(x, y, dx * 0.02f, -0.05f, 0f, 0.8f + speed * 0.4f, colour, 0.006f, kind)
                Kind.Spark -> Particle(x, y, dx * burst, dy * burst, 0f, 0.3f + speed * 0.2f, colour, 0.005f, kind)
                Kind.Splash -> Particle(x, y, dx * (0.1f + speed * 0.2f), dy * 0.1f + 0.2f, 0f, 0.4f + speed * 0.3f, colour, 0.008f, kind)
                Kind.Lantern -> Particle(x + dx * 0.05f, y, dx * 0.03f, 0.15f + speed * 0.15f, 0f, 2.5f + speed, colour, 0.014f, kind)
                Kind.Confetti -> Particle(x, y, dx * (0.1f + speed * 0.3f), -speed * 0.1f, 0f, 1.5f + speed, colour, 0.007f, kind)
            }
            particles.addLast(p)
        }
        while (particles.size > cap) particles.removeFirst()
    }

    /** Moves every particle by [dt] seconds and removes the dead. */
    fun step(dt: Float) {
        particles.forEach { p ->
            p.life += dt
            when (p.kind) {
                Kind.Spark -> {
                    p.vx *= 1f - 4f * dt
                    p.vy *= 1f - 4f * dt
                }

                Kind.Splash -> p.vy -= SPLASH_GRAVITY * dt

                Kind.Lantern -> p.vy += LANTERN_LIFT * dt

                Kind.Confetti -> {
                    p.vy -= CONFETTI_GRAVITY * dt
                    p.vy *= 1f - 2f * dt
                    p.vx = sin(p.life * 9f + p.x * 40f) * 0.08f
                }

                Kind.Wake -> Unit
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        particles.removeAll { it.life >= it.maxLife }
    }

    private companion object {
        const val SPLASH_GRAVITY = 1.2f
        const val LANTERN_LIFT = 0.05f
        const val CONFETTI_GRAVITY = 0.4f
    }
}
