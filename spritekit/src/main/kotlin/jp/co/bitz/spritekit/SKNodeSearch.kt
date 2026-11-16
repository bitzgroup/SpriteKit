package jp.co.bitz.spritekit

/**
 * Every node [name] finds, searching from this node — Apple's `subscript(_:)` (`node["//enemy"]`).
 * See [SKNode.childNode] for the search syntax.
 */
public operator fun SKNode.get(name: String): List<SKNode> = searchNodes(name)

/**
 * Runs Apple's node-search syntax (verified on iOS/tvOS simulators) from this node:
 *
 * - A plain name matches this node's direct children. `*` matches any run of characters and
 *   `[ab]`/`[a-z]` one character from a set; a pattern must match the whole name, and a node with
 *   no name is treated as having an empty one (so `*` matches it).
 * - `/` separates steps: `a/b` is each child `b` of each child `a`.
 * - `//` before a step searches the whole subtree for it instead of only the children. Each
 *   node's matching children come before the matches deeper down: a node's children are listed
 *   first, then each child is searched in turn.
 * - A leading `/` or `//` starts at the root of this node's tree instead of this node. The root's
 *   own name is never matched.
 * - `.` is the node itself and `..` its parent (a root's `..` is the root itself).
 *
 * A search string with no steps (`"/"`, `"//"`) matches nothing.
 *
 * @throws IllegalArgumentException if [query] is empty — Apple raises an exception for it too.
 */
internal fun SKNode.searchNodes(query: String): List<SKNode> {
    require(query.isNotEmpty()) { "The search string is empty." }
    val root = generateSequence(this) { it.parent }.last()
    val (start, path, leadingRecursive) =
        when {
            query.startsWith("//") -> Triple(root, query.substring(2), true)
            query.startsWith("/") -> Triple(root, query.substring(1), false)
            else -> Triple(this, query, false)
        }
    var current = listOf(start)
    var recursive = leadingRecursive
    var anyStep = false
    for (segment in path.split('/')) {
        if (segment.isEmpty()) {
            recursive = true
            continue
        }
        val stepRecursive = recursive
        current = current.flatMap { it.searchStep(segment, stepRecursive) }
        recursive = false
        anyStep = true
    }
    return if (anyStep) current else emptyList()
}

private fun SKNode.searchStep(
    pattern: String,
    recursive: Boolean,
): List<SKNode> =
    when (pattern) {
        "." -> if (recursive) listOf(this) + descendantsInPreorder() else listOf(this)
        ".." -> listOf(parent ?: this)
        else -> {
            val regex = namePatternRegex(pattern)
            if (recursive) recursiveMatches(regex) else children.filter { regex.matches(it.name.orEmpty()) }
        }
    }

private fun SKNode.recursiveMatches(regex: Regex): List<SKNode> =
    children.filter { regex.matches(it.name.orEmpty()) } + children.flatMap { it.recursiveMatches(regex) }

private fun SKNode.descendantsInPreorder(): List<SKNode> = children.flatMap { listOf(it) + it.descendantsInPreorder() }

/** Turns a name pattern (`*`, `[...]`, everything else literal) into a whole-name [Regex]. */
internal fun namePatternRegex(pattern: String): Regex {
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        val classEnd = if (c == '[') pattern.indexOf(']', i + 1) else -1
        when {
            c == '*' -> out.append(".*")
            classEnd > i + 1 -> {
                out.append('[').append(pattern.substring(i + 1, classEnd).replace("\\", "\\\\")).append(']')
                i = classEnd
            }
            else -> out.append(Regex.escape(c.toString()))
        }
        i++
    }
    return Regex(out.toString())
}
