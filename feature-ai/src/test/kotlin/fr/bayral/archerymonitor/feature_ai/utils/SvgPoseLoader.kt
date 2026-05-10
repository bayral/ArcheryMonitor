package fr.bayral.archerymonitor.feature_ai.utils

import fr.bayral.archerymonitor.core.interfaces.Landmark
import fr.bayral.archerymonitor.core.interfaces.PoseResult
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Utility to load MediaPipe PoseResults from SVG files.
 * Each <circle> or element with an 'id' matching a MediaPipe landmark index (0-32)
 * will be converted into a Landmark.
 */
object SvgPoseLoader {

    fun loadFromSvg(inputStream: InputStream, timestamp: Long = 0): PoseResult {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        
        val svgElement = doc.documentElement
        val viewBox = svgElement.getAttribute("viewBox").split(" ")
        val viewW = if (viewBox.size == 4) viewBox[2].toFloat() else 100f
        val viewH = if (viewBox.size == 4) viewBox[3].toFloat() else 100f

        val landmarks = MutableList(33) { Landmark(0f, 0f, 0f, 0f, 0f) }
        
        // Helper to extract landmarks from elements with numeric IDs
        fun processNodeList(nodes: org.w3c.dom.NodeList) {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node is Element) {
                    val id = node.getAttribute("id")
                    val index = id.toIntOrNull()
                    if (index != null && index in 0..32) {
                        val cx = node.getAttribute("cx").toFloatOrNull() ?: node.getAttribute("x").toFloatOrNull() ?: 0f
                        val cy = node.getAttribute("cy").toFloatOrNull() ?: node.getAttribute("y").toFloatOrNull() ?: 0f
                        
                        // Normalize to 0..1 based on viewBox
                        landmarks[index] = Landmark(
                            x = cx / viewW,
                            y = cy / viewH,
                            z = 0f,
                            visibility = 1f,
                            presence = 1f
                        )
                    }
                    if (node.hasChildNodes()) {
                        processNodeList(node.childNodes)
                    }
                }
            }
        }

        processNodeList(doc.getElementsByTagName("*"))

        return PoseResult(
            landmarks = landmarks,
            worldLandmarks = landmarks, // Simplified for testing
            timestamp = timestamp
        )
    }

    /**
     * Loads a sequence of frames if the SVG uses groups <g> with IDs like "frame_0", "frame_1".
     */
    fun loadSequenceFromSvg(inputStream: InputStream): List<PoseResult> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        
        val svgElement = doc.documentElement
        val viewBox = svgElement.getAttribute("viewBox").split(" ")
        val viewW = if (viewBox.size == 4) viewBox[2].toFloat() else 100f
        val viewH = if (viewBox.size == 4) viewBox[3].toFloat() else 100f

        val frames = mutableListOf<PoseResult>()
        val groups = doc.getElementsByTagName("g")
        
        var frameFound = false
        for (i in 0 until groups.length) {
            val group = groups.item(i) as Element
            val id = group.getAttribute("id")
            if (id.startsWith("frame_")) {
                frameFound = true
                val landmarks = MutableList(33) { Landmark(0f, 0f, 0f, 0f, 0f) }
                processGroup(group, landmarks, viewW, viewH)
                frames.add(PoseResult(
                    landmarks = landmarks,
                    worldLandmarks = landmarks,
                    timestamp = i.toLong() * 33333 // Mock 30fps
                ))
            }
        }
        
        if (!frameFound) {
            // If no <g id="frame_N"> found, treat the whole SVG as a single frame
            val landmarks = MutableList(33) { Landmark(0f, 0f, 0f, 0f, 0f) }
            processGroup(doc.documentElement, landmarks, viewW, viewH)
            frames.add(PoseResult(landmarks, landmarks, 0))
        }

        return frames
    }

    private fun processGroup(group: Element, landmarks: MutableList<Landmark>, viewW: Float, viewH: Float) {
        val nodes = group.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as Element
            val id = node.getAttribute("id")
            val index = id.toIntOrNull()
            if (index != null && index in 0..32) {
                val cx = node.getAttribute("cx").toFloatOrNull() ?: node.getAttribute("x").toFloatOrNull() ?: 0f
                val cy = node.getAttribute("cy").toFloatOrNull() ?: node.getAttribute("y").toFloatOrNull() ?: 0f
                landmarks[index] = Landmark(cx / viewW, cy / viewH, 0f, 1f, 1f)
            }
        }
    }
}
