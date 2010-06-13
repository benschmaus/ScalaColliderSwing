/*
 *  ScalaColliderSwing.scala
 *  (ScalaCollider-Swing)
 *
 *  Copyright (c) 2008-2010 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 *
 *
 *  Changelog:
 */

package de.sciss.synth.swing

import java.awt.EventQueue
import java.io.File
import de.sciss.synth.{ BootingServer, Server, ServerOptionsBuilder }
import actors.DaemonActor

/**
 *    @version 0.14, 09-Jun-10
 */
object ScalaColliderSwing {
   val name          = "ScalaCollider-Swing"
   val version       = 0.16
   val copyright     = "(C)opyright 2008-2010 Hanns Holger Rutz"
   def versionString = (version + 0.001).toString.substring( 0, 4 )

   class REPLSupport( ssp: ServerStatusPanel, ntp: NodeTreePanel ) {
      var s : Server = null
      val so = new ServerOptionsBuilder()
      private val sync = new AnyRef
      private var booting: BootingServer = null

      // ---- constructor ----
      {
         Runtime.getRuntime().addShutdownHook( new Thread { override def run = shutDown })
         ssp.bootAction = Some( () => boot )
      }

      def boot { sync.synchronized {
         shutDown
         booting = Server.boot( options = so.build )
         booting.addListener {
            case BootingServer.Running( srv ) => {
               sync.synchronized {
                  booting = null
                  s = srv
                  ntp.server = Some( srv )
               }
            }
         }
         ssp.booting = Some( booting )
         booting.start
      }}

      private def shutDown { sync.synchronized {
         if( (s != null) && (s.condition != Server.Offline) ) {
            s.quit
            s = null
         }
         if( booting != null ) {
            booting.abort
            booting = null
         }
      }}
   }

   def main( args: Array[ String ]) {
//      EventQueue.invokeLater( this )
//      start
      defer {
         val ssp  = new ServerStatusPanel()
         val sspw = ssp.makeWindow
         val ntp  = new NodeTreePanel()
         val ntpw = ntp.makeWindow
         val so   = new ServerOptionsBuilder()
         val repl = new REPLSupport( ssp, ntp )
         val sif  = new ScalaInterpreterFrame( repl )
         ntpw.setLocation( sspw.getX, sspw.getY + sspw.getHeight + 32 )
         sspw.setVisible( true )
         ntpw.setVisible( true )
         sif.setLocation( sspw.getX + sspw.getWidth + 32, sif.getY )
         sif.setVisible( true )
//         val booting = Server.boot()
//         booting.addListener {
//            case BootingServer.Running( s ) => {
//               ssp.server = Some( s )
//               ntp.server = Some( s )
////               sif.withInterpreter( _.bind( "replSupport", classOf[ REPLSupport ].getName, s ))
//               sif.withInterpreter( _.bind( "s", classOf[ Server ].getName, s ))
//
//            }
//         }
//         booting.start
      }
   }

   private def defer( thunk: => Unit ) {
      EventQueue.invokeLater( new Runnable {
         def run = thunk
      })
   }
}
