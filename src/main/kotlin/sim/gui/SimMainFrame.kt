package sim.gui

import java.awt.*
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.Duration
import java.util.*
import javax.swing.*
import javax.swing.event.MouseInputAdapter


/**
 * @author  alexander_larsson, last modified by $Author$
 * @version SVN $Revision$ $Date$
 * @since
 */
class SimMainFrame(title: String) : JFrame(title) {
    private val tickDelta = Duration.ofMillis(10).toMillis()
    private val numShutters = 10
    private val content: JPanel

    private val upButtons = ArrayList<Button>(numShutters)
    private val downButtons = ArrayList<Button>(numShutters)
    private val shutters = ArrayList<Shutter>(numShutters)

    @Volatile
    private var doRun = true

    init {
        // init buttons states and shutters
        for (i in 0 until numShutters) {
            upButtons.add(Button())
            downButtons.add(Button())
            shutters.add(Shutter())
        }

        content = JPanel(GridLayout(1, numShutters, 0, 0))
        content.background = Color.LIGHT_GRAY
    }

    fun initShuttersGui() {
        val arrowUp = '\u25B2'.toString()
        val arrowDown = '\u25BC'.toString()

        for (i in 0 until numShutters) {

            val upButton = upButtons[i]
            val downButton = downButtons[i]
            val shutter = shutters[i]

            val label = JLabel(i.toString())
            label.alignmentX = Component.CENTER_ALIGNMENT

            val progressBar = MyProgressBar()

            val up = JButton(arrowUp)
            up.alignmentX = Component.CENTER_ALIGNMENT
            up.addMouseListener(object : MouseInputAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    upButton.press()
                }

                override fun mouseReleased(e: MouseEvent) {
                    upButton.release()
                }
            })

            val down = JButton(arrowDown)
            down.alignmentX = Component.CENTER_ALIGNMENT
            down.addMouseListener(object : MouseInputAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    downButton.press()
                }

                override fun mouseReleased(e: MouseEvent) {
                    downButton.release()
                }
            })

            shutter.setProgressListener { progress -> SwingUtilities.invokeLater { progressBar.setValue(progress) } }

            val p = JPanel()
            p.layout = BoxLayout(p, BoxLayout.PAGE_AXIS)
            p.border = BorderFactory.createLineBorder(Color.BLACK)
            p.add(label)
            p.add(up)
            p.add(down)
            p.add(progressBar)
            content.add(p)

            this.setSize(600, 200)
            this.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            this.addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    doRun = false
                }
            })
            this.contentPane = content
            this.isVisible = true
        }
    }

    fun doRun() {
        var lastTickTime: Long = 0

        while (doRun) {
            val currentTime = System.currentTimeMillis()
            var doTick = false
            if (currentTime - lastTickTime > tickDelta) {
                lastTickTime = currentTime
                doTick = true
            }
            for (i in 0 until numShutters) {
                val shutter = shutters[i]
                if (!downButtons[i].isPressed && !upButtons[i].isPressed) { // no button is pressed
                    // do nothing
                } else if (upButtons[i].isPressed && !downButtons[i].isPressed) { // up button is pressed
                    val pressId = upButtons[i].pressId
                    if (upButtons[i].holdDuration > Duration.ofSeconds(1).toMillis()) {
                        shutters.stream().forEach { it -> it.moveUp(pressId) }
                    } else {
                        shutter.moveUp(pressId)
                    }
                } else if (downButtons[i].isPressed && !upButtons[i].isPressed) { // down button is pressed
                    val pressId = downButtons[i].pressId
                    if (downButtons[i].holdDuration > Duration.ofSeconds(1).toMillis()) {
                        shutters.stream().forEach { it -> it.moveDown(pressId) }
                    } else {
                        shutter.moveDown(pressId)
                    }
                } else if (downButtons[i].isPressed && upButtons[i].isPressed) { // both buttons are pressed
                    shutter.stop()
                }

                if (doTick) {
                    shutter.tick()
                }
            }
        }
    }

    class Shutter {
        private var status = ShutterStatus.IDLE
        private var position = 0.0 // 0.0 is up 1.0 is down
        private var progressListener: ((Double) -> Unit)? = null
        private var moveUpPressId: Long = -1
        private var moveDownPressId: Long = -1

        fun moveUp(pressId: Long) {
            if (moveUpPressId == pressId) {
                return
            }

            if (status != ShutterStatus.IDLE) {
                stop()
            } else {
                status = ShutterStatus.MOVING_UP
            }
            moveUpPressId = pressId
        }

        fun moveDown(pressId: Long) {
            if (moveDownPressId == pressId) {
                return
            }

            if (status != ShutterStatus.IDLE) {
                stop()
            } else {
                status = ShutterStatus.MOVING_DOWN
            }
            moveDownPressId = pressId
        }

        fun stop() {
            status = ShutterStatus.IDLE
        }

        fun tick() {
            when (status) {
                ShutterStatus.MOVING_UP -> doMoveUp()
                ShutterStatus.MOVING_DOWN -> doMoveDown()
                else -> {
                } // do nothing
            }
        }

        private fun doMoveDown() {
            if (position < 1.0) {
                position += speedPerTick
            } else {
                position = 1.0
                stop()
            }
            progressListener?.invoke(position)
        }

        private fun doMoveUp() {
            if (position > 0.0) {
                position -= speedPerTick
            } else {
                position = 0.0
                stop()
            }
            progressListener?.invoke(position)
        }

        fun setProgressListener(progressListener: (Double) -> Unit) {
            this.progressListener = progressListener
        }

        companion object {
            private val speedPerTick = 0.005
        }
    }

    enum class ShutterStatus {
        MOVING_UP,
        IDLE,
        MOVING_DOWN
    }

    class Button {
        @Volatile
        var isPressed = false
            private set
        @Volatile
        var pressId: Long = 0
            private set
        @Volatile private var holdStart: Long = 0

        val holdDuration: Long
            get() = if (isPressed) {
                System.currentTimeMillis() - holdStart
            } else {
                0
            }

        fun press() {
            if (!isPressed) {
                holdStart = System.currentTimeMillis()
                isPressed = true
            }
        }

        fun release() {
            isPressed = false
            holdStart = 0
            pressId++
        }
    }


    class MyProgressBar : JPanel() {
        private var value = 0.0

        fun setValue(value: Double) {
            when {
                value < 0.0 -> this.value = 0.0
                value > 1.0 -> this.value = 1.0
                else -> this.value = value
            }
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            val size = size
            g2d.color = Color.DARK_GRAY
            g2d.fillRect(0, 0, size.width, (size.height * value).toInt())
        }
    }
}

fun main(args: Array<String>) {

    // init and start gui
    UIManager.getLookAndFeelDefaults()
            .put("defaultFont", Font("Arial Unicode MS", Font.PLAIN, 14))

    val mainFrame = SimMainFrame("FSM")

    SwingUtilities.invokeLater { mainFrame.initShuttersGui() }

    // blocks until the gui is ended
    mainFrame.doRun()

    System.exit(0)
}