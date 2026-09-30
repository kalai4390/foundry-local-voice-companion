package dev.daylight.core

enum class DebriefFactId(val modelId: Int) { STEPS(1), SLEEP(2), EXERCISE(3) }

data class DebriefCard(val id: DebriefFactId, val summary: String, val evidence: EvidenceFact)

data class DebriefCatalog(
    val cards: List<DebriefCard>,
    val context: String,
    val availability: SnapshotAvailability
) {
    init {
        require(cards.size <= DebriefFactId.entries.size && cards.map { it.id }.distinct().size == cards.size)
    }

    val selectionSize: Int get() = minOf(2, cards.size)
    val canOrganize: Boolean get() = cards.size >= 2
}

enum class DebriefStatus { MODEL_ORGANIZED, COMPUTED, MODEL_OUTPUT_REJECTED, NO_RECENT_RECORDS }

data class DebriefResult(val reply: Reply, val selectedIds: List<DebriefFactId>, val status: DebriefStatus)

object DebriefOrganizer {
    const val PROMPT_VERSION = "evidence-card-options-v3"
    const val SYSTEM_PROMPT = "You organize a nonclinical recorded-week recap. The supplied cards are already calculated. " +
        "Select exactly two distinct numeric IDs from the supplied cards, in a useful reading order. " +
        "Prefer cards with more observed days; a recorded difference is not a medical finding. " +
        "Your entire reply must be one of the exact JSON arrays in VALID RESPONSES. " +
        "The measurements in the facts are not IDs. Do not write names, explanations, advice or markdown."

    private val selectionPattern = Regex("""\[[ \t\r\n]*([1-3])[ \t\r\n]*,[ \t\r\n]*([1-3])[ \t\r\n]*\]""")

    fun prompt(catalog: DebriefCatalog): String {
        require(catalog.canOrganize) { "At least two observed categories are needed for model organization." }
        val options = catalog.cards.flatMap { first ->
            catalog.cards.filter { second -> second.id != first.id }.map { second ->
                "[${first.id.modelId},${second.id.modelId}]"
            }
        }
        return "Available calculated cards:\n" + catalog.cards.joinToString("\n") { card ->
            "ID ${card.id.modelId} (${card.id.name}): ${card.summary}"
        } + "\nAllowed IDs: ${catalog.cards.joinToString(",") { card -> card.id.modelId.toString() }}." +
            "\nVALID RESPONSES: ${options.joinToString(" or ")}.\nCopy exactly one valid array. Nothing else."
    }

    fun resolve(catalog: DebriefCatalog, modelOutput: String? = null): DebriefResult {
        if (catalog.cards.isEmpty()) {
            return DebriefResult(
                Reply(Topic.BRIEF, "No completed-week recap can be made. ${catalog.availability.detail} " +
                    "No language model was used. No records were invented."),
                emptyList(), DebriefStatus.NO_RECENT_RECORDS
            )
        }
        val proposed = if (catalog.canOrganize && modelOutput != null && modelOutput.length <= 64) {
            selectionPattern.matchEntire(modelOutput.trim(' ', '\t', '\r', '\n'))?.groupValues?.drop(1)
                ?.map { identifier -> identifier.toInt() }
        } else null
        val offered = catalog.cards.associateBy { card -> card.id.modelId }
        val accepted = proposed != null && proposed.size == catalog.selectionSize &&
            proposed.distinct().size == proposed.size && proposed.all { identifier -> identifier in offered }
        val selected = if (accepted) proposed!!.map { identifier -> offered.getValue(identifier) }
            else catalog.cards.take(catalog.selectionSize)
        val status = when {
            accepted -> DebriefStatus.MODEL_ORGANIZED
            catalog.canOrganize && modelOutput != null -> DebriefStatus.MODEL_OUTPUT_REJECTED
            else -> DebriefStatus.COMPUTED
        }
        val introduction = when (status) {
            DebriefStatus.MODEL_ORGANIZED -> "Experimental model-organized recap; Ami calculated every fact. Selection is not medical prioritization."
            DebriefStatus.MODEL_OUTPUT_REJECTED -> "Computed recap. The model's selection failed validation; none of its generated text is shown."
            else -> "Computed recap. No language model was used."
        }
        val text = "$introduction\n${catalog.context}\n" +
            selected.joinToString("\n") { card -> card.summary } +
            "\nMissing records are not zero or inactivity. Sleep-session duration is not physiological time asleep. " +
            "This short recap includes ${selected.size} of ${catalog.cards.size} recently observed categories. " +
            "Inspect Evidence for all categories and exact dates. No advice or action was generated."
        return DebriefResult(
            Reply(Topic.BRIEF, text, selected.map { card -> card.evidence }),
            selected.map { card -> card.id }, status
        )
    }
}
