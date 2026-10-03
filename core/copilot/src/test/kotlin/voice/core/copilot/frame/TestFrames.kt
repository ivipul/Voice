package voice.core.copilot.frame

internal fun plannedCharacter(
  character: String? = null,
  noun: String = "man",
  pron: String = "he",
  framing: String = "full_body",
  prominence: Int? = 75,
  box: List<Double> = listOf(0.1, 0.2, 0.6, 0.95),
  scale: String? = null,
) = PlannedCharacter(
  character = character,
  noun = noun,
  pron = pron,
  description = "a tall man in a worn leather jacket",
  placement = "standing beside the bare tree",
  framing = framing,
  prominencePct = prominence,
  lowAngle = true,
  pose = "He reaches both arms up toward the branches",
  gaze = "His head is turned in profile toward the branches, face not covered by his arms",
  scale = scale,
  maskBox = box,
)

internal fun framePlan(vararg characters: PlannedCharacter, establishing: Boolean = false) = FramePlan(
  palette = "the same cold blue night palette and sodium-orange lamp glow",
  location = "Empty residential street at night in deep winter, a bare tree beside the sidewalk",
  viewpoint = "The camera is at street level and tilts up in a dramatic low-angle shot",
  roomFor = "Leave an empty stretch of sidewalk beside the tree",
  establishing = establishing,
  characters = characters.toList(),
)
