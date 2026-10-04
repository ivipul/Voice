package voice.core.data

private val LEADING_NUMBER = Regex("""^\s*(\d{1,3})\s*[.)\-:_]\s+""")

/** The "N. " series number a book name starts with, e.g. 2 for "2. Carl's Doomsday Scenario". */
public fun leadingBookNumber(name: String): Int? =
  LEADING_NUMBER.find(name)?.groupValues?.get(1)?.toIntOrNull()

public fun withoutBookNumber(name: String): String = name.replace(LEADING_NUMBER, "")

/** [name] with its "N. " prefix, or unchanged when there is no [number] or [name] already starts with one. */
public fun numberedBookName(
  name: String,
  number: Int?,
): String = if (number == null || leadingBookNumber(name) != null) name else "$number. $name"

/** The number from the series part tag when it has one, else from a leading number in the file name. */
public fun resolveBookNumber(
  partTag: String?,
  fileName: String?,
): Int? = partTag?.trim()?.takeWhile(Char::isDigit)?.toIntOrNull()
  ?: fileName?.let(::leadingBookNumber)
