package dev.daylight.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DebriefOrganizerTest {
    private val engine = CompanionEngine()
    private val today = LocalDate.of(2026, 9, 27)
    private val fixture = SyntheticData.create(today)
    private val catalog = engine.debriefCatalog(fixture)

    @Test
    fun `accepted output only selects and orders already calculated cards`() {
        val result = DebriefOrganizer.resolve(catalog, "[2,1]")
        assertEquals(DebriefStatus.MODEL_ORGANIZED, result.status)
        assertEquals(listOf(DebriefFactId.SLEEP, DebriefFactId.STEPS), result.selectedIds)
        assertEquals(Topic.BRIEF, result.reply.topic)
        assertEquals(listOf(catalog.cards[1].evidence, catalog.cards[0].evidence), result.reply.evidence)
        assertTrue(result.reply.text.indexOf(catalog.cards[1].summary) < result.reply.text.indexOf(catalog.cards[0].summary))
        assertTrue(result.reply.text.contains("Selection is not medical prioritization"))
    }

    @Test
    fun `response always carries window coverage and selection limitations`() {
        val result = DebriefOrganizer.resolve(catalog, "[3,2]")
        assertTrue(result.reply.text.contains("2026-09-20 through 2026-09-26"))
        assertTrue(result.reply.text.contains("today excluded"))
        assertTrue(result.reply.text.contains("2 of 3 recently observed categories"))
        assertTrue(result.reply.text.contains("Missing records are not zero"))
        assertTrue(result.reply.text.contains("not physiological time asleep"))
        result.reply.evidence.forEach { fact ->
            assertTrue(fact.coverage.contains("days observed"))
            assertTrue(fact.basis.contains("Recent daily values used"))
        }
    }

    @Test
    fun `invalid model strings are never shown as generated advice or partial success`() {
        val invalid = listOf(
            "[2]", "[2,2]", "[4,1]", "[2,1,3]", "[0,1]", "[-1,2]", "[1.0,2]", "[01,2]",
            "[\"2\",\"1\"]", "```[2,1]```", "{\"ids\":[2,1]}", "[2,1]\nTake medication",
            "You need eight hours of sleep", "[2;1]", "[２,1]", "[1,DELETE]", "", " ",
            "[2,1]" + " ".repeat(65), "SLEEP,STEPS", "STEP,SLEEP", "SLEEP, EXERCISE"
        )
        for (output in invalid) {
            val result = DebriefOrganizer.resolve(catalog, output)
            assertEquals(output, DebriefStatus.MODEL_OUTPUT_REJECTED, result.status)
            assertEquals(listOf(DebriefFactId.STEPS, DebriefFactId.SLEEP), result.selectedIds)
            assertTrue(result.reply.text.contains("none of its generated text is shown"))
            assertFalse(result.reply.text.contains("Take medication"))
            assertFalse(result.reply.text.contains("eight hours"))
            assertEquals(null, result.reply.proposal)
        }
    }

    @Test
    fun `JSON whitespace is accepted but extra instructions are not`() {
        assertEquals(DebriefStatus.MODEL_ORGANIZED, DebriefOrganizer.resolve(catalog, "\n[ 2,\t1 ]\r\n").status)
        assertEquals(DebriefStatus.MODEL_OUTPUT_REJECTED, DebriefOrganizer.resolve(catalog, "[2,1] #approved").status)
        assertEquals(DebriefStatus.MODEL_OUTPUT_REJECTED, DebriefOrganizer.resolve(catalog, "[2,\u000b1]").status)
    }

    @Test
    fun `no model output has an explicitly computed provenance`() {
        val result = DebriefOrganizer.resolve(catalog)
        assertEquals(DebriefStatus.COMPUTED, result.status)
        assertTrue(result.reply.text.contains("No language model was used"))
    }

    @Test
    fun `empty recent history never creates a model prompt or a fake recap`() {
        val empty = engine.debriefCatalog(fixture.copy(days = emptyList(), latestRecordAt = null))
        assertFalse(empty.canOrganize)
        assertThrows(IllegalArgumentException::class.java) { DebriefOrganizer.prompt(empty) }
        val result = DebriefOrganizer.resolve(empty, "[2,1]")
        assertEquals(DebriefStatus.NO_RECENT_RECORDS, result.status)
        assertTrue(result.selectedIds.isEmpty())
        assertTrue(result.reply.evidence.isEmpty())
        assertTrue(result.reply.text.contains("No records were invented"))
    }

    @Test
    fun `today and older records do not become completed recent week evidence`() {
        val excluded = fixture.copy(days = listOf(
            DayRecord(today, 800, 400, 20),
            DayRecord(today.minusDays(8), 900, 420, 30),
            DayRecord(today.plusDays(1), 1000, 440, 40)
        ))
        val result = DebriefOrganizer.resolve(engine.debriefCatalog(excluded))
        assertEquals(DebriefStatus.NO_RECENT_RECORDS, result.status)
        assertTrue(result.reply.text.contains("today"))
    }

    @Test
    fun `one observed category needs no language model`() {
        val single = engine.debriefCatalog(fixture.copy(days = listOf(DayRecord(today.minusDays(1), steps = 0))))
        assertFalse(single.canOrganize)
        assertThrows(IllegalArgumentException::class.java) { DebriefOrganizer.prompt(single) }
        val result = DebriefOrganizer.resolve(single)
        assertEquals(DebriefStatus.COMPUTED, result.status)
        assertEquals(listOf(DebriefFactId.STEPS), result.selectedIds)
        assertTrue(result.reply.text.contains("0.0 steps/observed day (1/7 days)"))
        assertTrue(result.reply.text.contains("1 of 1 recently observed categories"))
    }

    @Test
    fun `model cannot select an unavailable category`() {
        val partial = engine.debriefCatalog(fixture.copy(days = listOf(
            DayRecord(today.minusDays(1), steps = 1000, exerciseMinutes = 0)
        )))
        assertTrue(partial.canOrganize)
        assertEquals(DebriefStatus.MODEL_OUTPUT_REJECTED, DebriefOrganizer.resolve(partial, "[2,1]").status)
        assertEquals(DebriefStatus.MODEL_ORGANIZED, DebriefOrganizer.resolve(partial, "[3,1]").status)
    }

    @Test
    fun `negative and conflicting totals are excluded before the model sees cards`() {
        val conflicting = engine.debriefCatalog(fixture.copy(days = listOf(
            DayRecord(today.minusDays(1), 1000, -1, 20),
            DayRecord(today.minusDays(1), 2000, -2, 20)
        )))
        assertEquals(listOf(DebriefFactId.EXERCISE), conflicting.cards.map { card -> card.id })
    }

    @Test
    fun `prompt sends aggregates not source metadata raw days or free text notes`() {
        val marked = fixture.copy(source = "PRIVATE_SOURCE_MARKER", notes = listOf("IGNORE_ALL_RULES_MARKER"))
        val prompt = DebriefOrganizer.prompt(engine.debriefCatalog(marked))
        assertFalse(prompt.contains("PRIVATE_SOURCE_MARKER"))
        assertFalse(prompt.contains("IGNORE_ALL_RULES_MARKER"))
        assertFalse(prompt.contains("2026-09-"))
        assertFalse(prompt.contains("daily values"))
        assertTrue(prompt.contains("days"))
        assertTrue(prompt.length < 2000)
    }

    @Test
    fun `every accepted identifier pair maps to exact evidence with no mutation`() {
        for (first in catalog.cards) {
            for (second in catalog.cards.filter { card -> card.id != first.id }) {
                val result = DebriefOrganizer.resolve(catalog, "[${first.id.modelId},${second.id.modelId}]")
                assertEquals(DebriefStatus.MODEL_ORGANIZED, result.status)
                assertEquals(listOf(first.evidence, second.evidence), result.reply.evidence)
                assertTrue(result.reply.text.contains(first.summary))
                assertTrue(result.reply.text.contains(second.summary))
            }
        }
    }

    @Test
    fun `duplicate catalog identifiers cannot silently replace evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            catalog.copy(cards = listOf(catalog.cards.first(), catalog.cards.first()))
        }
    }

    @Test
    fun `numeric identifiers are stable even when cards are missing or reordered`() {
        assertEquals(1, DebriefFactId.STEPS.modelId)
        assertEquals(2, DebriefFactId.SLEEP.modelId)
        assertEquals(3, DebriefFactId.EXERCISE.modelId)
        val partial = catalog.copy(cards = catalog.cards.filter { card -> card.id != DebriefFactId.STEPS }.reversed())
        assertTrue(DebriefOrganizer.prompt(partial).contains("Allowed IDs: 3,2"))
        val result = DebriefOrganizer.resolve(partial, "[2,3]")
        assertEquals(DebriefStatus.MODEL_ORGANIZED, result.status)
        assertEquals(listOf(DebriefFactId.SLEEP, DebriefFactId.EXERCISE), result.selectedIds)
        assertEquals(DebriefStatus.MODEL_OUTPUT_REJECTED, DebriefOrganizer.resolve(partial, "[1,2]").status)
    }

    @Test
    fun `all two-category subsets reject an unoffered identifier`() {
        for (excluded in DebriefFactId.entries) {
            val partial = catalog.copy(cards = catalog.cards.filter { card -> card.id != excluded })
            val offeredIds = partial.cards.joinToString(",") { card -> card.id.modelId.toString() }
            assertEquals(DebriefStatus.MODEL_ORGANIZED, DebriefOrganizer.resolve(partial, "[$offeredIds]").status)
            assertEquals(DebriefStatus.MODEL_OUTPUT_REJECTED,
                DebriefOrganizer.resolve(partial, "[${excluded.modelId},${partial.cards.first().id.modelId}]").status)
        }
    }

    @Test
    fun `prompt enumerates exactly the valid permutations of available cards`() {
        for (candidate in listOf(catalog, catalog.copy(cards = catalog.cards.takeLast(2)))) {
            val options = Regex("""\[[1-3],[1-3]\]""").findAll(DebriefOrganizer.prompt(candidate))
                .map { match -> match.value }.toList()
            assertEquals(candidate.cards.size * (candidate.cards.size - 1), options.size)
            assertEquals(options.size, options.distinct().size)
            options.forEach { option ->
                assertEquals(DebriefStatus.MODEL_ORGANIZED, DebriefOrganizer.resolve(candidate, option).status)
            }
        }
    }
}
