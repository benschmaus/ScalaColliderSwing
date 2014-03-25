/*
 *  Main.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v2+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth.swing

import de.sciss.desktop.impl.{WindowHandlerImpl, SwingApplicationImpl, WindowImpl}
import de.sciss.desktop.{DialogSource, OptionPane, FileDialog, RecentFiles, KeyStrokes, LogPane, Desktop, Window, WindowHandler, Menu}
import javax.swing.{KeyStroke, UIManager, SwingUtilities}
import bibliothek.gui.dock.common.{MultipleCDockableLayout, MultipleCDockable, MultipleCDockableFactory, DefaultMultipleCDockable, EmptyMultipleCDockableFactory, CLocation, DefaultSingleCDockable, CControl}
import java.awt.GraphicsEnvironment
import scala.swing.{Action, Swing, Orientation, BoxPanel, Component, BorderPanel, ScrollPane, EditorPane}
import org.fit.cssbox.swingbox.BrowserPane
import org.fusesource.scalamd.Markdown
import bibliothek.gui.dock.common.theme.ThemeMap
import de.sciss.{scalainterpreter => si}
import scala.tools.nsc.interpreter.NamedParam
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import scala.util.control.NonFatal
import de.sciss.file._
import de.sciss.synth.Server
import scala.concurrent.Future
import bibliothek.gui.dock.dockable.IconHandling
import de.sciss.scalainterpreter.CodePane
import bibliothek.gui.dock.common.event.CFocusListener
import bibliothek.gui.dock.common.intern.CDockable
import java.io.{OutputStreamWriter, FileOutputStream}
import scala.util.{Success, Failure, Try}
import scala.swing.event.{WindowClosing, WindowClosed}

object Main extends SwingApplicationImpl("ScalaCollider") {
  type Document = TextViewDockable

  override lazy val windowHandler: WindowHandler = new WindowHandlerImpl(this, menuFactory) {
    override def usesInternalFrames: Boolean = false
  }

  private val bodyUrl = "https://raw2.github.com/Sciss/ScalaCollider/master/README.md"
  private val cssUrl  = "https://gist.github.com/andyferra/2554919/raw/2e66cabdafe1c9a7f354aa2ebf5bc38265e638e5/github.css"

  // is this the best way?
  private def readURL(url: String): String = io.Source.fromURL(url, "UTF-8").getLines().mkString("\n")

  private lazy val sp = new ServerStatusPanel

  private lazy val lg = LogPane(rows = 12)

  // private lazy val codePane = sip.codePane

  private lazy val repl: REPLSupport = new REPLSupport(sp, null)

  private lazy val intpFut: Future[Interpreter] = {
    val intpCfg = si.Interpreter.Config()
    intpCfg.imports = List(
      //         "Predef.{any2stringadd => _}",
      "scala.math._",                     // functions such as cos(), random, constants such as Pi
      "de.sciss.file._",                  // simple file path construction, constants such as userHome
      // "scalax.chart.api._",               // simple plotting
    // "scalax.chart._","scalax.chart.Charting._",  // for version 0.3.0
      "de.sciss.osc",                     // import osc namespace, e.g. osc.Message
      "de.sciss.osc.{TCP, UDP}",
      "de.sciss.osc.Dump.{Off, Both, Text}",
      "de.sciss.osc.Implicits._",
      "de.sciss.synth._",                     // main ScalaCollider stuff
      "de.sciss.synth.Ops._",                 // imperative resource commands
      "de.sciss.synth.swing.SynthGraphPanel._",
      "de.sciss.synth.swing.Implicits._",     // ScalaCollider swing extensions
      "de.sciss.synth.swing.AppFunctions._",  // ScalaCollider swing app extensions
      "de.sciss.synth.swing.Plotting._",      // ScalaCollider swing app extensions
      "de.sciss.synth.ugen._",                // UGens
      "replSupport._"                         // REPL bindings
    )
    // intpCfg.quietImports = false

    intpCfg.bindings = List(NamedParam("replSupport", repl))
    // intpCfg.out = Some(lp.writer)

    Interpreter(intpCfg)
  }

  private def initPrefs(): Unit = {
    def updateProgramPath(): Unit = {
      val file = Prefs.superCollider.getOrElse(Prefs.defaultSuperCollider)
      val path = if (file == Prefs.defaultSuperCollider) Server.defaultProgramPath else file.path
      repl.config.programPath = path
    }

    def updateAudioDevice(): Unit = {
      val audioDevice = Prefs.audioDevice.getOrElse(Prefs.defaultAudioDevice)
      val opt = if (audioDevice == Prefs.defaultAudioDevice) None else Some(audioDevice)
      repl.config.deviceName = opt
    }

    def updateNumOutputs(): Unit =
      repl.config.outputBusChannels = Prefs.audioNumOutputs.getOrElse(Prefs.defaultAudioNumOutputs)

    Prefs.superCollider   .addListener { case _ => updateProgramPath() }
    Prefs.audioDevice     .addListener { case _ => updateAudioDevice() }
    Prefs.audioNumOutputs .addListener { case _ => updateNumOutputs () }

    updateProgramPath()
    updateAudioDevice()
    updateNumOutputs ()
  }

  private class MainWindow extends WindowImpl {
    def handler: WindowHandler = Main.windowHandler
    title     = "ScalaCollider"
    // contents  = bp
    closeOperation = Window.CloseIgnore

    reactions += {
      case Window.Closing(_) => if (Desktop.mayQuit()) quit()
    }

    def init(c: Component): Unit = {
      contents  = c
      bounds    = GraphicsEnvironment.getLocalGraphicsEnvironment.getMaximumWindowBounds
      front()
    }
  }

  private lazy val frame = new MainWindow

  private lazy val hp: EditorPane = {
    val md    = readURL(bodyUrl)
    val css   = readURL(cssUrl )
    val html  = Markdown(md)

    val html1 = s"<html><head><style>$css</style></head><body>$html</body></html>"
    //    new EditorPane("text/html", "") {
    //      editable  = false
    //      editorKit = new SwingBoxEditorKit()
    //      text      = html1
    //    }

    new EditorPane("text/html", "") {
      override lazy val peer: BrowserPane = new BrowserPane with SuperMixin
      // text = html1
    }
  }

  class TextViewDockable(val view: TextView) extends
    DefaultMultipleCDockable(docFact, view.component.peer) {

    private var fntSizeAmt = 0

    private def updateFontSize(): Unit = {
      val ed    = view.editor.editor
      val fnt   = ed.getFont
      val scale = math.pow(1.08334, fntSizeAmt)
      // note: deriveFont _replaces_ the affine transform, does not concatenate it
      ed.setFont(fnt.deriveFont(AffineTransform.getScaleInstance(scale, scale)))
    }

    private def fontSizeReset(): Unit = {
      fntSizeAmt = 0
      updateFontSize()
    }

    def fontSizeChange(rel: Int): Unit =
      if (rel == 0) fontSizeReset() else {
        fntSizeAmt += rel
        updateFontSize()
      }

    def close(): Unit = {
      dockCtrl.removeDockable(this)
      documentHandler.removeDocument(this)
    }
  }

  private lazy val docFact: MultipleCDockableFactory[MultipleCDockable, MultipleCDockableLayout] =
    new EmptyMultipleCDockableFactory[MultipleCDockable] {
      def createDockable(): MultipleCDockable = newFile()
    }

  private lazy val dockCtrl: CControl = {
    val jf  = SwingUtilities.getWindowAncestor(frame.component.peer.getRootPane).asInstanceOf[javax.swing.JFrame]
    val res = new CControl(jf)
    val th  = res.getThemes
    th.select(ThemeMap.KEY_FLAT_THEME)
    res.addMultipleDockableFactory("de.sciss.synth.swing.TextView", docFact)
    //    res.addControlListener(new CControlListener {
    //      def opened (control: CControl, dockable: CDockable): Unit =
    //        println(s"CControlListener.opened ($control, $dockable")
    //      def closed (control: CControl , dockable: CDockable): Unit =
    //        println(s"CControlListener.closed ($control, $dockable")
    //      def added  (control: CControl  , dockable: CDockable): Unit =
    //        println(s"CControlListener.added  ($control, $dockable")
    //      def removed(control: CControl, dockable: CDockable): Unit =
    //        println(s"CControlListener.removed($control, $dockable")
    //    })

    //    res.addStateListener(new CDockableStateListener {
    //      def visibilityChanged(dockable: CDockable): Unit =
    //        println(s"CDockableStateListener.visibilityChanged($dockable")
    //
    //      def extendedModeChanged(dockable: CDockable, mode: ExtendedMode): Unit =
    //        println(s"CDockableStateListener.extendedModeChanged($dockable, $mode")
    //    })

    res.addFocusListener(new CFocusListener {
      def focusLost  (dockable: CDockable): Unit = dockable match {
        case tvd: TextViewDockable =>
          fileActions.foreach(_.view = None)
        case _ =>
      }

      def focusGained(dockable: CDockable): Unit = dockable match {
        case tvd: TextViewDockable =>
          val viewOpt = Some(tvd)
          fileActions.foreach(_.view = viewOpt)
        case _ =>
      }
    })

    res
  }

  override protected def init(): Unit = {
    try {
      UIManager.setLookAndFeel(Prefs.lookAndFeel.getOrElse(Prefs.defaultLookAndFeel).getClassName)
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }

    super.init()

    val bot   = new BoxPanel(Orientation.Horizontal)
    bot.contents += Swing.HGlue
    bot.contents += sp

    val bp = new BorderPanel {
      add(Component.wrap(dockCtrl.getContentArea), BorderPanel.Position.Center)
      add(bot, BorderPanel.Position.South)
    }

    lg.makeDefault()
    Console.setOut(lg.outputStream)
    Console.setErr(lg.outputStream)

    // lg.background = sif.pane.codePane.component.getBackground
    // lg.foreground = sif.pane.codePane.component.getForeground
    val lgd = new DefaultSingleCDockable("log", "Log", lg.component.peer)
    lgd.setLocation(CLocation.base().normalSouth(0.25))
    lgd.setTitleIconHandling(IconHandling.KEEP_NULL_ICON) // this must be called before setTitleIcon
    lgd.setTitleIcon(null)

    // hp.peer.setPage("http://www.sciss.de/scalaCollider")
    val hps   = new ScrollPane(hp)
    val hpd   = new DefaultSingleCDockable("help", "Help", hps.peer)
    hpd.setLocation(CLocation.base().normalNorth(0.65))
    hpd.setTitleIconHandling(IconHandling.KEEP_NULL_ICON) // this must be called before setTitleIcon
    hpd.setTitleIcon(null)

    dockCtrl.addDockable(lgd)
    // ctl.addDockable(spd)
    dockCtrl.addDockable(hpd)
    lgd.setVisible(true)
    // spd.setVisible(true)
    hpd.setVisible(true)

    frame.init(bp)

    initPrefs()
    newFile()
  }

  private var newFileCount = 0

  private def newFile(): MultipleCDockable = {
    newFileCount += 1
    createText(text0 = "", file = None)
  }

  private def createText(text0: String, file: Option[File]): MultipleCDockable = {
    val cnt   = newFileCount
    val cc    = CodePane.Config()
    cc.text   = text0
    val text  = TextView(intpFut, cc)
    text.file = file
    // val sid = docFact.create() new DefaultSingleCDockable("interpreter", "Interpreter", sip.component.peer)

    def mkTitle() = {
      val name = text.file.fold {
        if (cnt == 1) "Untitled" else s"Untitled $cnt"
      } { f =>
        f.base
      }
      if (text.dirty) s"*$name" else name
    }

    // val content   = text.component.peer
    val dock      = new TextViewDockable(text)
    dock.setLocation(CLocation.base().normalWest(0.6))

    def updateTitle(): Unit = dock.setTitleText(mkTitle())

    updateTitle()

    text.addListener {
      case TextView.DirtyChange(_) => updateTitle()
      case TextView.FileChange (_) => updateTitle()
    }
    dockCtrl.addDockable(dock)
    dock.setVisible(true)
    documentHandler.addDocument(dock)
    dock
  }

  private sealed trait UnsavedResult
  private case object UnsavedDiscard extends UnsavedResult
  private case object UnsavedCancel  extends UnsavedResult
  private case object UnsavedSave    extends UnsavedResult

  private def checkUnsaved(dock: TextViewDockable): Boolean = {
    if (!dock.view.dirty) return true

    warnUnsaved(dock) match {
      case UnsavedDiscard => true
      case UnsavedCancel  => false
      case UnsavedSave    => saveOrSaveAs(dock) match {
        case SaveResult(Success(_)) => true
        case _ => false
      }
    }
  }

  private def closeAll(): Boolean = documentHandler.documents.forall(checkUnsaved)

  private def warnUnsaved(dock: TextViewDockable): UnsavedResult = {
    val name = dock.view.file.fold {
      val s = dock.getTitleText
      if (s.startsWith("*")) s.substring(1) else s
    } (_.name)
    val msg = s"""<html><body><b>Save changes to document "$name" """ +
      "before closing?</b><p>If you don't save, changes will be permanently lost."
    val saveText    = if (dock.view.file.isDefined) "Save" else "Save As"
    val options     = Seq(saveText, "Cancel", "Close without Saving")
    val dlg = OptionPane(message = msg, optionType = OptionPane.Options.YesNoCancel,
      messageType = OptionPane.Message.Warning, entries = options, initial = Some(saveText))
    val idx = dlg.show(Some(frame)).id
    if (idx == 0) UnsavedSave else if (idx == 2) UnsavedDiscard else UnsavedCancel
  }

  private def queryOpenFile(): Unit = {
    val dlg = FileDialog.open()
    dlg.show(Some(frame)).foreach(checkOpenFile)
  }

  private def checkOpenFile(file: File): Unit = {
    val fileOpt = Some(file)
    val dockOpt: Option[Document] = documentHandler.documents.find(_.view.file == fileOpt)
    dockOpt.fold(openFile(file)) { dock =>
      dock.toFront()
    }
  }

  private def openFile(file: File): Unit =
    try {
      // val file = file0.getCanonicalFile
      val src = io.Source.fromFile(file)
      try {
        val text0 = src.mkString
        // XXX TODO: if the current window is empty and untitled, simply replace its content
        // (or close it)
        createText(text0, Some(file))
        _recent.add(file)
      } finally {
        src.close()
      }
    } catch {
      case NonFatal(e) => DialogSource.Exception(e -> s"Open '${file.name}'").show(Some(frame))
    }

  private def bootServer(): Unit =
    if (sp.booting.isEmpty && sp.server.isEmpty) rebootServer()

  private def rebootServer() :Unit =
    sp.peer.boot()

  private def serverMeters() :Unit = {
    import de.sciss.synth.swing.Implicits._
    sp.server.foreach { s =>
      val f = s.gui.meter()
      f.peer.setAlwaysOnTop(true)
    }
  }

  private def stop(): Unit = {
    import de.sciss.synth.Ops._
    sp.server.foreach(_.defaultGroup.freeAll())
  }

  private def clearLog(): Unit = lg.clear()

  private def dumpNodes(controls: Boolean): Unit = {
    import de.sciss.synth.Ops._
    sp.server.foreach(_.dumpTree(controls = controls))
  }

  private lazy val _recent = RecentFiles(Main.userPrefs("recent-docs"))(checkOpenFile)

  def openURL(url: String): Unit = hp.peer.setPage(url)

  private def lookUpHelp(): Unit = {
    println("(TODO: lookUpHelp()")
    val url = getClass.getResource("""/de/sciss/synth/Server.html""")
    if (url != null) {
      // --- version 1: shows as plain text ---
      // hp.peer.setPage(url)

      // --- version 2: kind of works (doesn't find css and scripts though) ---
      // hp.text = io.Source.fromURL(url, "UTF-8").mkString

      // --- version 3: creates new document (again doesn't find css and scripts) ---
      val doc = hp.editorKit.createDefaultDocument()
      hp.editorKit.read(url.openStream(), doc, 0)
      hp.peer.setDocument(doc)
    }
    else println("!Help sources not found!")
  }

  private trait FileAction {
    _: Action =>

    protected var _view: Option[TextViewDockable] = None
    def view = _view
    def view_=(value: Option[TextViewDockable]): Unit = if (_view != value) {
      _view   = value
      enabled = value.isDefined
      viewChanged()
    }

    def apply(): Unit = _view.foreach(perform)

    protected def viewChanged(): Unit = ()

    protected def perform(dock: TextViewDockable): Unit
  }

  private object ActionFileClose extends Action("Close") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + KeyEvent.VK_W)

    protected def perform(dock: TextViewDockable): Unit =
      if (checkUnsaved(dock)) dock.close()
  }

  private def save(text: TextView, file: File): SaveResult = {
    try {
      val fw = new FileOutputStream(file)
      val w  = new OutputStreamWriter(fw, "UTF-8")
      try {
        w.write(text.editor.editor.getText)
        w.flush()
      } finally {
        w.close()
      }
      _recent.add(file)
      text.file = Some(file)
      SaveResult(Success(file))
    } catch {
      case NonFatal(e) =>
        DialogSource.Exception(e -> s"Save ${file.name}").show(Some(frame))
        SaveResult(Failure(e))
    }
  }

  private sealed trait SaveOrCancelResult
  private case object SaveCancel extends SaveOrCancelResult
  private case class SaveResult(result: Try[File]) extends SaveOrCancelResult

  private def saveOrSaveAs(dock: TextViewDockable): SaveOrCancelResult =
    dock.view.file.fold(saveAs(dock)) { f =>
      save(dock.view, f)
    }

  private def saveAs(dock: TextViewDockable): SaveOrCancelResult = {
    val dlg = FileDialog.save(init = dock.view.file)
    dlg.show(Some(frame)).fold(SaveCancel: SaveOrCancelResult) { f =>
    //        val ok = !f.exists() || {
    //          val msg  = s"""<html><body><b>A file named "${f.name}" already exists. Do you want to replace it?</b><p>""" +
    //           f.parentOption.fold("")(p => s"""The file already exists in "${p.name}". """) +
    //           "Replacing it will overwrite its contents.</body></html>"
    //          val dlg1 = OptionPane.confirmation(message = msg, optionType = OptionPane.Options.OkCancel,
    //            messageType = OptionPane.Message.Warning)
    //          dlg1.show(Some(frame)) == OptionPane.Result.Ok
    //        }

    // the "sure-to-replace" check is already performed by the file dialog
      save(dock.view, f)
    }
  }

  private object ActionFileSave extends Action("Save") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + KeyEvent.VK_S)

    protected def perform(dock: TextViewDockable): Unit = saveOrSaveAs(dock)
  }

  private object ActionFileSaveAs extends Action("Save As...") with FileAction {
    accelerator = Some(KeyStrokes.menu1 + KeyStrokes.shift + KeyEvent.VK_S)

    protected def perform(dock: TextViewDockable): Unit = saveAs(dock)
  }

  private lazy val actionEnlargeFont = new ActionFontSize("Enlarge Font"    , KeyStrokes.menu1 + KeyEvent.VK_PLUS ,  1)
  private lazy val actionShrinkFont  = new ActionFontSize("Shrink Font"     , KeyStrokes.menu1 + KeyEvent.VK_MINUS, -1)
  private lazy val actionResetFont   = new ActionFontSize("Reset Font Size" , KeyStrokes.menu1 + KeyEvent.VK_0    ,  0)

  private class ActionFontSize(text: String, shortcut: KeyStroke, amount: Int )
    extends Action(text) with FileAction {

    accelerator = Some(shortcut)

    protected def perform(dock: TextViewDockable): Unit = dock.fontSizeChange(amount)
  }

  private lazy val fileActions = List(ActionFileClose, ActionFileSave, ActionFileSaveAs,
    actionEnlargeFont, actionShrinkFont, actionResetFont)

  protected lazy val menuFactory: Menu.Root = {
    import Menu._
    import KeyStrokes._
    import KeyEvent._
    import de.sciss.synth.swing.{Main => App}

    val itAbout = Item.About(App) {
      val html =
        s"""<html><center>
           |<font size=+1><b>About $name</b></font><p>
           |Copyright (c) 2008&ndash;2014 Hanns Holger Rutz. All rights reserved.<p>
           |This software is published under the GNU General Public License v2+
           |""".stripMargin
      OptionPane.message(message = new javax.swing.JLabel(html)).show(Some(frame))
    }
    val itPrefs = Item.Preferences(App)(ActionPreferences())
    val itQuit  = Item.Quit(App)

    Desktop.addQuitAcceptor(closeAll())

    val gFile = Group("file", "File")
      .add(Item("new" )("New"     -> (menu1 + VK_N))(newFile()))
      .add(Item("open")("Open..." -> (menu1 + VK_O))(queryOpenFile()))
      .add(_recent.menu)
      .addLine()
      .add(Item("close"  , ActionFileClose ))
      .add(Item("close-all")("Close All" -> (menu1 + shift + VK_W))(closeAll()))
      .add(Item("save"   , ActionFileSave  ))
      .add(Item("save-as", ActionFileSaveAs))

    if (itQuit.visible) gFile.addLine().add(itQuit)

    val gEdit = Group("edit", "Edit")
      .add(Item("undo", proxy("Undo" -> (menu1 + VK_Z))))
      .add(Item("redo", proxy("Redo" -> (menu1 + shift + VK_Z))))

    if (itPrefs.visible /* && Desktop.isLinux */) gEdit.addLine().add(itPrefs)

    val gView = Group("view", "View")
      .add(Item("inc-font",   actionEnlargeFont))
      .add(Item("dec-font",   actionShrinkFont ))
      .add(Item("reset-font", actionResetFont  ))

    val gActions = Group("actions", "Actions")
      .add(Item("boot-server"   )("Boot Server"       -> (menu1 + VK_B))(bootServer()))
      .add(Item("reboot-server" )("Reboot Server"                      )(rebootServer()))
      .add(Item("server-meters" )("Show Server Meter" -> (menu1 + VK_M))(serverMeters()))
      .add(Item("dump-tree"     )("Dump Node Tree"               -> (menu1         + VK_T))(dumpNodes(controls = false)))
      .add(Item("dump-tree-ctrl")("Dump Node Tree with Controls" -> (menu1 + shift + VK_T))(dumpNodes(controls = true )))
      .addLine()
      .add(Item("stop")("Stop" -> (menu1 + VK_PERIOD))(stop()))
      .addLine()
      .add(Item("clear-log")("Clear Log Window" -> (menu1 + shift + VK_P))(clearLog()))

    val gHelp = Group("help", "Help")
      .add(Item("help-for-cursor")("Look up Documentation for Cursor" -> (menu1 + VK_D))(lookUpHelp()))

    if (itAbout.visible) gHelp.addLine().add(itAbout)

    Root().add(gFile).add(gEdit).add(gView).add(gActions).add(gHelp)
  }
}
